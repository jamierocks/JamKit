/*
 * Copyright 2011-2013 Tyler Blair. All rights reserved.
 * Copyright 2015 Jamie Mansfield.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */
package uk.jamierocks.jamkit.stats;

import com.google.gson.Gson;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.scheduler.BukkitTask;
import uk.jamierocks.jamkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

public class Metrics {

    /**
     * The current revision number
     */
    private final static int REVISION = 7;

    /**
     * The base url of the metrics domain
     */
    private static final String BASE_URL = "http://report.mcstats.org";

    /**
     * The url used to report a server's status
     */
    private static final String REPORT_URL = "/plugin/%s";

    /**
     * Interval of time to ping (in minutes)
     */
    private static final int PING_INTERVAL = 15;

    /**
     * The {@link Plugin} this metrics submits for
     */
    private final Plugin plugin;

    /**
     * The {@link Logger} that metrics uses
     */
    private final Logger logger = Logger.getLogger("Metrics");

    /**
     * All of the custom graphs to submit to metrics
     */
    private final Set<Graph> graphs = Collections.synchronizedSet(new HashSet<Graph>());

    /**
     * The {@link Plugin} configuration file
     */
    private final YamlConfiguration configuration;

    /**
     * The {@link Plugin} configuration file
     */
    private final File configurationFile;

    /**
     * Unique server id
     */
    private final String guid;

    /**
     * Debug mode
     */
    private final boolean debug;

    /**
     * Lock for synchronization
     */
    private final Object optOutLock = new Object();

    /**
     * The scheduled task
     */
    private volatile BukkitTask task = null;

    public Metrics(final Plugin plugin) {
        Validate.notNull(plugin, "Plugin cannot be null");

        this.plugin = plugin;
        this.logger.setParent(plugin.getLogger());

        // load the config
        configurationFile = getConfigFile();
        configuration = YamlConfiguration.loadConfiguration(configurationFile);

        // add some defaults
        configuration.addDefault("opt-out", false);
        configuration.addDefault("guid", UUID.randomUUID().toString());
        configuration.addDefault("debug", false);

        // Do we need to create the file?
        if ((configuration.get("guid") == null) || configuration.getString("guid").equals("")) {
            configuration.options().header("http://mcstats.org").copyDefaults(true);
            try {
                configuration.save(configurationFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Oh noes... Something broke.", ex);
            }
        }

        // Load the guid then
        guid = configuration.getString("guid");
        debug = configuration.getBoolean("debug", false);
    }

    /**
     * Construct and create a {@link Graph} that can be used to separate specific plotters to their own graphs on the
     * * metrics
     * website. Plotters can be added to the {@link Graph} object returned.
     *
     * @param name The name of the graph
     * @return {@link Graph} object created. Will never return NULL under normal circumstances unless bad parameters are
     * * given
     */
    public Graph createGraph(final String name) {
        Validate.notNull(name, "Graph name cannot be null");

        // Construct the graph object
        final Graph graph = new Graph(name);

        // Now we can add our graph
        graphs.add(graph);

        // and return back
        return graph;
    }

    /**
     * Add a {@link Graph} object to BukkitMetrics that represents data for the plugin that should be sent to the 
     * * backend
     *
     * @param graph The name of the {@link Graph}
     */
    public void addGraph(final Graph graph) {
        Validate.notNull(graph, "Graph cannot be null");

        graphs.add(graph);
    }

    /**
     * Start measuring statistics. This will immediately create an async repeating task as the plugin and send the
     * initial data to the metrics backend, and then after that it will post in increments of PING_INTERVAL * 1200
     * ticks.
     *
     * @return True if statistics measuring is running, otherwise false.
     */
    public boolean start() {
        synchronized (optOutLock) {
            // Did we opt out?
            if (isOptOut()) {
                return false;
            }

            // Is metrics already running?
            if (task != null) {
                return true;
            }

            // Begin hitting the server with glorious data
            task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                private boolean firstPost = true;

                public void run() {
                    try {
                        // This has to be synchronized or it can collide with the disable method.
                        synchronized (optOutLock) {
                            // Disable Task, if it is running and the server owner decided to opt-out
                            if (isOptOut() && task != null) {
                                task.cancel();
                                task = null;
                                // Tell all plotters to stop gathering information.
                                for (Graph graph : graphs) {
                                    graph.onOptOut();
                                }
                            }
                        }

                        // We use the inverse of firstPost because if it is the first time we are posting,
                        // it is not a interval ping, so it evaluates to FALSE
                        // Each time thereafter it will evaluate to TRUE, i.e PING!
                        postPlugin(!firstPost);

                        // After the first post we set firstPost to false
                        // Each post thereafter will be a ping
                        firstPost = false;
                    } catch (IOException e) {
                        if (debug) {
                            logger.log(Level.INFO, e.getMessage());
                        }
                    }
                }
            }, 0, PING_INTERVAL * 1200);

            return true;
        }
    }

    /**
     * Has the server owner denied plugin metrics?
     *
     * @return true if metrics should be opted out of it
     */
    public boolean isOptOut() {
        synchronized (optOutLock) {
            try {
                // Reload the metrics file
                configuration.load(getConfigFile());
            } catch (IOException ex) {
                if (debug) {
                    logger.log(Level.INFO, ex.getMessage());
                }
                return true;
            } catch (InvalidConfigurationException ex) {
                if (debug) {
                    logger.log(Level.INFO, ex.getMessage());
                }
                return true;
            }
            return configuration.getBoolean("opt-out", false);
        }
    }

    /**
     * Enables metrics for the server by setting "opt-out" to false in the config file and starting the metrics task.
     *
     * @throws java.io.IOException
     */
    public void enable() throws IOException {
        // This has to be synchronized or it can collide with the check in the task.
        synchronized (optOutLock) {
            // Check if the server owner has already set opt-out, if not, set it.
            if (isOptOut()) {
                configuration.set("opt-out", false);
                configuration.save(configurationFile);
            }

            // Enable Task, if it is not running
            if (task == null) {
                start();
            }
        }
    }

    /**
     * Disables metrics for the server by setting "opt-out" to true in the config file and canceling the metrics task.
     *
     * @throws java.io.IOException
     */
    public void disable() throws IOException {
        // This has to be synchronized or it can collide with the check in the task.
        synchronized (optOutLock) {
            // Check if the server owner has already set opt-out, if not, set it.
            if (!isOptOut()) {
                configuration.set("opt-out", true);
                configuration.save(configurationFile);
            }

            // Disable Task, if it is running
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    /**
     * Gets the File object of the config file that should be used to store data such as the GUID and opt-out status
     *
     * @return the File object for the config file
     */
    public File getConfigFile() {
        // I believe the easiest way to get the base folder (e.g craftbukkit set via -P) for plugins to use
        // is to abuse the plugin object we already have
        // plugin.getDataFolder() => base/plugins/PluginA/
        // pluginsFolder => base/plugins/
        // The base is not necessarily relative to the startup directory.
        File pluginsFolder = plugin.getDataFolder().getParentFile();

        // return => base/plugins/PluginMetrics/config.yml
        return new File(new File(pluginsFolder, "PluginMetrics"), "config.yml");
    }

    /**
     * Generic method that posts a plugin to the metrics website
     */
    private void postPlugin(final boolean isPing) throws IOException {
        // Server software specific section
        PluginDescriptionFile description = plugin.getDescription();
        String pluginName = description.getName();
        boolean onlineMode = plugin.getServer().getOnlineMode(); // TRUE if online mode is enabled
        String pluginVersion = description.getVersion();
        String serverVersion = Bukkit.getVersion();
        int playersOnline = plugin.getServer().getOnlinePlayers().length;

        // END server software specific section -- all code below does not use any code outside of this class / Java

        // Construct the post data
        Map<String, Object> json = new HashMap<String, Object>();

        // The plugin's description file containg all of the plugin data such as name, version, author, etc
        json.put("guid", guid);
        json.put("plugin_version", pluginVersion);
        json.put("server_version", serverVersion);
        json.put("players_online", Integer.toString(playersOnline));

        // New data as of R6
        String osname = System.getProperty("os.name");
        String osarch = System.getProperty("os.arch");
        String osversion = System.getProperty("os.version");
        String java_version = System.getProperty("java.version");
        int coreCount = Runtime.getRuntime().availableProcessors();

        // normalize os arch .. amd64 -> x86_64
        if (osarch.equals("amd64")) {
            osarch = "x86_64";
        }

        json.put("osname", osname);
        json.put("osarch", osarch);
        json.put("osversion", osversion);
        json.put("cores", Integer.toString(coreCount));
        json.put("auth_mode", onlineMode ? "1" : "0");
        json.put("java_version", java_version);

        // If we're pinging, append it
        if (isPing) {
            json.put("ping", "1");
        }

        if (graphs.size() >= 0) {
            synchronized (graphs) {
                List<Object> graphsList = new ArrayList<Object>();
                for (Graph graph : graphs) {
                    Map<String, Object> graphJson = new HashMap<String, Object>();

                    for (Plotter plotter : graph.getPlotters()) {
                        json.put(plotter.getColumnName(), Integer.toString(plotter.getValue()));
                    }

                    graphJson.put(graph.getName(), graphJson);
                    graphsList.add(graphJson);
                }
                Map<String, Object> graphJson = new HashMap<String, Object>();
                graphJson.put("graphs", graphsList);
            }
        }

        // Create the url
        URL url = new URL(BASE_URL + String.format(REPORT_URL, urlEncode(pluginName)));

        // Connect to the website
        URLConnection connection;

        // Mineshafter creates a socks proxy, so we can safely bypass it
        // It does not reroute POST requests so we need to go around it
        if (isMineshafterPresent()) {
            connection = url.openConnection(Proxy.NO_PROXY);
        } else {
            connection = url.openConnection();
        }

        String jsonString = new Gson().toJson(json);

        byte[] uncompressed = jsonString.getBytes();
        byte[] compressed = gzip(jsonString);

        // Headers
        connection.addRequestProperty("User-Agent", "MCStats/" + REVISION);
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("Content-Encoding", "gzip");
        connection.addRequestProperty("Content-Length", Integer.toString(compressed.length));
        connection.addRequestProperty("Accept", "application/json");
        connection.addRequestProperty("Connection", "close");

        connection.setDoOutput(true);

        if (debug) {
            logger.log(Level.INFO, String.format("Prepared request for %s uncompressed=%d compressed=%d", pluginName,
                    uncompressed.length, compressed.length));
        }

        // Write the data
        OutputStream os = connection.getOutputStream();
        os.write(compressed);
        os.flush();

        // Now read the response
        final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String response = reader.readLine();

        // close resources
        os.close();
        reader.close();

        if (response == null || response.startsWith("ERR") || response.startsWith("7")) {
            if (response == null) {
                response = "null";
            } else if (response.startsWith("7")) {
                response = response.substring(response.startsWith("7,") ? 2 : 1);
            }

            throw new IOException(response);
        } else {
            // Is this the first update this hour?
            if (response.equals("1") || response.contains("This is your first update this hour")) {
                synchronized (graphs) {
                    for (Graph graph : graphs) {
                        for (Plotter plotter : graph.getPlotters()) {
                            plotter.reset();
                        }
                    }
                }
            }
        }
    }

    /**
     * GZip compress a string of bytes
     *
     * @param input
     * @return
     */
    public static byte[] gzip(String input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzos = null;

        try {
            gzos = new GZIPOutputStream(baos);
            gzos.write(input.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzos != null) {
                try {
                    gzos.close();
                } catch (IOException ignore) {
                }
            }
        }

        return baos.toByteArray();
    }

    /**
     * Check if mineshafter is present. If it is, we need to bypass it to send POST requests
     *
     * @return true if mineshafter is installed on the server
     */
    private boolean isMineshafterPresent() {
        try {
            Class.forName("mineshafter.MineServer");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encode text as UTF-8
     *
     * @param text the text to encode
     * @return the encoded text, as UTF-8
     */
    private static String urlEncode(final String text) throws UnsupportedEncodingException {
        return URLEncoder.encode(text, "UTF-8");
    }
}
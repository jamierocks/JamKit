/*
 * This file is part of JamKit, licensed under the MIT License (MIT).
 *
 * Copyright (c) Jamie Mansfield <https://github.com/jamierocks>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package uk.jamierocks.jamkit.locale;

import uk.jamierocks.jamkit.plugin.Plugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

/**
 * Created by jamie on 18/03/15.
 */
public class Language {

    private final Map<String, Properties> langs = new HashMap<String, Properties>();
    private volatile String current;
    private final String fallback = "enUS";

    private final Plugin plugin;

    public Language(Plugin plugin) {
        this.plugin = plugin;

        try {
            load(plugin.getConfig().getString("lang", fallback));
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Oh noes... Something broke.", ex);
        }
    }

    public void load(String lang) throws IOException {
        if (!langs.containsKey(lang)) {
            Properties props = new Properties();
            props.load(plugin.getClass().getResourceAsStream("/locale/" + lang + ".lang"));
            langs.put(lang, props);
            plugin.getLogger().info("Loading Language: " + localize("locale.name"));
        }

        current = lang;
    }

    public void reload(String lang) throws IOException {
        if (langs.containsKey(lang)) {
            langs.remove(lang);
        }

        load(lang);
    }

    public String localize(String lang, String tag) {
        if (langs.containsKey(lang)) {
            Properties props = langs.get(lang);
            if (props.containsKey(tag)) {
                return props.getProperty(tag, tag);
            } else {
                if (lang.equalsIgnoreCase(fallback)) {
                    return "Unknown";
                } else {
                    return localize(fallback, tag);
                }
            }
        } else {
            return localize(fallback, tag);
        }
    }

    public String localize(String tag) {
        return localize(current, tag);
    }

    public String getCurrent() {
        return current;
    }
}
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
package uk.jamierocks.jamkit;

import uk.jamierocks.jamkit.plugin.Plugin;
import uk.jamierocks.jamkit.stats.Graph;
import uk.jamierocks.jamkit.stats.Plotter;

/**
 * Created by jamie on 18/03/15.
 */
public class JamKitPlugin extends Plugin {

    @Override
    public void onEnable() {
        Graph plugins = getMetrics().createGraph("Plugins using JamKit");

        for (org.bukkit.plugin.Plugin plugin : getServer().getPluginManager().getPlugins()) {
            if(plugin instanceof Plugin) {
                plugins.addPlotter(new Plotter(plugin.getName()) {
                    @Override
                    public int getValue() {
                        return 1;
                    }
                });
            }
        }

        getMetrics().start();
    }

    @Override
    public void onDisable() {

    }
}

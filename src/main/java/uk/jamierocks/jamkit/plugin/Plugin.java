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
package uk.jamierocks.jamkit.plugin;

import com.minnymin.command.CommandFramework;
import org.bukkit.plugin.java.JavaPlugin;
import uk.jamierocks.jamkit.locale.Language;
import uk.jamierocks.jamkit.stats.Metrics;

/**
 * Created by jamie on 18/03/15.
 */
public abstract class Plugin extends JavaPlugin {

    private CommandFramework commandFramework = new CommandFramework(this);

    private Language language = new Language(this);

    private Metrics metrics = new Metrics(this);

    /**
     * Registers a collection of commands
     *
     * @see CommandFramework#registerCommands(Object)
     */
    protected void registerCommands(Object obj) {
        commandFramework.registerCommands(obj);
    }

    protected CommandFramework getCommandFramework() {
        return commandFramework;
    }

    /**
     * Localizes a String
     *
     * @see Language#localize(String)
     */
    public String localize(String tag) {
        return language.localize(tag);
    }

    public Language getLanguage() {
        return language;
    }

    protected Metrics getMetrics() {
        return metrics;
    }

    public abstract void onEnable();

    public abstract void onDisable();
}

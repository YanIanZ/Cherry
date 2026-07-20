package com.example.cherrydemo;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * The example's ordinary Paper plugin entry point - proving a Cherry plugin is, first, a completely
 * normal Paper plugin (see {@code src/main/resources/paper-plugin.yml}); cherry-gradle-plugin only
 * adds extra generated files alongside this, it never changes how the plugin itself is written.
 */
public final class CherryDemoPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getSLF4JLogger().info("CherryDemo enabled - see cherry-plugin.json for the Cherry mixin/AT declarations this jar carries.");
    }
}

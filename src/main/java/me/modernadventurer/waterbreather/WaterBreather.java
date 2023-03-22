package me.modernadventurer.waterbreather;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class WaterBreather extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(new MyListener(this), this);

        //Setup Repeating Tasks
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            int tick = 0;
            public void run() {
                Bukkit.getServer().getPluginManager().callEvent(new GameTickEvent(tick));
                tick += 1;
            }
        }, 0L, 1L);
    }
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

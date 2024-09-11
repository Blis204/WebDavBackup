package org.webdavbackup.webDavBackup;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.webdavbackup.webDavBackup.commands.BackupCommand;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public final class WebDavBackup extends JavaPlugin {

    @Override
    public void onEnable() {
        File configFile;
        FileConfiguration config;
        Timer timer;

        // Plugin startup logic
        this.getCommand("backup").setExecutor(new BackupCommand(this));

        // Scheduled backups
        // Load config
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Execute the backup command every intervalMinutes minutes
        int intervalMinutes = config.getInt("backup-interval-minutes");
        long intervalTicks = intervalMinutes * 60 * 20L; // Convert minutes to ticks

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Schedule the command execution on the main thread
                    Bukkit.getScheduler().runTask(WebDavBackup.this, () -> {
                        Bukkit.getLogger().info("Running scheduled backup...");
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "backup");
                    });
                } catch (Exception e) {
                    getLogger().severe("Failed to execute scheduled backup command: " + e.getMessage());
                }
            }
        }.runTaskTimer(this, 20*60, intervalTicks);

        Bukkit.getLogger().info("WebDavBackup has been enabled.");
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getLogger().info("WebDavBackup has been disabled.");
    }
}
package org.webdavbackup.webDavBackup.commands;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.webdavbackup.webDavBackup.utils.WebDavUtils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;

public class BackupCommand implements CommandExecutor {

    static {
        Logger logger = LogManager.getLogger("org.apache.http.client.protocol.ResponseProcessCookies");
        ((org.apache.logging.log4j.core.Logger) logger).setLevel(Level.ERROR);
    }

    private final JavaPlugin plugin;
    private List<String> backupDirectories;
    private WebDavUtils webDAVUtils;

    public BackupCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        loadBackupDirectories();
        initWebDAVUtils();
    }

    private void initWebDAVUtils() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        boolean enableWebDAV = config.getBoolean("enable-webdav", false);
        if (enableWebDAV) {
            String webDAVUrl = config.getString("webdav-url");
            String webDAVUsername = config.getString("webdav-username");
            String webDAVPassword = config.getString("webdav-password");
            this.webDAVUtils = new WebDavUtils(webDAVUrl, webDAVUsername, webDAVPassword);
        }
    }

    private void loadBackupDirectories() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        List<String> directories = config.getStringList("backup-directories");
        this.backupDirectories = directories.stream()
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private void createDefaultConfig(File configFile) {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            config.set("backup-directories", Arrays.asList("world", "plugins"));
            config.set("delete-local-backup", false);
            config.set("enable-webdav", false);
            config.set("webdav-url", "https://your-webdav-server.com/path/");
            config.set("webdav-username", "your_username");
            config.set("webdav-password", "your_password");
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("This command can only be executed by a player.");
            return true;
        }

        Player player = (Player) commandSender;

        // Run the backup process asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (String directoryName : backupDirectories) {
                    File directory = new File(plugin.getServer().getWorldContainer(), directoryName);
                    if (!directory.exists() || !directory.isDirectory()) {
                        player.sendMessage(String.format("Directory §l%s§r does not exist. Skipping backup.", directoryName));
                        continue;
                    }

                    // Create and show the boss bar for the current directory
                    BossBar currentBossBar = Bukkit.createBossBar(String.format("Backing up §l%s§r", directoryName), BarColor.BLUE, BarStyle.SOLID);
                    currentBossBar.addPlayer(player);
                    currentBossBar.setVisible(true);

                    File zipFile = new File(plugin.getDataFolder(), String.format("%s_backup_%s.zip", directoryName, getTimestamp()));
                    backupDirectory(directory, zipFile, currentBossBar);
                    player.sendMessage(String.format("Directory §l%s§r has been backed up successfully.", directoryName));
                    // Upload the backup file to the WebDAV server


                    if (webDAVUtils != null) {
                        final BossBar uploadBossBar = Bukkit.createBossBar(String.format("Uploading §l%s§r...", directoryName), BarColor.GREEN, BarStyle.SOLID);
                        uploadBossBar.addPlayer(player);
                        uploadBossBar.setVisible(true);

                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                webDAVUtils.uploadFile(zipFile);

                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage(String.format("Backup for §l%s§r has been uploaded!", directoryName));
                                    uploadBossBar.setProgress(1.0);
                                    uploadBossBar.setTitle("Upload Complete");
                                    uploadBossBar.setVisible(false);
                                });

                                boolean deleteLocalBackup = config.getBoolean("delete-local-backup", false);
                                if (deleteLocalBackup) {
                                    if(zipFile.delete()) {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            player.sendMessage("Deleted local backup.");
                                        });
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    player.sendMessage("An error occurred while uploading the backup.");
                                    uploadBossBar.setTitle("Upload Failed");
                                    uploadBossBar.setVisible(false);
                                });
                            }
                        });
                    }

                    // Update the player on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        currentBossBar.setProgress(1.0);
                        currentBossBar.setTitle("Backup Complete");
                        currentBossBar.setVisible(false);
                    });
                }
            } catch (IOException e) {
                // Update the player on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("An error occurred while backing up the directories.");
                });
                e.printStackTrace();
            }
        });

        return true;
    }

    private void backupDirectory(File source, File zipFile, BossBar bossBar) throws IOException {
        long totalFiles = Files.walk(source.toPath())
                .filter(path -> !Files.isDirectory(path))
                .filter(path -> !path.toFile().getName().endsWith(".mcfunction"))
                .filter(path -> !path.toFile().getName().equals("session.lock"))
                .filter(path -> !isBackupZip(path, zipFile))
                .count();
        final long[] copiedFiles = {0};

        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Files.walk(source.toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !path.toFile().getName().endsWith(".mcfunction"))
                    .filter(path -> !path.toFile().getName().equals("session.lock"))
                    .filter(path -> !isBackupZip(path, zipFile))
                    .forEach(path -> {
                        try {
                            String entryName = source.toPath().relativize(path).toString();
                            zos.putNextEntry(new ZipEntry(entryName));
                            try (InputStream is = Files.newInputStream(path)) {
                                is.transferTo(zos);
                            }
                            copiedFiles[0]++;
                            updateBossBar(bossBar, copiedFiles[0], totalFiles, source.getName());
                            zos.closeEntry();
                        } catch (IOException e) {
                            Bukkit.getLogger().warning("Failed to backup file: " + path + " due to " + e.getMessage());
                        }
                    });
        }
    }

    private boolean isBackupZip(Path path, File zipFile) {
        // Check if the file is a ZIP file and matches the backup naming pattern
        return path.toFile().getName().endsWith(".zip") && path.toFile().getName().contains("_backup_");
    }

    private void updateBossBar(BossBar bossBar, long progress, long total, String directoryName) {
        double percentage = (double) progress / total;
        Bukkit.getScheduler().runTask(plugin, () -> {
            bossBar.setProgress(percentage);
            bossBar.setTitle(String.format("Backing up '%s': %.2f%%", directoryName, percentage * 100));
        });
    }

    private String getTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }
}
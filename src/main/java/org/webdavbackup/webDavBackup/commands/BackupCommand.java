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
            config.set("auto-backup", false);
            config.set("only-backup-when-players-online", false);
            config.set("backup-interval-minutes", 720);
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
    public boolean onCommand(CommandSender sender, Command command, String s, String[] strings) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Run the backup process asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (String directoryName : backupDirectories) {
                    File directory = new File(plugin.getServer().getWorldContainer(), directoryName);
                    if (!directory.exists() || !directory.isDirectory()) {
                        sendMessage(sender, String.format("Directory §l%s§r does not exist. Skipping backup.", directoryName));
                        continue;
                    }

                    BossBar currentBossBar;
                    if (sender instanceof Player) {
                        currentBossBar = Bukkit.createBossBar(String.format("Backing up §l%s§r", directoryName), BarColor.BLUE, BarStyle.SOLID);
                        currentBossBar.addPlayer((Player) sender);
                        currentBossBar.setVisible(true);
                    } else {
                        currentBossBar = null;
                    }

                    File zipFile = new File(plugin.getDataFolder(), String.format("%s_backup_%s.zip", directoryName, getTimestamp()));
                    backupDirectory(directory, zipFile, currentBossBar, sender);
                    sendMessage(sender, String.format("Directory §l%s§r has been backed up successfully.", directoryName));

                    if (webDAVUtils != null) {
                        final BossBar uploadBossBar = (sender instanceof Player) ?
                                Bukkit.createBossBar(String.format("Uploading §l%s§r...", directoryName), BarColor.GREEN, BarStyle.SOLID) : null;
                        if (uploadBossBar != null) {
                            uploadBossBar.addPlayer((Player) sender);
                            uploadBossBar.setVisible(true);
                        }

                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                webDAVUtils.uploadFile(zipFile);

                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sendMessage(sender, String.format("Backup for §l%s§r has been uploaded!", directoryName));
                                    if (uploadBossBar != null) {
                                        uploadBossBar.setProgress(1.0);
                                        uploadBossBar.setTitle("Upload Complete");
                                        uploadBossBar.setVisible(false);
                                    }
                                });

                                boolean deleteLocalBackup = config.getBoolean("delete-local-backup", false);
                                if (deleteLocalBackup) {
                                    if(zipFile.delete()) {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            sendMessage(sender, "Deleted local backup.");
                                        });
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sendMessage(sender, "An error occurred while uploading the backup.");
                                    if (uploadBossBar != null) {
                                        uploadBossBar.setTitle("Upload Failed");
                                        uploadBossBar.setVisible(false);
                                    }
                                });
                            }
                        });
                    }

                    // Update on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (currentBossBar != null) {
                            currentBossBar.setProgress(1.0);
                            currentBossBar.setTitle("Backup Complete");
                            currentBossBar.setVisible(false);
                        }
                    });
                }
            } catch (IOException e) {
                // Update on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sendMessage(sender, "An error occurred while backing up the directories.");
                });
                e.printStackTrace();
            }
        });

        return true;
    }

    private void backupDirectory(File source, File zipFile, BossBar bossBar, CommandSender sender) throws IOException {
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
                            updateBossBar(bossBar, copiedFiles[0], totalFiles, source.getName(), sender);
                            zos.closeEntry();
                        } catch (IOException e) {
                            Bukkit.getLogger().warning("Failed to backup file: " + path + " due to " + e.getMessage());
                        }
                    });
        }
    }

    private void updateBossBar(BossBar bossBar, long progress, long total, String directoryName, CommandSender sender) {
        double percentage = (double) progress / total;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (bossBar != null) {
                bossBar.setProgress(percentage);
                bossBar.setTitle(String.format("Backing up '%s': %.2f%%", directoryName, percentage * 100));
            }
            if (progress % 1000 == 0 || progress == total) {
                sendMessage(sender, String.format("Backed up §l%s§r (%d/%d files)", directoryName,  progress, total));
            }
        });
    }

    private void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player) {
            sender.sendMessage(message);
        } else {
            plugin.getLogger().info(message);
        }
    }

    private boolean isBackupZip(Path path, File zipFile) {
        // Check if the file is a ZIP file and matches the backup naming pattern
        return path.toFile().getName().endsWith(".zip") && path.toFile().getName().contains("_backup_");
    }

    private String getTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        return now.format(formatter);
    }
}
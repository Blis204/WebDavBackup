package org.webdavbackup.webDavBackup.utils;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class WebDavUtils {
    private final String webDAVUrl;
    private final String webDAVUsername;
    private final String webDAVPassword;
    private final JavaPlugin plugin;

    public WebDavUtils(JavaPlugin plugin, String webDAVUrl, String webDAVUsername, String webDAVPassword) {
        this.plugin = plugin;
        this.webDAVUrl = webDAVUrl;
        this.webDAVUsername = webDAVUsername;
        this.webDAVPassword = webDAVPassword;
    }

    public void uploadFile(File file, Player player) {
        try {
            Bukkit.getLogger().info("Starting WebDAV upload for file: " + file.getName());
            Sardine sardine = SardineFactory.begin(webDAVUsername, webDAVPassword);
            String remoteFilePath = String.format("%s/%s", webDAVUrl, file.getName());
            Bukkit.getLogger().info("Remote file path: " + remoteFilePath);

            // Create a boss bar for upload progress
            BossBar uploadBar = Bukkit.createBossBar("Uploading to WebDAV", BarColor.PURPLE, BarStyle.SOLID);
            uploadBar.addPlayer(player);
            uploadBar.setVisible(true);

            long fileSize = file.length();
            Bukkit.getLogger().info("File size: " + fileSize + " bytes");

            InputStream inputStream = new FileInputStream(file) {
                private long bytesRead = 0;

                @Override
                public int read(byte[] b) throws IOException {
                    int result = super.read(b);
                    if (result != -1) {
                        bytesRead += result;
                        double progress = (double) bytesRead / fileSize;
                        Bukkit.getScheduler().runTask(plugin, () -> uploadBar.setProgress(progress));
                        if (bytesRead % (1024 * 1024) == 0) { // Log every MB
                            Bukkit.getLogger().info("Uploaded " + bytesRead + " bytes");
                        }
                    }
                    return result;
                }
            };

            Bukkit.getLogger().info("Starting Sardine put operation");
            sardine.put(remoteFilePath, inputStream);
            Bukkit.getLogger().info("Sardine put operation completed");

            inputStream.close();

            Bukkit.getScheduler().runTask(plugin, () -> {
                uploadBar.setProgress(1.0);
                uploadBar.setVisible(false);
                player.sendMessage("File uploaded successfully to WebDAV.");
            });

        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to upload file to WebDAV: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("Failed to upload file to WebDAV: " + e.getMessage());
        }
    }

    public void deleteOldBackups(String directoryName) {
        try {
            Sardine sardine = SardineFactory.begin(webDAVUsername, webDAVPassword);
            String remoteDirectoryPath = webDAVUrl;
            List<DavResource> resources = sardine.list(remoteDirectoryPath);

            // Filter resources to only include backups for the specified directory
            List<DavResource> directoryBackups = resources.stream()
                    .filter(resource -> resource.getName().startsWith(directoryName + "_backup_"))
                    .sorted(Comparator.comparing(DavResource::getModified).reversed())
                    .collect(Collectors.toList());

            // Delete old backup files, keeping the latest ones
            int maxBackupsToKeep = 5; // Adjust this value to control the number of backups to keep
            for (int i = maxBackupsToKeep; i < directoryBackups.size(); i++) {
                DavResource resource = directoryBackups.get(i);
                try {
                    sardine.delete(resource.getHref().toString());
                } catch (IOException e) {
                    Bukkit.getLogger().warning("Failed to delete old backup: " + resource.getName() + ". Error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (e.getMessage().contains("404")) {
                Bukkit.getLogger().warning("WebDAV directory not found. Skipping old backup deletion.");
            } else {
                Bukkit.getLogger().severe("Failed to delete old backups from WebDAV: " + e.getMessage());
            }
        }
    }
}

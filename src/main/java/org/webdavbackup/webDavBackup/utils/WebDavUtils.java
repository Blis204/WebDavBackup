package org.webdavbackup.webDavBackup.utils;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.DavResource;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

public class WebDavUtils {
    private final String webDAVUrl;
    private final String webDAVUsername;
    private final String webDAVPassword;

    public WebDavUtils(String webDAVUrl, String webDAVUsername, String webDAVPassword) {
        this.webDAVUrl = webDAVUrl;
        this.webDAVUsername = webDAVUsername;
        this.webDAVPassword = webDAVPassword;
    }

    public void uploadFile(File file) {
        try {
            Sardine sardine = SardineFactory.begin(webDAVUsername, webDAVPassword);
            String remoteFilePath = String.format("%s/%s", webDAVUrl, file.getName());
            sardine.put(remoteFilePath, Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to upload file to WebDAV: " + e.getMessage());
        }
    }
}

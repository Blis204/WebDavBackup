package org.webdavbackup.webDavBackup;

import org.bukkit.plugin.java.JavaPlugin;
import org.webdavbackup.webDavBackup.commands.BackupCommand;

public final class WebDavBackup extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.getCommand("backup").setExecutor(new BackupCommand(this));
    }
    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

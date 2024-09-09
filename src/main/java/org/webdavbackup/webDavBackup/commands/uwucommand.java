package org.webdavbackup.webDavBackup.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class uwucommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player) {
            Player player = (Player) commandSender;
            player.sendMessage("uwu");
        }else {
            commandSender.sendMessage("This command can only be used by players");
        }
        return true;
    }
}

package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ItemReloadCommand implements CommandExecutor {

    private final ImpossbleEscapeMC plugin;

    public ItemReloadCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("op")) {
            sender.sendMessage("§cYou don't have permission.");
            return true;
        }

        plugin.getLogger().info("Reloading config and items...");
        plugin.reloadConfig();
        ItemRegistry.loadAllItems(plugin);
        
        sender.sendMessage("§a[ImpossbleEscapeMC] Item definitions and config reloaded!");
        return true;
    }
}

package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

@Command(name = "itemreload", description = "アイテム設定をリロードします。", permission = "op")
public class ItemReloadCommand implements BasicCommand {

    private final ImpossbleEscapeMC plugin;

    public ItemReloadCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!sender.hasPermission("op")) {
            sender.sendMessage("§cYou don't have permission.");
            return;
        }

        plugin.getLogger().info("Reloading config and items...");
        plugin.reloadConfig();
        ItemRegistry.loadAllItems(plugin);
        
        sender.sendMessage("§a[ImpossbleEscapeMC] Item definitions and config reloaded!");
        return;
    }
}

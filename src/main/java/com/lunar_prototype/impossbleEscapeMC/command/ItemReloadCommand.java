package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
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
            Messages.chat(sender, "iem.system.itemreloadcommand.execute.notice_001", MessageText.of("iem.system.itemreloadcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.system.itemreloadcommand.execute.notice_001.text_01"))));
            return;
        }

        plugin.getLogger().info("Reloading config and items...");
        plugin.reloadConfig();
        ItemRegistry.loadAllItems(plugin);
        
        Messages.chat(sender, "iem.system.itemreloadcommand.execute.notice_002", MessageText.of("iem.system.itemreloadcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.system.itemreloadcommand.execute.notice_002.text_01"))));
        return;
    }
}

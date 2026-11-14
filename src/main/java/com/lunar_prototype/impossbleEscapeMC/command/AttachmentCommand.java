package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Command(name = "attachment", description = "銃のアタッチメントGUIを開きます。")
public class AttachmentCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            Messages.chat(sender, "iem.inventory.attachmentcommand.execute.notice_001", MessageText.of("iem.inventory.attachmentcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.inventory.attachmentcommand.execute.notice_001.text_01"))));
            return;
        }

        // データパック銃なら、データパックのアタッチメントの画面
        if (com.lunar_prototype.impossbleEscapeMC.gui.DatapackAttachmentGUI.open(player)) return;

        // ヘルメット (手に持っている物、無ければかぶっている物) ならバイザーの画面
        if (com.lunar_prototype.impossbleEscapeMC.gui.HelmetAttachmentGUI.open(player)) return;
        Messages.chat(player, "iem.inventory.attachmentcommand.execute.notice_002", MessageText.of("iem.inventory.attachmentcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.inventory.attachmentcommand.execute.notice_002.text_01"))));
    }
}

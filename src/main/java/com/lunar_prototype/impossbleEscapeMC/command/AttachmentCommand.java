package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUI;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Command(name = "attachment", description = "銃のアタッチメントGUIを開きます。")
public class AttachmentCommand implements BasicCommand {

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        // データパック銃なら、データパックのアタッチメントの画面
        if (com.lunar_prototype.impossbleEscapeMC.gui.DatapackAttachmentGUI.open(player)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 銃を持っているかチェック (何も持っていなければ、かぶっているヘルメットのバイザーの画面)
        if (mainHand.getType() == Material.AIR || !mainHand.hasItemMeta()) {
            if (com.lunar_prototype.impossbleEscapeMC.gui.HelmetAttachmentGUI.open(player)) return;
            player.sendMessage("§c銃かヘルメットを手に持った状態で実行してください");
            return;
        }

        String itemId = mainHand.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || !"GUN".equalsIgnoreCase(def.type)) {
            // ヘルメット (手に持っている物、無ければかぶっている物) ならバイザーの画面
            if (com.lunar_prototype.impossbleEscapeMC.gui.HelmetAttachmentGUI.open(player)) return;
            player.sendMessage("§c銃かヘルメットを手に持った状態で実行してください");
            return;
        }

        // GUIを開く
        new AttachmentGUI(player, mainHand).open();
        return;
    }
}

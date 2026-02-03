package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUI;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AttachmentCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 銃を持っているかチェック
        if (mainHand.getType() == Material.AIR || !mainHand.hasItemMeta()) {
            player.sendMessage("§c銃を手に持った状態で実行してください");
            return true;
        }

        String itemId = mainHand.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || !"GUN".equalsIgnoreCase(def.type)) {
            player.sendMessage("§c銃を手に持った状態で実行してください");
            return true;
        }

        // GUIを開く
        new AttachmentGUI(player, mainHand).open();
        return true;
    }
}

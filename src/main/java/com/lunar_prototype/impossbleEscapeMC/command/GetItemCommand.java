package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GetItemCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // プレイヤーチェック
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        // 権限チェック
        if (!player.hasPermission("impossibleescape.getitem")) {
            player.sendMessage("§cYou don't have permission.");
            return true;
        }

        // 引数チェック
        if (args.length < 1) {
            player.sendMessage("§7Usage: /getitem <itemId>");
            return true;
        }

        String itemId = args[0];

        // --- 修正ポイント：Registryからアイテムを生成 ---
        // ItemFactory.create(String id) 内で Registry.get() を呼ぶ設計にしています
        ItemStack item = ItemFactory.create(itemId);

        if (item == null) {
            player.sendMessage("§cItem not found: §f" + itemId);
            return true;
        }

        // インベントリに追加
        player.getInventory().addItem(item);
        player.sendMessage("§aYou received item: §f" + itemId);

        return true;
    }
}
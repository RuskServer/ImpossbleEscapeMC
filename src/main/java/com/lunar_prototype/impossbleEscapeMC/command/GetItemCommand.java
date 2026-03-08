package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GetItemCommand implements CommandExecutor, TabCompleter {

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
            player.sendMessage("§7Usage: /getitem <itemId> [amount]");
            return true;
        }

        String itemId = args[0];
        int amount = 1;

        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64; // スタック制限（必要に応じて調整）
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid amount: " + args[1]);
                return true;
            }
        }

        // --- 修正ポイント：Registryからアイテムを生成 ---
        ItemStack item = ItemFactory.create(itemId);

        if (item == null) {
            player.sendMessage("§cItem not found: §f" + itemId);
            return true;
        }

        item.setAmount(amount);

        // インベントリに追加
        player.getInventory().addItem(item);
        player.sendMessage("§aYou received " + amount + "x item: §f" + itemId);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], ItemRegistry.getAllItemIds(), completions);
            Collections.sort(completions);
            return completions;
        } else if (args.length == 2) {
            List<String> amounts = List.of("1", "16", "32", "64");
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], amounts, completions);
            return completions;
        }
        return Collections.emptyList();
    }
}
package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LootCommand implements CommandExecutor, TabCompleter {
    private final ImpossbleEscapeMC plugin;

    public LootCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.isOp()) return true;

        if (args.length < 1) return false;

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "table" -> handleTable(player, args);
            case "container" -> handleContainer(player, args);
            case "refill" -> {
                plugin.getLootManager().refillAllContainers();
                player.sendMessage(Component.text("全てのコンテナを補充しました。", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("不明なコマンドです。", NamedTextColor.RED));
        }

        return true;
    }

    private void handleTable(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /loot table <create|add|info> <id> ...", NamedTextColor.RED));
            return;
        }
        String action = args[1].toLowerCase();
        String id = args[2];

        LootManager lm = plugin.getLootManager();

        switch (action) {
            case "create" -> {
                lm.createLootTable(id);
                player.sendMessage(Component.text("ルートテーブル " + id + " を作成しました。", NamedTextColor.GREEN));
            }
            case "add" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("使用法: /loot table add <id> <weight> [chance] [min] [max]", NamedTextColor.RED));
                    return;
                }
                LootTable table = lm.getLootTable(id);
                if (table == null) {
                    player.sendMessage(Component.text("テーブルが見つかりません。", NamedTextColor.RED));
                    return;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType() == Material.AIR) {
                    player.sendMessage(Component.text("アイテムを手に持ってください。", NamedTextColor.RED));
                    return;
                }

                // Get item ID from PDC
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                if (itemId == null) {
                    player.sendMessage(Component.text("このアイテムにはカスタムIDがありません。", NamedTextColor.RED));
                    return;
                }

                LootTable.LootEntry entry = new LootTable.LootEntry();
                entry.itemId = itemId;
                entry.weight = Double.parseDouble(args[3]);
                entry.chance = args.length >= 5 ? Double.parseDouble(args[4]) : 1.0;
                entry.minAmount = args.length >= 6 ? Integer.parseInt(args[5]) : 1;
                entry.maxAmount = args.length >= 7 ? Integer.parseInt(args[6]) : 1;

                table.entries.add(entry);
                lm.saveLootTable(table);
                player.sendMessage(Component.text("アイテム " + itemId + " をテーブル " + id + " に追加しました。", NamedTextColor.GREEN));
            }
            case "info" -> {
                LootTable table = lm.getLootTable(id);
                if (table == null) {
                    player.sendMessage(Component.text("テーブルが見つかりません。", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("=== Loot Table: " + id + " ===", NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Rolls: " + table.minRolls + "-" + table.maxRolls, NamedTextColor.WHITE));
                for (LootTable.LootEntry e : table.entries) {
                    player.sendMessage(Component.text("- " + e.itemId + " (w:" + e.weight + ", c:" + e.chance + ", n:" + e.minAmount + "-" + e.maxAmount + ")", NamedTextColor.GRAY));
                }
            }
        }
    }

    private void handleContainer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /loot container <set|remove> [mapId] [tableId]", NamedTextColor.RED));
            return;
        }

        String action = args[1].toLowerCase();
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Container)) {
            player.sendMessage(Component.text("コンテナ(チェスト等)をターゲットしてください。", NamedTextColor.RED));
            return;
        }

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    player.sendMessage(Component.text("使用法: /loot container set <mapId> <tableId>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2];
                String tableId = args[3];

                RaidMap map = plugin.getRaidManager().getMap(mapId);
                if (map == null) {
                    player.sendMessage(Component.text("マップが見つかりません。", NamedTextColor.RED));
                    return;
                }
                if (plugin.getLootManager().getLootTable(tableId) == null) {
                    player.sendMessage(Component.text("テーブルが見つかりません。", NamedTextColor.RED));
                    return;
                }

                map.addLootContainer(block.getLocation(), tableId);
                plugin.getRaidManager().saveMap(map);

                // Set PDC for immediate recognition (optional, but good for SearchGUI later)
                Container container = (Container) block.getState();
                container.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, tableId);
                container.update();

                player.sendMessage(Component.text("このコンテナをマップ " + mapId + " のルートコンテナ(テーブル: " + tableId + ")として登録しました。", NamedTextColor.GREEN));
            }
            case "remove" -> {
                // TODO: Implement removal from RaidMap
                player.sendMessage(Component.text("削除機能はまだ実装されていません。JSONを直接編集してください。", NamedTextColor.YELLOW));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("table", "container", "refill");

        if (args[0].equalsIgnoreCase("table")) {
            if (args.length == 2) return Arrays.asList("create", "add", "info");
            if (args.length == 3) return new ArrayList<>(plugin.getLootManager().getLootTableIds());
        }

        if (args[0].equalsIgnoreCase("container")) {
            if (args.length == 2) return Arrays.asList("set", "remove");
            if (args.length == 3) return plugin.getRaidManager().getMapIds();
            if (args.length == 4) return new ArrayList<>(plugin.getLootManager().getLootTableIds());
        }

        return Collections.emptyList();
    }
}

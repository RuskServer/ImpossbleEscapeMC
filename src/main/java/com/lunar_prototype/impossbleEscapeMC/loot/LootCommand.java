package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
            case "container" -> handleContainer(player, args);
            case "refill" -> {
                plugin.getLootManager().refillAllContainers();
                player.sendMessage(Component.text("全てのコンテナを補充しました。", NamedTextColor.GREEN));
            }
            case "reload" -> {
                plugin.getLootManager().loadAll();
                player.sendMessage(Component.text("loot.yml をリロードしました。", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text("不明なコマンドです。", NamedTextColor.RED));
        }

        return true;
    }

    private void handleContainer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /loot container <set|remove> [mapId] [crateId]", NamedTextColor.RED));
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
                    player.sendMessage(Component.text("使用法: /loot container set <mapId> <crateId>", NamedTextColor.RED));
                    return;
                }
                String mapId = args[2];
                String crateId = args[3];

                RaidMap map = plugin.getRaidManager().getMap(mapId);
                if (map == null) {
                    player.sendMessage(Component.text("マップが見つかりません。", NamedTextColor.RED));
                    return;
                }

                map.addLootContainer(block.getLocation(), crateId);
                plugin.getRaidManager().saveMap(map);

                Container container = (Container) block.getState();
                container.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, crateId);
                container.update();

                player.sendMessage(Component.text("このコンテナをマップ " + mapId + " のルートコンテナ(Crate: " + crateId + ")として登録しました。", NamedTextColor.GREEN));
            }
            case "remove" -> {
                player.sendMessage(Component.text("削除機能はまだ実装されていません。JSONを直接編集してください。", NamedTextColor.YELLOW));
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return Arrays.asList("container", "refill", "reload");

        if (args[0].equalsIgnoreCase("container")) {
            if (args.length == 2) return Arrays.asList("set", "remove");
            if (args.length == 3) return plugin.getRaidManager().getMapIds();
            if (args.length == 4) return plugin.getLootManager().getCrateIds();
        }

        return Collections.emptyList();
    }
}

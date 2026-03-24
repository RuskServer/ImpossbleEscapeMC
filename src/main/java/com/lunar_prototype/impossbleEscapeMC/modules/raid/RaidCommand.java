package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RaidCommand implements CommandExecutor, TabCompleter {
    private final RaidModule manager;

    public RaidCommand(RaidModule manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            new RaidSelectionGUI(manager).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open":
                new RaidSelectionGUI(manager).open(player);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "leave":
                manager.leaveQueue(player);
                player.sendMessage(Component.text("出撃待機列から離脱しました。", NamedTextColor.YELLOW));
                break;
            case "start":
                if (player.isOp()) {
                    manager.forceStartCycle();
                    player.sendMessage(Component.text("レイドサイクルを強制終了し、出撃を開始しました。", NamedTextColor.GREEN));
                }
                break;
            case "map":
                if (player.isOp()) handleMap(player, args);
                break;
            case "spawn":
                if (player.isOp()) handleSpawn(player, args);
                break;
            case "extract":
                if (player.isOp()) handleExtract(player, args);
                break;
            case "scavspawn":
                if (player.isOp()) handleScavSpawn(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("/raid join <mapID>", NamedTextColor.RED));
            return;
        }
        String mapId = args[1];
        if (!manager.joinQueue(player, mapId)) {
            player.sendMessage(Component.text("待機列への参加に失敗しました。マップIDを確認してください。", NamedTextColor.RED));
        }
    }

    private void handleMap(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("/raid map <create/delete> <id>", NamedTextColor.RED));
            return;
        }
        String action = args[1].toLowerCase();
        String mapId = args[2];

        if (action.equals("create")) {
            manager.createMap(mapId);
            player.sendMessage(Component.text("Map '" + mapId + "' created.", NamedTextColor.GREEN));
        } else if (action.equals("delete")) {
            manager.deleteMap(mapId);
            player.sendMessage(Component.text("Map '" + mapId + "' deleted.", NamedTextColor.RED));
        }
    }

    private void handleSpawn(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /raid spawn <add/clear> <mapID>", NamedTextColor.RED));
            return;
        }
        
        String action = args[1].toLowerCase();
        RaidMap map = manager.getMap(args[2]);
        if (map == null) {
            player.sendMessage(Component.text("Map not found.", NamedTextColor.RED));
            return;
        }

        if (action.equals("add")) {
            map.addSpawnPoint(player.getLocation());
            manager.saveMap(map);
            player.sendMessage(Component.text("Spawn point added to " + args[2], NamedTextColor.GREEN));
        } else if (action.equals("clear")) {
            map.clearSpawnPoints();
            manager.saveMap(map);
            player.sendMessage(Component.text("Map '" + args[2] + "' の全スポーン地点を削除しました。", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("使用法: /raid spawn <add/clear> <mapID>", NamedTextColor.RED));
        }
    }

    private void handleExtract(Player player, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("add")) {
            player.sendMessage(Component.text("/raid extract add <mapID> <name> [radius]", NamedTextColor.RED));
            return;
        }
        RaidMap map = manager.getMap(args[2]);
        if (map == null) {
            player.sendMessage(Component.text("Map not found.", NamedTextColor.RED));
            return;
        }
        String name = args[3];
        double radius = 5.0;
        if (args.length >= 5) {
            try {
                radius = Double.parseDouble(args[4]);
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text("Radius must be a number greater than 0.", NamedTextColor.RED));
                return;
            }
            if (radius <= 0) {
                player.sendMessage(Component.text("Radius must be greater than 0.", NamedTextColor.RED));
                return;
            }
        }
        map.addExtractionPoint(player.getLocation(), name, radius);
        manager.saveMap(map);
        player.sendMessage(Component.text("Extraction point '" + name + "' added to " + args[2], NamedTextColor.GREEN));
    }

    private void handleScavSpawn(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /raid scavspawn <add/clear> <mapID> [permanent]", NamedTextColor.RED));
            return;
        }
        String action = args[1].toLowerCase();
        RaidMap map = manager.getMap(args[2]);
        if (map == null) {
            player.sendMessage(Component.text("Map not found.", NamedTextColor.RED));
            return;
        }

        if (action.equals("add")) {
            boolean permanent = false;
            if (args.length >= 4) {
                permanent = Boolean.parseBoolean(args[3]);
            }
            map.addScavSpawnPoint(player.getLocation(), permanent);
            manager.saveMap(map);
            player.sendMessage(Component.text("SCAV spawn point added to " + args[2] + (permanent ? " (Permanent)" : ""), NamedTextColor.GREEN));
        } else if (action.equals("clear")) {
            map.clearScavSpawnPoints();
            manager.saveMap(map);
            player.sendMessage(Component.text("Map '" + args[2] + "' のSCAVスポーン地点を全て削除しました。", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("使用法: /raid scavspawn <add/clear> <mapID> [permanent]", NamedTextColor.RED));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Raid System Commands ---", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/raid open - レイド選択GUIを開く", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/raid join <mapID> - 出撃待機列に参加", NamedTextColor.WHITE));
        player.sendMessage(Component.text("/raid leave - 出撃待機列から離脱", NamedTextColor.WHITE));
        
        if (player.isOp()) {
            player.sendMessage(Component.text("--- Admin Commands ---", NamedTextColor.RED));
            player.sendMessage(Component.text("/raid map <create/delete> <id>", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid start - レイドを強制開始 (デバッグ用)", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid spawn add <mapID>", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid extract add <mapID> <name> [radius]", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid scavspawn <add/clear> <mapID> [permanent]", NamedTextColor.WHITE));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("open", "join", "leave"));
            if (sender.isOp()) {
                subs.addAll(Arrays.asList("map", "spawn", "extract", "scavspawn", "start"));
            }
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("join")) return manager.getMapIds().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            if (sender.isOp()) {
                if (sub.equals("map")) return Arrays.asList("create", "delete");
                if (sub.equals("spawn") || sub.equals("extract")) return Arrays.asList("add");
                if (sub.equals("scavspawn")) return Arrays.asList("add", "clear");
            }
        }

        if (args.length == 3) {
            return manager.getMapIds().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("scavspawn") && args[1].equalsIgnoreCase("add")) {
                return Arrays.asList("true", "false");
            }
        }

        return new ArrayList<>();
    }
}

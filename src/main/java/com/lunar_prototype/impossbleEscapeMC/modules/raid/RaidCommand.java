package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "raid", description = "レイドシステム管理・出撃コマンド", permission = "op")
public class RaidCommand implements BasicCommand {
    private final RaidModule manager;
    private final SpawnDiagnostics spawnDiagnostics;

    public RaidCommand(RaidModule manager) {
        this.manager = manager;
        this.spawnDiagnostics = new SpawnDiagnostics(manager);
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) return;

        if (args.length == 0) {
            new RaidSelectionGUI(manager).open(player);
            return;
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
                    String targetMap = args.length >= 2 ? args[1] : null;
                    manager.forceStartCycle(targetMap);
                    if (targetMap != null) {
                        player.sendMessage(Component.text("マップ " + targetMap + " のレイドサイクルを強制終了し、出撃を開始しました。", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("全マップのレイドサイクルを強制終了し、出撃を開始しました。", NamedTextColor.GREEN));
                    }
                }
                break;
            case "map":
                if (player.isOp()) handleMap(player, args);
                break;
            case "spawn":
                if (player.isOp()) handleSpawn(player, args);
                break;
            case "danger":
                if(player.isOp())handleDanger(player,args);
                break;
            case "radio":
                if(player.isOp()) {
                    RaidInstance raid=manager.getRaidOf(player);
                    if(args.length!=3||!args[1].equalsIgnoreCase("preview")||!List.of("zone","item").contains(args[2]))
                        player.sendMessage(Component.text("/raid radio preview <zone|item>",NamedTextColor.YELLOW));
                    else if(raid==null)player.sendMessage(Component.text("レイド参加中に実行してください。",NamedTextColor.YELLOW));
                    else raid.radio().preview(player,args[2]);
                }
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

        return;
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
        String usage = "/raid spawn <add|remove|list|analyze|clear> <mapID> [地点ID / ページ]";
        if (args.length < 3) { player.sendMessage(Component.text(usage, NamedTextColor.RED)); return; }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        RaidMap map = manager.getMap(args[2]);
        if (map == null) { player.sendMessage(Component.text("Map not found.", NamedTextColor.RED)); return; }
        if (action.equals("list") || action.equals("analyze")) {
            int page = 0;
            try { if (args.length >= 4) { page = Integer.parseInt(args[3]); if (page < 1) throw new NumberFormatException(); } }
            catch (NumberFormatException ex) { player.sendMessage(Component.text("ページは1以上の整数です。", NamedTextColor.RED)); return; }
            spawnDiagnostics.request(player, map, page, action.equals("analyze")); return;
        }
        if (action.equals("add")) {
            if (map.getWorldName() != null && !map.isWorld(player.getWorld())) {
                player.sendMessage(Component.text("マップと異なるワールドには追加できません。", NamedTextColor.RED)); return;
            }
            map.addSpawnPoint(player.getLocation());
            int id = map.spawnEntries().getLast().id();
            player.sendMessage(Component.text("スポーン #" + id + " を追加しました。", NamedTextColor.GREEN));
        } else if (action.equals("remove")) {
            if (args.length != 4) { player.sendMessage(Component.text(usage, NamedTextColor.RED)); return; }
            int id;
            try { id = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { player.sendMessage(Component.text("地点IDは整数です。", NamedTextColor.RED)); return; }
            if (!map.removeSpawnPoint(id)) { player.sendMessage(Component.text("地点 #" + id + " はありません。listで確認してください。", NamedTextColor.RED)); return; }
            player.sendMessage(Component.text("スポーン #" + id + " を削除しました。他の地点IDは変わりません。", NamedTextColor.GREEN));
        } else if (action.equals("clear")) {
            map.clearSpawnPoints();
            player.sendMessage(Component.text("全スポーン地点を削除しました。", NamedTextColor.YELLOW));
        } else { player.sendMessage(Component.text(usage, NamedTextColor.RED)); return; }
        spawnDiagnostics.invalidate(map);
        manager.saveMap(map);
        spawnDiagnostics.request(player, map, 0, true);
    }

    private void handleDanger(Player player,String[] args) {
        String usage="/raid danger <add|list|remove> <mapID> [区域ID] [表示名]";
        if(args.length<3){player.sendMessage(Component.text(usage,NamedTextColor.YELLOW));return;}
        RaidMap map=manager.getMap(args[2]);
        if(map==null){player.sendMessage(Component.text("マップがありません。",NamedTextColor.RED));return;}
        switch(args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "list" -> {
                player.sendMessage(Component.text("高脅威区域: "+map.getMapId()+" / "+map.highThreatZones().size()+"件",NamedTextColor.GOLD));
                for(var zone:map.highThreatZones())player.sendMessage(Component.text(zone.id()+" — "+zone.name()+"  ("
                        +(int)zone.minX()+", "+(int)zone.minY()+", "+(int)zone.minZ()+") ～ ("
                        +(int)(zone.maxX()-1)+", "+(int)(zone.maxY()-1)+", "+(int)(zone.maxZ()-1)+")",NamedTextColor.WHITE));
            }
            case "remove" -> {
                if(args.length!=4){player.sendMessage(Component.text(usage,NamedTextColor.YELLOW));return;}
                if(!map.removeHighThreatZone(args[3])){player.sendMessage(Component.text("その区域IDはありません。",NamedTextColor.RED));return;}
                manager.saveMap(map);
                player.sendMessage(Component.text("区域 "+args[3]+" を削除しました。",NamedTextColor.GREEN));
            }
            case "add" -> {
                if(args.length<4){player.sendMessage(Component.text(usage,NamedTextColor.YELLOW));return;}
                if(!map.isWorld(player.getWorld())){player.sendMessage(Component.text("対象マップのワールドで選択してください。",NamedTextColor.RED));return;}
                if(!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                        &&!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                    player.sendMessage(Component.text("WorldEdit または FAWE が必要です。",NamedTextColor.RED));return;
                }
                String name=args.length>4?String.join(" ",Arrays.copyOfRange(args,4,args.length)):args[3];
                try {
                    var zone=RaidWorldEditSelection.zone(player,map.getMapId(),args[3],name);
                    if(!map.addHighThreatZone(zone)){player.sendMessage(Component.text("同じ区域IDがあります。listで確認してください。",NamedTextColor.RED));return;}
                    manager.saveMap(map);
                    player.sendMessage(Component.text("選択範囲を高脅威区域「"+name+"」として保存しました。",NamedTextColor.GREEN));
                } catch(IllegalArgumentException ex) {player.sendMessage(Component.text(ex.getMessage(),NamedTextColor.RED));}
            }
            default -> player.sendMessage(Component.text(usage,NamedTextColor.YELLOW));
        }
    }

    private void handleExtract(Player player, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("add")) {
            player.sendMessage(Component.text("/raid danger <add|list|remove> <mapID> [区域ID] [表示名] (WorldEdit選択)",NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid radio preview <zone|item>",NamedTextColor.WHITE));
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
            player.sendMessage(Component.text("/raid spawn <add|remove|list|analyze|clear> <mapID> [地点ID / ページ]", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid danger <add|list|remove> <mapID> [区域ID] [表示名] (WorldEdit選択)",NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid radio preview <zone|item>",NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid extract add <mapID> <name> [radius]", NamedTextColor.WHITE));
            player.sendMessage(Component.text("/raid scavspawn <add/clear> <mapID> [permanent]", NamedTextColor.WHITE));
        }
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("open", "join", "leave"));
            if (sender.isOp()) {
                subs.addAll(Arrays.asList("map", "spawn", "extract", "scavspawn", "start", "danger", "radio"));
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
                if(sub.equals("danger"))return List.of("add","list","remove").stream().filter(v->v.startsWith(args[1])).toList();
                if(sub.equals("radio"))return List.of("preview");
                if (sub.equals("map")) return Arrays.asList("create", "delete");
                if (sub.equals("spawn")) return Arrays.asList("add", "remove", "list", "analyze", "clear").stream()
                        .filter(v -> v.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
                if (sub.equals("extract")) return Arrays.asList("add");
                if (sub.equals("scavspawn")) return Arrays.asList("add", "clear");
                if (sub.equals("start")) return manager.getMapIds().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if(args.length==3&&args[0].equalsIgnoreCase("radio"))return List.of("zone","item").stream().filter(v->v.startsWith(args[2])).toList();
        if(args.length==4&&args[0].equalsIgnoreCase("danger")&&args[1].equalsIgnoreCase("remove")) {
            RaidMap map=manager.getMap(args[2]);
            return map==null?List.of():map.highThreatZones().stream().map(RaidRadioZone::id).filter(id->id.startsWith(args[3])).toList();
        }
        if (args.length == 3) {
            return manager.getMapIds().stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && sender.isOp() && args[0].equalsIgnoreCase("spawn")) {
            RaidMap map = manager.getMap(args[2]);
            if (map == null) return List.of();
            if (args[1].equalsIgnoreCase("remove")) return map.spawnEntries().stream()
                    .map(e -> String.valueOf(e.id())).filter(v -> v.startsWith(args[3])).toList();
            if (args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("analyze")) return java.util.stream.IntStream
                    .rangeClosed(1, Math.max(1, (map.spawnEntries().size()+5)/6)).mapToObj(String::valueOf)
                    .filter(v -> v.startsWith(args[3])).toList();
        }
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("scavspawn") && args[1].equalsIgnoreCase("add")) {
                return Arrays.asList("true", "false");
            }
        }

        return new ArrayList<>();
    }
}

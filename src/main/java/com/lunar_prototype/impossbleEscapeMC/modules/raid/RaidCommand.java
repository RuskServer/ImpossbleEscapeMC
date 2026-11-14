package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

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
                Messages.chat(player, "iem.raid.raidcommand.execute.notice_001", MessageText.of("iem.raid.raidcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.execute.notice_001.text_01").color(NamedTextColor.YELLOW))));
                break;
            case "start":
                if (player.isOp()) {
                    String targetMap = args.length >= 2 ? args[1] : null;
                    manager.forceStartCycle(targetMap);
                    if (targetMap != null) {
                        Messages.chat(player, "iem.raid.raidcommand.execute.notice_002", MessageText.of("iem.raid.raidcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.execute.notice_002.text_01", targetMap).color(NamedTextColor.GREEN))));
                    } else {
                        Messages.chat(player, "iem.raid.raidcommand.execute.notice_003", MessageText.of("iem.raid.raidcommand.execute.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.execute.notice_003.text_01").color(NamedTextColor.GREEN))));
                    }
                }
                break;
            case "map":
                if (player.isOp()) handleMap(player, args);
                break;
            case "spawn":
                if (player.isOp()) handleSpawn(player, args);
                break;
            case "boss":
                if(player.isOp())handleBoss(player,args);
                break;
            case "danger":
                if(player.isOp())handleDanger(player,args);
                break;
            case "radio":
                if(player.isOp()) {
                    RaidInstance raid=manager.getRaidOf(player);
                    if(args.length!=3||!args[1].equalsIgnoreCase("preview")||!List.of("zone","item").contains(args[2]))
                        Messages.chat(player, "iem.raid.raidcommand.execute.notice_004", MessageText.of("iem.raid.raidcommand.execute.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.execute.notice_004.text_01").color(NamedTextColor.YELLOW))));
                    else if(raid==null)Messages.chat(player, "iem.raid.raidcommand.execute.notice_005", MessageText.of("iem.raid.raidcommand.execute.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.execute.notice_005.text_01").color(NamedTextColor.YELLOW))));
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
            Messages.chat(player, "iem.raid.raidcommand.handlejoin.notice_001", MessageText.of("iem.raid.raidcommand.handlejoin.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlejoin.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        String mapId = args[1];
        new RaidSelectionGUI(manager).confirm(player, mapId);
    }

    private void handleMap(Player player, String[] args) {
        if (args.length < 3) {
            Messages.chat(player, "iem.raid.raidcommand.handlemap.notice_001", MessageText.of("iem.raid.raidcommand.handlemap.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlemap.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        String action = args[1].toLowerCase();
        String mapId = args[2];

        if (action.equals("create")) {
            manager.createMap(mapId);
            Messages.chat(player, "iem.raid.raidcommand.handlemap.notice_002", MessageText.of("iem.raid.raidcommand.handlemap.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlemap.notice_002.text_01", mapId).color(NamedTextColor.GREEN))));
        } else if (action.equals("delete")) {
            manager.deleteMap(mapId);
            Messages.chat(player, "iem.raid.raidcommand.handlemap.notice_003", MessageText.of("iem.raid.raidcommand.handlemap.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlemap.notice_003.text_01", mapId).color(NamedTextColor.RED))));
        }
    }

    private void handleSpawn(Player player, String[] args) {
        Component usage = MessageText.of("iem.admin.raidcommand.handlespawn.support_001");
        if (args.length < 3) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_001", MessageText.of("iem.raid.raidcommand.handlespawn.notice_001", MessageText.payload(usage.color(NamedTextColor.RED)))); return; }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        RaidMap map = manager.getMap(args[2]);
        if (map == null) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_002", MessageText.of("iem.raid.raidcommand.handlespawn.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_002.text_01").color(NamedTextColor.RED)))); return; }
        if (action.equals("list") || action.equals("analyze")) {
            int page = 0;
            try { if (args.length >= 4) { page = Integer.parseInt(args[3]); if (page < 1) throw new NumberFormatException(); } }
            catch (NumberFormatException ex) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_003", MessageText.of("iem.raid.raidcommand.handlespawn.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_003.text_01").color(NamedTextColor.RED)))); return; }
            spawnDiagnostics.request(player, map, page, action.equals("analyze")); return;
        }
        if (action.equals("add")) {
            if (map.getWorldName() != null && !map.isWorld(player.getWorld())) {
                Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_004", MessageText.of("iem.raid.raidcommand.handlespawn.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_004.text_01").color(NamedTextColor.RED)))); return;
            }
            map.addSpawnPoint(player.getLocation());
            int id = map.spawnEntries().getLast().id();
            Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_005", MessageText.of("iem.raid.raidcommand.handlespawn.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_005.text_01", id).color(NamedTextColor.GREEN))));
        } else if (action.equals("remove")) {
            if (args.length != 4) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_006", MessageText.of("iem.raid.raidcommand.handlespawn.notice_006", MessageText.payload(usage.color(NamedTextColor.RED)))); return; }
            int id;
            try { id = Integer.parseInt(args[3]); }
            catch (NumberFormatException ex) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_007", MessageText.of("iem.raid.raidcommand.handlespawn.notice_007", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_007.text_01").color(NamedTextColor.RED)))); return; }
            if (!map.removeSpawnPoint(id)) { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_008", MessageText.of("iem.raid.raidcommand.handlespawn.notice_008", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_008.text_01", id).color(NamedTextColor.RED)))); return; }
            Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_009", MessageText.of("iem.raid.raidcommand.handlespawn.notice_009", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_009.text_01", id).color(NamedTextColor.GREEN))));
        } else if (action.equals("clear")) {
            map.clearSpawnPoints();
            Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_010", MessageText.of("iem.raid.raidcommand.handlespawn.notice_010", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlespawn.notice_010.text_01").color(NamedTextColor.YELLOW))));
        } else { Messages.chat(player, "iem.raid.raidcommand.handlespawn.notice_011", MessageText.of("iem.raid.raidcommand.handlespawn.notice_011", MessageText.payload(usage.color(NamedTextColor.RED)))); return; }
        spawnDiagnostics.invalidate(map);
        manager.saveMap(map);
        spawnDiagnostics.request(player, map, 0, true);
    }

    private void handleBoss(Player player,String[] args) {
        Component usage=MessageText.of("iem.admin.raidcommand.handleboss.support_001");
        if(args.length<3){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_001", MessageText.of("iem.raid.raidcommand.handleboss.notice_001", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
        RaidMap map=manager.getMap(args[2]);if(map==null){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_002", MessageText.of("iem.raid.raidcommand.handleboss.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_002.text_01").color(NamedTextColor.RED))));return;}
        switch(args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "set" -> {
                if(args.length!=5||!RaidBossGroup.ROLES.contains(args[4])){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_003", MessageText.of("iem.raid.raidcommand.handleboss.notice_003", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
                if(!map.isWorld(player.getWorld())){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_004", MessageText.of("iem.raid.raidcommand.handleboss.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_004.text_01").color(NamedTextColor.RED))));return;}
                if(!RaidBossEncounter.safePosition(player.getLocation())){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_005", MessageText.of("iem.raid.raidcommand.handleboss.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_005.text_01").color(NamedTextColor.RED))));return;}
                try {
                    map.setBossPosition(args[3],args[4],player.getLocation());manager.saveMap(map);
                    Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_006", MessageText.of("iem.raid.raidcommand.handleboss.notice_006", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_006.text_01", args[3], args[4]).color(NamedTextColor.GREEN))));
                }catch(IllegalArgumentException ex){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_007", MessageText.of("iem.raid.raidcommand.handleboss.notice_007", MessageText.payload(Component.text(ex.getMessage(),NamedTextColor.RED))));}
            }
            case "remove" -> {
                if(args.length!=4){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_008", MessageText.of("iem.raid.raidcommand.handleboss.notice_008", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
                if(!map.removeBossGroup(args[3])){Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_009", MessageText.of("iem.raid.raidcommand.handleboss.notice_009", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_009.text_01").color(NamedTextColor.RED))));return;}
                manager.saveMap(map);Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_010", MessageText.of("iem.raid.raidcommand.handleboss.notice_010", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_010.text_01").color(NamedTextColor.GREEN))));
            }
            case "list" -> {
                Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_011", MessageText.of("iem.raid.raidcommand.handleboss.notice_011", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_011.text_01", map.getMapId(), map.bossGroups().size()).color(NamedTextColor.GOLD))));
                for(var group:map.bossGroups()) {
                    var zone=map.highThreatZones().stream().filter(z->z.id().equals(group.zoneId())).findFirst().orElse(null);
                    String problem=group.problem(zone);
                    Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_012", MessageText.of("iem.raid.raidcommand.handleboss.notice_012", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_012.text_01", group.zoneId(), (problem==null?MessageText.of("iem.raid.raidcommand.registered"):problem)).color(problem==null?NamedTextColor.GREEN:NamedTextColor.YELLOW))));
                    for(String role:RaidBossGroup.ROLES) {
                        double[] p=group.position(role);
                        Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_013", MessageText.of("iem.raid.raidcommand.handleboss.notice_013", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleboss.notice_013.text_01", role, (p==null?MessageText.of("iem.raid.raidcommand.unregistered"):String.format(java.util.Locale.ROOT,"%.1f %.1f %.1f",p[0],p[1],p[2]))).color(NamedTextColor.GRAY))));
                    }
                }
            }
            default -> Messages.chat(player, "iem.raid.raidcommand.handleboss.notice_014", MessageText.of("iem.raid.raidcommand.handleboss.notice_014", MessageText.payload(usage.color(NamedTextColor.YELLOW))));
        }
    }

    private void handleDanger(Player player,String[] args) {
        Component usage=MessageText.of("iem.admin.raidcommand.handledanger.support_001");
        if(args.length<3){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_001", MessageText.of("iem.raid.raidcommand.handledanger.notice_001", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
        RaidMap map=manager.getMap(args[2]);
        if(map==null){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_002", MessageText.of("iem.raid.raidcommand.handledanger.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_002.text_01").color(NamedTextColor.RED))));return;}
        switch(args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "list" -> {
                Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_003", MessageText.of("iem.raid.raidcommand.handledanger.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_003.text_01", map.getMapId(), map.highThreatZones().size()).color(NamedTextColor.GOLD))));
                for(var zone:map.highThreatZones())Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_004", MessageText.of("iem.raid.raidcommand.handledanger.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_004.text_01", zone.id(), zone.name(), (int)zone.minX(), (int)zone.minY(), (int)zone.minZ(), (int)(zone.maxX()-1), (int)(zone.maxY()-1), (int)(zone.maxZ()-1)).color(NamedTextColor.WHITE))));
            }
            case "remove" -> {
                if(args.length!=4){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_005", MessageText.of("iem.raid.raidcommand.handledanger.notice_005", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
                if(!map.removeHighThreatZone(args[3])){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_006", MessageText.of("iem.raid.raidcommand.handledanger.notice_006", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_006.text_01").color(NamedTextColor.RED))));return;}
                manager.saveMap(map);
                Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_007", MessageText.of("iem.raid.raidcommand.handledanger.notice_007", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_007.text_01", args[3]).color(NamedTextColor.GREEN))));
            }
            case "add" -> {
                if(args.length<4){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_008", MessageText.of("iem.raid.raidcommand.handledanger.notice_008", MessageText.payload(usage.color(NamedTextColor.YELLOW))));return;}
                if(!map.isWorld(player.getWorld())){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_009", MessageText.of("iem.raid.raidcommand.handledanger.notice_009", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_009.text_01").color(NamedTextColor.RED))));return;}
                if(!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("WorldEdit")
                        &&!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
                    Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_010", MessageText.of("iem.raid.raidcommand.handledanger.notice_010", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_010.text_01").color(NamedTextColor.RED))));return;
                }
                String name=args.length>4?String.join(" ",Arrays.copyOfRange(args,4,args.length)):args[3];
                try {
                    var zone=RaidWorldEditSelection.zone(player,map.getMapId(),args[3],name);
                    if(!map.addHighThreatZone(zone)){Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_011", MessageText.of("iem.raid.raidcommand.handledanger.notice_011", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_011.text_01").color(NamedTextColor.RED))));return;}
                    manager.saveMap(map);
                    Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_012", MessageText.of("iem.raid.raidcommand.handledanger.notice_012", MessageText.payload(MessageText.of("iem.raid.raidcommand.handledanger.notice_012.text_01", name).color(NamedTextColor.GREEN))));
                } catch(IllegalArgumentException ex) {Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_013", MessageText.of("iem.raid.raidcommand.handledanger.notice_013", MessageText.payload(Component.text(ex.getMessage(),NamedTextColor.RED))));}
            }
            default -> Messages.chat(player, "iem.raid.raidcommand.handledanger.notice_014", MessageText.of("iem.raid.raidcommand.handledanger.notice_014", MessageText.payload(usage.color(NamedTextColor.YELLOW))));
        }
    }

    private void handleExtract(Player player, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("add")) {
            Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_001", MessageText.of("iem.raid.raidcommand.handleextract.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_001.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_002", MessageText.of("iem.raid.raidcommand.handleextract.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_002.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_003", MessageText.of("iem.raid.raidcommand.handleextract.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_003.text_01").color(NamedTextColor.RED))));
            return;
        }
        RaidMap map = manager.getMap(args[2]);
        if (map == null) {
            Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_004", MessageText.of("iem.raid.raidcommand.handleextract.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_004.text_01").color(NamedTextColor.RED))));
            return;
        }
        String name = args[3];
        double radius = 5.0;
        if (args.length >= 5) {
            try {
                radius = Double.parseDouble(args[4]);
            } catch (NumberFormatException ignored) {
                Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_005", MessageText.of("iem.raid.raidcommand.handleextract.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_005.text_01").color(NamedTextColor.RED))));
                return;
            }
            if (radius <= 0) {
                Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_006", MessageText.of("iem.raid.raidcommand.handleextract.notice_006", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_006.text_01").color(NamedTextColor.RED))));
                return;
            }
        }
        map.addExtractionPoint(player.getLocation(), name, radius);
        manager.saveMap(map);
        Messages.chat(player, "iem.raid.raidcommand.handleextract.notice_007", MessageText.of("iem.raid.raidcommand.handleextract.notice_007", MessageText.payload(MessageText.of("iem.raid.raidcommand.handleextract.notice_007.text_01", name, args[2]).color(NamedTextColor.GREEN))));
    }

    private void handleScavSpawn(Player player, String[] args) {
        if (args.length < 3) {
            Messages.chat(player, "iem.raid.raidcommand.handlescavspawn.notice_001", MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        String action = args[1].toLowerCase();
        RaidMap map = manager.getMap(args[2]);
        if (map == null) {
            Messages.chat(player, "iem.raid.raidcommand.handlescavspawn.notice_002", MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        if (action.equals("add")) {
            boolean permanent = false;
            if (args.length >= 4) {
                permanent = Boolean.parseBoolean(args[3]);
            }
            map.addScavSpawnPoint(player.getLocation(), permanent);
            manager.saveMap(map);
            Messages.chat(player, "iem.raid.raidcommand.handlescavspawn.notice_003", MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_003.text_01", args[2], (permanent ? " (Permanent)" : "")).color(NamedTextColor.GREEN))));
        } else if (action.equals("clear")) {
            map.clearScavSpawnPoints();
            manager.saveMap(map);
            Messages.chat(player, "iem.raid.raidcommand.handlescavspawn.notice_004", MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_004.text_01", args[2]).color(NamedTextColor.GREEN))));
        } else {
            Messages.chat(player, "iem.raid.raidcommand.handlescavspawn.notice_005", MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.handlescavspawn.notice_005.text_01").color(NamedTextColor.RED))));
        }
    }

    private void sendHelp(Player player) {
        Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_001", MessageText.of("iem.raid.raidcommand.sendhelp.notice_001", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_001.text_01").color(NamedTextColor.YELLOW))));
        Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_002", MessageText.of("iem.raid.raidcommand.sendhelp.notice_002", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_002.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_003", MessageText.of("iem.raid.raidcommand.sendhelp.notice_003", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_003.text_01").color(NamedTextColor.WHITE))));
        Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_004", MessageText.of("iem.raid.raidcommand.sendhelp.notice_004", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_004.text_01").color(NamedTextColor.WHITE))));
        
        if (player.isOp()) {
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_005", MessageText.of("iem.raid.raidcommand.sendhelp.notice_005", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_005.text_01").color(NamedTextColor.RED))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_006", MessageText.of("iem.raid.raidcommand.sendhelp.notice_006", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_006.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_007", MessageText.of("iem.raid.raidcommand.sendhelp.notice_007", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_007.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_008", MessageText.of("iem.raid.raidcommand.sendhelp.notice_008", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_008.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_009", MessageText.of("iem.raid.raidcommand.sendhelp.notice_009", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_009.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_010", MessageText.of("iem.raid.raidcommand.sendhelp.notice_010", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_010.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_011", MessageText.of("iem.raid.raidcommand.sendhelp.notice_011", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_011.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_012", MessageText.of("iem.raid.raidcommand.sendhelp.notice_012", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_012.text_01").color(NamedTextColor.WHITE))));
            Messages.chat(player, "iem.raid.raidcommand.sendhelp.notice_013", MessageText.of("iem.raid.raidcommand.sendhelp.notice_013", MessageText.payload(MessageText.of("iem.raid.raidcommand.sendhelp.notice_013.text_01").color(NamedTextColor.WHITE))));
        }
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("open", "join", "leave"));
            if (sender.isOp()) {
                subs.addAll(Arrays.asList("map", "spawn", "extract", "scavspawn", "start", "danger", "radio", "boss"));
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
                if(sub.equals("boss"))return List.of("set","list","remove").stream().filter(v->v.startsWith(args[1])).toList();
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

        if(args.length>0&&sender.isOp()&&args[0].equalsIgnoreCase("boss")) {
            if(args.length==4){RaidMap map=manager.getMap(args[2]);return map==null?List.of():map.highThreatZones().stream().map(RaidRadioZone::id).filter(v->v.startsWith(args[3])).toList();}
            if(args.length==5&&args[1].equalsIgnoreCase("set"))return RaidBossGroup.ROLES.stream().filter(v->v.startsWith(args[4])).toList();
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

package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Command(name = "loot", description = "ルートシステム管理コマンド", permission = "op")
public class LootCommand implements BasicCommand {
    private final ImpossbleEscapeMC plugin;

    public LootCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) return;
        if (!player.isOp()) return;

        if (args.length < 1) {
            Messages.chat(player, "iem.loot.lootcommand.execute.notice_001", MessageText.of("iem.loot.lootcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "container" -> handleContainer(player, args);
            case "drawer" -> handleDrawer(player, args);
            case "locker" -> handleLocker(player,args);
            case "lockeregg" -> handleLockerEgg(player,args);
            case "egg" -> handleEgg(player, args);
            case "draweregg" -> handleDrawerEgg(player,args);
            case "groundegg" -> handleGroundEgg(player, args);
            case "remover" -> {
                player.getInventory().addItem(plugin.getLootEggListener().createRemover());
                Messages.chat(player, "iem.loot.lootcommand.execute.notice_002", MessageText.of("iem.loot.lootcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_002.text_01").color(NamedTextColor.GREEN))));
            }
            case "refill" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("this")) {
                    Block b = player.getTargetBlockExact(5);
                    if (b != null && plugin.getServiceContainer().get(LockerLootModule.class).refillAt(b)) {
                        Messages.chat(player, "iem.loot.lootcommand.execute.notice_003", MessageText.of("iem.loot.lootcommand.execute.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_003.text_01").color(NamedTextColor.GREEN))));
                    } else if (b != null && plugin.getServiceContainer().get(DrawerLootModule.class).refillAt(b)) {
                        Messages.chat(player, "iem.loot.lootcommand.execute.notice_004", MessageText.of("iem.loot.lootcommand.execute.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_004.text_01").color(NamedTextColor.GREEN))));
                    } else if (b != null && b.getState() instanceof Container container) {
                        String crateId = container.getPersistentDataContainer().get(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING);
                        if (crateId != null) {
                            plugin.getLootManager().refillContainer(container, crateId);
                            Messages.chat(player, "iem.loot.lootcommand.execute.notice_005", MessageText.of("iem.loot.lootcommand.execute.notice_005", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_005.text_01", crateId).color(NamedTextColor.GREEN))));
                        } else {
                            Messages.chat(player, "iem.loot.lootcommand.execute.notice_006", MessageText.of("iem.loot.lootcommand.execute.notice_006", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_006.text_01").color(NamedTextColor.RED))));
                        }
                    } else {
                        Messages.chat(player, "iem.loot.lootcommand.execute.notice_007", MessageText.of("iem.loot.lootcommand.execute.notice_007", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_007.text_01").color(NamedTextColor.RED))));
                    }
                } else {
                    plugin.getLootManager().refillAllContainers();
                    Messages.chat(player, "iem.loot.lootcommand.execute.notice_008", MessageText.of("iem.loot.lootcommand.execute.notice_008", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_008.text_01").color(NamedTextColor.GREEN))));
                }
            }
            default -> Messages.chat(player, "iem.loot.lootcommand.execute.notice_009", MessageText.of("iem.loot.lootcommand.execute.notice_009", MessageText.payload(MessageText.of("iem.loot.lootcommand.execute.notice_009.text_01").color(NamedTextColor.RED))));
        }

        return;
    }

    private void handleLocker(Player player,String[] args) {
        var lockers=plugin.getServiceContainer().get(LockerLootModule.class);if(lockers==null)return;
        if(args.length==2&&args[1].equalsIgnoreCase("remove")) {
            boolean removed=lockers.removeAt(player.getTargetBlockExact(5));
            Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_001", MessageText.of("iem.loot.lootcommand.handlelocker.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelocker.notice_001.text_01", removed ? MessageText.of("iem.loot.lootcommand.locker_removal.1") : MessageText.of("iem.loot.lootcommand.locker_removal.2")).color(removed?NamedTextColor.GREEN:NamedTextColor.RED))));return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("list")) {
            RaidMap map=plugin.getRaidModule().getMap(args[2]);
            if(map==null){Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_002", MessageText.of("iem.loot.lootcommand.handlelocker.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelocker.notice_002.text_01").color(NamedTextColor.RED))));return;}
            Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_003", MessageText.of("iem.loot.lootcommand.handlelocker.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelocker.notice_003.text_01", map.getMapId(), map.getLootLockers().size()).color(NamedTextColor.GOLD))));
            for(var locker:map.getLootLockers())Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_004", MessageText.of("iem.loot.lootcommand.handlelocker.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelocker.notice_004.text_01", locker.x, locker.y, locker.z).color(NamedTextColor.WHITE))));return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("place")) {
            Block floor=player.getTargetBlockExact(5);
            try {lockers.place(player,args[2],floor==null?null:floor.getRelative(org.bukkit.block.BlockFace.UP));}
            catch(IllegalArgumentException ex){Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_005", MessageText.of("iem.loot.lootcommand.handlelocker.notice_005", MessageText.payload(Component.text(ex.getMessage(),NamedTextColor.RED))));}return;
        }
        Messages.chat(player, "iem.loot.lootcommand.handlelocker.notice_006", MessageText.of("iem.loot.lootcommand.handlelocker.notice_006", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelocker.notice_006.text_01").color(NamedTextColor.YELLOW))));
    }
    private void handleLockerEgg(Player player,String[] args) {
        if(args.length!=2){Messages.chat(player, "iem.loot.lootcommand.handlelockeregg.notice_001", MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_001.text_01").color(NamedTextColor.YELLOW))));return;}
        if(plugin.getRaidModule().getMap(args[1])==null){Messages.chat(player, "iem.loot.lootcommand.handlelockeregg.notice_002", MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_002.text_01").color(NamedTextColor.RED))));return;}
        var lockers=plugin.getServiceContainer().get(LockerLootModule.class);if(lockers==null)return;
        if(!player.getInventory().addItem(lockers.createEgg(args[1])).isEmpty()) {
            Messages.chat(player, "iem.loot.lootcommand.handlelockeregg.notice_003", MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_003.text_01").color(NamedTextColor.RED))));return;
        }
        Messages.chat(player, "iem.loot.lootcommand.handlelockeregg.notice_004", MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlelockeregg.notice_004.text_01", args[1]).color(NamedTextColor.GREEN))));
    }

    private void handleDrawer(Player player,String[] args) {
        var drawers=plugin.getServiceContainer().get(DrawerLootModule.class);
        if(drawers==null)return;
        if(args.length==2&&args[1].equalsIgnoreCase("remove")) {
            if(!drawers.removeAt(player.getTargetBlockExact(5)))Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_001", MessageText.of("iem.loot.lootcommand.handledrawer.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_001.text_01").color(NamedTextColor.RED))));
            else Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_002", MessageText.of("iem.loot.lootcommand.handledrawer.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_002.text_01").color(NamedTextColor.GREEN))));
            return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("list")) {
            RaidMap map=plugin.getRaidModule().getMap(args[2]);
            if(map==null){Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_003", MessageText.of("iem.loot.lootcommand.handledrawer.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_003.text_01").color(NamedTextColor.RED))));return;}
            Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_004", MessageText.of("iem.loot.lootcommand.handledrawer.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_004.text_01", map.getMapId(), map.getLootDrawers().size()).color(NamedTextColor.GOLD))));
            for(var drawer:map.getLootDrawers())Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_005", MessageText.of("iem.loot.lootcommand.handledrawer.notice_005", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_005.text_01", drawer.crateId, drawer.x, drawer.y, drawer.z).color(NamedTextColor.WHITE))));
            return;
        }
        if(args.length==4&&args[1].equalsIgnoreCase("place")) {
            Block floor=player.getTargetBlockExact(5);
            try {drawers.place(player,args[2],args[3],floor==null?null:floor.getRelative(org.bukkit.block.BlockFace.UP));}
            catch(IllegalArgumentException ex){Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_006", MessageText.of("iem.loot.lootcommand.handledrawer.notice_006", MessageText.payload(Component.text(ex.getMessage(),NamedTextColor.RED))));}
            return;
        }
        Messages.chat(player, "iem.loot.lootcommand.handledrawer.notice_007", MessageText.of("iem.loot.lootcommand.handledrawer.notice_007", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledrawer.notice_007.text_01").color(NamedTextColor.YELLOW))));
    }

    private void handleContainer(Player player, String[] args) {
        if (args.length < 2) {
            Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_001", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        String action = args[1].toLowerCase();
        Block block = player.getTargetBlockExact(5);
        if (block == null || !(block.getState() instanceof Container)) {
            Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_002", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_002.text_01").color(NamedTextColor.RED))));
            return;
        }

        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_003", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_003.text_01").color(NamedTextColor.RED))));
                    return;
                }
                String mapId = args[2];
                String crateId = args[3];

                RaidMap map = plugin.getRaidModule().getMap(mapId);
                if (map == null) {
                    Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_004", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_004.text_01").color(NamedTextColor.RED))));
                    return;
                }

                map.addLootContainer(block.getLocation(), crateId);
                plugin.getRaidModule().saveMap(map);

                Container container = (Container) block.getState();
                container.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, crateId);
                container.update();

                Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_005", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_005", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_005.text_01", mapId, crateId).color(NamedTextColor.GREEN))));
            }
            case "remove" -> {
                List<String> removedFrom = plugin.getLootManager().removeLootContainer(block);
                if (removedFrom.isEmpty()) {
                    Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_006", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_006", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_006.text_01").color(NamedTextColor.RED))));
                    return;
                }
                Messages.chat(player, "iem.loot.lootcommand.handlecontainer.notice_007", MessageText.of("iem.loot.lootcommand.handlecontainer.notice_007", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlecontainer.notice_007.text_01", String.join(", ", removedFrom)).color(NamedTextColor.GREEN))));
            }
        }
    }

    private void handleEgg(Player player, String[] args) {
        if (args.length < 3) {
            Messages.chat(player, "iem.loot.lootcommand.handleegg.notice_001", MessageText.of("iem.loot.lootcommand.handleegg.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handleegg.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        String mapId = args[1];
        String crateId = args[2];

        ItemStack egg = plugin.getLootEggListener().createEgg(mapId, crateId);
        player.getInventory().addItem(egg);
        Messages.chat(player, "iem.loot.lootcommand.handleegg.notice_002", MessageText.of("iem.loot.lootcommand.handleegg.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handleegg.notice_002.text_01", crateId, mapId).color(NamedTextColor.GREEN))));
    }

    private void handleDrawerEgg(Player player,String[] args) {
        if(args.length!=3) {
            Messages.chat(player, "iem.loot.lootcommand.handledraweregg.notice_001", MessageText.of("iem.loot.lootcommand.handledraweregg.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledraweregg.notice_001.text_01").color(NamedTextColor.YELLOW))));return;
        }
        if(plugin.getRaidModule().getMap(args[1])==null||plugin.getLootManager().getCrate(args[2])==null) {
            Messages.chat(player, "iem.loot.lootcommand.handledraweregg.notice_002", MessageText.of("iem.loot.lootcommand.handledraweregg.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledraweregg.notice_002.text_01").color(NamedTextColor.RED))));return;
        }
        if(!player.getInventory().addItem(plugin.getLootEggListener().createDrawerEgg(args[1],args[2])).isEmpty()) {
            Messages.chat(player, "iem.loot.lootcommand.handledraweregg.notice_003", MessageText.of("iem.loot.lootcommand.handledraweregg.notice_003", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledraweregg.notice_003.text_01").color(NamedTextColor.RED))));return;
        }
        Messages.chat(player, "iem.loot.lootcommand.handledraweregg.notice_004", MessageText.of("iem.loot.lootcommand.handledraweregg.notice_004", MessageText.payload(MessageText.of("iem.loot.lootcommand.handledraweregg.notice_004.text_01", args[2], args[1]).color(NamedTextColor.GREEN))));
    }

    private void handleGroundEgg(Player player, String[] args) {
        if (args.length < 3) {
            Messages.chat(player, "iem.loot.lootcommand.handlegroundegg.notice_001", MessageText.of("iem.loot.lootcommand.handlegroundegg.notice_001", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlegroundegg.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        player.getInventory().addItem(plugin.getLootEggListener().createGroundEgg(args[1], args[2]));
        Messages.chat(player, "iem.loot.lootcommand.handlegroundegg.notice_002", MessageText.of("iem.loot.lootcommand.handlegroundegg.notice_002", MessageText.payload(MessageText.of("iem.loot.lootcommand.handlegroundegg.notice_002.text_01", args[2], args[1]).color(NamedTextColor.GREEN))));
    }

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) return Arrays.asList("container", "drawer", "locker", "egg", "draweregg", "lockeregg", "groundegg", "remover", "refill");

        if(args[0].equalsIgnoreCase("locker")) {
            if(args.length==2)return List.of("place","remove","list");
            if(args.length==3&&(args[1].equalsIgnoreCase("place")||args[1].equalsIgnoreCase("list")))return plugin.getRaidModule().getMapIds();
        }
        if(args[0].equalsIgnoreCase("lockeregg")&&args.length==2)return plugin.getRaidModule().getMapIds();
        if (args[0].equalsIgnoreCase("drawer")) {
            if(args.length==2)return List.of("place","remove","list");
            if(args.length==3&&(args[1].equalsIgnoreCase("place")||args[1].equalsIgnoreCase("list")))return plugin.getRaidModule().getMapIds();
            if(args.length==4&&args[1].equalsIgnoreCase("place"))return plugin.getLootManager().getCrateIds();
        }
        if (args[0].equalsIgnoreCase("container")) {
            if (args.length == 2) return Arrays.asList("set", "remove");
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) return plugin.getRaidModule().getMapIds();
            if (args.length == 4 && args[1].equalsIgnoreCase("set")) return plugin.getLootManager().getCrateIds();
        }

        if (args[0].equalsIgnoreCase("egg") || args[0].equalsIgnoreCase("draweregg") || args[0].equalsIgnoreCase("groundegg")) {
            if (args.length == 2) return plugin.getRaidModule().getMapIds();
            if (args.length == 3) return plugin.getLootManager().getCrateIds();
        }

        if (args[0].equalsIgnoreCase("refill")) {
            if (args.length == 2) return Arrays.asList("all", "this");
        }

        return Collections.emptyList();
    }
}

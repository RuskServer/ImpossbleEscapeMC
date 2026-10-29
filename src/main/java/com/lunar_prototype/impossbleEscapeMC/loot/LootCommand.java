package com.lunar_prototype.impossbleEscapeMC.loot;

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
            player.sendMessage(Component.text("/loot <container|drawer|locker|egg|draweregg|lockeregg|groundegg|remover|refill>",NamedTextColor.RED));
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
                player.sendMessage(Component.text("ルート削除ツールを付与しました (クレートか、地面ルートの地点の床を右クリック)", NamedTextColor.GREEN));
            }
            case "refill" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("this")) {
                    Block b = player.getTargetBlockExact(5);
                    if (b != null && plugin.getServiceContainer().get(LockerLootModule.class).refillAt(b)) {
                        player.sendMessage(Component.text("ロッカーを再抽選・補充しました。",NamedTextColor.GREEN));
                    } else if (b != null && plugin.getServiceContainer().get(DrawerLootModule.class).refillAt(b)) {
                        player.sendMessage(Component.text("引き出しの上下を再補充しました。",NamedTextColor.GREEN));
                    } else if (b != null && b.getState() instanceof Container container) {
                        String crateId = container.getPersistentDataContainer().get(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING);
                        if (crateId != null) {
                            plugin.getLootManager().refillContainer(container, crateId);
                            player.sendMessage(Component.text("このコンテナを再補充しました (" + crateId + ")", NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("このコンテナにはルート設定がありません。", NamedTextColor.RED));
                        }
                    } else {
                        player.sendMessage(Component.text("コンテナをターゲットしてください。", NamedTextColor.RED));
                    }
                } else {
                    plugin.getLootManager().refillAllContainers();
                    player.sendMessage(Component.text("全てのコンテナを補充しました。", NamedTextColor.GREEN));
                }
            }
            default -> player.sendMessage(Component.text("不明なコマンドです。", NamedTextColor.RED));
        }

        return;
    }

    private void handleLocker(Player player,String[] args) {
        var lockers=plugin.getServiceContainer().get(LockerLootModule.class);if(lockers==null)return;
        if(args.length==2&&args[1].equalsIgnoreCase("remove")) {
            boolean removed=lockers.removeAt(player.getTargetBlockExact(5));
            player.sendMessage(Component.text(removed?"ロッカーを削除しました。":"ロッカーをターゲットしてください。",removed?NamedTextColor.GREEN:NamedTextColor.RED));return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("list")) {
            RaidMap map=plugin.getRaidModule().getMap(args[2]);
            if(map==null){player.sendMessage(Component.text("そのマップはありません。",NamedTextColor.RED));return;}
            player.sendMessage(Component.text("ロッカー: "+map.getMapId()+" / "+map.getLootLockers().size()+"台",NamedTextColor.GOLD));
            for(var locker:map.getLootLockers())player.sendMessage(Component.text(locker.x+" "+locker.y+" "+locker.z,NamedTextColor.WHITE));return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("place")) {
            Block floor=player.getTargetBlockExact(5);
            try {lockers.place(player,args[2],floor==null?null:floor.getRelative(org.bukkit.block.BlockFace.UP));}
            catch(IllegalArgumentException ex){player.sendMessage(Component.text(ex.getMessage(),NamedTextColor.RED));}return;
        }
        player.sendMessage(Component.text("/loot locker place <mapId> | remove | list <mapId>",NamedTextColor.YELLOW));
    }
    private void handleLockerEgg(Player player,String[] args) {
        if(args.length!=2){player.sendMessage(Component.text("/loot lockeregg <mapId>",NamedTextColor.YELLOW));return;}
        if(plugin.getRaidModule().getMap(args[1])==null){player.sendMessage(Component.text("そのマップはありません。",NamedTextColor.RED));return;}
        var lockers=plugin.getServiceContainer().get(LockerLootModule.class);if(lockers==null)return;
        if(!player.getInventory().addItem(lockers.createEgg(args[1])).isEmpty()) {
            player.sendMessage(Component.text("インベントリに空きがありません。",NamedTextColor.RED));return;
        }
        player.sendMessage(Component.text("ロッカー設置用エッグを付与しました ("+args[1]+")",NamedTextColor.GREEN));
    }

    private void handleDrawer(Player player,String[] args) {
        var drawers=plugin.getServiceContainer().get(DrawerLootModule.class);
        if(drawers==null)return;
        if(args.length==2&&args[1].equalsIgnoreCase("remove")) {
            if(!drawers.removeAt(player.getTargetBlockExact(5)))player.sendMessage(Component.text("引き出しをターゲットしてください。",NamedTextColor.RED));
            else player.sendMessage(Component.text("引き出しを削除しました。",NamedTextColor.GREEN));
            return;
        }
        if(args.length==3&&args[1].equalsIgnoreCase("list")) {
            RaidMap map=plugin.getRaidModule().getMap(args[2]);
            if(map==null){player.sendMessage(Component.text("そのマップはありません。",NamedTextColor.RED));return;}
            player.sendMessage(Component.text("引き出し: "+map.getMapId()+" / "+map.getLootDrawers().size()+"台",NamedTextColor.GOLD));
            for(var drawer:map.getLootDrawers())player.sendMessage(Component.text(drawer.crateId+"  "+drawer.x+" "+drawer.y+" "+drawer.z,NamedTextColor.WHITE));
            return;
        }
        if(args.length==4&&args[1].equalsIgnoreCase("place")) {
            Block floor=player.getTargetBlockExact(5);
            try {drawers.place(player,args[2],args[3],floor==null?null:floor.getRelative(org.bukkit.block.BlockFace.UP));}
            catch(IllegalArgumentException ex){player.sendMessage(Component.text(ex.getMessage(),NamedTextColor.RED));}
            return;
        }
        player.sendMessage(Component.text("/loot drawer place <mapId> <crateId> | remove | list <mapId>",NamedTextColor.YELLOW));
    }

    private void handleContainer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("使用法: /loot container set <mapId> <crateId> | /loot container remove", NamedTextColor.RED));
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

                RaidMap map = plugin.getRaidModule().getMap(mapId);
                if (map == null) {
                    player.sendMessage(Component.text("マップが見つかりません。", NamedTextColor.RED));
                    return;
                }

                map.addLootContainer(block.getLocation(), crateId);
                plugin.getRaidModule().saveMap(map);

                Container container = (Container) block.getState();
                container.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, crateId);
                container.update();

                player.sendMessage(Component.text("このコンテナをマップ " + mapId + " のルートコンテナ(Crate: " + crateId + ")として登録しました。", NamedTextColor.GREEN));
            }
            case "remove" -> {
                List<String> removedFrom = plugin.getLootManager().removeLootContainer(block);
                if (removedFrom.isEmpty()) {
                    player.sendMessage(Component.text("このコンテナはどのマップにもルートコンテナとして登録されていません。", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text("ルートコンテナの登録を外し、ブロックを消しました (" + String.join(", ", removedFrom) + ")", NamedTextColor.GREEN));
            }
        }
    }

    private void handleEgg(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /loot egg <mapId> <crateId>", NamedTextColor.RED));
            return;
        }
        String mapId = args[1];
        String crateId = args[2];

        ItemStack egg = plugin.getLootEggListener().createEgg(mapId, crateId);
        player.getInventory().addItem(egg);
        player.sendMessage(Component.text("ルート設置用エッグを付与しました: " + crateId + " (" + mapId + ")", NamedTextColor.GREEN));
    }

    private void handleDrawerEgg(Player player,String[] args) {
        if(args.length!=3) {
            player.sendMessage(Component.text("/loot draweregg <mapId> <crateId>",NamedTextColor.YELLOW));return;
        }
        if(plugin.getRaidModule().getMap(args[1])==null||plugin.getLootManager().getCrate(args[2])==null) {
            player.sendMessage(Component.text("マップIDとクレートIDを確認してください。",NamedTextColor.RED));return;
        }
        if(!player.getInventory().addItem(plugin.getLootEggListener().createDrawerEgg(args[1],args[2])).isEmpty()) {
            player.sendMessage(Component.text("インベントリに空きがありません。",NamedTextColor.RED));return;
        }
        player.sendMessage(Component.text("引き出し設置用エッグを付与しました: "+args[2]+" ("+args[1]+")",NamedTextColor.GREEN));
    }

    private void handleGroundEgg(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /loot groundegg <mapId> <crateId>", NamedTextColor.RED));
            return;
        }
        player.getInventory().addItem(plugin.getLootEggListener().createGroundEgg(args[1], args[2]));
        player.sendMessage(Component.text("地面ルート設置用エッグを付与しました: " + args[2] + " (" + args[1] + ")", NamedTextColor.GREEN));
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

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
            player.sendMessage("§c/loot <container|egg|refill>");
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "container" -> handleContainer(player, args);
            case "egg" -> handleEgg(player, args);
            case "refill" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("this")) {
                    Block b = player.getTargetBlockExact(5);
                    if (b != null && b.getState() instanceof Container container) {
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
                // 登録を外してからブロックを消す。中身は先に空にする (チェストは置き換えると中身を落とすため)
                List<String> removedFrom = new ArrayList<>();
                for (RaidMap map : plugin.getRaidModule().getMaps().values()) {
                    if (map.removeLootContainersAt(block.getLocation()) > 0) {
                        plugin.getRaidModule().saveMap(map);
                        removedFrom.add(map.getMapId());
                    }
                }
                if (removedFrom.isEmpty()) {
                    player.sendMessage(Component.text("このコンテナはどのマップにもルートコンテナとして登録されていません。", NamedTextColor.RED));
                    return;
                }
                if (block.getState() instanceof org.bukkit.block.Chest chest) {
                    chest.getBlockInventory().clear();
                } else {
                    ((Container) block.getState()).getInventory().clear();
                }
                block.setType(org.bukkit.Material.AIR);
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

    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (args.length == 1) return Arrays.asList("container", "egg", "refill");

        if (args[0].equalsIgnoreCase("container")) {
            if (args.length == 2) return Arrays.asList("set", "remove");
            if (args.length == 3 && args[1].equalsIgnoreCase("set")) return plugin.getRaidModule().getMapIds();
            if (args.length == 4 && args[1].equalsIgnoreCase("set")) return plugin.getLootManager().getCrateIds();
        }

        if (args[0].equalsIgnoreCase("egg")) {
            if (args.length == 2) return plugin.getRaidModule().getMapIds();
            if (args.length == 3) return plugin.getLootManager().getCrateIds();
        }

        if (args[0].equalsIgnoreCase("refill")) {
            if (args.length == 2) return Arrays.asList("all", "this");
        }

        return Collections.emptyList();
    }
}

package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public class HideoutCommand implements CommandExecutor, TabCompleter {
    private final HideoutModule module;

    public HideoutCommand(HideoutModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行可能です。");
            return true;
        }

        if (args.length == 0) {
            new HideoutGUI(module, player).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "visit":
                handleVisit(player);
                break;
            case "editor":
                handleEditor(player, args);
                break;
            case "save":
                handleSave(player, args);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleVisit(Player player) {
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        int index = data.getHideoutIndex();

        // 未割り当ての場合は新規割り当て
        if (index < 0) {
            // カウンター管理 (簡易的にUUIDのハッシュか何かでバラけさせるか、全プレイヤー数)
            index = Bukkit.getOfflinePlayers().length; 
            data.setHideoutIndex(index);
            module.getDataModule().saveAsync(player.getUniqueId());

            // 初回訪問: ベース（空き部屋）の配置
            java.io.File baseFile = new java.io.File(module.getPlugin().getDataFolder(), "structures/base.schem");
            if (baseFile.exists()) {
                Location center = module.getWorldManager().getPlayerCenter(index);
                module.getStructureService().placeSmart(baseFile, center, false); // 空気を無視しない (地盤を固めるため)
                player.sendMessage(Component.text("新しい隠れ家を準備しました！", NamedTextColor.YELLOW));
            }
        }

        Location loc = module.getWorldManager().getPlayerCenter(index);
        if (loc != null) {
            player.teleport(loc);
            player.sendMessage(Component.text("自分の隠れ家に移動しました。", NamedTextColor.GREEN));
        }
    }

    private void handleEditor(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("権限がありません。", NamedTextColor.RED));
            return;
        }

        // /hideout editor <module> <level>
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /hideout editor <module> <level>", NamedTextColor.RED));
            return;
        }

        String moduleType = args[1];
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("レベルは数字で指定してください。", NamedTextColor.RED));
            return;
        }

        // エディターワールドの座標計算 (簡易)
        // x = level * 100, z = moduleHash * 100
        int zOffset = Math.abs(moduleType.hashCode() % 10) * 100;
        Location loc = new Location(module.getWorldManager().getEditorWorld(), level * 100 + 0.5, 64, zOffset + 0.5);
        player.teleport(loc);
        player.sendMessage(Component.text("エディターモード: " + moduleType + " Lv." + level + " の区画に移動しました。", NamedTextColor.YELLOW));
    }

    private void handleSave(Player player, String[] args) {
        if (!player.isOp()) return;
        
        // /hideout save <module> <level>
        if (args.length < 3) {
            player.sendMessage(Component.text("使用法: /hideout save <module> <level>", NamedTextColor.RED));
            return;
        }

        String moduleType = args[1].toLowerCase();
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("レベルは数字で指定してください。", NamedTextColor.RED));
            return;
        }

        // 保存先ファイルパス (.schem)
        java.io.File file = new java.io.File(module.getPlugin().getDataFolder(), "structures/" + moduleType + "_" + level + ".schem");
        
        // アンカー (Origin) はプレイヤーの足元
        Location anchor = player.getLocation();
        
        // 広域スキャン範囲 (例: 半径32, 下に16、上に32)
        Location p1 = anchor.clone().add(-32, -16, -32);
        Location p2 = anchor.clone().add(32, 32, 32);

        module.getStructureService().saveOptimizedSchematic(file, anchor, p1, p2);
        player.sendMessage(Component.text("最適化保存(非同期)を開始しました: " + file.getName(), NamedTextColor.GREEN));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("--- Hideout Commands ---", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/hideout visit - 自分の隠れ家へ移動", NamedTextColor.YELLOW));
        if (player.isOp()) {
            player.sendMessage(Component.text("/hideout editor <module> <level> - 設備を編集する", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/hideout save <module> <level> - 編集内容を保存", NamedTextColor.YELLOW));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("visit", "editor", "save").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}

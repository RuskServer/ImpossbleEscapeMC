package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class ScavCommand implements CommandExecutor {

    public ScavCommand(ImpossbleEscapeMC plugin) {}

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        // 1. 真の実行者 (Callee) を特定する
        // /execute as ... で実行された場合、その対象を基準にする
        CommandSender effectiveSender = (sender instanceof ProxiedCommandSender proxied)
                ? proxied.getCallee()
                : sender;

        Location spawnLoc = null;

        // 2. 実行者がエンティティ（PlayerやMob、execute as対象）なら、その現在地を基準にする
        if (effectiveSender instanceof Entity entity) {
            spawnLoc = entity.getLocation();
        }

        // 3. 引数 (~ ~ ~) がある場合は、それを最優先で適用する
        // これにより /execute at や /execute positioned が「引数経由」で反映されるようになります
        if (args.length >= 3) {
            try {
                // spawnLocがnull（コンソール等）の場合のフォールバック
                World world = (spawnLoc != null) ? spawnLoc.getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
                double baseEntityX = (spawnLoc != null) ? spawnLoc.getX() : 0;
                double baseEntityY = (spawnLoc != null) ? spawnLoc.getY() : 0;
                double baseEntityZ = (spawnLoc != null) ? spawnLoc.getZ() : 0;

                double x = parseCoord(baseEntityX, args[0]);
                double y = parseCoord(baseEntityY, args[1]);
                double z = parseCoord(baseEntityZ, args[2]);

                spawnLoc = new Location(world, x, y, z);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "座標引数の解析に失敗しました。");
                return true;
            }
        }

        // 最終チェック
        if (spawnLoc == null) {
            sender.sendMessage(ChatColor.RED + "座標を特定できません。エンティティとして実行するか、座標を指定してください。");
            return true;
        }

        // SCAVスポーン実行
        try {
            ImpossbleEscapeMC.getInstance().getScavSpawner().spawnScav(spawnLoc);
            sender.sendMessage(ChatColor.GREEN + "SCAVを召喚しました。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "エラー: " + e.getMessage());
        }

        return true;
    }

    private double parseCoord(double base, String input) {
        if (input.startsWith("~")) {
            return (input.length() == 1) ? base : base + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }
}
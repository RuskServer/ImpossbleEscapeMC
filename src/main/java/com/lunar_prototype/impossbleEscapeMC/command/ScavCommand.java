package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScavCommand implements CommandExecutor {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, BukkitRunnable> heatmapTasks = new HashMap<>();

    public ScavCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "権限がありません。");
            return true;
        }

        // ヒートマップ表示トグル
        if (args.length > 0 && args[0].equalsIgnoreCase("heatmap")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行可能です。");
                return true;
            }

            UUID uuid = player.getUniqueId();
            if (heatmapTasks.containsKey(uuid)) {
                heatmapTasks.remove(uuid).cancel();
                player.sendMessage(ChatColor.YELLOW + "ヒートマップ表示をオフにしました。");
            } else {
                startHeatmapTask(player);
                player.sendMessage(ChatColor.GREEN + "ヒートマップ表示をオンにしました。");
            }
            return true;
        }

        // 既存のスポーン処理
        CommandSender effectiveSender = (sender instanceof ProxiedCommandSender proxied)
                ? proxied.getCallee()
                : sender;

        Location spawnLoc = null;
        if (effectiveSender instanceof Entity entity) {
            spawnLoc = entity.getLocation();
        }

        if (args.length >= 3) {
            try {
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

        if (spawnLoc == null) {
            sender.sendMessage(ChatColor.RED + "座標を特定できません。エンティティとして実行するか、座標を指定してください。");
            return true;
        }

        try {
            plugin.getScavSpawner().spawnScav(spawnLoc);
            sender.sendMessage(ChatColor.GREEN + "SCAVを召喚しました。");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "エラー: " + e.getMessage());
        }

        return true;
    }

    private void startHeatmapTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    heatmapTasks.remove(player.getUniqueId());
                    return;
                }

                Location pLoc = player.getLocation();
                int radius = 32; // 表示半径を少し広げる

                // 周辺のスコア付きデータを一括取得
                Map<Location, Float> scores = CombatHeatmapManager.getNearbyScores(pLoc, radius);

                for (Map.Entry<Location, Float> entry : scores.entrySet()) {
                    Location loc = entry.getKey();
                    float score = entry.getValue();

                    // スコアに応じて色を変える (正=赤=危険, 負=緑=安全)
                    org.bukkit.Color color = (score > 0) ? org.bukkit.Color.RED : org.bukkit.Color.GREEN;
                    float size = Math.min(1.5f, Math.abs(score) * 0.4f + 0.2f);

                    player.spawnParticle(
                            Particle.DUST,
                            loc.getX(), loc.getY(), loc.getZ(),
                            1, 0, 0, 0, 0,
                            new Particle.DustOptions(color, size)
                    );
                }
            }
        };
        task.runTaskTimer(plugin, 0, 5); // 0.25秒ごとに更新 (よりスムーズに)
        heatmapTasks.put(player.getUniqueId(), task);
    }

    private double parseCoord(double base, String input) {
        if (input.startsWith("~")) {
            return (input.length() == 1) ? base : base + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }
}
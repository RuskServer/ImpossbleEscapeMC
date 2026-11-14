package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Command(name = "scavspawn", description = "自分の位置にAI搭載SCAVを召喚します。", permission = "op")
public class ScavCommand implements BasicCommand {

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, BukkitRunnable> heatmapTasks = new HashMap<>();

    public ScavCommand(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!sender.isOp()) {
            Messages.chat(sender, "iem.admin.scavcommand.execute.notice_001", MessageText.of("iem.admin.scavcommand.execute.notice_001", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_001.text_01"))));
            return;
        }

        if(args.length>0&&args[0].equalsIgnoreCase("inspect")) {
            if(args.length!=1||!(sender instanceof Player player)) {
                Messages.chat(sender, "iem.admin.scavcommand.execute.notice_002", MessageText.of("iem.admin.scavcommand.execute.notice_002", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_002.text_01").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))));return;
            }
            Location eye=player.getEyeLocation();double distance=32;
            var wall=player.getWorld().rayTraceBlocks(eye,eye.getDirection(),distance,org.bukkit.FluidCollisionMode.NEVER,true);
            if(wall!=null)distance=eye.toVector().distance(wall.getHitPosition());
            var hit=player.getWorld().rayTraceEntities(eye,eye.getDirection(),Math.max(.001,distance),.25,e->com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner.isScav(e.getUniqueId()));
            var controller=hit==null||hit.getHitEntity()==null?null:com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner.getController(hit.getHitEntity().getUniqueId());
            if(controller==null)Messages.chat(player, "iem.admin.scavcommand.execute.notice_003", MessageText.of("iem.admin.scavcommand.execute.notice_003", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_003.text_01").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))));
            else for(String line:controller.inspectCombat())Messages.chat(player, "iem.admin.scavcommand.execute.notice_004", MessageText.of("iem.admin.scavcommand.execute.notice_004", MessageText.payload(net.kyori.adventure.text.Component.text(line,net.kyori.adventure.text.format.NamedTextColor.YELLOW))));
            return;
        }

        if(args.length>0&&args[0].equalsIgnoreCase("corpsecheck")) {
            if(args.length!=1||!(sender instanceof Player player)) {
                Messages.chat(sender, "iem.admin.scavcommand.execute.notice_005", MessageText.of("iem.admin.scavcommand.execute.notice_005", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_005.text_01").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))));return;
            }
            var module=plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.loot.ragdoll.ScavRagdollModule.class);
            if(module==null)Messages.chat(sender, "iem.admin.scavcommand.execute.notice_006", MessageText.of("iem.admin.scavcommand.execute.notice_006", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_006.text_01").color(net.kyori.adventure.text.format.NamedTextColor.RED))));
            else for(String line:module.diagnose(player))Messages.chat(player, "iem.admin.scavcommand.execute.notice_007", MessageText.of("iem.admin.scavcommand.execute.notice_007", MessageText.payload(net.kyori.adventure.text.Component.text(line,net.kyori.adventure.text.format.NamedTextColor.YELLOW))));
            return;
        }

        ScavSpawnArguments options;
        try {
            if(args.length>0&&args[0].equalsIgnoreCase("heatmap")) {
                if(args.length!=1)throw new IllegalArgumentException();
                options=null;
            }else options=ScavSpawnArguments.parse(args);
        }catch(IllegalArgumentException ex) {
            Messages.chat(sender, "iem.admin.scavcommand.execute.notice_008", MessageText.of("iem.admin.scavcommand.execute.notice_008", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_008.text_01").color(net.kyori.adventure.text.format.NamedTextColor.RED))));
            return;
        }

        // ヒートマップ表示トグル
        if (args.length > 0 && args[0].equalsIgnoreCase("heatmap")) {
            if (!(sender instanceof Player player)) {
                Messages.chat(sender, "iem.admin.scavcommand.execute.notice_009", MessageText.of("iem.admin.scavcommand.execute.notice_009", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_009.text_01"))));
                return;
            }

            UUID uuid = player.getUniqueId();
            if (heatmapTasks.containsKey(uuid)) {
                heatmapTasks.remove(uuid).cancel();
                Messages.chat(player, "iem.admin.scavcommand.execute.notice_010", MessageText.of("iem.admin.scavcommand.execute.notice_010", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_010.text_01"))));
            } else {
                startHeatmapTask(player);
                Messages.chat(player, "iem.admin.scavcommand.execute.notice_011", MessageText.of("iem.admin.scavcommand.execute.notice_011", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_011.text_01"))));
            }
            return;
        }

        // 既存のスポーン処理。/execute as … で実行された時は、その実体 (executor) の位置に出す
        CommandSender effectiveSender = stack.getExecutor() != null ? stack.getExecutor() : sender;

        Location spawnLoc = null;
        if (effectiveSender instanceof Entity entity) {
            spawnLoc = entity.getLocation();
        }

        if (options.coordinates().length == 3) {
            try {
                World world = (spawnLoc != null) ? spawnLoc.getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
                double baseEntityX = (spawnLoc != null) ? spawnLoc.getX() : 0;
                double baseEntityY = (spawnLoc != null) ? spawnLoc.getY() : 0;
                double baseEntityZ = (spawnLoc != null) ? spawnLoc.getZ() : 0;

                double x = ScavSpawnArguments.coordinate(baseEntityX, options.coordinates()[0]);
                double y = ScavSpawnArguments.coordinate(baseEntityY, options.coordinates()[1]);
                double z = ScavSpawnArguments.coordinate(baseEntityZ, options.coordinates()[2]);

                spawnLoc = new Location(world, x, y, z);
            } catch (Exception e) {
                Messages.chat(sender, "iem.admin.scavcommand.execute.notice_012", MessageText.of("iem.admin.scavcommand.execute.notice_012", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_012.text_01"))));
                return;
            }
        }

        if (spawnLoc == null) {
            Messages.chat(sender, "iem.admin.scavcommand.execute.notice_013", MessageText.of("iem.admin.scavcommand.execute.notice_013", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_013.text_01"))));
            return;
        }

        try {
            if (plugin.getScavSpawner().spawnScav(spawnLoc,options.level()) == null) {
                Messages.chat(sender, "iem.admin.scavcommand.execute.notice_014", MessageText.of("iem.admin.scavcommand.execute.notice_014", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_014.text_01"))));
                return;
            }
            Messages.chat(sender, "iem.admin.scavcommand.execute.notice_015", MessageText.of("iem.admin.scavcommand.execute.notice_015", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_015.text_01", (options.level()==null?"":" AI: "+options.level())).color(net.kyori.adventure.text.format.NamedTextColor.GREEN))));
        } catch (Exception e) {
            Messages.chat(sender, "iem.admin.scavcommand.execute.notice_016", MessageText.of("iem.admin.scavcommand.execute.notice_016", MessageText.payload(MessageText.of("iem.admin.scavcommand.execute.notice_016.text_01", e.getMessage()))));
        }

        return;
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

    @Override
    public @NotNull java.util.List<String> suggest(@NotNull CommandSourceStack stack,@NotNull String[] args) {
        if(!stack.getSender().isOp())return java.util.List.of();
        if(args.length==1)return java.util.List.of("low","mid","high","heatmap","corpsecheck","inspect").stream()
                .filter(v->v.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if(args.length==4&&ScavSpawnArguments.rank(args[0])==null)return java.util.List.of("low","mid","high").stream()
                .filter(v->v.startsWith(args[3].toLowerCase(java.util.Locale.ROOT))).toList();
        return java.util.List.of();
    }
}

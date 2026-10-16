package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * データパック銃 (Toi's Armory) の発射信号をプラグイン側に橋渡しする。
 * 撃った人の周囲への効果処理 (SCAVへの銃声通知・ヒートマップへの制圧記録・マズルフラッシュ) を受け持つ。
 */
public class GunListener implements org.bukkit.event.Listener {

    private final ImpossbleEscapeMC plugin;

    private static final String DATAPACK_FIRING_OBJECTIVE = "toisarm.timer.firing";
    private static final int DATAPACK_GUNSHOT_DEDUP_TICKS = 1;
    /** Toi's Armory の ADS の進み具合 (10000 = 構えていない、0 = 覗き切った) */
    private static final String DATAPACK_ADS_PROGRESS_OBJECTIVE = "toisarm.ads_progress";
    /** これより覗き込んでいればマズルフラッシュを ADS 用にする */
    private static final int ADS_FLASH_THRESHOLD = 5000;
    /** 銃身と一体のサプレッサーを持つ銃 */
    private static final java.util.Set<String> INTEGRALLY_SUPPRESSED_GUNS = java.util.Set.of("as_val");
    private static final double HEATMAP_SHOT_LINE_RANGE = 64.0;
    /** 弾がこの距離以内を通ったSCAVは至近弾として制圧される (データパック銃のニアミス判定と同じ範囲) */
    private static final double NEAR_MISS_RADIUS = 2.0;
    private static final float NEAR_MISS_SUPPRESSION = 0.1f;
    private static final double SCAV_GUNSHOT_HEARING_RANGE = 64.0;
    private static final double SCAV_GUNSHOT_HEARING_RANGE_SQUARED =
            SCAV_GUNSHOT_HEARING_RANGE * SCAV_GUNSHOT_HEARING_RANGE;
    private final Map<UUID, Integer> lastDatapackGunshotTick = new HashMap<>();

    public GunListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                pollDatapackGunshots();
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    /**
     * Toi's Armory marks an actual shot with toisarm.timer.firing (1 -> 0 -> -1).
     * Polling that score restores the AI gunshot signal without changing the datapack.
     */
    private void pollDatapackGunshots() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        org.bukkit.scoreboard.Objective firingObjective = manager.getMainScoreboard()
                .getObjective(DATAPACK_FIRING_OBJECTIVE);
        if (firingObjective == null) return;

        int currentTick = Bukkit.getCurrentTick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            org.bukkit.scoreboard.Score firingScore = firingObjective.getScore(player.getName());
            if (!firingScore.isScoreSet() || firingScore.getScore() < 0) continue;

            Integer lastTick = lastDatapackGunshotTick.get(player.getUniqueId());
            if (lastTick != null && currentTick - lastTick <= DATAPACK_GUNSHOT_DEDUP_TICKS) continue;

            lastDatapackGunshotTick.put(player.getUniqueId(), currentTick);
            notifyScavsOfPlayerGunshot(player);
            if (!com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.isGunner(player)) {
                // 当たった時に、撃った時に込めていた弾の種類を使う
                com.lunar_prototype.impossbleEscapeMC.item.DatapackAmmo.recordShot(player);
                handleDatapackShotLine(player);
                com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService.playMuzzleFlash(
                        player, isAimingDatapackGun(player), isSuppressed(player.getInventory().getItemInMainHand()));
            }
        }
    }

    private static boolean isAimingDatapackGun(Player player) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        org.bukkit.scoreboard.Objective objective = manager != null
                ? manager.getMainScoreboard().getObjective(DATAPACK_ADS_PROGRESS_OBJECTIVE) : null;
        if (objective == null) return false;
        org.bukkit.scoreboard.Score score = objective.getScore(player.getName());
        return score.isScoreSet() && score.getScore() < ADS_FLASH_THRESHOLD;
    }

    /** サプレッサーが付いている (またはサプレッサーと一体の) データパック銃か */
    private static boolean isSuppressed(org.bukkit.inventory.ItemStack gun) {
        String gunId = com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog.gunIdOf(gun);
        if (gunId == null) return false;
        if (INTEGRALLY_SUPPRESSED_GUNS.contains(gunId)) return true;
        org.bukkit.inventory.ItemStack muzzle = com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments.attached(gun).get("muzzle");
        String attachmentId = muzzle != null
                ? com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments.attachmentIdOf(muzzle) : null;
        return attachmentId != null && attachmentId.contains("suppressor");
    }

    private void notifyScavsOfPlayerGunshot(Player player) {
        player.setMetadata("last_fired_tick",
                new org.bukkit.metadata.FixedMetadataValue(plugin, Bukkit.getCurrentTick()));

        Location gunshotLocation = player.getLocation();
        // SCAVのデータパック銃のFakePlayerなら、撃ったSCAV自身は自分の銃声に反応させない
        Entity shooter = com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager.resolveShooter(player);
        for (Entity entity : player.getNearbyEntities(
                SCAV_GUNSHOT_HEARING_RANGE,
                SCAV_GUNSHOT_HEARING_RANGE,
                SCAV_GUNSHOT_HEARING_RANGE)) {
            if (!(entity instanceof Mob mob)) continue;
            if (entity.equals(shooter)) continue;
            if (!entity.getWorld().equals(gunshotLocation.getWorld())
                    || entity.getLocation().distanceSquared(gunshotLocation)
                    > SCAV_GUNSHOT_HEARING_RANGE_SQUARED) {
                continue;
            }

            ScavController controller = ScavSpawner.getController(mob.getUniqueId());
            if (controller != null) {
                // 味方のSCAVの銃声は「近くで戦闘が起きている」ことだけ伝わる (撃っている味方の位置を敵の位置とは扱わない)
                if (shooter != null && ScavSpawner.isScav(shooter.getUniqueId())) controller.onAllyGunfire();
                else controller.onSoundHeard(ScavController.SoundContact.gunshot(gunshotLocation));
            }
        }
    }

    /**
     * プレイヤーのデータパック銃の射線 (遮蔽物に当たるまで) を、ヒートマップに「制圧」として記録し、
     * 射線のそばにいたSCAVに至近弾の制圧を与える。
     * ここで扱わないとプレイヤーの射撃がヒートマップにも制圧にも反映されない
     */
    private void handleDatapackShotLine(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        var hit = eye.getWorld().rayTraceBlocks(eye, direction, HEATMAP_SHOT_LINE_RANGE, FluidCollisionMode.NEVER, true);
        double distance = (hit != null && hit.getHitPosition() != null)
                ? hit.getHitPosition().distance(eye.toVector())
                : HEATMAP_SHOT_LINE_RANGE;
        com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager.recordLine(eye, direction, distance, 1.0f);
        suppressScavsNearShotLine(eye, direction, distance);
    }

    /** 射線から NEAR_MISS_RADIUS 以内を弾が通ったSCAVに至近弾の制圧を与える */
    private void suppressScavsNearShotLine(Location eye, Vector direction, double distance) {
        Vector start = eye.toVector();
        Vector dir = direction.clone().normalize();
        Vector end = start.clone().add(dir.clone().multiply(distance));
        BoundingBox area = BoundingBox.of(start, end).expand(NEAR_MISS_RADIUS);
        for (Entity entity : eye.getWorld().getNearbyEntities(area)) {
            if (!(entity instanceof Mob mob)) continue;
            com.lunar_prototype.impossbleEscapeMC.ai.ScavController controller =
                    com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner.getController(mob.getUniqueId());
            if (controller == null) continue;
            Vector body = mob.getLocation().toVector().add(new Vector(0, mob.getHeight() / 2.0, 0));
            double along = Math.max(0.0, Math.min(distance, body.clone().subtract(start).dot(dir)));
            Vector closest = start.clone().add(dir.clone().multiply(along));
            if (closest.distanceSquared(body) <= NEAR_MISS_RADIUS * NEAR_MISS_RADIUS) {
                controller.addSuppression(NEAR_MISS_SUPPRESSION);
            }
        }
    }
}

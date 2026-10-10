package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ScavVision {
    private final Mob scav;
    private static final double MAX_VISION_DISTANCE = 96.0;
    private static final double FOV_ANGLE = 120.0;
    /** これより近い相手には、視野の外でも気付く (ブロック) */
    private static final double CLOSE_AWARENESS_DISTANCE = 4.0;
    /** しゃがまずに動いている相手には、視野の外でもこの距離まで気付く (動きと物音は視界の端でも分かる) */
    private static final double MOVING_AWARENESS_DISTANCE = 8.0;
    private LosSnapshot lastLosSnapshot = null;
    private float alertness = 0.25f;

    public static class LosRay {
        public String band;
        public String lateral;
        public double yawOffset;
        public double pitchOffset;
        public double maxDistance;
        public boolean hit;
        public boolean clear;
        public double distance;
        public String blockType;
        public String face;
        public double hitX;
        public double hitY;
        public double hitZ;
    }

    public static class LosSnapshot {
        public UUID targetId;
        public long tick;
        public boolean visible;
        public LosRay primaryRay;
        public List<LosRay> rays = Collections.emptyList();
    }

    public ScavVision(Mob scav) {
        this.scav = scav;
    }

    public LosSnapshot getLastLosSnapshot() {
        return lastLosSnapshot;
    }

    /** 新しく見つける時の視野角 (度、全幅)。警戒しているほど広い */
    public double fovDegrees() {
        return FOV_ANGLE * (0.8 + (0.25 * alertness));
    }

    /** 見える最大距離 (ブロック)。警戒しているほど遠い */
    public double maxVisionDistance() {
        return MAX_VISION_DISTANCE * (0.85 + (0.35 * alertness));
    }

    public void setAlertness(float alertness) {
        this.alertness = Math.max(0.0f, Math.min(1.0f, alertness));
    }

    public LivingEntity scanForTargets() {
        // 対象はプレイヤーだけなので、周囲の全エンティティではなくワールドのプレイヤーから探す
        Location eye = scav.getEyeLocation();
        double maxDistanceSquared = MAX_VISION_DISTANCE * MAX_VISION_DISTANCE;
        for (org.bukkit.entity.Player p : scav.getWorld().getPlayers()) {
            if (DatapackGunnerManager.isGunner(p)) continue;
            if (!isTargetable(p)) continue;
            if (p.getEyeLocation().distanceSquared(eye) > maxDistanceSquared) continue;
            if (checkVision(p)) return p;
        }
        return null;
    }

    // --- プレイヤーの移動速度 ---
    // サーバー側のプレイヤーの getVelocity() は歩いても変わらない (移動はクライアントが決めて位置だけ届く) ため、
    // 位置の変化から速度を求める。全SCAVで共有する
    private static final java.util.Map<UUID, double[]> motionSamples = new java.util.HashMap<>();
    private static final int MOTION_SAMPLE_INTERVAL_TICKS = 5;
    private static final int MOTION_SAMPLE_EXPIRE_TICKS = 200;

    /** 相手の水平移動速度 (ブロック/tick) */
    static double horizontalSpeed(LivingEntity target) {
        if (!(target instanceof org.bukkit.entity.Player)) {
            Vector velocity = target.getVelocity();
            return Math.hypot(velocity.getX(), velocity.getZ());
        }
        int now = Bukkit.getCurrentTick();
        Location loc = target.getLocation();
        // {x, z, 計測tick, 速度}
        double[] sample = motionSamples.get(target.getUniqueId());
        if (sample == null || now - (int) sample[2] > MOTION_SAMPLE_EXPIRE_TICKS) {
            if (motionSamples.size() > 256) {
                motionSamples.values().removeIf(old -> now - (int) old[2] > MOTION_SAMPLE_EXPIRE_TICKS);
            }
            motionSamples.put(target.getUniqueId(), new double[] { loc.getX(), loc.getZ(), now, 0.0 });
            return 0.0;
        }
        int elapsed = now - (int) sample[2];
        if (elapsed >= MOTION_SAMPLE_INTERVAL_TICKS) {
            sample[3] = Math.hypot(loc.getX() - sample[0], loc.getZ() - sample[1]) / elapsed;
            sample[0] = loc.getX();
            sample[1] = loc.getZ();
            sample[2] = now;
        }
        return sample[3];
    }

    /**
     * 狙う対象になるプレイヤーか。レイド中のプレイヤーはアドベンチャーモードに固定されるため、
     * サバイバルだけでなくアドベンチャーも対象にする
     */
    private static boolean isTargetable(org.bukkit.entity.Player player) {
        org.bukkit.GameMode mode = player.getGameMode();
        return mode == org.bukkit.GameMode.SURVIVAL || mode == org.bukkit.GameMode.ADVENTURE;
    }

    /**
     * 追跡中のターゲットが見えているか。
     * 一度捉えた相手は視野角・明るさに関係なく、視認距離内で遮蔽物が無ければ見えているとみなす
     * (移動中は頭が進行方向を向くため、視野角で判定すると目の前の相手でも見失ってしまう)
     */
    public boolean checkTrackingVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        if (eye.getWorld() != targetLoc.getWorld()) return false;
        double effectiveMaxVisionDistance = MAX_VISION_DISTANCE * (0.85 + (0.35 * alertness));
        if (eye.distanceSquared(targetLoc) > effectiveMaxVisionDistance * effectiveMaxVisionDistance) return false;
        return hasAdvancedLoS(target);
    }

    /** 新しくターゲットを見つける時の視認判定。視野角・明るさ・しゃがみ・発砲を考慮する */
    public boolean checkVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        double dist = eye.distance(targetLoc);
        double distanceScale = 0.85 + (0.35 * alertness);
        double effectiveMaxVisionDistance = MAX_VISION_DISTANCE * distanceScale;
        if (dist > effectiveMaxVisionDistance) return false;

        boolean isFiring = false;
        if (target.hasMetadata("last_fired_tick")) {
            int lastFired = target.getMetadata("last_fired_tick").get(0).asInt();
            if (Bukkit.getCurrentTick() - lastFired < 5) {
                isFiring = true;
            }
        }

        Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
        Vector direction = eye.getDirection();
        double angle = direction.angle(toTarget) * 180 / Math.PI;
        double relaxedFovScale = 0.8 + (0.25 * alertness);
        double currentFov = isFiring ? 200.0 : (FOV_ANGLE * relaxedFovScale);
        // すぐ近く (足音や気配が分かる距離) なら、視野の外でも気付く (壁越しは下の射線判定で弾く)。
        // しゃがまずに動いている相手は、もう少し遠くまで気付く
        boolean sneaking = target instanceof org.bukkit.entity.Player p && p.isSneaking();
        double awareness = !sneaking && horizontalSpeed(target) > 0.1 ? MOVING_AWARENESS_DISTANCE : CLOSE_AWARENESS_DISTANCE;
        if (angle > currentFov / 2.0 && dist > awareness) return false;

        double visibility = 1.0;
        int light = targetLoc.getBlock().getLightLevel();
        if (light < 4) visibility *= 0.2;
        else if (light < 8) visibility *= 0.5;
        else if (light < 12) visibility *= 0.8;

        if (sneaking) visibility *= 0.6;
        if (horizontalSpeed(target) > 0.1) visibility *= 1.2; // 歩き (約0.22ブロック/tick) 以上で動いている相手は目立つ
        if (isFiring) visibility = 5.0; 

        double effectiveRange = effectiveMaxVisionDistance * visibility;
        if (dist > effectiveRange) return false;

        return hasAdvancedLoS(target);
    }

    private boolean hasAdvancedLoS(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        World world = scav.getWorld();
        
        double h = target.getHeight();
        double w = target.getWidth() * 0.45;

        Vector toTarget = target.getLocation().toVector().subtract(eye.toVector()).normalize();
        Vector leftVec = new Vector(-toTarget.getZ(), 0, toTarget.getX()).normalize().multiply(w);
        Vector rightVec = leftVec.clone().multiply(-1);

        List<Location> checkPoints = new ArrayList<>();
        List<String> bands = new ArrayList<>();
        List<String> laterals = new ArrayList<>();
        Location base = target.getLocation();
        double[] heights = { h * 0.9, h * 0.5, h * 0.1 };
        String[] bandNames = { "upper", "middle", "lower" };

        for (int i = 0; i < heights.length; i++) {
            double y = heights[i];
            Location center = base.clone().add(0, y, 0);
            checkPoints.add(center);
            bands.add(bandNames[i]);
            laterals.add("center");
            checkPoints.add(center.clone().add(leftVec));
            bands.add(bandNames[i]);
            laterals.add("left");
            checkPoints.add(center.clone().add(rightVec));
            bands.add(bandNames[i]);
            laterals.add("right");
        }

        LosSnapshot snapshot = new LosSnapshot();
        snapshot.targetId = target.getUniqueId();
        snapshot.tick = Bukkit.getCurrentTick();
        List<LosRay> rays = new ArrayList<>();
        Vector eyeDir = eye.getDirection().normalize();

        for (int i = 0; i < checkPoints.size(); i++) {
            Location targetPoint = checkPoints.get(i);
            Vector direction = targetPoint.toVector().subtract(eye.toVector());
            double maxDist = direction.length();
            Vector norm = direction.clone().normalize();
            RayTraceResult result = world.rayTraceBlocks(eye, norm, maxDist, FluidCollisionMode.NEVER, true);

            LosRay ray = new LosRay();
            ray.band = bands.get(i);
            ray.lateral = laterals.get(i);
            ray.maxDistance = maxDist;
            ray.clear = result == null || result.getHitBlock() == null;
            ray.hit = result != null && result.getHitPosition() != null;
            ray.distance = ray.hit ? result.getHitPosition().distance(eye.toVector()) : maxDist;
            ray.yawOffset = normalizeAngle(vectorToYaw(norm) - eye.getYaw());
            ray.pitchOffset = vectorToPitch(norm) - eye.getPitch();
            if (result != null && result.getHitPosition() != null) {
                ray.hitX = result.getHitPosition().getX();
                ray.hitY = result.getHitPosition().getY();
                ray.hitZ = result.getHitPosition().getZ();
            }
            if (result != null && result.getHitBlock() != null) {
                ray.blockType = result.getHitBlock().getType().name();
            }
            if (result != null && result.getHitBlockFace() != null) {
                ray.face = result.getHitBlockFace().name();
            }

            rays.add(ray);

            if (ray.clear && snapshot.primaryRay == null) {
                snapshot.primaryRay = ray;
            }
        }

        snapshot.rays = rays;
        snapshot.visible = snapshot.primaryRay != null;
        lastLosSnapshot = snapshot;
        return snapshot.visible;
    }

    private float vectorToYaw(Vector v) {
        return (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ()));
    }

    private float vectorToPitch(Vector v) {
        return (float) Math.toDegrees(-Math.asin(v.getY()));
    }

    private float normalizeAngle(float angle) {
        float out = angle;
        while (out <= -180f) out += 360f;
        while (out > 180f) out -= 360f;
        return out;
    }
}

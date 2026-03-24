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

    public void setAlertness(float alertness) {
        this.alertness = Math.max(0.0f, Math.min(1.0f, alertness));
    }

    public LivingEntity scanForTargets() {
        for (Entity e : scav.getNearbyEntities(MAX_VISION_DISTANCE, 64, MAX_VISION_DISTANCE)) {
            if (e instanceof org.bukkit.entity.Player p) {
                if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL && checkVision(p)) return p;
            }
        }
        return null;
    }

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
        if (angle > currentFov / 2.0) return false;

        double visibility = 1.0;
        int light = targetLoc.getBlock().getLightLevel();
        if (light < 4) visibility *= 0.2;
        else if (light < 8) visibility *= 0.5;
        else if (light < 12) visibility *= 0.8;

        if (target instanceof org.bukkit.entity.Player p && p.isSneaking()) visibility *= 0.6;
        if (target.getVelocity().lengthSquared() > 0.05) visibility *= 1.2;
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

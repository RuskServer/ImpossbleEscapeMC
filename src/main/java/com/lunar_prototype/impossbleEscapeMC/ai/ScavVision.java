package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class ScavVision {
    private final Mob scav;
    private static final double MAX_VISION_DISTANCE = 96.0;
    private static final double FOV_ANGLE = 120.0;

    public ScavVision(Mob scav) {
        this.scav = scav;
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
        if (dist > MAX_VISION_DISTANCE) return false;

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
        double currentFov = isFiring ? 200.0 : FOV_ANGLE;
        if (angle > currentFov / 2.0) return false;

        double visibility = 1.0;
        int light = targetLoc.getBlock().getLightLevel();
        if (light < 4) visibility *= 0.2;
        else if (light < 8) visibility *= 0.5;
        else if (light < 12) visibility *= 0.8;

        if (target instanceof org.bukkit.entity.Player p && p.isSneaking()) visibility *= 0.6;
        if (target.getVelocity().lengthSquared() > 0.05) visibility *= 1.2;
        if (isFiring) visibility = 5.0; 

        double effectiveRange = MAX_VISION_DISTANCE * visibility;
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
        Location base = target.getLocation();
        double[] heights = { h * 0.9, h * 0.5, h * 0.1 };

        for (double y : heights) {
            Location center = base.clone().add(0, y, 0);
            checkPoints.add(center);
            checkPoints.add(center.clone().add(leftVec));
            checkPoints.add(center.clone().add(rightVec));
        }

        for (Location targetPoint : checkPoints) {
            Vector direction = targetPoint.toVector().subtract(eye.toVector());
            double maxDist = direction.length();
            var result = world.rayTraceBlocks(eye, direction.normalize(), maxDist, 
                org.bukkit.FluidCollisionMode.NEVER, true);
            if (result == null || result.getHitBlock() == null) return true;
        }
        return false;
    }
}

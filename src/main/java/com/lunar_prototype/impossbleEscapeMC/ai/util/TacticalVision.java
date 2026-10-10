package com.lunar_prototype.impossbleEscapeMC.ai.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 地形や視線を分析するユーティリティ
 */
public class TacticalVision {

    /** 見通せる地点を探す、相手のいそうな場所からの距離 (ブロック) と方向の数 */
    private static final double[] VANTAGE_RADII = {5.0, 8.0, 12.0};
    private static final int VANTAGE_DIRECTIONS = 16;
    /** 見通せる地点の、相手のいそうな場所から取りたい距離 (近すぎると鉢合わせ、遠すぎると見落とす) */
    private static final double VANTAGE_PREFERRED_RADIUS = 8.0;
    private static final double EYE_HEIGHT = 1.6;

    /**
     * 相手のいそうな場所を確認しに行く地点。今いる所から見通せなければ、その周りで立てて、そこから見通せる地点のうち
     * 近いものを選ぶ (壁の真正面ではなく、壁の端を回り込んで覗ける所)。見通せる地点がなければ、遮っている壁の手前
     *
     * @return 確認しに行く地点。今いる所から見通せるならnull
     */
    public static Location findSlicingPoint(Location current, Location lastSeen) {
        World world = current.getWorld();
        if (world == null || world != lastSeen.getWorld()) return null;
        Location threatEye = lastSeen.clone().add(0, EYE_HEIGHT, 0);
        if (clear(world, current.clone().add(0, EYE_HEIGHT, 0), threatEye)) {
            return null; // 遮蔽がない
        }

        Location best = null;
        double bestScore = Double.MAX_VALUE;
        for (double radius : VANTAGE_RADII) {
            for (int i = 0; i < VANTAGE_DIRECTIONS; i++) {
                double angle = 2.0 * Math.PI * i / VANTAGE_DIRECTIONS;
                Location stand = standable(world, lastSeen.getX() + Math.cos(angle) * radius, lastSeen.getBlockY(), lastSeen.getZ() + Math.sin(angle) * radius);
                if (stand == null || !clear(world, stand.clone().add(0, EYE_HEIGHT, 0), threatEye)) continue;
                double score = stand.distance(current) + 0.5 * Math.abs(radius - VANTAGE_PREFERRED_RADIUS);
                if (score < bestScore) {
                    bestScore = score;
                    best = stand;
                }
            }
        }
        if (best != null) return best;

        // 見通せる地点がない: 遮っている壁の2ブロック手前まで行く
        Vector direction = lastSeen.toVector().subtract(current.toVector());
        double dist = direction.length();
        if (dist < 1.0E-3) return null;
        RayTraceResult result = world.rayTraceBlocks(current, direction.multiply(1.0 / dist), dist, org.bukkit.FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitBlock() == null) return null;
        Location corner = result.getHitBlock().getLocation();
        Vector toCorner = corner.toVector().subtract(current.toVector()).normalize();
        return corner.clone().subtract(toCorner.multiply(2.0));
    }

    private static boolean clear(World world, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 1.0E-3) return true;
        RayTraceResult hit = world.rayTraceBlocks(from, direction.multiply(1.0 / length), length, org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null || hit.getHitBlock() == null;
    }

    /** 足と頭の高さが通れて足元に床がある場所 (同じ高さ・1段上・1段下の順) */
    private static Location standable(World world, double x, int y, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dy : new int[]{0, 1, -1}) {
            org.bukkit.block.Block feet = world.getBlockAt(bx, y + dy, bz);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                return new Location(world, bx + 0.5, y + dy, bz + 0.5);
            }
        }
        return null;
    }

    /**
     * プレイヤーが自分を見ているかどうかを視認判定
     */
    public static boolean isBeingWatched(Location myLoc, Location watcherLoc, Vector watcherDir) {
        Vector toSelf = myLoc.toVector().subtract(watcherLoc.toVector()).normalize();
        double dot = toSelf.dot(watcherDir.normalize());
        return dot > 0.95; // 視野角 約18度以内
    }
}

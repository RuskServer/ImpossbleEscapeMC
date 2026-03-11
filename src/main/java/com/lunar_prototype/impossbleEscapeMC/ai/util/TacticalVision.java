package com.lunar_prototype.impossbleEscapeMC.ai.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 地形や視線を分析するユーティリティ
 */
public class TacticalVision {

    /**
     * 指定した目的地までの経路で「視線が遮られる最後の角（Corner）」を特定する
     * 1ブロック単位でサンプリングしてエッジを特定
     */
    public static Location findSlicingPoint(Location current, Location lastSeen) {
        World world = current.getWorld();
        Vector direction = lastSeen.toVector().subtract(current.toVector());
        double dist = direction.length();
        direction.normalize();

        // 目的地までの視線をチェック
        RayTraceResult result = world.rayTraceBlocks(current, direction, dist, 
            org.bukkit.FluidCollisionMode.NEVER, true);

        if (result == null || result.getHitBlock() == null) {
            return null; // 遮蔽がない
        }

        // ヒットしたブロックの位置から少し手前（2ブロック）をクリアリング地点とする
        Location corner = result.getHitBlock().getLocation();
        Vector toCorner = corner.toVector().subtract(current.toVector()).normalize();
        
        return corner.clone().subtract(toCorner.multiply(2.0));
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

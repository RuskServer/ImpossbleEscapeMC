package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脱出範囲の表示: 脱出地点の範囲に入っている間、その人にだけ、範囲 (脱出の判定と同じ、中心から半径) の円周を
 * 地面に沿って緑の粒子で描く (RaidInstance が {@link #INTERVAL_TICKS} ごとに呼ぶ)。
 * 円周の点の高さは、地点ごとに最初に一度だけ、中心の少し上から真下へ調べて決める (天井のある所でも床に沿う)
 */
final class ExtractionRange {

    static final int INTERVAL_TICKS = 10;
    /** 円周の点の間隔 (ブロック) */
    private static final double SPACING = 0.6;
    /** 床を探す: 中心の高さ + START から、下へ REACH まで */
    private static final double START = 1.5, REACH = 6;
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(90, 220, 110), 1.0f);

    /** 地点ごとの円周の点 (床の少し上) */
    private final Map<RaidMap.ExtractionPoint, List<Location>> rings = new HashMap<>();

    /** player に、地点 point (中心 center) の範囲の円周を見せる */
    void show(Player player, RaidMap.ExtractionPoint point, Location center) {
        for (Location at : rings.computeIfAbsent(point, p -> ring(center, p.getRadius()))) {
            player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, DUST);
        }
    }

    /** 円周の点。壁の中・下に床が無い所は飛ばす */
    static List<Location> ring(Location center, double radius) {
        World world = center.getWorld();
        int count = Math.max(16, (int) Math.ceil(2 * Math.PI * radius / SPACING));
        List<Location> points = new ArrayList<>(count);
        Vector down = new Vector(0, -1, 0);
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Location from = new Location(world, center.getX() + radius * Math.cos(angle), center.getY() + START,
                    center.getZ() + radius * Math.sin(angle));
            if (from.getBlock().getType().isSolid()) continue;
            RayTraceResult hit = world.rayTraceBlocks(from, down, REACH, FluidCollisionMode.NEVER, true);
            if (hit == null) continue;
            points.add(hit.getHitPosition().toLocation(world).add(0, 0.1, 0));
        }
        return points;
    }
}

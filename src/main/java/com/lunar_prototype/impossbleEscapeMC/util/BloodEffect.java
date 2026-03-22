package com.lunar_prototype.impossbleEscapeMC.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class BloodEffect {

    private static final BlockData BLOOD_DATA = Material.REDSTONE_BLOCK.createBlockData();

    /**
     * リアルな血飛沫をスポーンさせます
     * @param loc 着弾地点
     * @param direction 弾の進行方向（nullの場合は全方位）
     * @param intensity ダメージ量に応じた強さ
     */
    public static void spawn(Location loc, Vector direction, double intensity) {
        World world = loc.getWorld();
        if (world == null) return;

        // 発生位置を下げて、首から胸のあたりから出るように調整（視界確保）
        loc.add(0, -0.2, 0);

        // パーティクル量を抑え、上限を設ける
        int particleCount = Math.min(10, (int) (intensity * 0.5 + 1)); 
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. メインの飛沫
        for (int i = 0; i < particleCount; i++) {
            Vector v = (direction != null) ? direction.clone().multiply(0.12) : new Vector(0, 0, 0);
            v.add(new Vector(
                    random.nextDouble(-0.1, 0.1),
                    random.nextDouble(-0.25, 0.05), // ほぼ下向き〜水平に飛ぶように
                    random.nextDouble(-0.1, 0.1)
            ));

            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    0, 
                    v.getX(), v.getY(), v.getZ(),
                    0.5, // スピードをさらに抑制
                    BLOOD_DATA
            );
        }

        // 2. 血霧（非常に薄く）
        world.spawnParticle(
                Particle.DUST,
                loc,
                Math.min(3, (int) (intensity / 8 + 1)),
                0.05, 0.05, 0.05,
                0.01,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 0, 0), 0.5f)
        );
    }
}
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

        int particleCount = (int) (intensity * 2); // 以前の5倍から2倍に減少
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. メインの飛沫
        for (int i = 0; i < particleCount; i++) {
            Vector v = (direction != null) ? direction.clone().multiply(0.2) : new Vector(0, 0, 0);
            v.add(new Vector(
                    random.nextDouble(-0.15, 0.15),
                    random.nextDouble(-0.1, 0.2),
                    random.nextDouble(-0.15, 0.15)
            ));

            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    0, 
                    v.getX(), v.getY(), v.getZ(),
                    0.8, // スピードも少し抑える
                    BLOOD_DATA
            );
        }

        // 2. 血霧（量を減らす）
        world.spawnParticle(
                Particle.DUST,
                loc,
                (int) (intensity / 2 + 1),
                0.1, 0.1, 0.1,
                0.05,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 0.8f)
        );

        // 3. 滴り（残留エフェクト）は削除
    }
}
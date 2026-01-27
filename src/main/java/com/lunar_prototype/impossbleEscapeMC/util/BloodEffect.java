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

        int particleCount = (int) (intensity * 5); // ダメージが大きいほど血が出る
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // 1. メインの飛沫（重めの破片）
        for (int i = 0; i < particleCount; i++) {
            // 弾の方向に少し偏らせたランダムベクトル
            Vector v = (direction != null) ? direction.clone().multiply(0.2) : new Vector(0, 0, 0);
            v.add(new Vector(
                    random.nextDouble(-0.2, 0.2),
                    random.nextDouble(-0.1, 0.3),
                    random.nextDouble(-0.2, 0.2)
            ));

            world.spawnParticle(
                    Particle.BLOCK,
                    loc,
                    0, // 数量を0にすると、直後のVectorが「速度」として扱われる
                    v.getX(), v.getY(), v.getZ(),
                    1, // 速度倍率
                    BLOOD_DATA
            );
        }

        // 2. 血霧（漂う細かい霧）
        // ARMA3のACE3 MODっぽい「プシュッ」という霧感
        world.spawnParticle(
                Particle.DUST,
                loc,
                (int) (intensity * 3),
                0.1, 0.1, 0.1, // 分散
                0.05, // スピード
                new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 1.2f)
        );

        // 3. 滴り（時間差で落ちる血）
        world.spawnParticle(
                Particle.DRIPPING_DRIPSTONE_LAVA, // 1.21ならこれが赤っぽくて使いやすい
                loc,
                (int) (intensity),
                0.2, 0.2, 0.2,
                0.01
        );
    }
}
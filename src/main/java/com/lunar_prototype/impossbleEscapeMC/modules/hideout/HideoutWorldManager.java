package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * 隠れ家用のワールド（プレイヤー用、エディター用）を管理するクラス
 */
public class HideoutWorldManager {
    public static final String PLAYER_WORLD_NAME = "hideout_world";
    public static final String EDITOR_WORLD_NAME = "hideout_editor_world";

    /**
     * 必要なワールドを初期化・ロードする
     */
    public void initWorlds() {
        createVoidWorld(PLAYER_WORLD_NAME);
        createVoidWorld(EDITOR_WORLD_NAME);
    }

    /**
     * 空（Void）のワールドを生成またはロードする
     */
    private void createVoidWorld(String name) {
        if (Bukkit.getWorld(name) != null) return;

        WorldCreator creator = new WorldCreator(name);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.generator(new ChunkGenerator() {
            @Override
            public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z, @NotNull ChunkGenerator.ChunkData chunkData) {
                // 何もしない（空）
            }

            @Override
            public boolean shouldGenerateStructures() { return false; }
        });

        World world = creator.createWorld();
        if (world != null) {
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setTime(6000); // 常に昼
            Bukkit.getLogger().info("Hideout world created: " + name);
        }
    }

    public World getPlayerWorld() {
        return Bukkit.getWorld(PLAYER_WORLD_NAME);
    }

    public World getEditorWorld() {
        return Bukkit.getWorld(EDITOR_WORLD_NAME);
    }

    /**
     * プレイヤーのインデックスに基づいた中心座標を取得
     */
    public Location getPlayerCenter(int index) {
        World world = getPlayerWorld();
        if (world == null) return null;
        return new Location(world, index * 1000.0 + 0.5, 64, 0.5);
    }
}

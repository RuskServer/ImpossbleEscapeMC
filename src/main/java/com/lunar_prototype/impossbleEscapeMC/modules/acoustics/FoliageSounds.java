package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

/**
 * 草・茂みをかき分ける音: 足の位置か体の高さに草むら・茂みがある間、動くと iem:foliage.&lt;grass|leaves&gt; を鳴らす。
 * 素材はリソースパックの IEM_shader/tools/footsteps.py (FOLIAGE) が作る
 */
public final class FoliageSounds {

    private FoliageSounds() {
    }

    /** 草の音にするブロック (名前。版によって無いものもあり、タグはサーバーが無いと読めないので名前で比べる) */
    private static final Set<String> GRASS = Set.of("SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
            "SHORT_DRY_GRASS", "TALL_DRY_GRASS", "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "SUGAR_CANE", "VINE",
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY",
            "WITHER_ROSE", "TORCHFLOWER", "PINK_PETALS", "WILDFLOWERS", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY");
    /** 茂み・葉の音にするブロック */
    private static final Set<String> LEAVES = Set.of("AZALEA", "FLOWERING_AZALEA", "SWEET_BERRY_BUSH", "BUSH", "FIREFLY_BUSH",
            "BAMBOO", "CAVE_VINES", "CAVE_VINES_PLANT");

    public static void play(LivingEntity entity, String kind, SoundCategory category, float volume, float pitch) {
        Location location = entity.getLocation();
        entity.getWorld().playSound(location, "iem:foliage." + kind, category, volume, pitch);
    }

    /** 体のまわりの草むら・茂みの種類 (無ければ null)。足の位置と、その 1 つ上 (腰から胸の高さ) を見る */
    public static String kindAround(LivingEntity entity) {
        Block feet = entity.getLocation().getBlock();
        String kind = kind(feet.getType());
        return kind != null ? kind : kind(feet.getRelative(0, 1, 0).getType());
    }

    static String kind(Material type) {
        String name = type.name();
        if (name.endsWith("_LEAVES") || LEAVES.contains(name)) return "leaves";
        if (GRASS.contains(name) || name.endsWith("_TULIP")) return "grass";
        return null;
    }
}

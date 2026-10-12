package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.LivingEntity;

import java.util.Set;

/**
 * 足音: 足元の材質と、その場所の響き方 ({@link AcousticMap}) で iem:step.&lt;材質&gt;.&lt;場所&gt; を鳴らす。
 * 素材はリソースパックの IEM_shader/tools/footsteps.py が作る (材質の名前は SOURCES と同じ)。
 * クライアントが鳴らすバニラの足音 (block.*.step) は、同じツールがリソースパックで消している
 */
public final class FootstepSounds {

    private FootstepSounds() {
    }

    /** 土の音にするブロック (バニラでは砂利・草の音のもの) */
    private static final Set<Material> DIRT = Set.of(Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.PODZOL,
            Material.FARMLAND, Material.DIRT_PATH, Material.MUD, Material.PACKED_MUD, Material.MUDDY_MANGROVE_ROOTS, Material.CLAY);

    public static void play(LivingEntity entity, SoundCategory category, float volume, float pitch) {
        Location location = entity.getLocation();
        String sound = "iem:step." + materialAt(entity) + "." + AcousticMap.zoneAt(location).id;
        entity.getWorld().playSound(location, sound, category, volume, pitch);
    }

    /** 足元の材質 */
    public static String materialAt(LivingEntity entity) {
        Block ground = ground(entity);
        boolean water = entity.isInWater() || isWater(entity.getLocation().getBlock());
        return material(ground.getType(), ground.getBlockSoundGroup().getStepSound().key().value(), water);
    }

    /** 立っているブロック。薄いもの (カーペット・雪の層など) は足の位置にあるので、そちらを先に見る */
    private static Block ground(LivingEntity entity) {
        Location location = entity.getLocation();
        Block feet = location.getBlock();
        if (!feet.isPassable() || Tag.WOOL_CARPETS.isTagged(feet.getType()) || feet.getType() == Material.SNOW) return feet;
        return location.clone().subtract(0, 0.2, 0).getBlock();
    }

    private static boolean isWater(Block block) {
        return block.getType() == Material.WATER || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged());
    }

    /**
     * 材質を決める。名前で分かる物 (コンクリ・タイル類・土類・カーペット) を先に、残りはバニラの足音の種類 (stepSound) で分ける
     *
     * @param stepSound バニラの足音の名前 (例 "block.wood.step")
     */
    static String material(Material type, String stepSound, boolean water) {
        if (water) return "water";
        String name = type.name();
        if (name.endsWith("_CARPET") || stepSound.contains("wool")) return "carpet";
        if ((name.contains("CONCRETE") && !name.contains("POWDER")) || name.contains("TERRACOTTA") || name.contains("QUARTZ")
                || name.contains("TILES") || name.startsWith("SMOOTH_") || name.startsWith("POLISHED_") || name.contains("PURPUR")) {
            return "tile";
        }
        if (DIRT.contains(type)) return "dirt";
        if (stepSound.contains("gravel")) return "gravel";
        if (stepSound.contains("snow")) return "snow";
        if (stepSound.contains("sand")) return "sand";
        if (stepSound.contains("grass") || stepSound.contains("moss") || stepSound.contains("azalea") || stepSound.contains("leaves")
                || stepSound.contains("vine") || stepSound.contains("nylium") || stepSound.contains("roots") || stepSound.contains("crop")) {
            return "grass";
        }
        if (stepSound.contains("wood") || stepSound.contains("bamboo") || stepSound.contains("ladder") || stepSound.contains("scaffolding")) {
            return "wood";
        }
        if (stepSound.contains("metal") || stepSound.contains("copper") || stepSound.contains("chain") || stepSound.contains("anvil")
                || stepSound.contains("iron") || stepSound.contains("netherite") || stepSound.contains("lantern") || stepSound.contains("heavy_core")) {
            return "metal";
        }
        return "stone";
    }
}

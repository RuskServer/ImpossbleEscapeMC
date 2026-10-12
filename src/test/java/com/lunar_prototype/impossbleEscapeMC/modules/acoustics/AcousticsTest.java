package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

/** 場所の判定のしきい値、マスの番号、足元の材質の決め方を確かめる。サーバーは要らない */
public final class AcousticsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // 空が見える (5 本中 3 本抜ける) なら、頭の上に何があっても屋外
        check(AcousticZone.classify(3, 5, 10, 64, 32) == AcousticZone.OUTDOOR, "open sky");
        check(AcousticZone.classify(2, 5, 1, 3, 4) == AcousticZone.INDOOR, "small room");
        check(AcousticZone.classify(0, 5, 2, 9, 6) == AcousticZone.HALL, "high ceiling");
        check(AcousticZone.classify(0, 5, 2, 4, 14) == AcousticZone.HALL, "wide floor");
        check(AcousticZone.classify(0, 5, 6, 3, 4) == AcousticZone.UNDERGROUND, "under thick ground");
        for (AcousticZone zone : AcousticZone.values()) check(AcousticZone.byId(zone.id) == zone, "byId " + zone);

        // マスの番号: 同じマスは同じ、隣のマス・負の座標も別
        check(AcousticMap.cellKey(0, 64, 0) == AcousticMap.cellKey(1, 65, 1), "same cell");
        Set<Long> keys = new HashSet<>();
        for (int x = -6; x <= 6; x += 2) for (int y = -64; y <= 318; y += 2) for (int z = -6; z <= 6; z += 2) keys.add(AcousticMap.cellKey(x, y, z));
        check(keys.size() == 7 * 192 * 7, "unique keys " + keys.size());
        check(AcousticMap.cellKey(-1, 64, 0) != AcousticMap.cellKey(1, 64, 0), "negative x");

        // 材質: 名前で分かる物を先に、残りはバニラの足音の種類で
        check(FootstepSounds.material(Material.GRAY_CONCRETE, "block.stone.step", false).equals("tile"), "concrete");
        check(FootstepSounds.material(Material.GRAY_CONCRETE_POWDER, "block.sand.step", false).equals("sand"), "concrete powder");
        check(FootstepSounds.material(Material.STONE, "block.stone.step", false).equals("stone"), "stone");
        check(FootstepSounds.material(Material.DIRT, "block.gravel.step", false).equals("dirt"), "dirt");
        check(FootstepSounds.material(Material.GRAVEL, "block.gravel.step", false).equals("gravel"), "gravel");
        check(FootstepSounds.material(Material.GRASS_BLOCK, "block.grass.step", false).equals("grass"), "grass");
        check(FootstepSounds.material(Material.OAK_PLANKS, "block.wood.step", false).equals("wood"), "planks");
        check(FootstepSounds.material(Material.IRON_BLOCK, "block.metal.step", false).equals("metal"), "iron");
        check(FootstepSounds.material(Material.COPPER_GRATE, "block.copper_grate.step", false).equals("metal"), "copper grate");
        check(FootstepSounds.material(Material.RED_CARPET, "block.wool.step", false).equals("carpet"), "carpet");
        check(FootstepSounds.material(Material.SNOW_BLOCK, "block.snow.step", false).equals("snow"), "snow");
        check(FootstepSounds.material(Material.STONE, "block.stone.step", true).equals("water"), "water");
        // 草むら・茂み
        check("grass".equals(FoliageSounds.kind(Material.TALL_GRASS)), "tall grass");
        check("grass".equals(FoliageSounds.kind(Material.FERN)), "fern");
        check("leaves".equals(FoliageSounds.kind(Material.OAK_LEAVES)), "leaves");
        check("leaves".equals(FoliageSounds.kind(Material.SWEET_BERRY_BUSH)), "berry bush");
        check(FoliageSounds.kind(Material.STONE) == null && FoliageSounds.kind(Material.AIR) == null, "not foliage");
        // 背景: 屋外・屋内は昼夜で分け、広い屋内・地下は分けない。夜は 13000〜22999
        check(AmbienceService.bed(AcousticZone.OUTDOOR, false).equals("outdoor_day"), "outdoor day");
        check(AmbienceService.bed(AcousticZone.INDOOR, true).equals("indoor_night"), "indoor night");
        check(AmbienceService.bed(AcousticZone.HALL, true).equals("hall"), "hall");
        check(AmbienceService.bed(AcousticZone.UNDERGROUND, false).equals("underground"), "underground");
        check(!AmbienceService.isNight(6000) && AmbienceService.isNight(18000) && !AmbienceService.isNight(23500) && AmbienceService.isNight(13000 + 24000 * 5), "night range");
        System.out.println("AcousticsTest: zone thresholds, cell keys, footstep materials and ambience beds passed");
    }
}

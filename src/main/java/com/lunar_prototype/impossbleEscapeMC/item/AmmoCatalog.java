package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 弾の定義。
 * <p>
 * 弾のIDはアイテム (PDC の item_id)・ルート表・トレーダー・銃に込めた弾の記録が参照するため変えない
 * (以前の ammo/*.yml のキーと同じ)。口径は銃 (プラグイン銃の caliber、データパック銃の ammo_type) と同じ書き方にする
 * (大文字小文字は区別しない。データパックの 12ga は {@link DatapackAmmo} が 12x70mm に読み替える)。
 * 威力は同じ口径のいちばん弱い弾が基準 (データパック銃では、その比をデータパックのダメージに掛ける)
 */
final class AmmoCatalog {

    static final String CAL_545X39 = "5.45x39mm";
    static final String CAL_556X45 = "5.56x45mm";
    static final String CAL_762X51 = "7.62x51mm";
    static final String CAL_762X54R = "7.62x54mmR";
    static final String CAL_9X19 = "9x19mm";
    static final String CAL_9X39 = "9x39mm";
    static final String CAL_45ACP = ".45ACP";
    static final String CAL_12X70 = "12x70mm";

    private AmmoCatalog() {
    }

    static List<AmmoDefinition> create() {
        List<AmmoDefinition> ammo = new ArrayList<>();

        // --- ライフル弾 ---
        ammo.add(ammo("545x39_ps", CAL_545X39, "5.45x39mm PS弾", 12.0, 3, 12)); // 60発スタックで720g
        ammo.add(ammo("545x39_bs", CAL_545X39, "5.45x39mm BS弾", 13.5, 5, 12)); // 貫通弾だが口径が同じため重量は維持
        ammo.add(ammo("556x45_hp", CAL_556X45, "5.56x45mm HP弾", 11.0, 3, 13)); // 5.45mmより僅かに重い
        ammo.add(ammo("762x51_fmj_28", CAL_762X51, "7.62x51mm FMJ-28弾", 15.0, 3, 25)); // 大口径のため小口径の約2倍
        ammo.add(ammo("762x54r_lps_gzh", CAL_762X54R, "7.62x54mmR LPS Gzh弾", 16.0, 4, 22)); // SVD用の狙撃弾。7.62x51mmより一段強い
        ammo.add(ammo("9x39_sp5", CAL_9X39, "9x39mm SP-5弾", 13.0, 3, 23)); // 亜音速の重い弾頭。威力は高め、貫通は普通

        // --- 拳銃弾 ---
        ammo.add(ammo("9x19_pst_gzh", CAL_9X19, "9x19mm Pst gzh弾", 7.0, 2, 12)); // .45ACPより弱い拳銃弾
        ammo.add(ammo("45acp_match_fmj_26", CAL_45ACP, ".45ACP Match FMJ-26弾", 8.0, 3, 21)); // 拳銃弾としては重い

        // --- ショットシェル ---
        ammo.add(ammo("12x70_steel_buckshot", CAL_12X70, "12x70 7.5mm スチールバックショット", 4.0, 1, 45)); // 火薬量と粒弾のため最も重い

        return ammo;
    }

    /**
     * @param damage    基礎ダメージ
     * @param ammoClass 貫通クラス (1-6)
     * @param weight    1発の重さ (グラム)
     */
    private static AmmoDefinition ammo(String id, String caliber, String displayName, double damage, int ammoClass, int weight) {
        AmmoDefinition ammo = new AmmoDefinition();
        ammo.id = id;
        ammo.caliber = caliber;
        ammo.displayName = displayName;
        ammo.damage = damage;
        ammo.ammoClass = ammoClass;
        ammo.weight = weight;
        ammo.material = Material.STICK.name();
        ammo.rarity = 1;
        ammo.customModelData = 0;
        return ammo;
    }
}

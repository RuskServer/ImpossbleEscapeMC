package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 弾の定義。口径ごとに貫通クラス1〜6を1種類ずつ揃える。
 * <p>
 * 弾のIDはアイテム (PDC の item_id)・ルート表・トレーダー・銃に込めた弾の記録が参照するため変えない
 * (最初の6種は以前の ammo/*.yml のキーと同じ)。口径は銃 (プラグイン銃の caliber、データパック銃の ammo_type) と同じ書き方にする
 * (大文字小文字は区別しない。データパックの 12ga は {@link DatapackAmmo} が 12x70mm に読み替える)。
 * <ul>
 *   <li>威力は口径ごとの基準弾 ({@link #reference}、データパック銃のダメージに合わせた弾) との比で効く
 *       (データパック銃では、その比をデータパックのダメージに掛ける)</li>
 *   <li>クラス1〜2 (ホローポイント・ソフトポイントなど) は体へのダメージが高いが、防具にはほぼ止められる。
 *       クラス3〜4 は標準弾と貫通力を上げた弾。クラス5 は2030年代の量産型の改良弾、クラス6 は最新の特殊弾
 *       (ロシア系の口径は Kopye、西側の口径は Lancer)。高いクラスほど手に入りにくい</li>
 *   <li>レアリティ (名前の色・ツールチップ) は貫通クラスで決める ({@link #rarityOf})</li>
 * </ul>
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

        // --- 5.45x39mm (基準: PS弾。1発12g) ---
        ammo.add(ammo("545x39_hp", CAL_545X39, "5.45x39mm HP弾", 15.0, 1, 12,
                "ホローポイント。体には深い傷を残すが、防具にはほぼ止められる"));
        ammo.add(ammo("545x39_sp", CAL_545X39, "5.45x39mm SP弾", 13.4, 2, 12,
                "先端の鉛が露出したソフトポイントの狩猟弾"));
        ammo.add(reference(ammo("545x39_ps", CAL_545X39, "5.45x39mm PS弾", 12.0, 3, 12,
                "鋼芯の標準弾"))); // 60発スタックで720g
        ammo.add(ammo("545x39_pp", CAL_545X39, "5.45x39mm PP弾", 12.2, 4, 12,
                "熱処理した鋼芯で貫通力を上げた弾"));
        ammo.add(ammo("545x39_bs", CAL_545X39, "5.45x39mm BS弾", 13.5, 5, 12,
                "炭化タングステン芯の徹甲弾")); // 貫通弾だが口径が同じため重量は維持
        ammo.add(ammo("545x39_7n55", CAL_545X39, "5.45x39mm 7N55 Kopye弾", 13.8, 6, 10,
                "2030年代のロシア製。超硬合金の細い弾芯とポリマー複合ケースで、重防具も撃ち抜く")); // ポリマーケースで軽い

        // --- 5.56x45mm (基準: HP弾。1発13g) ---
        ammo.add(ammo("556x45_rrlp", CAL_556X45, "5.56x45mm RRLP弾", 13.8, 1, 12,
                "銅粉を固めた破砕弾。跳弾しにくく、当たると砕けて傷を広げる"));
        ammo.add(ammo("556x45_m193", CAL_556X45, "5.56x45mm M193弾", 12.3, 2, 12,
                "旧式の鉛芯弾。軽く速いが、硬い物には弱い"));
        ammo.add(reference(ammo("556x45_hp", CAL_556X45, "5.56x45mm HP弾", 11.0, 3, 13,
                "弾頭の重いホローポイント"))); // 5.45mmより僅かに重い
        ammo.add(ammo("556x45_m855", CAL_556X45, "5.56x45mm M855弾", 11.2, 4, 13,
                "鋼の先端を持つNATO標準弾"));
        ammo.add(ammo("556x45_m855a2", CAL_556X45, "5.56x45mm M855A2弾", 11.6, 5, 13,
                "2030年代のNATO標準弾。無鉛の強化貫通弾 (EPR) を改良したもの"));
        ammo.add(ammo("556x45_lancer", CAL_556X45, "5.56x45mm LNC-56 Lancer弾", 12.1, 6, 10,
                "高圧ハイブリッドケースで撃ち出す炭化タングステン弾芯。2030年代後半の最新型"));

        // --- 7.62x51mm (基準: FMJ-28弾。1発25g、小口径の約2倍) ---
        ammo.add(ammo("762x51_sp", CAL_762X51, "7.62x51mm SP弾", 18.8, 1, 25,
                "大型獣用のソフトポイント。大きく潰れて止める"));
        ammo.add(ammo("762x51_m80", CAL_762X51, "7.62x51mm M80弾", 16.8, 2, 25,
                "鉛芯の旧NATO標準弾"));
        ammo.add(reference(ammo("762x51_fmj_28", CAL_762X51, "7.62x51mm FMJ-28弾", 15.0, 3, 25,
                "鋼芯入りのフルメタルジャケット")));
        ammo.add(ammo("762x51_m62", CAL_762X51, "7.62x51mm M62曳光弾", 15.2, 4, 25,
                "弾道が光る曳光弾。鋼芯で貫通力も高い"));
        ammo.add(ammo("762x51_m80a2", CAL_762X51, "7.62x51mm M80A2弾", 15.8, 5, 25,
                "2030年代の西側標準弾。硬化鋼の先端と銅の弾体を組み合わせた無鉛弾"));
        ammo.add(ammo("762x51_lancer", CAL_762X51, "7.62x51mm LNC-76 Lancer弾", 16.5, 6, 21,
                "高圧ハイブリッドケースと炭化タングステン弾芯。重いプレートも割る"));

        // --- 7.62x54mmR (基準: LPS Gzh弾。1発22g) ---
        ammo.add(ammo("762x54r_hp_bt", CAL_762X54R, "7.62x54mmR HP BT弾", 20.0, 1, 22,
                "狩猟用のホローポイント・ボートテイル"));
        ammo.add(ammo("762x54r_t46m", CAL_762X54R, "7.62x54mmR T-46M曳光弾", 17.6, 2, 22,
                "旧式の曳光弾"));
        ammo.add(ammo("762x54r_ps", CAL_762X54R, "7.62x54mmR PS弾", 16.4, 3, 22,
                "鋼芯の標準弾"));
        ammo.add(reference(ammo("762x54r_lps_gzh", CAL_762X54R, "7.62x54mmR LPS Gzh弾", 16.0, 4, 22,
                "SVD用の軽量鋼芯弾"))); // 7.62x51mmより一段強い
        ammo.add(ammo("762x54r_7n44", CAL_762X54R, "7.62x54mmR 7N44弾", 16.8, 5, 22,
                "2030年代のロシア製狙撃弾。焼き入れした鋼芯を精密に揃えたもの"));
        ammo.add(ammo("762x54r_7n58", CAL_762X54R, "7.62x54mmR 7N58 Kopye弾", 17.6, 6, 19,
                "超硬合金の弾芯とポリマー複合ケース。ロシア最新の狙撃用徹甲弾"));

        // --- 9x39mm (基準: SP-5弾。1発23g) ---
        ammo.add(ammo("9x39_hp", CAL_9X39, "9x39mm HP弾", 16.0, 1, 23,
                "亜音速のホローポイント。至近距離の柔らかい目標向け"));
        ammo.add(ammo("9x39_pab9", CAL_9X39, "9x39mm PAB-9弾", 14.4, 2, 23,
                "安価な輸出向けの弾"));
        ammo.add(reference(ammo("9x39_sp5", CAL_9X39, "9x39mm SP-5弾", 13.0, 3, 23,
                "亜音速の重い弾頭の標準弾"))); // 威力は高め、貫通は普通
        ammo.add(ammo("9x39_sp6", CAL_9X39, "9x39mm SP-6弾", 13.2, 4, 23,
                "硬化鋼芯の亜音速徹甲弾"));
        ammo.add(ammo("9x39_7n45", CAL_9X39, "9x39mm 7N45弾", 13.7, 5, 23,
                "2030年代のロシア製。タングステン合金芯の亜音速徹甲弾"));
        ammo.add(ammo("9x39_7n56", CAL_9X39, "9x39mm 7N56 Kopye弾", 14.3, 6, 20,
                "超硬合金の弾芯とポリマー複合ケース。音もなく重防具を貫く"));

        // --- 9x19mm (基準: Pst gzh弾。1発12g) ---
        ammo.add(ammo("9x19_rip", CAL_9X19, "9x19mm RIP弾", 8.8, 1, 11,
                "命中すると花弁状に裂ける破砕弾"));
        ammo.add(reference(ammo("9x19_pst_gzh", CAL_9X19, "9x19mm Pst gzh弾", 7.0, 2, 12,
                "鋼芯の標準弾"))); // .45ACPより弱い拳銃弾
        ammo.add(ammo("9x19_ap63", CAL_9X19, "9x19mm AP 6.3弾", 6.9, 3, 12,
                "硬化鋼芯の徹甲弾"));
        ammo.add(ammo("9x19_7n21", CAL_9X19, "9x19mm 7N21弾", 7.0, 4, 12,
                "高圧装薬 (+P+) の徹甲弾"));
        ammo.add(ammo("9x19_pbpm", CAL_9X19, "9x19mm PBP-M弾", 7.3, 5, 12,
                "2030年代に改良された高圧徹甲弾"));
        ammo.add(ammo("9x19_lancer", CAL_9X19, "9x19mm LNC-9 Lancer弾", 7.7, 6, 10,
                "拳銃弾で重防具に届く数少ない弾。タングステン弾芯とポリマーケース"));

        // --- .45ACP (基準: Match FMJ-26弾。1発21g、拳銃弾としては重い) ---
        ammo.add(ammo("45acp_hydra_shok", CAL_45ACP, ".45ACP Hydra-Shok弾", 10.0, 1, 21,
                "中心に芯のあるホローポイント"));
        ammo.add(ammo("45acp_lasermatch", CAL_45ACP, ".45ACP Lasermatch FMJ弾", 8.8, 2, 21,
                "競技用のフルメタルジャケット"));
        ammo.add(reference(ammo("45acp_match_fmj_26", CAL_45ACP, ".45ACP Match FMJ-26弾", 8.0, 3, 21,
                "精度を揃えた競技用の標準弾")));
        ammo.add(ammo("45acp_ap", CAL_45ACP, ".45ACP AP弾", 8.1, 4, 21,
                "鋼芯の徹甲弾"));
        ammo.add(ammo("45acp_m1302", CAL_45ACP, ".45ACP M1302弾", 8.4, 5, 21,
                "2030年代の西側製。硬化鋼の先端を持つ無鉛の強化貫通弾"));
        ammo.add(ammo("45acp_lancer", CAL_45ACP, ".45ACP LNC-45 Lancer弾", 8.9, 6, 17,
                "重い弾頭にタングステン弾芯を収めた最新型"));

        // --- 12x70mm (基準: 7.5mm スチールバックショット。1粒あたりのダメージ。1発45g、火薬量と粒弾のため最も重い) ---
        ammo.add(reference(ammo("12x70_steel_buckshot", CAL_12X70, "12x70 7.5mm スチールバックショット", 4.0, 1, 45,
                "鋼の小粒を詰めた散弾")));
        ammo.add(ammo("12x70_magnum_buckshot", CAL_12X70, "12x70 8.5mm マグナムバックショット", 4.4, 2, 48,
                "大粒の鉛を強装薬で撃ち出す散弾"));
        ammo.add(ammo("12x70_flechette", CAL_12X70, "12x70 フレシェット弾", 3.9, 3, 42,
                "鋼の矢弾を詰めた散弾"));
        ammo.add(ammo("12x70_tss", CAL_12X70, "12x70 TSS タングステン粒弾", 4.0, 4, 45,
                "高密度のタングステン粒を詰めた散弾"));
        ammo.add(ammo("12x70_fx5", CAL_12X70, "12x70 FX-5 硬化フレシェット弾", 4.1, 5, 42,
                "2030年代の西側製。焼き入れした細い矢弾で防具を抜く"));
        ammo.add(ammo("12x70_lancer", CAL_12X70, "12x70 LNC-12 Lancer フレシェット弾", 4.3, 6, 38,
                "炭化タングステンの矢弾をポリマーケースに収めた最新型"));

        return ammo;
    }

    /**
     * 貫通クラスに応じたレアリティ: クラス1〜2は1 (白)、3は2 (緑)、4は3 (青)、5は4 (紫)、6は5 (金)。
     * 貫通力の高い弾ほど名前の色で分かるように
     */
    static int rarityOf(int ammoClass) {
        return ammoClass <= 2 ? 1 : Math.min(5, ammoClass - 1);
    }

    /** 口径の基準弾にする (データパック銃のダメージはこの弾に合わせてある) */
    private static AmmoDefinition reference(AmmoDefinition ammo) {
        ammo.reference = true;
        return ammo;
    }

    /**
     * @param damage      基礎ダメージ
     * @param ammoClass   貫通クラス (1-6)
     * @param weight      1発の重さ (グラム)
     * @param description 説明 (ツールチップに出す)
     */
    private static AmmoDefinition ammo(String id, String caliber, String displayName, double damage, int ammoClass, int weight,
                                       String description) {
        AmmoDefinition ammo = new AmmoDefinition();
        ammo.id = id;
        ammo.caliber = caliber;
        ammo.displayName = displayName;
        ammo.damage = damage;
        ammo.ammoClass = ammoClass;
        ammo.weight = weight;
        ammo.description = description;
        ammo.material = Material.STICK.name();
        ammo.rarity = rarityOf(ammoClass);
        ammo.customModelData = 0;
        return ammo;
    }
}

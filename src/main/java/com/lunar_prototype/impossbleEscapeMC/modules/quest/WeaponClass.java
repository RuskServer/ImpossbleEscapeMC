package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;

import java.util.Map;

/**
 * クエストの「この種類の銃で倒す」に使う銃の種類。
 * 今あるデータパックの銃は {@link #KNOWN} で決め、知らない銃 (ガンパックで増えた物) は性能から決める
 */
public enum WeaponClass {
    PISTOL("ハンドガン"),
    SMG("サブマシンガン"),
    ASSAULT_RIFLE("アサルトライフル"),
    MARKSMAN("狙撃銃"),
    SHOTGUN("ショットガン");

    /** データパックの銃 ID → 種類 */
    static final Map<String, WeaponClass> KNOWN = Map.ofEntries(
            Map.entry("usp45", PISTOL),
            Map.entry("mp5", SMG), Map.entry("uzi", SMG), Map.entry("scorpion_evo3", SMG),
            Map.entry("ak74", ASSAULT_RIFLE), Map.entry("m4a1", ASSAULT_RIFLE), Map.entry("scar_h", ASSAULT_RIFLE),
            Map.entry("as_val", ASSAULT_RIFLE),
            Map.entry("m700", MARKSMAN), Map.entry("svd", MARKSMAN),
            Map.entry("mossberg_590", SHOTGUN));

    private final String displayName;

    WeaponClass(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 銃 ID の種類 (データパックの銃でなければ null) */
    public static WeaponClass of(String gunId) {
        if (gunId == null) return null;
        WeaponClass known = KNOWN.get(gunId);
        if (known != null) return known;
        DatapackGunProfile profile = DatapackGunCatalog.get(gunId);
        return profile != null ? of(profile) : null;
    }

    /** 性能からの種類 (散弾はショットガン、射程 300 以上は狙撃銃、射程 95 以下は連射できればサブマシンガン・できなければハンドガン) */
    static WeaponClass of(DatapackGunProfile profile) {
        if (profile.projectilesPerShot() > 1) return SHOTGUN;
        if (profile.maxRange() >= 300) return MARKSMAN;
        if (profile.maxRange() <= 95) return profile.automatic() ? SMG : PISTOL;
        return ASSAULT_RIFLE;
    }
}

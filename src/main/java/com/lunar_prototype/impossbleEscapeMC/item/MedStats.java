package com.lunar_prototype.impossbleEscapeMC.item;

public class MedStats {
    public double heal;
    public int useTime;
    public boolean bleedStop;

    // --- 長押し継続使用の設定 ---
    public boolean continuous;           // 長押しで使い続けるタイプか
    public double healPerUse;           // 1回(0.5s)ごとの回復量
    public int durabilityPerUse;        // 1回(0.5s)ごとの耐久消費量
    public int usingCustomModelData;    // 使用中のモデルID
}

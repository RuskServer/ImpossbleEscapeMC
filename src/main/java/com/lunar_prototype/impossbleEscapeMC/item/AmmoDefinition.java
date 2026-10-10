package com.lunar_prototype.impossbleEscapeMC.item;

public class AmmoDefinition {
    public String id;          // e.g. ammo_545x39_ps
    public String caliber;     // e.g. 5.45x39mm (銃側の設定と一致させる)
    public double damage;      // 弾丸の基礎ダメージ
    public int ammoClass;      // 貫通クラス (1-6)
    public String displayName; // 表示名
    public String material;
    public int rarity;
    public int weight; // weight in grams
    public int customModelData;
    public String description; // ツールチップの説明 (無ければnull)
    public boolean reference;  // 口径の基準弾 (データパック銃のダメージはこの弾に合わせてある)
}
package com.lunar_prototype.impossbleEscapeMC.item;

public class AmmoDefinition {
    public String id;          // e.g. ammo_545x39_ps
    public String caliber;     // e.g. 5.45x39mm (銃側の設定と一致させる)
    public double damage;      // 弾丸の基礎ダメージ
    public int ammoClass;      // 貫通クラス (1-6)
    public String displayName; // 表示名
    public String material;
    public int rarity;
}
package com.lunar_prototype.impossbleEscapeMC.item;

public class GunStats {
    public double damage;
    public double recoil;
    public int rpm;
    public int magSize;
    public String fireMode; // "SEMI" or "AUTO"
    public int customModelData; // 【追加】 ベースとなるモデル番号 (例: 20)
    public int reloadTime; // 【追加】リロード時間 (ミリ秒)
    public String shotSound;
    public String caliber;
}

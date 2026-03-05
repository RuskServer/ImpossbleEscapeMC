package com.lunar_prototype.impossbleEscapeMC.item;

public class GunStats {
    public double damage;
    public double recoil;
    public int rpm;
    public int magSize;
    public String fireMode; // "SEMI" or "AUTO"
    public int customModelData; // 【追加】 ベースとなるモデル番号 (例: 20)
    public int reloadTime; // 【追加】リロード時間 (ミリ秒)
    public int adsTime; // 【追加】ADS時間 (ミリ秒)
    public int boltingTime; // 【追加】手動コッキングの時間 (ミリ秒)
    public String shotSound;
    public String caliber;
    public String boltType = "CLOSED"; // OPEN, CLOSED, BOLT_ACTION, PUMP_ACTION
    public java.util.List<String> defaultAttachments; // 【追加】デフォルトのアタッチメント構成
    public AnimationStats reloadAnimation;
    public AnimationStats tacticalReloadAnimation;
    public AnimationStats boltingAnimation; // 【追加】ボルトアクション用アニメーション
    public AnimationStats aimAnimation;
    public AnimationStats sprintAnimation;
    public AnimationStats idleAnimation;

    public static class AnimationStats {
        public String model;
        public int frameCount;
        public int fps;
    }
}

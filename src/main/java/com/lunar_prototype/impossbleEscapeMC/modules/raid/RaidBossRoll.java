package com.lunar_prototype.impossbleEscapeMC.modules.raid;

/** 抽選は生成時の1回だけ。安全待ち・討伐・スポーン失敗・復元で引き直さない。 */
final class RaidBossRoll {
    private boolean waiting;
    RaidBossRoll(double chance,double draw,boolean eligible,boolean restored) {
        waiting=!restored&&eligible&&Double.isFinite(chance)&&chance>=0&&chance<=1&&draw>=0&&draw<chance;
    }
    boolean waiting(){return waiting;}
    boolean consume(){boolean result=waiting;waiting=false;return result;}
}

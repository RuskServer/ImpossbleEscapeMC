package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.configuration.file.FileConfiguration;

/** 銃声の反応と、元の配置場所を基準にした音捜索の境界。距離は3次元のm。 */
public record ScavSoundPolicy(double investigateRadius, double alertRadius, double homeRadius) {
    public enum Reaction { IGNORE, ALERT, INVESTIGATE }
    public ScavSoundPolicy {
        if(!Double.isFinite(investigateRadius+alertRadius+homeRadius) || investigateRadius<0
                || alertRadius<investigateRadius || homeRadius<=0)throw new IllegalArgumentException("Invalid sound policy");
    }
    public static ScavSoundPolicy from(FileConfiguration config) {
        double alert=value(config,"gunshot-alert-radius",64,0,128);
        return new ScavSoundPolicy(value(config,"gunshot-investigate-radius",32,0,alert),alert,
                value(config,"sound-home-radius",40,1,128));
    }
    private static double value(FileConfiguration c,String key,double fallback,double lo,double hi) {
        double n=c.getDouble("scav-hearing."+key,fallback);
        return Double.isFinite(n)?Math.clamp(n,lo,hi):Math.clamp(fallback,lo,hi);
    }
    public Reaction gunshot(double distance,double sourceHomeDistance,double scavHomeDistance) {
        if(!Double.isFinite(distance) || distance<0 || distance> alertRadius)return Reaction.IGNORE;
        return distance<=investigateRadius && withinHome(sourceHomeDistance) && withinHome(scavHomeDistance)
                ?Reaction.INVESTIGATE:Reaction.ALERT;
    }
    public boolean withinHome(double distance) {return Double.isFinite(distance)&&distance>=0&&distance<=homeRadius;}
    public boolean canContinue(double scavHomeDistance,double goalHomeDistance) {
        return withinHome(scavHomeDistance)&&withinHome(goalHomeDistance);
    }
}

package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.configuration.file.YamlConfiguration;
import static com.lunar_prototype.impossbleEscapeMC.ai.ScavSoundPolicy.Reaction.*;

public final class ScavSoundPolicyTest {
    public static void main(String[] args) {
        var p=new ScavSoundPolicy(32,64,40);
        check(p.gunshot(31.99,20,5)==INVESTIGATE,"near gunshot investigates");
        check(p.gunshot(32,40,40)==INVESTIGATE,"inclusive inner and home boundaries");
        check(p.gunshot(32.01,20,5)==ALERT,"outside inner ring only alerts");
        check(p.gunshot(64,20,5)==ALERT,"inclusive outer boundary");
        check(p.gunshot(64.01,20,5)==IGNORE,"outside outer ring ignored");
        check(p.gunshot(12,40.01,30)==ALERT,"nearby sound outside home must not pull a patrol farther");
        check(p.gunshot(12,20,40.01)==ALERT,"drifted scav must not continue chaining sounds");
        check(p.canContinue(20,39.9),"normal local search");
        check(!p.canContinue(40.01,20),"current position outside home stops sound-only search");
        check(!p.canContinue(20,40.01),"belief drift outside home stops sound-only search");
        double[] detour={10,20,41,38};
        boolean routeAllowed=true;for(double distance:detour)routeAllowed&=p.withinHome(distance);
        check(!routeAllowed,"a legal endpoint does not make an out-of-bounds detour legal");
        check(p.gunshot(Double.NaN,0,0)==IGNORE,"invalid distance rejected");
        var config=new YamlConfiguration();
        check(ScavSoundPolicy.from(config).equals(p),"default config");
        config.set("scav-hearing.gunshot-alert-radius",20);
        config.set("scav-hearing.gunshot-investigate-radius",50);
        var clamped=ScavSoundPolicy.from(config);
        check(clamped.alertRadius()==20&&clamped.investigateRadius()==20,"inner radius cannot exceed outer radius");
        config.set("scav-hearing.sound-home-radius",Double.NaN);
        check(ScavSoundPolicy.from(config).homeRadius()==40,"invalid config falls back");
        System.out.println("ScavSoundPolicyTest OK");
    }
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}

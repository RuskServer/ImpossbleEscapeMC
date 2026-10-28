package com.lunar_prototype.impossbleEscapeMC.ai;

public final class ScavSightRecoveryTest {
    public static void main(String[] args) {
        var state=new ScavSightRecovery();
        check(!step(state,0,true,false,false,0),"start tracking known unseen contact");
        for(int tick=3;tick<60;tick+=3)check(!step(state,tick,true,false,false,0.1),"short cover waits and small jitter do not trigger");
        check(step(state,60,true,false,false,0),"three seconds without sight or movement starts recovery");
        check(step(state,100,true,false,false,2),"progress preserves committed four-second recovery");
        check(!step(state,140,true,false,false,2),"recovery expires instead of staying permanently aggressive");
        check(!step(state,197,true,false,false,2),"new stationary interval required after expiry");
        check(step(state,200,true,false,false,2),"a later stall can retry");
        check(!step(state,203,true,true,false,2),"actual visual reacquisition immediately ends recovery");

        var moving=new ScavSightRecovery();
        for(int tick=0;tick<600;tick+=3)check(!step(moving,tick,true,false,false,tick/20.0),"moving along a path is not stationary deadlock");
        var protectedWait=new ScavSightRecovery();
        for(int tick=0;tick<300;tick+=3)check(!step(protectedWait,tick,true,false,true,0),"reload/injury/suppression/withdrawal wait is protected");
        check(!step(protectedWait,300,true,false,false,0),"timer starts after protected action ends");
        check(!step(protectedWait,357,true,false,false,0),"protected wait does not accumulate hidden stall time");
        check(step(protectedWait,360,true,false,false,0),"healthy ready stationary scav can recover");
        check(!step(protectedWait,363,true,false,true,0),"new damage or reload interrupts recovery immediately");
        var unknown=new ScavSightRecovery();
        for(int tick=0;tick<300;tick+=3)check(!step(unknown,tick,false,false,false,0),"no known combat contact or sound-only contact must not start pursuit recovery");
        System.out.println("ScavSightRecoveryTest OK");
    }
    private static boolean step(ScavSightRecovery state,int now,boolean known,boolean visible,boolean protectedAction,double x) {
        return state.update(now,known,visible,protectedAction,x,64,0);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

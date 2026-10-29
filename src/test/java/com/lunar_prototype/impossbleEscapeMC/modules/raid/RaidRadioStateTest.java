package com.lunar_prototype.impossbleEscapeMC.modules.raid;

public final class RaidRadioStateTest {
    public static void main(String[] args) {
        var s=new RaidRadioState();
        check(!s.observeZone("factory",100),"entry starts dwell timer");
        check(!s.observeZone("factory",119),"no warning on brief boundary contact");
        check(s.observeZone("factory",120),"stable entry produces one alert");
        check(!s.observeZone("factory",200),"remaining inside never repeats warning");
        check(s.takeZone(120)&&!s.takeZone(121),"one queued zone voice");
        s.discover("ram",125);
        check(!s.takeItem(140)&&!s.takeItem(259),"item waits for reveal effect and earlier radio to finish");
        check(s.takeItem(260),"pending item voice plays after zone voice");
        s.discover("ram",1000);check(!s.takeItem(1020),"same type does not repeat within raid");
        s.observeZone(null,300);s.observeZone("factory",301);
        check(!s.observeZone("factory",321),"reentry cooldown suppresses repeated zone alert");
        check(!s.observeZone("factory",1400),"cooldown expiry alone cannot retrigger while inside");
        s.observeZone(null,1401);s.observeZone("factory",1402);
        check(s.observeZone("factory",1422),"later genuine entry can warn again");
        s.discover("drill",1423);s.urgent(1424,200);
        check(!s.takeZone(1630)&&!s.takeItem(1630),"urgent end sequence cancels stale lower-priority voices");
        s.suspend();s.discover("ram",2000);check(!s.takeItem(2020),"rejoining the same raid does not forget discovered item types");
        check(s.frame(2020)==-1,"leaving clears presentation");
        var expiry=new RaidRadioState();expiry.discover("docs",0);
        check(!expiry.takeItem(301),"stale discovery cannot speak long after event");
        check(RaidRadioState.frameAt(-1)==-1&&RaidRadioState.frameAt(0)==0,"animation start");
        check(RaidRadioState.frameAt(15)==7&&RaidRadioState.frameAt(79)==7,"opening then stable hold");
        check(RaidRadioState.frameAt(80)==8&&RaidRadioState.frameAt(99)==11&&RaidRadioState.frameAt(100)==-1,"finite fade and clear");
        System.out.println("RaidRadioStateTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.HashSet;
import java.util.Set;

/** 無線の重複・重なりと、区域境界での連打を抑える。1人・1レイドごとの状態。 */
final class RaidRadioState {
    private final Set<String> discovered=new HashSet<>();
    private String zone;
    private int zoneSince, zoneCooldownUntil, busyUntil, itemCooldownUntil;
    private boolean entered, pendingZone, pendingItem;
    private int zoneDeadline,itemDeadline,itemReadyAt;
    private int alertStarted=-1;

    boolean observeZone(String id,int now) {
        if(!java.util.Objects.equals(zone,id)) {zone=id;zoneSince=now;entered=false;pendingZone=false;}
        if(id==null||entered||now-zoneSince<20)return false;
        entered=true;
        if(now<zoneCooldownUntil)return false;
        zoneCooldownUntil=now+1200;alertStarted=now;
        pendingZone=true;zoneDeadline=now+160;
        return true;
    }
    String zone(){return zone;}
    void discover(String id,int now) {
        if(id!=null&&discovered.add(id)){if(!pendingItem)itemReadyAt=now+18;pendingItem=true;itemDeadline=now+300;}
    }
    boolean takeZone(int now) {
        if(now>=zoneDeadline)pendingZone=false;
        if(!pendingZone||now<busyUntil)return false;
        pendingZone=false;busyUntil=now+140;return true;
    }
    boolean takeItem(int now) {
        if(now>=itemDeadline)pendingItem=false;
        if(!pendingItem||now<itemReadyAt||now<busyUntil||now<itemCooldownUntil)return false;
        pendingItem=false;itemCooldownUntil=now+600;busyUntil=now+100;return true;
    }
    void urgent(int now,int duration) {busyUntil=Math.max(busyUntil,now+duration);pendingItem=false;pendingZone=false;}
    void suspend(){zone=null;entered=false;pendingItem=false;pendingZone=false;alertStarted=-1;busyUntil=0;}
    void previewZone(int now){alertStarted=now;pendingZone=true;zoneDeadline=now+160;}
    int frame(int now){return alertStarted<0?-1:frameAt(now-alertStarted);}
    static int frameAt(int age) {
        if(age<0||age>=100)return -1;
        if(age<16)return age/2;
        if(age<80)return 7;
        return 8+(age-80)/5;
    }
}

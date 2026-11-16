package com.lunar_prototype.impossbleEscapeMC.modules.raid;

/** 1人・1レイドにつき1回。開始タイトル後に再生し、中断や再接続では再開しない。 */
final class RaidBossAlertState {
    static final int DURATION=212,START_DELAY=80;
    private final String name;
    private final int readyAt;
    private int started=-1;
    private boolean cancelled;
    RaidBossAlertState(String name,int now,boolean preview){this.name=name;readyAt=now+(preview?0:START_DELAY);}
    boolean start(int now,boolean idle) {
        if(cancelled||started>=0||!idle||now<readyAt)return false;
        started=now;return true;
    }
    boolean pending(int now){return !cancelled&&(started<0||now-started<DURATION);}
    int frame(int now){int age=started<0?-1:now-started;return !cancelled&&age>=0&&age<DURATION?age:-1;}
    String name(){return name;}
    void cancel(){cancelled=true;}
}

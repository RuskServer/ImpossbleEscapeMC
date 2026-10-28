package com.lunar_prototype.impossbleEscapeMC.ai;

/** 射線も移動の進展もない時だけ、短時間の立ち位置変更・捜索へ切り替える。 */
final class ScavSightRecovery {
    static final int STALL_TICKS = 60;
    static final int RECOVERY_TICKS = 80;
    private boolean tracking;
    private double x,y,z;
    private int progressTick;
    private int recoveryUntil;

    boolean update(int now, boolean knownContact, boolean visible, boolean protectedAction,
                   double currentX, double currentY, double currentZ) {
        if(!knownContact || visible || protectedAction) {
            tracking=false;recoveryUntil=0;
            return false;
        }
        double distance=Math.pow(currentX-x,2)+Math.pow(currentY-y,2)+Math.pow(currentZ-z,2);
        if(!tracking || distance>=0.75*0.75) {
            tracking=true;x=currentX;y=currentY;z=currentZ;progressTick=now;
        }
        if(recoveryUntil>0 && now>=recoveryUntil) {
            recoveryUntil=0;
            progressTick=now;x=currentX;y=currentY;z=currentZ;
            return false;
        }
        if(recoveryUntil==0 && now-progressTick>=STALL_TICKS) {
            recoveryUntil=now+RECOVERY_TICKS;
            progressTick=now;
        }
        return now<recoveryUntil;
    }
}

package com.lunar_prototype.impossbleEscapeMC.ai;

public final class ScavShotAimTest {
    public static void main(String[] args) {
        var aim=new ScavShotAim();
        for(int shot=0;shot<100;shot++) {
            aim.capture(23.5f,12.25f,6);
            for(int tick=0;tick<6;tick++) {
                check(aim.held().yaw()==23.5f&&aim.held().pitch()==12.25f,"shooter/visual snapshot must not gain random spread or hidden recoil");aim.tick();
            }
            check(aim.held()==null,"finite trigger hold");
        }
        aim.capture(0,0,6);aim.tick();aim.capture(18,-5,6);
        check(aim.held().yaw()==18&&aim.held().pitch()==-5,"a new shot uses current desired aim instead of perpetually holding old aim");
        aim.clear();check(aim.held()==null,"movement cancels queued trigger aim");
        aim.capture(0,Float.NaN,6);check(aim.held()==null,"nonfinite aim rejected");
        System.out.println("ScavShotAimTest OK: shared orientation, retrigger, expiry and movement cleanup");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}

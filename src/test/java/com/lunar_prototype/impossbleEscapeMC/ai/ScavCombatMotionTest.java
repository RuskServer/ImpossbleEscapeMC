package com.lunar_prototype.impossbleEscapeMC.ai;

/** SCAVの属性・高速な戦闘移動指定が変わっても、共通上限と加速上限を超えないことを検証する。 */
public final class ScavCombatMotionTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        for(double request:new double[]{.35,1,1.2,1.5})for(int angle=0;angle<360;angle+=15) {
            var motion=new ScavCombatMotion();double oldX=0,oldZ=0;
            for(int tick=0;tick<180;tick++) {
                // 装備・効果で属性が変わる場合も、倍率ではなく属性適用後の量を揃える。
                double attribute=new double[]{.05,.10,.15,.3,1}[tick/36];
                double sign=tick<90?1:-1,dx=Math.cos(Math.toRadians(angle))*request*sign,dz=Math.sin(Math.toRadians(angle))*request*sign;
                var input=motion.step(dx,dz,attribute,tick);
                double x=input.x()*attribute,z=input.z()*attribute;
                check(Math.hypot(x,z)<=.080000001,"native movement cap despite rank/attribute/request changes");
                check(Math.hypot(x-oldX,z-oldZ)<=.005000001,"continuous native acceleration, including reversals");
                var duplicate=motion.step(dx,dz,attribute,tick);
                check(duplicate.equals(input),"AI step and render tick must not accelerate twice in the same server tick");
                oldX=x;oldZ=z;
            }
            motion.clear();var initial=motion.step(request,0,.15,200);
            check(initial.x()*.15<=.005000001,"restart begins with a bounded first step");
        }
        // 同じ通常地面を仮定した加速・摩擦モデルで、停止からの人間の横歩きを追い越さない。
        var motion=new ScavCombatMotion();double scav=0,player=0,scavX=0,playerX=0;
        for(int tick=0;tick<120;tick++) {
            var input=motion.step(1.5,0,.15,tick);
            scav=scav*.546+input.x()*.15*.98;
            player=player*.546+.1*.98;
            scavX+=scav;playerX+=player;
            check(scav<=player&&scavX<=playerX,"bounded combat strafe cannot beat reference player walking in ground model");
        }
        for(double attribute:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1e-300}) {
            var input=motion.step(1,1,attribute,300);check(input.x()==0&&input.z()==0,"invalid attribute stops input safely");
        }
        System.out.println("ScavCombatMotionTest OK: 96 movement scenarios, attribute changes, cap/ramp/reversal, ground reference and invalid inputs");
    }
}

package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavWeapon;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Mob;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.UUID;

/** 実際の顔出し処理を呼び、到着直後の射撃と中断時の待ち残りを検証する。 */
public final class ScavPeekTimingTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static void set(ScavTactics tactics,String name,Object value)throws Exception {
        Field f=ScavTactics.class.getDeclaredField(name);f.setAccessible(true);f.set(tactics,value);
    }
    private static int aimSteps(ScavTactics tactics)throws Exception {
        Field f=ScavTactics.class.getDeclaredField("peekAimSteps");f.setAccessible(true);return f.getInt(tactics);
    }
    private static final class Tactics extends ScavTactics {
        int stops;
        Tactics(Mob mob) {super(mob,null);}
        @Override public void stopMoving() {stops++;}
        @Override void applyPeekInput(double x, double z) { }
    }
    private static void begin(Tactics tactics,Location destination)throws Exception {
        tactics.cancelPeek();
        set(tactics,"peekPhase",1);set(tactics,"peekLocation",destination.clone());
        set(tactics,"coverLocation",destination.clone().add(0,0,-1.5));
        set(tactics,"peekOutLimit",20);set(tactics,"peekShotsRemaining",3);
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Block wall=proxy(Block.class,(p,m,a)->{throw new AssertionError(m.getName());});
        World world=proxy(World.class,(p,m,a)->switch(m.getName()) {
            case "equals"->p==a[0];
            case "isChunkLoaded"->true;
            case "rayTraceBlocks"->new RayTraceResult(new Vector(),wall,BlockFace.WEST);
            default->throw new AssertionError(m.getName());
        });
        Location position=new Location(world,0,0,0);
        Mob mob=proxy(Mob.class,(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->new UUID(0,1);
            case "getLocation"->position.clone();
            case "getVelocity"->new Vector();
            default->throw new AssertionError(m.getName());
        });
        int[] shots={0},aimed={0},ready={0};
        ScavWeapon weapon=proxy(ScavWeapon.class,(p,m,a)->switch(m.getName()) {
            case "rpm"->600.0;
            case "ticksUntilReady"->ready[0];
            case "fire"->{check(!(boolean)a[1],"peek stays single-shot");shots[0]++;yield true;}
            default->throw new AssertionError(m.getName());
        });
        var tactics=new Tactics(mob);
        Runnable aim=()->aimed[0]++;
        begin(tactics,position);
        for(int frame=0;frame<2;frame++) {
            tactics.handlePeekManeuver(true,true,aim,weapon,0,false,0,t->{});
            check(shots[0]==0&&aimed[0]==0,"arrival and three-tick frame cannot shoot");
        }
        tactics.handlePeekManeuver(true,true,aim,weapon,0,false,0,t->{});
        check(shots[0]==1&&aimed[0]==1&&tactics.stops==3,"six ticks after arrival can aim and shoot while stopped");
        int before=shots[0];
        begin(tactics,position);
        for(int i=0;i<4;i++)tactics.handlePeekManeuver(true,false,aim,weapon,0,false,0,t->{});
        check(shots[0]==before,"reaction hold remains mandatory after settling");
        tactics.handlePeekManeuver(true,true,aim,weapon,0,false,System.currentTimeMillis()+1000,t->{});
        check(shots[0]==before,"RPM gate remains mandatory after settling");
        // 押されて到着範囲を出ると構え直し。通路も塞がれた場合は即座に戻る。
        position.setX(1);
        tactics.handlePeekManeuver(true,true,aim,weapon,0,false,0,t->{});
        check(aimSteps(tactics)==0&&tactics.getPeekPhase()==2&&shots[0]==before,"displacement resets hold and blocked travel retreats without firing");
        position.setX(0);
        begin(tactics,position);
        tactics.handlePeekManeuver(true,true,aim,weapon,0,false,0,t->{});
        check(shots[0]==before,"new peek cannot reuse previous settled time");
        ready[0]=20;
        tactics.handlePeekManeuver(true,true,aim,weapon,0,false,0,t->{});
        check(tactics.getPeekPhase()==2&&shots[0]==before,"unready weapon retreats during hold");
        ready[0]=0;begin(tactics,position);
        for(int i=0;i<3;i++)tactics.handlePeekManeuver(false,true,aim,weapon,0,false,0,t->{});
        check(shots[0]==before,"settling cannot permit blind shots");
        tactics.cancelPeek();
        check(aimSteps(tactics)==0&&tactics.getPeekPhase()==0,"cancel clears settling state");
        System.out.println("ScavPeekTimingTest OK: settled hold, reaction/RPM/sight gates, displacement, restart, readiness and cancel");
    }
}

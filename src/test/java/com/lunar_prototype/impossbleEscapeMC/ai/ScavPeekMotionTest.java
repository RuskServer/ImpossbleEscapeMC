package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavWeapon;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.util.UUID;

public final class ScavPeekMotionTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);
    }
    private static void set(Object object,String name,Object value)throws Exception {
        Field f=ScavTactics.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    private static final class Tactics extends ScavTactics {
        double x,z;
        boolean clear=true;
        Tactics(Mob mob) {super(mob,null);}
        @Override void applyPeekInput(double x,double z) {this.x=x;this.z=z;}
        @Override boolean peekRouteClear(Location dest) {return clear;}
        @Override public void stopMoving() {x=z=0;}
    }
    public static void main(String[] args)throws Exception {
        // 方向・移動距離に依らず、立ち上がりと反転の入力変化に上限がある。
        for(int angle=0;angle<360;angle+=15) {
            var motion=new ScavPeekMotion();
            double dx=Math.cos(Math.toRadians(angle)),dz=Math.sin(Math.toRadians(angle));
            var previous=new ScavPeekMotion.Input(0,0);
            for(int tick=0;tick<40;tick++) {
                double sign=tick<20?1:-1;
                var input=motion.step(dx*3*sign,dz*3*sign);
                check(Math.hypot(input.x()-previous.x(),input.z()-previous.z())<=.0600001,"no instantaneous acceleration or reversal");
                check(Math.hypot(input.x(),input.z())<=.3500001,"input speed cap");
                if(tick==20)check(input.x()*dx+input.z()*dz>0,"reversal brakes before going the other way");
                previous=input;
            }
        }
        // 実際の状態機械と毎tickの入力経路を通す。物理は床上の加速・摩擦を模した検証用モデル。
        for(double distance:new double[]{1,1.5,2.5,3}) {
            World world=proxy(World.class,(p,m,a)-> {if(m.getName().equals("equals"))return p==a[0];throw new AssertionError(m.getName());});
            Location position=new Location(world,0,0,0),destination=position.clone().add(distance,0,0);
            Vector velocity=new Vector();
            int[] paths={0},stoppedPaths={0};
            Object pathfinder=proxy(Mob.class.getMethod("getPathfinder").getReturnType(),(p,m,a)->switch(m.getName()) {
                case "moveTo"->{paths[0]++;yield true;}
                case "stopPathfinding"->{stoppedPaths[0]++;yield null;}
                default->throw new AssertionError(m.getName());
            });
            Mob mob=proxy(Mob.class,(p,m,a)->switch(m.getName()) {
                case "getUniqueId"->new UUID(0,1);
                case "getLocation"->position.clone();
                case "getVelocity"->velocity.clone();
                case "getPathfinder"->pathfinder;
                default->throw new AssertionError(m.getName());
            });
            var tactics=new Tactics(mob);
            int[] shots={0};
            ScavWeapon weapon=proxy(ScavWeapon.class,(p,m,a)->switch(m.getName()) {
                case "rpm"->600.0;
                case "ticksUntilReady"->0;
                case "fire"->{check(Math.abs(velocity.getX())<.025,"no firing while sliding");shots[0]++;yield true;}
                default->throw new AssertionError(m.getName());
            });
            set(tactics,"peekPhase",1);set(tactics,"peekLocation",destination);set(tactics,"coverLocation",position.clone());
            set(tactics,"peekOutLimit",ScavPeekMotion.travelSteps(distance)+8);set(tactics,"peekShotsRemaining",1);
            double previous=0;int lastPhase=1;boolean returned=false;
            for(int tick=0;tick<300&&tactics.getPeekPhase()!=0;tick++) {
                if(tick%3==0)tactics.handlePeekManeuver(true,true,()->{},weapon,0,false,0,t->{});
                int phase=tactics.getPeekPhase();
                if(phase==0)break;
                tactics.tickDirectMove();
                check(Math.abs(tactics.x-previous)<=.0600001,"per-tick bridge retains continuous input");
                if(phase==1)check(position.getX()<=distance+.15,"no outward overshoot");
                if(phase!=lastPhase) {returned=true;lastPhase=phase;}
                previous=tactics.x;
                velocity.setX(velocity.getX()*.546+tactics.x*.2);
                position.add(velocity);
            }
            check(shots[0]==1&&returned&&tactics.getPeekPhase()==0,"out/fire/back cycle completes");
            check(position.distance(new Location(world,0,0,0))<.3,"return reaches cover rather than timing out early");
            check(tactics.x==0&&tactics.z==0,"finished peek leaves no movement input");
            set(tactics,"peekPhase",1);set(tactics,"peekLocation",destination);set(tactics,"peekInputAge",0);
            for(int tick=0;tick<8;tick++)tactics.tickDirectMove();
            check(tactics.x==0&&tactics.z==0,"stale decision cannot keep steering forever");
            tactics.cancelPeek();check(tactics.x==0&&tactics.z==0,"cancel clears input");
            set(tactics,"peekPhase",2);set(tactics,"coverLocation",position.clone().add(-1,0,0));
            set(tactics,"peekInputAge",0);tactics.clear=false;
            tactics.tickDirectMove();
            check(tactics.isPeekPathfinding()&&paths[0]==1&&tactics.x==0&&tactics.z==0,"blocked return yields to pathfinder with no strafe input");
            tactics.tickDirectMove();check(paths[0]==1,"fallback path is not restarted every tick");
            tactics.cancelPeek();check(stoppedPaths[0]==1&&!tactics.isPeekPathfinding(),"cancel stops owned fallback path");
        }
        System.out.println("ScavPeekMotionTest OK: 24 directions, acceleration/reversal limits, per-tick out/fire/back, no sliding fire, cover return and stale-input cleanup");
    }
}

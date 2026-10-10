package com.lunar_prototype.impossbleEscapeMC.ai;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public final class ScavCoordinationTest {
    static UUID id(int n) { return new UUID(0,n); }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy,method,arguments) -> switch(method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> 1;
                    case "getUID" -> id(999);
                    case "getName", "toString" -> "test";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var point = new Location(world,0,64,0); var velocity = new Vector(1,0,0);
        var report = new ScavContactReport(id(100),id(1),point,velocity,0);
        point.add(100,0,0); velocity.setX(100);
        check(report.position().getX()==0 && report.velocity().getX()==1,"input defensively copied");
        report.position().add(100,0,0); report.velocity().setX(100);
        check(report.position().getX()==0 && report.velocity().getX()==1,"output defensively copied");
        check(report.fresh(199)&&!report.fresh(200)&&!report.fresh(-1),"report expires and rejects future time");
        check(report.predicted(100).getX()==6,"prediction bounded by six blocks");
        check(report.uncertainty(100)>report.uncertainty(1),"older observation less precise");
        double routine=ScavHelpPolicy.priority(1,0,false,true,0,10);
        double urgent=ScavHelpPolicy.priority(.2,.8,true,true,0,12);
        check(urgent>routine+20,"urgent injured suppressed caller outranks routine fight");
        check(!ScavHelpPolicy.shouldSwitch(routine,urgent,39,true),"minimum commitment");
        check(ScavHelpPolicy.shouldSwitch(routine,urgent,40,true),"urgent call can preempt after commitment");
        check(!ScavHelpPolicy.shouldSwitch(routine,routine+20,100,true),"hysteresis prevents oscillation");
        check(ScavHelpPolicy.shouldSwitch(urgent,routine,0,false),"dead/expired caller can be replaced");
        check(ScavHelpPolicy.priority(.2,.8,true,true,2,12)<urgent,"existing help reduces priority");
        var plan=new ScavSearchPlan();
        var stationary=new ScavContactReport(id(100),id(1),new Location(world,0,64,0),new Vector(),0);
        var first=plan.assign(id(1),stationary,0,p->true);
        var second=plan.assign(id(2),stationary,0,p->true);
        var third=plan.assign(id(3),stationary,0,p->true);
        check(first.distanceSquared(second)>=9 && first.distanceSquared(third)>=9 && second.distanceSquared(third)>=9,"sectors don't overlap");
        check(plan.assign(id(1),stationary,20,p->true).distanceSquared(first)==0,"assignment stable while leased");
        plan.inspected(id(1),id(100),first,20);
        var next=plan.assign(id(1),stationary,21,p->true);
        check(next.distanceSquared(first)>=9,"checked sector avoided");
        plan.release(id(2)); plan.release(id(3)); plan.release(id(1));
        var refreshed=new ScavContactReport(id(100),id(1),new Location(world,0,64,0),new Vector(),30);
        check(plan.assign(id(4),refreshed,30,p->true).distanceSquared(first)==0,"new sighting invalidates earlier empty evidence");
        check(new ScavSearchPlan().assign(id(1),stationary,0,p->false)==null,"unreachable sectors not assigned");
        check(plan.assign(id(1),stationary,200,p->true)==null,"expired observation cannot create search assignment");
        var timeout=new ScavSearchPlan();
        var before=timeout.assign(id(1),stationary,0,p->true);
        var afterTimeout=timeout.assign(id(2),stationary,60,p->true);
        check(afterTimeout.getX()==before.getX()&&afterTimeout.getZ()>0,"dead/departed lease expires");
        var registry=new ScavHelpEncounter.Registry();
        var encounter=registry.contact(null,id(1),id(100),0);
        check(encounter.recruit(id(2),0)&&encounter.recruit(id(3),0),"two reinforcements accepted");
        check(encounter.supportSide(id(2))!=encounter.supportSide(id(3)),"responders occupy opposite support sides");
        check(encounter.recruit(id(2),40)&&!encounter.recruit(id(4),40),"reassignment doesn't refill cumulative budget");
        var separate=registry.contact(null,id(5),id(101),0);
        check(separate.recruit(id(2),40),"switching fight spends destination slot");
        check(encounter.full()&&!encounter.recruit(id(6),40),"switching never refunds source slot");
        var merged=registry.contact(encounter,id(1),id(101),41);
        check(merged.full()&&!separate.recruit(id(7),41),"merged references preserve cumulative cap");
        System.out.println("ScavCoordinationTest: all checks passed");
    }
}

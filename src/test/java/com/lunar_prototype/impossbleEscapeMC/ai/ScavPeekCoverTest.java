package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiPredicate;

public final class ScavPeekCoverTest {
    private static final class Geometry {
        final List<BoundingBox> obstacles;
        BiPredicate<Integer,Integer> loaded=(x,z)->true;
        int rays;
        final World world;
        Geometry(BoundingBox... boxes) {
            obstacles=List.of(boxes);
            world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()) {
            case "equals"->p==a[0];
            case "hashCode"->System.identityHashCode(p);
            case "isChunkLoaded"->loaded.test((int)a[0],(int)a[1]);
            case "rayTraceBlocks"->{
                rays++;
                Vector start=((Location)a[0]).toVector(),direction=(Vector)a[1];double limit=(double)a[2],nearest=Double.POSITIVE_INFINITY;
                BoundingBox best=null;Vector position=null;
                for(var box:obstacles) {
                    var hit=box.rayTrace(start,direction,limit);
                    if(hit!=null&&start.distance(hit.getHitPosition())<nearest) {best=box;position=hit.getHitPosition();nearest=start.distance(position);}
                }
                if(best==null)yield null;
                Block block=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},(q,n,b)-> {throw new AssertionError("unexpected block access: "+n.getName());});
                yield new RayTraceResult(position,block,BlockFace.WEST);
            }
            default->throw new AssertionError("unexpected world access: "+m.getName());
            });
        }
        Location feet() {return new Location(world,0,0,0);}
        Location threat() {return new Location(world,10,1.6,0);}
    }
    private static Mob mob(Geometry geometry) {
        LivingEntity target=(LivingEntity)Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),new Class<?>[]{LivingEntity.class},(p,m,a)->switch(m.getName()) {
            case "getLocation"->new Location(geometry.world,10,0,0);
            case "getEyeLocation"->geometry.threat();
            default->throw new AssertionError("unexpected target access: "+m.getName());
        });
        return (Mob)Proxy.newProxyInstance(Mob.class.getClassLoader(),new Class<?>[]{Mob.class},(p,m,a)->switch(m.getName()) {
            case "getLocation"->geometry.feet();
            case "getWorld"->geometry.world;
            case "getTarget"->target;
            case "getEyeHeight"->1.6;
            default->throw new AssertionError("rejected peek must not issue movement: "+m.getName());
        });
    }
    public static void main(String[] args) {
        Geometry open=new Geometry();
        check(!ScavPeekCover.covered(open.feet(),open.threat())&&open.rays==1,"open ground is not cover");
        var wall=new Geometry(new BoundingBox(.7,0,-2,1,3,2));
        check(ScavPeekCover.covered(wall.feet(),wall.threat())&&wall.rays==3,"near full wall covers head and torso width");
        var far=new Geometry(new BoundingBox(6,0,-2,7,3,2));
        check(!ScavPeekCover.covered(far.feet(),far.threat()),"remote wall does not make open ground a peek origin");
        var low=new Geometry(new BoundingBox(.7,0,-2,1,1.3,2));
        check(!ScavPeekCover.covered(low.feet(),low.threat()),"exposed head cannot peek from low cover");
        var tallEye=new Geometry(new BoundingBox(.7,0,-2,1,1.7,2));
        check(!ScavPeekCover.covered(tallEye.feet(),tallEye.threat(),1.8),"actual eye height must be hidden");
        var beam=new Geometry(new BoundingBox(.7,1.4,-2,1,2,2));
        check(!ScavPeekCover.covered(beam.feet(),beam.threat()),"head-only obstruction leaves torso exposed");
        var pole=new Geometry(new BoundingBox(.7,0,-.02,1,3,.02));
        check(!ScavPeekCover.covered(pole.feet(),pole.threat()),"thin post cannot shelter torso width");
        var behind=new Geometry(new BoundingBox(-1,0,-2,-.7,3,2));
        check(!ScavPeekCover.covered(behind.feet(),behind.threat()),"wall behind scav does not shield threat direction");
        var missing=new Geometry();missing.loaded=(x,z)->x!=0;
        check(!ScavPeekCover.covered(new Location(missing.world,-.1,0,0),missing.threat())&&missing.rays==0,"unloaded chunk crossing rejected before raycast");
        check(!ScavPeekCover.covered(wall.feet(),open.threat()),"world mismatch");
        check(!ScavPeekCover.covered(wall.feet(),wall.feet().add(0,1.6,0)),"coincident threat");
        check(!ScavPeekCover.covered(wall.feet(),null),"unknown threat");
        for(var geometry:new Geometry[]{open,far,low,beam,pole,behind,missing}) {
            var tactics=new ScavTactics(mob(geometry),null);
            Location destination=geometry.feet().add(0,0,1.5);
            check(!tactics.startPeek(new Location(geometry.world,10,0,0),false,true),"legacy and jump peeks require local cover");
            check(!tactics.startPeekTo(destination,geometry.threat()),"planned peek rechecks actual cover");
            check(!tactics.startPeekTo(destination),"compatible entrypoint cannot bypass cover check");
            check(tactics.getPeekPhase()==0&&tactics.peekMoveDestination()==null,"rejected peek leaves no active maneuver");
        }
        var tactics=new ScavTactics(mob(wall),null);
        check(!tactics.startPeekTo(wall.feet().add(0,0,.2),wall.threat()),"destination must actually expose a firing line");
        check(tactics.getPeekPhase()==0,"blocked destination does not begin movement");
        System.out.println("ScavPeekCoverTest OK: local cover, exposed terrain, remote walls, body width, unloaded chunks and actual peek entrypoints");
    }
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}

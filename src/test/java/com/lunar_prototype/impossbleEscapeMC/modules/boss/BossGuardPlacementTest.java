package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class BossGuardPlacementTest {
    private interface Shape {List<BoundingBox> at(int x,int y,int z);}
    private Shape shapes=(x,y,z)->y==0?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
    private BiPredicate<Integer,Integer> loaded=(x,z)->true;
    private int reads;
    private final World world=proxy(World.class,(p,m,a)->switch(m.getName()) {
        case "getMinHeight"->-64;case "getMaxHeight"->320;
        case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);
        case "isChunkLoaded"->loaded.test((Integer)a[0],(Integer)a[1]);
        case "getBlockAt"->{
            int x=(Integer)a[0],y=(Integer)a[1],z=(Integer)a[2];
            assertTrue(loaded.test(x>>4,z>>4),"must not access unloaded blocks");reads++;
            yield proxy(Block.class,(block,method,args)->switch(method.getName()) {
                case "isLiquid"->false;
                case "getCollisionShape"->proxy(VoxelShape.class,(shape,call,values)-> {
                    if(call.getName().equals("getBoundingBoxes"))return shapes.at(x,y,z);
                    throw new AssertionError(call.getName());
                });
                default->throw new AssertionError(method.getName());
            });
        }
        default->throw new AssertionError(m.getName());
    });
    private BossGuardPlacement planner(double x,double z,BossDefinition definition) {
        return new BossGuardPlacement(definition,new BossTerritory("zone",-20,-4,-20,40,10,40),new Location(world,x,1,z));
    }
    private static void finish(BossGuardPlacement planner) {
        for(int i=0;i<1000&&!planner.done();i++)planner.advance(24);
        assertTrue(planner.done(),"search must be bounded");
    }
    @Test void spreadsGuardsWithinEightBlocksWithSafeSpacing() {
        var d=BossDefinitionTest.defaults();var planner=planner(.5,.5,d);finish(planner);
        var points=planner.points();assertEquals(3,points.size());
        assertEquals(.5,points.getFirst().getX());assertTrue(planner.valid());
        for(int i=1;i<points.size();i++) {
            assertTrue(points.getFirst().distanceSquared(points.get(i))<=64);
            assertTrue(BossSpawnSafety.safePosition(points.get(i)));
            for(int j=0;j<i;j++)assertTrue(points.get(i).distanceSquared(points.get(j))>=2.25);
        }
        double dot=(points.get(1).getX()-.5)*(points.get(2).getX()-.5)+(points.get(1).getZ()-.5)*(points.get(2).getZ()-.5);
        assertTrue(dot<=0,"prefer separate sides");
    }
    @Test void cannotCrossWallsOrUseDisconnectedPlatforms() {
        shapes=(x,y,z)->y==0||x==1&&y>=1&&y<=4?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
        var planner=planner(.5,.5,BossDefinitionTest.defaults());finish(planner);
        assertNotNull(planner.points());for(var p:planner.points())assertTrue(p.getX()<1);
        shapes=(x,y,z)->y==0&&z==0&&(x==0||x==3)?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
        var isolated=planner(.5,.5,BossDefinitionTest.defaults());finish(isolated);assertNull(isolated.points());
    }
    @Test void unloadedChunksAreNeverReadAndSearchAdvancesInSmallBatches() {
        loaded=(x,z)->x==0&&z==0;
        var planner=planner(15.5,8.5,BossDefinitionTest.defaults());planner.advance(1);
        assertFalse(planner.done());assertTrue(reads<500,"single-node budget");
        finish(planner);assertNotNull(planner.points());
        for(var p:planner.points())assertTrue(p.getX()<15.71);
    }
    @Test void rejectsChangedTerrainAlongPreviouslySelectedRoutes() {
        var planner=planner(.5,.5,BossDefinitionTest.defaults());finish(planner);assertTrue(planner.valid());
        shapes=(x,y,z)->y==0||y==1&&(x!=0||z!=0)?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
        assertFalse(planner.valid());
    }
    @Test void handlesSingleLeaderAndLeaderLastInDefinition() {
        var d=BossDefinitionTest.defaults();
        var solo=new BossDefinition("solo","Solo",true,Set.of("map"),1,List.of(d.leader()));
        var planner=planner(.5,.5,solo);finish(planner);assertEquals(1,planner.points().size());assertTrue(planner.valid());
        var reverse=new ArrayList<>(d.members());Collections.reverse(reverse);
        var reordered=new BossDefinition(d.id(),d.name(),true,d.maps(),1,reverse);
        var last=planner(.5,.5,reordered);finish(last);assertEquals(.5,last.points().getLast().getX());assertEquals(.5,last.points().getLast().getZ());
    }
    @Test void sweepsBodyAndFootingBetweenNodesAndAllowsHalfBlockSteps() {
        Location from=new Location(world,.5,1,.5),to=new Location(world,1.5,1,.5);
        shapes=(x,y,z)->y==0?List.of(new BoundingBox(0,0,0,1,1,1)):
                x==1&&y==1?List.of(new BoundingBox(0,0,0,.02,1,1)):List.of();
        assertTrue(BossSpawnSafety.safePosition(to));assertFalse(BossSpawnSafety.walkable(from,to),"thin wall between nodes");
        shapes=(x,y,z)->y==0?List.of(new BoundingBox(.45,0,0,.55,1,1)):List.of();
        assertTrue(BossSpawnSafety.safePosition(from));assertTrue(BossSpawnSafety.safePosition(to));
        assertFalse(BossSpawnSafety.walkable(from,to),"unsupported gap between small platforms");
        shapes=(x,y,z)->y==0?List.of(new BoundingBox(0,0,0,1,1,1)):
                x==1&&y==1?List.of(new BoundingBox(0,0,0,1,.5,1)):List.of();
        assertTrue(BossSpawnSafety.walkable(from,to.clone().add(0,.5,0)),"half-block step");
        assertFalse(BossSpawnSafety.walkable(from,to.clone().add(0,1,0)),"full block requires jumping");
    }
    private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
}

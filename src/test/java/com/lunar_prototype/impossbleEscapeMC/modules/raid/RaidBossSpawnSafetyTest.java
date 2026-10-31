package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import java.lang.reflect.Proxy;
import java.util.*;

public final class RaidBossSpawnSafetyTest {
    private static boolean loaded=true,liquid;private static int reads;
    private static final Map<String,List<BoundingBox>> shapes=new HashMap<>();
    public static void main(String[] args) {
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()) {
            case "getMinHeight"->-64;
            case "getMaxHeight"->320;
            case "isChunkLoaded"->loaded;
            case "getBlockAt"->{
                reads++;String key=a[0]+","+a[1]+","+a[2];
                VoxelShape shape=(VoxelShape)Proxy.newProxyInstance(VoxelShape.class.getClassLoader(),new Class<?>[]{VoxelShape.class},(s,n,b)-> {
                    if(n.getName().equals("getBoundingBoxes"))return shapes.getOrDefault(key,List.of());
                    throw new AssertionError(n.getName());
                });
                yield Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},(b,n,v)->switch(n.getName()) {
                    case "isLiquid"->liquid;
                    case "getCollisionShape"->shape;
                    default->throw new AssertionError(n.getName());
                });
            }
            default->throw new AssertionError("unexpected world access "+m.getName());
        });
        Location point=new Location(world,.5,1,.5);
        check(!RaidBossEncounter.safePosition(point),"no floor");
        shapes.put("0,0,0",List.of(new BoundingBox(0,0,0,1,1,1)));
        check(RaidBossEncounter.safePosition(point),"clear supported position");
        shapes.put("0,2,0",List.of(new BoundingBox(0,0,0,1,1,1)));
        check(!RaidBossEncounter.safePosition(point),"blocked head");shapes.remove("0,2,0");
        shapes.put("0,1,0",List.of(new BoundingBox(.7,0,0,1,1,1)));
        check(!RaidBossEncounter.safePosition(point),"wall clips shoulder");shapes.remove("0,1,0");
        loaded=false;reads=0;check(!RaidBossEncounter.safePosition(point)&&reads==0,"unloaded chunks never read or generated");loaded=true;
        liquid=true;check(!RaidBossEncounter.safePosition(point),"water/lava rejected");liquid=false;
        check(!RaidBossEncounter.safePosition(new Location(world,.5,319,.5)),"world ceiling");
        check(!RaidBossEncounter.safePosition(new Location(world,Double.NaN,1,.5)),"invalid coordinates");
        System.out.println("RaidBossSpawnSafetyTest passed: floor, head, shoulder, unloaded chunks, fluids and world bounds");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}

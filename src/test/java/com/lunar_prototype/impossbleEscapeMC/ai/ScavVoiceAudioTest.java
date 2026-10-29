package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 実際の再生関数を通し、各聞き手に同じ発言を1回だけ口元から送る。 */
public final class ScavVoiceAudioTest {
    record Heard(Location position,String sound,float volume,float pitch){}
    static Player listener(World world,Location ears,boolean online,List<Heard> heard){
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "isOnline"->online;
            case "getEyeLocation"->ears.clone();
            case "playSound"->{heard.add(new Heard(((Location)a[0]).clone(),(String)a[1],(Float)a[2],(Float)a[3]));yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        List<Player> players=new ArrayList<>();int[] queries={0};
        List<BoundingBox> floors=List.of(new BoundingBox(-5,3,-5,5,4,5),new BoundingBox(-5,7,-5,5,8,5));
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)->switch(m.getName()){
            case "getNearbyPlayers"->players;
            case "isChunkLoaded"->true;
            case "equals"->p==a[0];
            case "hashCode"->1;
            case "rayTraceBlocks"->{
                queries[0]++;Vector start=((Location)a[0]).toVector(),dir=(Vector)a[1];double distance=(double)a[2];
                BoundingBox best=null;Vector hit=null;double nearest=Double.POSITIVE_INFINITY;
                for(var box:floors){var result=box.rayTrace(start,dir,distance);if(result==null)continue;
                    double d=result.getHitPosition().distanceSquared(start);if(d<nearest){best=box;hit=result.getHitPosition();nearest=d;}}
                if(best==null)yield null;
                BoundingBox box=best;
                Block block=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},(b,method,arguments)->{
                    if(method.getName().equals("getBoundingBox"))return box;throw new UnsupportedOperationException(method.getName());});
                yield new RayTraceResult(hit,block,BlockFace.UP);
            }
            default->throw new UnsupportedOperationException(m.getName());
        });
        LivingEntity scav=(LivingEntity)Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),new Class<?>[]{LivingEntity.class},(p,m,a)->switch(m.getName()){
            case "getWorld"->world;
            case "getEyeLocation"->new Location(world,0,1.62,0);
            default->throw new UnsupportedOperationException(m.getName());
        });
        List<Heard> same=new ArrayList<>(),upper=new ArrayList<>(),farUpper=new ArrayList<>(),outside=new ArrayList<>(),offline=new ArrayList<>();
        players.add(listener(world,new Location(world,2,1.62,0),true,same));
        players.add(listener(world,new Location(world,2,5.62,0),true,upper));
        players.add(listener(world,new Location(world,2,9.62,0),true,farUpper));
        players.add(listener(world,new Location(world,100,1.62,0),true,outside));
        players.add(listener(world,new Location(world,1,1.62,0),false,offline));
        ScavVoiceAudio.play(scav,ScavVoice.SPOTTED,2);
        check(same.size()==1&&upper.size()==1&&farUpper.size()==1&&outside.isEmpty()&&offline.isEmpty(),"one delivery per in-range listener");
        check(same.getFirst().sound.equals("iem:scav.voice.spotted.b1"),"fixed take chosen once");
        check(upper.getFirst().sound.equals(same.getFirst().sound+".muffled")&&farUpper.getFirst().sound.equals(same.getFirst().sound+".deep"),"same phrase with per-listener occlusion");
        check(same.getFirst().volume==1&&upper.getFirst().volume==.6f&&farUpper.getFirst().volume==.3f,"occluded gains");
        check(Math.abs(same.getFirst().position.getY()-1.44)<1e-9&&upper.getFirst().position.equals(same.getFirst().position),"mouth position used for every listener");
        check(same.getFirst().pitch==1&&upper.getFirst().pitch==1&&farUpper.getFirst().pitch==1,"height does not change voice pitch");
        check(queries[0]<=18,"collision work bounded per utterance");
        System.out.println("PASS: actual SCAV audio fan-out, mouth position, identical take, occlusion gains and distance/online filtering");
    }
}

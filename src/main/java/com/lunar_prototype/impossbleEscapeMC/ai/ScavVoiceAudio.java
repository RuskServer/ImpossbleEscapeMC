package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** 同じ発言のテイクを、聞く人ごとの遮蔽だけ変えて口元から再生する。 */
public final class ScavVoiceAudio {
    private ScavVoiceAudio() {}
    public static void play(LivingEntity scav,ScavVoice voice,int take) {
        Location mouth=scav.getEyeLocation().subtract(0,.18,0);
        World world=scav.getWorld();
        var geometry=new ScavVoiceOcclusion.Geometry(){
            @Override public boolean loaded(int x,int z){return world.isChunkLoaded(x,z);}
            @Override public ScavVoiceOcclusion.Obstacle firstHit(ScavVoiceOcclusion.Point origin,ScavVoiceOcclusion.Point direction,double distance){
                var hit=world.rayTraceBlocks(new Location(world,origin.x(),origin.y(),origin.z()),
                        new Vector(direction.x(),direction.y(),direction.z()),distance,FluidCollisionMode.NEVER,true);
                if(hit==null||hit.getHitBlock()==null)return null;
                var box=hit.getHitBlock().getBoundingBox();
                return new ScavVoiceOcclusion.Obstacle(box.getMinX(),box.getMinY(),box.getMinZ(),box.getMaxX(),box.getMaxY(),box.getMaxZ());
            }
        };
        var source=point(mouth);
        for(Player listener:world.getNearbyPlayers(mouth,voice.range()+2)) {
            if(!listener.isOnline()||listener.getEyeLocation().distanceSquared(mouth)>voice.range()*voice.range())continue;
            var level=ScavVoiceOcclusion.classify(source,point(listener.getEyeLocation()),geometry);
            listener.playSound(mouth,voice.sound(take,level),level.volume(),1f);
        }
    }
    private static ScavVoiceOcclusion.Point point(Location location){return new ScavVoiceOcclusion.Point(location.getX(),location.getY(),location.getZ());}
}

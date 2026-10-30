package com.lunar_prototype.impossbleEscapeMC.ai;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/** 顔出しの起点は、相手の方向に近接した遮蔽がある場所だけにする。 */
final class ScavPeekCover {
    private static final double MAX_COVER_DISTANCE=2.0;
    private ScavPeekCover() {}

    static boolean covered(Location feet,Location threatEye) {
        return covered(feet,threatEye,1.6);
    }

    static boolean covered(Location feet,Location threatEye,double eyeHeight) {
        if(feet==null||threatEye==null||feet.getWorld()==null||feet.getWorld()!=threatEye.getWorld())return false;
        if(!Double.isFinite(eyeHeight)||eyeHeight<=0)return false;
        // 頭だけ窓枠に隠れる場合や、細い柱が中心を隠すだけの場合は顔出しの遮蔽にしない。
        if(!blockedNearby(feet.clone().add(0,eyeHeight,0),threatEye))return false;
        double dx=threatEye.getX()-feet.getX(),dz=threatEye.getZ()-feet.getZ(),flat=Math.hypot(dx,dz);
        Vector side=flat<.001?new Vector(.2,0,0):new Vector(-dz/flat*.2,0,dx/flat*.2);
        return blockedNearby(feet.clone().add(0,eyeHeight*.625,0).add(side),threatEye)
                && blockedNearby(feet.clone().add(0,eyeHeight*.625,0).subtract(side),threatEye);
    }

    private static boolean blockedNearby(Location from,Location target) {
        Vector direction=target.toVector().subtract(from.toVector());
        double length=direction.length();
        if(!Double.isFinite(length)||length<.001)return false;
        double distance=Math.min(length,MAX_COVER_DISTANCE);
        direction.multiply(1/length);
        World world=from.getWorld();
        Location end=from.clone().add(direction.clone().multiply(distance));
        // 最大2mなので各軸で高々2チャンク。未読込を遮蔽とは認めず、読込も要求しない。
        for(int x=Math.min(from.getBlockX(),end.getBlockX())>>4;x<=(Math.max(from.getBlockX(),end.getBlockX())>>4);x++)
            for(int z=Math.min(from.getBlockZ(),end.getBlockZ())>>4;z<=(Math.max(from.getBlockZ(),end.getBlockZ())>>4);z++)
                if(!world.isChunkLoaded(x,z))return false;
        var hit=world.rayTraceBlocks(from,direction,distance,FluidCollisionMode.NEVER,true);
        return hit!=null&&hit.getHitBlock()!=null;
    }
}

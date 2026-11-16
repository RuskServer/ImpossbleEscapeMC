package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.bukkit.*;
import org.bukkit.util.BoundingBox;

public final class BossSpawnSafety {
    private BossSpawnSafety() {}
    /** 未読込チャンクは触らない。幅・頭上と足元のVoxelShapeを確認する。 */
    public static boolean safePosition(Location p) {
        World w=p.getWorld();if(w==null||!Double.isFinite(p.getX())||!Double.isFinite(p.getY())||!Double.isFinite(p.getZ()))return false;
        int minX=(int)Math.floor(p.getX()-.31),maxX=(int)Math.floor(p.getX()+.31),minZ=(int)Math.floor(p.getZ()-.31),maxZ=(int)Math.floor(p.getZ()+.31);
        int minY=(int)Math.floor(p.getY()-.08),maxY=(int)Math.floor(p.getY()+1.95);
        if(minY<w.getMinHeight()||maxY>=w.getMaxHeight())return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!w.isChunkLoaded(x>>4,z>>4))return false;
        BoundingBox body=new BoundingBox(p.getX()-.3,p.getY()+.015,p.getZ()-.3,p.getX()+.3,p.getY()+1.95,p.getZ()+.3);
        boolean support=false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            var block=w.getBlockAt(x,y,z);if(block.isLiquid())return false;
            for(var b:block.getCollisionShape().getBoundingBoxes()) {
                BoundingBox box=b.clone().shift(x,y,z);
                if(box.overlaps(body))return false;
                if(box.getMaxY()<=p.getY()+.02&&box.getMaxY()>=p.getY()-.08&&box.getMinX()<=p.getX()&&box.getMaxX()>=p.getX()&&box.getMinZ()<=p.getZ()&&box.getMaxZ()>=p.getZ())support=true;
            }
        }
        return support;
    }
}

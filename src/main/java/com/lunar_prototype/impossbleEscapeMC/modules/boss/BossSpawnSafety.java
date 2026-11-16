package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.bukkit.*;
import org.bukkit.util.BoundingBox;

public final class BossSpawnSafety {
    private BossSpawnSafety() {}
    /** 未読込チャンクは触らない。幅・頭上と足元のVoxelShapeを確認する。 */
    public static boolean safePosition(Location p) {return check(p,true);}
    static boolean clearBody(Location p) {return check(p,false);}
    private static boolean check(Location p,boolean requireSupport) {
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
        return !requireSupport||support;
    }

    /** 段差は通常の歩行で上れる0.6まで。水平移動中の体積も確認して壁を跨がない。 */
    static boolean walkable(Location from,Location to) {
        if(!from.getWorld().equals(to.getWorld())||Math.abs(from.getY()-to.getY())>.60001)return false;
        double distance=Math.hypot(to.getX()-from.getX(),to.getZ()-from.getZ());
        int steps=Math.max(1,(int)Math.ceil(distance/.25));
        double high=Math.max(from.getY(),to.getY());
        for(int i=0;i<=steps;i++) {
            double t=i/(double)steps;
            double x=from.getX()+(to.getX()-from.getX())*t,z=from.getZ()+(to.getZ()-from.getZ())*t;
            if(!clearBody(new Location(from.getWorld(),x,high,z)))return false;
            if(floorHeights(from.getWorld(),x,z,Math.min(from.getY(),to.getY())-.02,high+.02).isEmpty())return false;
        }
        return true;
    }
    static java.util.List<Double> floorHeights(World world,double x,double z,double min,double max) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        if(!world.isChunkLoaded(bx>>4,bz>>4))return java.util.List.of();
        var heights=new java.util.TreeSet<Double>();
        for(int y=Math.max(world.getMinHeight(),(int)Math.floor(min)-1);y<=Math.min(world.getMaxHeight()-1,(int)Math.floor(max));y++) {
            var block=world.getBlockAt(bx,y,bz);if(block.isLiquid())continue;
            for(var shape:block.getCollisionShape().getBoundingBoxes()) {
                double top=y+shape.getMaxY();
                if(top>=min-1e-6&&top<=max+1e-6&&x>=bx+shape.getMinX()&&x<=bx+shape.getMaxX()
                        &&z>=bz+shape.getMinZ()&&z<=bz+shape.getMaxZ())heights.add(top);
            }
        }
        return java.util.List.copyOf(heights);
    }
}

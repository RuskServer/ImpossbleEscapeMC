package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.bukkit.World;
import java.util.*;

/** 1回の物理更新だけ衝突形状をキャッシュする。未読込チャンクは壁扱いで、生成・読込しない。 */
final class RagdollTerrain implements RagdollPhysics.Terrain {
    private record Cell(int x,int y,int z) {}
    private final World world;
    private final Map<Cell,List<RagdollPhysics.Box>> blocks=new HashMap<>(),nearby=new HashMap<>();
    RagdollTerrain(World world){this.world=world;}
    @Override public List<RagdollPhysics.Box> boxes(double x,double y,double z) {
        Cell at=new Cell((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
        return nearby.computeIfAbsent(at,k->{
            var result=new ArrayList<RagdollPhysics.Box>();
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
                Cell c=new Cell(k.x+dx,k.y+dy,k.z+dz);
                result.addAll(blocks.computeIfAbsent(c,this::shape));
            }
            return result;
        });
    }
    private List<RagdollPhysics.Box> shape(Cell c) {
        if(c.y<world.getMinHeight()||c.y>=world.getMaxHeight()||!world.isChunkLoaded(c.x>>4,c.z>>4))
            return List.of(new RagdollPhysics.Box(c.x,c.y,c.z,c.x+1,c.y+1,c.z+1));
        var result=new ArrayList<RagdollPhysics.Box>();
        // BukkitのVoxelShapeはブロック相対座標。段差・階段・フェンスの実形状を世界座標へ変換する。
        for(var b:world.getBlockAt(c.x,c.y,c.z).getCollisionShape().getBoundingBoxes())
            result.add(new RagdollPhysics.Box(c.x+b.getMinX(),c.y+b.getMinY(),c.z+b.getMinZ(),c.x+b.getMaxX(),c.y+b.getMaxY(),c.z+b.getMaxZ()));
        return result;
    }
}

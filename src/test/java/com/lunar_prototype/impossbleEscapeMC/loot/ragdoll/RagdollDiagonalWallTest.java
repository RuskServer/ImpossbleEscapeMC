package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Vector3d;
import java.util.*;

/** 階段ブロックではなく、通常ブロックを斜めに並べた壁の凹凸で検証する。 */
public final class RagdollDiagonalWallTest {
    private static final Vector3d MOTION=new Vector3d(.3286947205545099,0,.30040347822525804);
    public static void main(String[] args) {
        // 旧実装はこの配置の10tick目、頭の回転箱が壁に約9mm入る。
        verify(2,false,0,new Vector3d(-.8255886960323915,.1,-1.5044129349351538),69,12);
        int cases=1;
        for(double slope:new double[]{.5,1,2})for(boolean corridor:new boolean[]{false,true})
            for(int rotation=0;rotation<4;rotation++)for(int yaw:new int[]{24,45,69,135,202})for(int seed:new int[]{0,12,19}) {
                Vector3d origin=slope==2?new Vector3d(-.8255886960323915,.1,-1.5044129349351538)
                        :new Vector3d(.65,.1,slope==1?1.35:2.35);
                verify(slope,corridor,rotation,origin,yaw,seed);cases++;
            }
        System.out.println("RagdollDiagonalWallTest OK: "+cases+" layouts, every tick and frozen pose");
    }
    private static Vector3d rotate(Vector3d v,int turns) {
        return switch(turns) {case 1->new Vector3d(v.z,v.y,-v.x);case 2->new Vector3d(-v.x,v.y,-v.z);case 3->new Vector3d(-v.z,v.y,v.x);default->new Vector3d(v);};
    }
    private static void verify(double slope,boolean corridor,int rotation,Vector3d origin,int yaw,int seed) {
        List<RagdollPhysics.Box> boxes=new ArrayList<>();
        boxes.add(new RagdollPhysics.Box(-30,-3,-30,30,0,30));
        for(int z=-8;z<=8;z++) {
            int start=(int)Math.floor(z*slope),width=Math.max(1,(int)slope);
            for(int x=start;x<start+width;x++)boxes.add(block(x,z,rotation));
            if(corridor)for(int x=start-4;x<start-4+width;x++)boxes.add(block(x,z,rotation));
        }
        Map<List<Integer>,List<RagdollPhysics.Box>> cache=new HashMap<>();
        RagdollPhysics.Terrain terrain=(x,y,z)->cache.computeIfAbsent(List.of((int)Math.floor(x),(int)Math.floor(z)),key->{
            List<RagdollPhysics.Box> near=new ArrayList<>();
            for(var box:boxes)if(box.maxX()>key.get(0)-1&&box.minX()<key.get(0)+2&&box.maxZ()>key.get(1)-1&&box.minZ()<key.get(1)+2)near.add(box);
            return near;
        });
        Vector3d motion=rotate(MOTION,rotation);
        var physics=new RagdollPhysics(rotate(origin,rotation),yaw-rotation*90,motion,motion,seed);
        String context="slope="+slope+" corridor="+corridor+" rotation="+rotation+" yaw="+yaw+" seed="+seed;
        for(int tick=0;tick<60;tick++) {
            physics.step(terrain);
            check(physics.valid(),"finite body "+context);
            check(physics.maxJointError()<.16,"connected body "+context+" error="+physics.maxJointError());
            for(var part:RagdollPhysics.Part.values())for(var box:boxes) {
                // 角だけでなく、薄い面の交差もSATで調べる。見える部位の侵入を許容しない。
                double depth=RagdollCollision.correction(physics.pose(part),RagdollPhysics.halfSize(part),box).length();
                check(depth<.001,"penetration "+context+" tick="+tick+" part="+part+" depth="+depth);
            }
            if(tick==47)physics.freeze(); // 実運用の標準48tick打ち切りでも埋まった姿勢を残さない。
        }
    }
    private static RagdollPhysics.Box block(int x,int z,int rotation) {
        Vector3d a=rotate(new Vector3d(x,0,z),rotation),b=rotate(new Vector3d(x+1,5,z+1),rotation);
        return new RagdollPhysics.Box(Math.min(a.x,b.x),0,Math.min(a.z,b.z),Math.max(a.x,b.x),5,Math.max(a.z,b.z));
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}

package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Vector3d;
import java.util.*;

public final class RagdollPhysicsTest {
    private static final RagdollPhysics.Box FLOOR=new RagdollPhysics.Box(-30,-3,-30,30,0,30);
    public static void main(String[] args) throws Exception {
        for(int yaw:new int[]{0,90,180,-90})for(int seed=0;seed<8;seed++) {
            var physics=new RagdollPhysics(new Vector3d(0,.1,0),yaw,new Vector3d(),new Vector3d(0,0,1),seed);
            for(int i=0;i<48;i++)physics.step((x,y,z)->List.of(FLOOR));
            check(physics.valid(),"finite, bounded body");
            check(physics.maxJointError()<.12,"connected limbs: "+physics.maxJointError());
            for(var part:RagdollPhysics.Part.values())check(physics.pose(part).center().y>-.02,"body part stays above floor");
            check(physics.pose(RagdollPhysics.Part.TORSO).center().y<.65,"body actually falls rather than freezing upright");
            for(var part:RagdollPhysics.Part.values()) {
                var pose=physics.pose(part);var half=RagdollPhysics.halfSize(part);
                for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                    Vector3d corner=pose.rotation().transform(new Vector3d(x*half.x,y*half.y,z*half.z)).add(pose.center());
                    check(corner.y>-.035,"rotated mesh corner must not sink through floor: "+part+" "+corner.y);
                }
            }
            for(int i=48;i<80;i++)physics.step((x,y,z)->List.of(FLOOR));
            check(physics.sleeping(),"body settles within finite simulation window");
            var before=physics.pose(RagdollPhysics.Part.HEAD).center();physics.step((x,y,z)->List.of(FLOOR));
            check(before.equals(physics.pose(RagdollPhysics.Part.HEAD).center()),"sleeping body stops changing");
        }
        var wall=new RagdollPhysics.Box(.7,-3,-30,1,5,30);
        var p=new RagdollPhysics(new Vector3d(0,.1,0),0,new Vector3d(.35,0,0),new Vector3d(1,0,0),7);
        for(int i=0;i<80;i++)p.step((x,y,z)->List.of(FLOOR,wall));
        check(p.valid(),"wall collision stays finite");
        for(var part:RagdollPhysics.Part.values())check(p.pose(part).center().x<.71,"wall stops corpse movement");
        var slab=new RagdollPhysics.Box(-30,-3,-30,30,.5,30);
        p=new RagdollPhysics(new Vector3d(0,.7,0),180,new Vector3d(),new Vector3d(0,0,-1),8);
        for(int i=0;i<80;i++)p.step((x,y,z)->List.of(slab));
        check(p.pose(RagdollPhysics.Part.TORSO).center().y>.49,"non-full block collision height");
        var plus=new RagdollPhysics(new Vector3d(0,.1,0),0,new Vector3d(),new Vector3d(1,0,0),1);
        var minus=new RagdollPhysics(new Vector3d(0,.1,0),0,new Vector3d(),new Vector3d(-1,0,0),1);
        for(int i=0;i<20;i++){plus.step((x,y,z)->List.of(FLOOR));minus.step((x,y,z)->List.of(FLOOR));}
        check(plus.pose(RagdollPhysics.Part.TORSO).center().x>minus.pose(RagdollPhysics.Part.TORSO).center().x,"impact direction changes fall");
        var far=new RagdollPhysics(new Vector3d(100000,-33,100000),0,new Vector3d(),new Vector3d(),1);
        var farFloor=new RagdollPhysics.Box(99970,-36,99970,100030,-33,100030);
        for(int i=0;i<80;i++)far.step((x,y,z)->List.of(farFloor));
        check(far.valid(),"large and negative world coordinates");
        System.out.println("RagdollPhysicsTest OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

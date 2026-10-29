package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Vector3d;
import java.util.List;

public final class RagdollWallTest {
    public static void main(String[] args) {
        var floor=new RagdollPhysics.Box(-20,-3,-20,20,0,20);
        for(double thickness:new double[]{1.52,.125,.03125})for(boolean cornerWall:new boolean[]{false,true}) {
            var wall=new RagdollPhysics.Box(.48,-3,-20,.48+thickness,5,20);
            var back=new RagdollPhysics.Box(-20,-3,.48,20,5,.48+thickness);
            for(int yaw:new int[]{0,30,60,90,135,180,-90})for(int seed=0;seed<8;seed++) {
                var physics=new RagdollPhysics(new Vector3d(0,.1,0),yaw,new Vector3d(.3,0,0),new Vector3d(1,0,0),seed);
                for(int tick=0;tick<60;tick++) {
                    physics.step((x,y,z)->cornerWall?List.of(floor,wall,back):List.of(floor,wall));
                    check(physics.valid(),"finite body");
                    for(var part:RagdollPhysics.Part.values()) {
                        var pose=physics.pose(part);var half=RagdollPhysics.halfSize(part);
                        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                            Vector3d corner=pose.rotation().transform(new Vector3d(x*half.x,y*half.y,z*half.z)).add(pose.center());
                            check(corner.x<=.485,"wall penetration yaw="+yaw+" seed="+seed+" tick="+tick+" part="+part+" x="+corner.x);
                            check(corner.y>=-.035,"floor penetration yaw="+yaw+" seed="+seed+" tick="+tick+" part="+part+" y="+corner.y);
                            if(cornerWall)check(corner.z<=.485,"corner wall penetration yaw="+yaw+" seed="+seed+" tick="+tick+" part="+part+" z="+corner.z);
                        }
                    }
                    check(physics.maxJointError()<.16,"connected body: "+physics.maxJointError());
                }
            }
        }
        System.out.println("RagdollWallTest OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

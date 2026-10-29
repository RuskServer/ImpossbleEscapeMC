package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollCollisionTest {
    public static void main(String[] args) {
        // 薄い壁が部位の中央を横切る。8頂点はいずれも壁の外なので角だけでは検出できない。
        var pose=new RagdollPhysics.Pose(new Vector3d(-.02,1,0),new Quaterniond());
        var half=new Vector3d(.25,.375,.125);
        var wall=new RagdollPhysics.Box(0,0,-2,.03125,2,2);
        var shift=RagdollCollision.correction(pose,half,wall);
        check(shift.x<-.2,"thin wall must expel the full box on its original side");
        var moved=new RagdollPhysics.Pose(new Vector3d(pose.center()).add(shift),pose.rotation());
        check(RagdollCollision.correction(moved,half,wall).lengthSquared()==0,"correction separates full box");
        var empty=new RagdollPhysics.Box(2,0,-2,3,2,2);
        check(RagdollCollision.correction(pose,half,empty).lengthSquared()==0,"separate boxes do not collide");
        // AABB同士では重なって見えるが、細い回転箱の斜辺の外。部位軸の分離も必要。
        pose=new RagdollPhysics.Pose(new Vector3d(),new Quaterniond().rotateY(Math.PI/4));
        half=new Vector3d(.7,.1,.03);
        var diagonalGap=new RagdollPhysics.Box(.35,-.05,.35,.4,.05,.4);
        check(RagdollCollision.correction(pose,half,diagonalGap).lengthSquared()==0,"rotated separating axis avoids false collision");
        System.out.println("RagdollCollisionTest OK");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

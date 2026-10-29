package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollPickingTest {
    public static void main(String[] args) {
        var pose=new RagdollPhysics.Pose(new Vector3d(0,0,-2),new Quaterniond());
        var half=new Vector3d(.25,.5,.125);
        close(RagdollPicking.distance(pose,half,new Vector3d(),new Vector3d(0,0,-1),3),1.875);
        miss(RagdollPicking.distance(pose,half,new Vector3d(),new Vector3d(0,0,1),3));
        miss(RagdollPicking.distance(pose,half,new Vector3d(1,0,0),new Vector3d(0,0,-1),3));
        miss(RagdollPicking.distance(pose,half,new Vector3d(),new Vector3d(0,0,-1),1.8));
        close(RagdollPicking.distance(pose,half,new Vector3d(0,0,-2),new Vector3d(0,0,-1),3),0);
        // 回転した長い部位を足先側から狙う。胴体中心の固定箱に依存しない。
        var rotated=new RagdollPhysics.Pose(new Vector3d(1,0,-2),new Quaterniond().rotateX(Math.PI/2));
        close(RagdollPicking.distance(rotated,half,new Vector3d(1,0,0),new Vector3d(0,0,-1),3),1.5);
        // 負の高さ・遠いワールド座標でも同じ距離。
        var offset=new Vector3d(12000,-34,-8000);
        var moved=new RagdollPhysics.Pose(new Vector3d(pose.center()).add(offset),new Quaterniond());
        close(RagdollPicking.distance(moved,half,offset,new Vector3d(0,0,-1),3),1.875);
        // 入力を変更しない。
        close(pose.center().z,-2);close(half.z,.125);
        System.out.println("RagdollPickingTest passed");
    }
    private static void close(double actual,double expected) {if(Math.abs(actual-expected)>1e-8)throw new AssertionError(actual+" != "+expected);}
    private static void miss(double value) {if(value!=Double.POSITIVE_INFINITY)throw new AssertionError("unexpected hit: "+value);}
}

package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollLootTargetTest {
    public static void main(String[] args) {
        var pose=new RagdollPhysics.Pose(new Vector3d(0,0,-2),new Quaterniond());
        var half=RagdollPhysics.halfSize(RagdollPhysics.Part.TORSO);
        var eye=new Vector3d(-1,.35,0);var nativeHit=new Vector3d(.274,.35,-1.725);
        var ray=new Vector3d(nativeHit).sub(eye).normalize();
        var bounds=RagdollInteractionGeometry.bounds(pose,half,1,2);
        check(RagdollLootTarget.distanceToBox(bounds,eye)<3,"native handle is reachable");
        check(!Double.isFinite(RagdollInteractionGeometry.distance(pose,half,eye,ray,3)),"old recast misses a valid native box corner");
        var captured=RagdollLootTarget.capture(pose,half,nativeHit);check(captured!=null,"capture native box hit");
        var point=captured.world(pose);check(Math.abs(point.x)<=.275001&&Math.abs(point.y)<=.400001&&Math.abs(point.z+2)<=.150001,"corner projected onto actual body shell");
        double distance=eye.distance(point);
        check(RagdollInteractionGeometry.visible(distance,Double.POSITIVE_INFINITY,Double.MAX_VALUE),"unobstructed clicked body point");
        check(!RagdollInteractionGeometry.visible(distance,distance-.1875,Double.MAX_VALUE),"projection does not bypass solid obstacles");
        var freshEye=new Vector3d(eye).add(.02,0,0);
        check(!Double.isFinite(RagdollInteractionGeometry.distance(pose,half,freshEye,new Vector3d(1,0,0),3)),"turning away next tick rejects old pipeline");
        check(RagdollLootTarget.distanceToBox(bounds,freshEye)<3&&captured.world(pose).equals(point),"captured point survives changed view and tiny player motion");
        var moved=new RagdollPhysics.Pose(new Vector3d(.1,0,-2),new Quaterniond().rotateY(.15));
        var follow=captured.world(moved);
        check(new Quaterniond(moved.rotation()).conjugate().transform(new Vector3d(follow).sub(moved.center())).distance(captured.local())<1e-9,"captured local point follows body pose");
        var far=new RagdollPhysics.Pose(new Vector3d(10,0,-2),new Quaterniond());
        check(RagdollLootTarget.distanceToBox(RagdollInteractionGeometry.bounds(far,half,1,2),eye)>3,"moving corpse out of reach must still reject");
        var copy=captured.local();copy.set(100,100,100);check(captured.world(pose).equals(point),"saved point immutable");
        for(int i=0;i<100;i++) {
            var rotation=new Quaterniond().rotateXYZ(i*.19,i*.13,i*.31);
            double extent=Math.abs(rotation.transform(new Vector3d(half.x,0,0)).y)+Math.abs(rotation.transform(new Vector3d(0,half.y,0)).y)+Math.abs(rotation.transform(new Vector3d(0,0,half.z)).y);
            var floorPose=new RagdollPhysics.Pose(new Vector3d(0,extent+.01,0),rotation);
            var nearFloor=RagdollLootTarget.capture(floorPose,half,new Vector3d(.3,-.01,.3));
            check(nearFloor.world(floorPose).y>=.01-1e-9,"interaction padding must not put occlusion target inside floor");
        }
        check(RagdollLootTarget.capture(pose,half,new Vector3d(Double.NaN,0,0))==null,"invalid packet position");
        check(RagdollLootTarget.capture(new RagdollPhysics.Pose(new Vector3d(),new Quaterniond(Double.NaN,0,0,1)),half,nativeHit)==null,"invalid orientation");
        System.out.println("RagdollLootTargetTest passed: reproduced native-corner and next-tick view misses; captured target, moving body, walls and range");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}

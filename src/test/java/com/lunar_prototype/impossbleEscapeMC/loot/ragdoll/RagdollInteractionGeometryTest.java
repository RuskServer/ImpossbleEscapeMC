package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RagdollInteractionGeometryTest {
    public static void main(String[] args) {
        int handles=0;
        for(var part:RagdollPhysics.Part.values())handles+=RagdollInteractionGeometry.sections(part);
        check(handles==11,"all six body parts covered by 11 handles");
        // 直立・寝姿勢・斜めの壁際。外層の全頂点が少なくとも1個の操作箱に入る。
        for(int i=0;i<200;i++) {
            var q=new Quaterniond().rotateXYZ(i*.17,i*.31,i*.23);
            var center=new Vector3d(12000,-34,-8000);
            var pose=new RagdollPhysics.Pose(center,q);
            for(var part:RagdollPhysics.Part.values()) {
                var half=RagdollPhysics.halfSize(part);int count=RagdollInteractionGeometry.sections(part);
                for(int sample=0;sample<=8;sample++)for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                    double y=-half.y+2*half.y*sample/8;
                    var point=q.transform(new Vector3d(x*(half.x+.008),y,z*(half.z+.008))).add(center);
                    boolean covered=false;
                    for(int section=0;section<count;section++) {
                        var bounds=RagdollInteractionGeometry.bounds(pose,half,section,count);
                        check(bounds.width()>0&&bounds.width()<1.2&&bounds.height()>0&&bounds.height()<1.2,"bounded handle dimensions");
                        var b=bounds.bottom();double w=bounds.width()/2;
                        covered|=point.x>=b.x-w-1e-6&&point.x<=b.x+w+1e-6&&point.y>=b.y-1e-6&&point.y<=b.y+bounds.height()+1e-6&&point.z>=b.z-w-1e-6&&point.z<=b.z+w+1e-6;
                    }
                    check(covered,"skin shell must have native clickable coverage");
                }
            }
        }
        var pose=new RagdollPhysics.Pose(new Vector3d(0,0,-2),new Quaterniond());
        var half=RagdollPhysics.halfSize(RagdollPhysics.Part.TORSO);
        var eye=new Vector3d(.26,0,0);var ray=new Vector3d(0,0,-1);
        check(!Double.isFinite(RagdollPicking.distance(pose,half,eye,ray,3)),"old inner-box picker misses visible outer shell");
        double distance=RagdollInteractionGeometry.distance(pose,half,eye,ray,3);
        check(RagdollInteractionGeometry.visible(distance,Double.POSITIVE_INFINITY,3),"outer skin shell is clickable");
        check(RagdollInteractionGeometry.visible(2,1.99,3),"one-centimeter surface contact tolerated");
        check(!RagdollInteractionGeometry.visible(2,1.97,3),"three-centimeter obstruction rejected");
        check(!RagdollInteractionGeometry.visible(2,1,3),"full wall cannot be looted through");
        check(!RagdollInteractionGeometry.visible(2,1.8125,3),"trapdoor-thickness obstruction rejected");
        check(RagdollInteractionGeometry.visible(3,Double.POSITIVE_INFINITY,3),"inclusive interaction range");
        check(!RagdollInteractionGeometry.visible(3.001,Double.POSITIVE_INFINITY,3),"outside range rejected");
        check(!RagdollInteractionGeometry.visible(Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,3),"miss rejected");
        check(!RagdollInteractionGeometry.visible(Double.NaN,Double.POSITIVE_INFINITY,3),"invalid hit rejected");
        check(!RagdollInteractionGeometry.visible(1,Double.NaN,3),"invalid wall rejected");
        check(half.x==.25&&half.y==.375&&half.z==.125,"input half-size unchanged");
        System.out.println("RagdollInteractionGeometryTest passed: 200 rotations, full body coverage, outer skin, contact tolerance, walls and reach");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}

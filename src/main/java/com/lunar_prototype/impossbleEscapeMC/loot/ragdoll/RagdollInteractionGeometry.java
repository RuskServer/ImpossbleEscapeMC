package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Vector3d;

/** 表示部位の外層と操作箱。Interactionは回転できないので長い部位を2分割する。 */
final class RagdollInteractionGeometry {
    static final double SKIN_MARGIN=.025;
    static final double CONTACT_TOLERANCE=.02;
    record Bounds(Vector3d bottom,float width,float height) {}
    private RagdollInteractionGeometry() {}
    static int sections(RagdollPhysics.Part part) {return part==RagdollPhysics.Part.HEAD?1:2;}
    static Bounds bounds(RagdollPhysics.Pose pose,Vector3d half,int section,int count) {
        if(count<1||section<0||section>=count)throw new IllegalArgumentException("Invalid body section");
        Vector3d local=new Vector3d(0,-half.y+(section+.5)*2*half.y/count,0);
        Vector3d center=pose.rotation().transform(local).add(pose.center());
        Vector3d size=new Vector3d(half.x,half.y/count,half.z).add(SKIN_MARGIN,SKIN_MARGIN,SKIN_MARGIN);
        Vector3d x=pose.rotation().transform(new Vector3d(size.x,0,0));
        Vector3d y=pose.rotation().transform(new Vector3d(0,size.y,0));
        Vector3d z=pose.rotation().transform(new Vector3d(0,0,size.z));
        double ex=Math.abs(x.x)+Math.abs(y.x)+Math.abs(z.x);
        double ey=Math.abs(x.y)+Math.abs(y.y)+Math.abs(z.y);
        double ez=Math.abs(x.z)+Math.abs(y.z)+Math.abs(z.z);
        return new Bounds(new Vector3d(center.x,center.y-ey,center.z),(float)(2*Math.max(ex,ez)),(float)(2*ey));
    }
    static double distance(RagdollPhysics.Pose pose,Vector3d half,Vector3d eye,Vector3d ray,double range) {
        return RagdollPicking.distance(pose,new Vector3d(half).add(SKIN_MARGIN,SKIN_MARGIN,SKIN_MARGIN),eye,ray,range);
    }
    static boolean visible(double distance,double wallDistance,double range) {
        return Double.isFinite(distance)&&distance>=0&&distance<=range
                &&!Double.isNaN(wallDistance)&&wallDistance>=0&&distance<=wallDistance+CONTACT_TOLERANCE;
    }
}

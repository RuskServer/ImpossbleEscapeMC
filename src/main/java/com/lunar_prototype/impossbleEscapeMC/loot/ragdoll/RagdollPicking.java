package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/** 見えている部位の回転箱と視線の交差。距離は正規化した視線に沿うメートル。 */
final class RagdollPicking {
    private RagdollPicking() {}
    static double distance(RagdollPhysics.Pose pose,Vector3d half,Vector3d eye,Vector3d direction,double range) {
        Quaterniond inverse=new Quaterniond(pose.rotation()).conjugate();
        Vector3d start=inverse.transform(new Vector3d(eye).sub(pose.center()));
        Vector3d ray=inverse.transform(new Vector3d(direction));
        double near=0,far=range;
        for(int axis=0;axis<3;axis++) {
            double origin=start.get(axis),delta=ray.get(axis),extent=half.get(axis);
            if(Math.abs(delta)<1e-10) {
                if(origin < -extent || origin > extent)return Double.POSITIVE_INFINITY;
                continue;
            }
            double a=(-extent-origin)/delta,b=(extent-origin)/delta;
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));
            if(near>far)return Double.POSITIVE_INFINITY;
        }
        return near;
    }
}

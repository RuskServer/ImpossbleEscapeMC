package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Vector3d;
import java.util.ArrayList;
import java.lang.Math;

/** 回転した部位箱とブロック箱の面・辺も含めた分離軸判定。角だけの判定では薄い壁が箱を横切れる。 */
final class RagdollCollision {
    private RagdollCollision() {}
    static Vector3d correction(RagdollPhysics.Pose pose,Vector3d half,RagdollPhysics.Box box) {
        Vector3d[] local={pose.rotation().transform(new Vector3d(1,0,0)),pose.rotation().transform(new Vector3d(0,1,0)),pose.rotation().transform(new Vector3d(0,0,1))};
        Vector3d extent=new Vector3d((box.maxX()-box.minX())*.5,(box.maxY()-box.minY())*.5,(box.maxZ()-box.minZ())*.5);
        Vector3d delta=new Vector3d(pose.center()).sub((box.minX()+box.maxX())*.5,(box.minY()+box.maxY())*.5,(box.minZ()+box.maxZ())*.5);
        // まずワールド軸で早期除外し、近い箱だけ残りの軸を調べる。
        for(int i=0;i<3;i++) {
            double radius=Math.abs(local[0].get(i))*half.x+Math.abs(local[1].get(i))*half.y+Math.abs(local[2].get(i))*half.z;
            if(Math.abs(delta.get(i))>=radius+extent.get(i))return new Vector3d();
        }
        Vector3d[] world={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)};
        var axes=new ArrayList<Vector3d>(15);
        for(Vector3d axis:world)axes.add(axis);
        for(Vector3d axis:local)axes.add(axis);
        for(Vector3d a:local)for(Vector3d b:world)axes.add(new Vector3d(a).cross(b));
        double depth=Double.POSITIVE_INFINITY;Vector3d result=new Vector3d();
        for(Vector3d axis:axes) {
            if(axis.lengthSquared()<1e-12)continue;
            axis.normalize();
            double a=Math.abs(axis.dot(local[0]))*half.x+Math.abs(axis.dot(local[1]))*half.y+Math.abs(axis.dot(local[2]))*half.z;
            double b=Math.abs(axis.x)*extent.x+Math.abs(axis.y)*extent.y+Math.abs(axis.z)*extent.z;
            double signed=delta.dot(axis),overlap=a+b-Math.abs(signed);
            if(overlap<=0)return new Vector3d();
            if(overlap<depth){depth=overlap;result.set(axis).mul(Math.copySign(overlap+.0001,signed));}
        }
        return result;
    }
}

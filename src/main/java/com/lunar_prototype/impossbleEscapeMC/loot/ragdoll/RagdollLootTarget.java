package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/** クライアントがクリックした位置を部位の座標へ保存する。後の視線方向には依存しない。 */
record RagdollLootTarget(Vector3d local) {
    RagdollLootTarget {local=new Vector3d(local);}
    @Override public Vector3d local(){return new Vector3d(local);}
    static RagdollLootTarget capture(RagdollPhysics.Pose pose,Vector3d half,Vector3d hit) {
        if(!pose.center().isFinite()||!pose.rotation().isFinite()||!hit.isFinite())return null;
        Vector3d point=new Quaterniond(pose.rotation()).conjugate().transform(new Vector3d(hit).sub(pose.center()));
        // クリックしやすさの余白は遮蔽の確認点へ持ち込まない。床・壁の中へ確認点を押し込まないため。
        Vector3d limit=new Vector3d(half);
        for(int axis=0;axis<3;axis++)point.setComponent(axis,Math.clamp(point.get(axis),-limit.get(axis),limit.get(axis)));
        return point.isFinite()?new RagdollLootTarget(point):null;
    }
    Vector3d world(RagdollPhysics.Pose pose){return pose.rotation().transform(new Vector3d(local)).add(pose.center());}
    static double distanceToBox(RagdollInteractionGeometry.Bounds box,Vector3d eye) {
        Vector3d bottom=box.bottom();double half=box.width()/2;
        double x=Math.clamp(eye.x,bottom.x-half,bottom.x+half),y=Math.clamp(eye.y,bottom.y,bottom.y+box.height()),z=Math.clamp(eye.z,bottom.z-half,bottom.z+half);
        return eye.distance(x,y,z);
    }
}

package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.joml.*;
import java.lang.Math;
import java.util.*;

/** 小さな位置ベースの関節シミュレーション。ワールド参照・エンティティ操作を持たない。 */
public final class RagdollPhysics {
    public enum Part { HEAD, TORSO, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }
    public record Pose(Vector3d center,Quaterniond rotation) {}
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    public interface Terrain { List<Box> boxes(double x,double y,double z); }
    private record Link(int a,int b,double length) {}
    // 胸・腰・左右肩・左右股関節を距離拘束で剛体にし、頭・手先・足先を関節で結ぶ。
    private static final double[][] REST={{0,1.5,0},{0,.75,0},{-.375,1.5,0},{.375,1.5,0},
            {-.125,.75,0},{.125,.75,0},{0,1.75,0},{-.375,.75,0},{.375,.75,0},{-.125,0,0},{.125,0,0}};
    private final Vector3d[] point=new Vector3d[11],velocity=new Vector3d[11];
    private final List<Link> links=new ArrayList<>();
    private final Vector3d origin;
    private final double[] radius={.15,.15,.125,.125,.125,.125,.25,.075,.075,.075,.075};
    private int ticks,quietTicks;
    private boolean sleeping;
    private int contacts;
    private final Vector3d initialRight;

    public RagdollPhysics(Vector3d origin,double yaw,Vector3d motion,Vector3d impact,long seed) {
        this.origin=new Vector3d(origin);
        Quaterniond rotation=new Quaterniond().rotateY(Math.PI-Math.toRadians(yaw));
        initialRight=rotation.transform(new Vector3d(1,0,0));
        Vector3d push=new Vector3d(impact).setComponent(1,0);
        if(!finite(push)||push.lengthSquared()<.00001)push=rotation.transform(new Vector3d(0,0,-1));
        push.normalize();
        Vector3d travel=finite(motion)?new Vector3d(motion):new Vector3d();
        if(travel.length()>.35)travel.normalize(.35);
        java.util.Random random=new java.util.Random(seed);
        for(int i=0;i<point.length;i++) {
            point[i]=rotation.transform(new Vector3d(REST[i])).add(origin);
            // 上体に強く、末端に遅れて力を伝える。完全な左右対称で直立したまま潰れるのを防ぐ。
            double strength=i==0||i==6?.16:i==1?.10:i<6?.08:.025;
            velocity[i]=new Vector3d(travel).add(new Vector3d(push).mul(strength));
            velocity[i].add((random.nextDouble()-.5)*.015,0,(random.nextDouble()-.5)*.015);
        }
        for(int a=0;a<6;a++)for(int b=a+1;b<6;b++)link(a,b);
        link(0,6);link(2,7);link(3,8);link(4,9);link(5,10);
    }
    private static boolean finite(Vector3dc v){return Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    private void link(int a,int b){links.add(new Link(a,b,point[a].distance(point[b])));}
    public int ticks(){return ticks;}
    public boolean sleeping(){return sleeping;}
    public void freeze(){sleeping=true;}
    public double maxJointError(){double error=0;for(Link l:links)error=Math.max(error,Math.abs(point[l.a].distance(point[l.b])-l.length));return error;}
    public double displacement(){double d=0;for(Vector3d p:point)d=Math.max(d,p.distance(origin));return d;}
    public boolean valid(){for(Vector3d p:point)if(!finite(p))return false;return displacement()<12;}

    public void step(Terrain terrain) {
        if(sleeping)return;
        double travel=0;contacts=0;
        for(int sub=0;sub<4;sub++) {
            Vector3d[] previous=new Vector3d[point.length];
            for(int i=0;i<point.length;i++) {
                previous[i]=new Vector3d(point[i]);velocity[i].y-=.08*.25;
                if(velocity[i].length()>.6)velocity[i].normalize(.6);
                point[i].fma(.25,velocity[i]);
            }
            for(int pass=0;pass<10;pass++) {
                for(Link l:links) {
                    Vector3d delta=new Vector3d(point[l.b]).sub(point[l.a]);double n=delta.length();if(n<1e-8)continue;
                    delta.mul((n-l.length)/n*.5);point[l.a].add(delta);point[l.b].sub(delta);
                }
                limit(0,6,up(),.25,Math.toRadians(55));
                limit(2,7,new Vector3d(up()).negate(),.75,Math.toRadians(155));
                limit(3,8,new Vector3d(up()).negate(),.75,Math.toRadians(155));
                limit(4,9,new Vector3d(up()).negate(),.75,Math.toRadians(95));
                limit(5,10,new Vector3d(up()).negate(),.75,Math.toRadians(95));
                for(int i=0;i<point.length;i++)collide(point[i],radius[i],terrain);
                // 末端だけでなく、細長い腕・脚の途中が床を突き抜けないようにする。
                for(int[] bone:new int[][]{{2,7},{3,8},{4,9},{5,10}}) {
                    Vector3d middle=new Vector3d(point[bone[0]]).lerp(point[bone[1]],.5),before=new Vector3d(middle);
                    collide(middle,.125,terrain);middle.sub(before);
                    point[bone[0]].add(middle);point[bone[1]].add(middle);
                }
                if(pass>=7)collideMeshes(terrain);
            }
            for(int i=0;i<point.length;i++) {
                double d=point[i].distance(previous[i]);travel=Math.max(travel,d);
                velocity[i].set(point[i]).sub(previous[i]).mul(4*.985);
                // 接地した点のエネルギーを落とし、小刻みに跳ね続けるのを抑える。
                Vector3d test=new Vector3d(point[i]);test.y-=.018;
                if(collide(test,radius[i],terrain))velocity[i].mul(.55);
            }
        }
        ticks++;
        quietTicks=contacts>0&&travel<.012?quietTicks+1:0;
        if(ticks>=18&&quietTicks>=8)sleeping=true;
    }
    private Vector3d up(){return new Vector3d(point[0]).sub(point[1]).normalize();}
    private void limit(int base,int end,Vector3d axis,double length,double maxAngle) {
        Vector3d direction=new Vector3d(point[end]).sub(point[base]);if(direction.lengthSquared()<1e-10)return;
        direction.normalize();double dot=Math.clamp(direction.dot(axis),-1,1);
        if(dot<Math.cos(maxAngle)) {
            Vector3d side=new Vector3d(direction).fma(-dot,axis);
            if(side.lengthSquared()<1e-10)side.set(initialRight).fma(-initialRight.dot(axis),axis);
            if(side.lengthSquared()<1e-10)side.set(axis).cross(0,1,0);
            if(side.lengthSquared()<1e-10)side.set(axis).cross(1,0,0);
            side.normalize();
            direction.set(axis).mul(Math.cos(maxAngle)).fma(Math.sin(maxAngle),side);
            point[end].set(point[base]).fma(length,direction);
        }
    }
    private boolean collide(Vector3d p,double r,Terrain terrain) {
        boolean hit=false;
        for(Box b:terrain.boxes(p.x,p.y,p.z)) {
            double cx=Math.clamp(p.x,b.minX,b.maxX),cy=Math.clamp(p.y,b.minY,b.maxY),cz=Math.clamp(p.z,b.minZ,b.maxZ);
            Vector3d normal=new Vector3d(p).sub(cx,cy,cz);double square=normal.lengthSquared();
            if(square>=r*r)continue;
            if(square>1e-12)p.fma((r-Math.sqrt(square))/Math.sqrt(square),normal);
            else {
                double[] distance={p.x-b.minX,b.maxX-p.x,p.y-b.minY,b.maxY-p.y,p.z-b.minZ,b.maxZ-p.z};
                int side=0;for(int i=1;i<6;i++)if(distance[i]<distance[side])side=i;
                switch(side){case 0->p.x=b.minX-r;case 1->p.x=b.maxX+r;case 2->p.y=b.minY-r;case 3->p.y=b.maxY+r;case 4->p.z=b.minZ-r;case 5->p.z=b.maxZ+r;}
            }
            contacts++;hit=true;
        }
        return hit;
    }
    public static Vector3d halfSize(Part part) {
        return switch(part){case HEAD->new Vector3d(.25,.25,.25);case TORSO->new Vector3d(.25,.375,.125);default->new Vector3d(.125,.375,.125);};
    }
    /** 最後の接触反復では表示箱の角も確認し、回転した頭や胴の角が床へ沈むのを抑える。 */
    private void collideMeshes(Terrain terrain) {
        for(Part part:Part.values()) {
            Pose pose=pose(part);Vector3d half=halfSize(part),shift=new Vector3d();
            for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}) {
                Vector3d corner=pose.rotation.transform(new Vector3d(x*half.x,y*half.y,z*half.z)).add(pose.center).add(shift);
                Vector3d original=new Vector3d(corner);collide(corner,.006,terrain);shift.add(corner.sub(original));
            }
            switch(part) {
                case TORSO->{for(int i=0;i<6;i++)point[i].add(shift);}
                case HEAD->point[6].add(shift);
                case RIGHT_ARM->{point[2].add(shift);point[7].add(shift);}
                case LEFT_ARM->{point[3].add(shift);point[8].add(shift);}
                case RIGHT_LEG->{point[4].add(shift);point[9].add(shift);}
                case LEFT_LEG->{point[5].add(shift);point[10].add(shift);}
            }
        }
    }
    public Pose pose(Part part) {
        Vector3d up=up(),right=new Vector3d(point[3]).sub(point[2]);right.fma(-right.dot(up),up).normalize();
        Vector3d back=new Vector3d(right).cross(up).normalize();
        Quaterniond body=new Quaterniond().setFromNormalized(new Matrix3d().setColumn(0,right).setColumn(1,up).setColumn(2,back));
        if(part==Part.TORSO)return new Pose(new Vector3d(point[1]).lerp(point[0],.5),body);
        if(part==Part.HEAD) {
            Vector3d headUp=new Vector3d(point[6]).sub(point[0]).normalize();
            return new Pose(new Vector3d(point[6]),new Quaterniond().rotationTo(up,headUp).mul(body));
        }
        int a,b;
        switch(part){case RIGHT_ARM->{a=2;b=7;}case LEFT_ARM->{a=3;b=8;}case RIGHT_LEG->{a=4;b=9;}default->{a=5;b=10;}}
        Vector3d axis=new Vector3d(point[a]).sub(point[b]).normalize();
        Quaterniond q=new Quaterniond().rotationTo(up,axis).mul(body);
        return new Pose(new Vector3d(point[a]).lerp(point[b],.5),q);
    }
}

package com.lunar_prototype.impossbleEscapeMC.listener;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import java.lang.reflect.Proxy;
import java.util.*;

/** 胴中心を通る弾の腕誤判定を再現し、胴防護と外周だけの腕命中を分離する。 */
public final class BallisticArmHitTest {
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private static final class Victim {
        double x=12000,y=-34,z=-8000,width=.6,eyeHeight=1.62;
        float yaw;
        final Set<String> tags=new HashSet<>();
        final LivingEntity entity=(LivingEntity)Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),new Class[]{LivingEntity.class},(p,m,a)->switch(m.getName()) {
            case "getLocation"->new Location(null,x,y,z,yaw,0);
            case "getEyeLocation"->new Location(null,x,y+eyeHeight,z,yaw,0);
            case "getBodyYaw"->yaw;
            case "getWidth"->width;
            case "getScoreboardTags"->tags;
            default->throw new AssertionError(m.getName());
        });
        Vector local(double right,double up,double forward) {
            double angle=Math.toRadians(yaw),cos=Math.cos(angle),sin=Math.sin(angle);
            return new Vector(x+right*cos-forward*sin,y+up,z+right*sin+forward*cos);
        }
        BoundingBox box(){return new BoundingBox(x-width/2,y,z-width/2,x+width/2,y+eyeHeight+.18,z+width/2).expand(.1);}
    }
    private static Vector entry(Victim v,Vector origin,Vector direction) {
        var hit=v.box().rayTrace(origin,direction,30);check(hit!=null,"test trajectory enters the expanded hit box");return hit.getHitPosition();
    }
    public static void main(String[] args)throws Exception {
        var v=new Victim();int cases=0,oldFalseArms=0;
        for(double width:new double[]{.3,.6,1.2})for(double eye:new double[]{1.62,1.27})
            for(int yaw:new int[]{0,45,90,135})for(int angle=0;angle<360;angle+=30)for(int elevation:new int[]{-10,0,10}) {
                v.width=width;v.eyeHeight=eye;v.yaw=yaw;
                double a=Math.toRadians(angle),e=Math.toRadians(elevation);
                Vector outward=new Vector(Math.sin(a)*Math.cos(e),Math.sin(e),Math.cos(a)*Math.cos(e));
                Vector direction=outward.clone().multiply(-1);
                double torsoHeight=(eye*.45+eye-.25)/2;
                Vector origin=new Vector(v.x,v.y+torsoHeight,v.z).add(outward.clone().multiply(10));
                Vector hit=entry(v,origin,direction),savedHit=hit.clone(),savedDirection=direction.clone();
                if(BulletDamageModel.ARMS.equals(BulletDamageModel.hitLocation(v.entity,hit)))oldFalseArms++;
                check(BulletDamageModel.BODY.equals(BulletDamageModel.hitLocation(v.entity,hit,direction)),"body-centre ray must remain body: yaw="+yaw+" angle="+angle+" width="+width);
                check(hit.equals(savedHit)&&direction.equals(savedDirection),"contact vectors must not be mutated");cases++;
            }
        check(cases==864&&oldFalseArms>300,"old side-surface bug reproduced across views and poses");
        v.width=.6;v.eyeHeight=1.62;
        for(int yaw:new int[]{0,45,90,135}) {
            v.yaw=yaw;
            for(double side:new double[]{-.26,.26}) {
                Vector origin=v.local(side,1.1,10),direction=v.local(side,1.1,0).subtract(origin).normalize();
                check(BulletDamageModel.ARMS.equals(BulletDamageModel.hitLocation(v.entity,entry(v,origin,direction),direction)),"front outer-arm trajectory remains arm");
            }
            Vector origin=v.local(10,1.1,.25),direction=v.local(0,1.1,.25).subtract(origin).normalize();
            check(BulletDamageModel.ARMS.equals(BulletDamageModel.hitLocation(v.entity,entry(v,origin,direction),direction)),"side graze outside torso depth remains arm");
        }
        v.yaw=0;
        Vector side=v.local(.4,1.1,0);
        check(BulletDamageModel.ARMS.equals(BulletDamageModel.hitLocation(v.entity,side,new Vector(1,0,0))),"only the forward ray counts; body behind contact cannot capture an arm hit");
        for(Vector unknown:Arrays.asList(null,new Vector(),new Vector(Double.NaN,0,0),new Vector(Double.POSITIVE_INFINITY,0,0)))
            check(BulletDamageModel.BODY.equals(BulletDamageModel.hitLocation(v.entity,side,unknown)),"unknown incoming direction does not invent unarmored arm damage");
        Vector leg=v.local(0,.3,.4),head=v.local(0,1.8,.4),face=v.local(0,1.5,.4);
        for(Vector point:List.of(leg,head,face))check(BulletDamageModel.hitLocation(v.entity,point).equals(BulletDamageModel.hitLocation(v.entity,point,new Vector(0,0,-1))),"head/face/leg classification unchanged");
        var bridge=DatapackBulletHitBridge.class.getDeclaredMethod("hitLocation",LivingEntity.class,Vector.class,Vector.class);bridge.setAccessible(true);
        check(bridge.invoke(null,v.entity,side,new Vector(-1,0,0)).equals(BulletDamageModel.BODY),"actual bridge passes incoming direction into body trace");
        check(bridge.invoke(null,v.entity,side,null).equals(BulletDamageModel.BODY),"nearest-marker fallback remains conservative");
        check(bridge.invoke(null,v.entity,head,new Vector(0,0,-1)).equals(BulletDamageModel.BODY),"unmarked head stays datapack-controlled");
        check(bridge.invoke(null,v.entity,face,new Vector(0,0,-1)).equals(BulletDamageModel.BODY),"unmarked face cannot bypass datapack head authority");
        v.tags.add("CRIT_HIT");
        check(bridge.invoke(null,v.entity,head,new Vector(0,0,-1)).equals(BulletDamageModel.HEAD),"marked head preserved");
        check(bridge.invoke(null,v.entity,face,new Vector(0,0,-1)).equals(BulletDamageModel.FACE),"marked face preserved");
        check(Math.abs(BulletDamageModel.damageForHit(12,"body",true,4)-7.2)<1e-9,"body armor reduction unchanged");
        check(BulletDamageModel.damageForHit(12,"arms",true,0)==12,"true arm damage multiplier unchanged");
        System.out.println("BallisticArmHitTest passed: "+cases+" centre rays (old false arms="+oldFalseArms+"), outer grazes, direction fallback, bridge and head/leg preservation");
    }
}

package com.lunar_prototype.impossbleEscapeMC.loot;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import org.joml.Vector3f;

public final class LockerModelTest {
    public static void main(String[] args) {
        check(LockerModel.PARTS.size()==14,"original display parts retained");
        check(LockerModel.PARTS.stream().filter(LockerModel.Part::door).count()==8,"all eight door parts animate");
        for(int facing=0;facing<4;facing++) {
            var rotate=new org.joml.Matrix4f().rotateY((float)(facing*Math.PI/2));
            var hinge=rotate.transformPosition(new Vector3f(-.4375f,0,-.5f));
            for(var part:LockerModel.PARTS) {
                var closed=LockerModel.transform(part,facing,false);
                var open=LockerModel.transform(part,facing,true);
                if(!part.door())check(closed.equals(open),"shelf and shell never move");
                for(int x=0;x<=1;x++)for(int y=0;y<=1;y++)for(int z=0;z<=1;z++) {
                    Vector3f local=new Vector3f(x,y*(part.slab()?.5f:1f),z);
                    var c=closed.transformPosition(new Vector3f(local));
                    var o=open.transformPosition(new Vector3f(local));
                    near(c.y,o.y,"opening preserves height");
                    near(new Vector3f(c).sub(hinge).length(),new Vector3f(o).sub(hinge).length(),"parts rotate without stretching");
                    check(c.y>=-.0001f&&c.y<=2.2501f,"original floor and top height");
                }
            }
            // 元コマンドの下側の扉パネル。右端は外（-Z）へ動く。
            var panel=LockerModel.PARTS.get(11);
            var c=LockerModel.transform(panel,facing,false).transformPosition(new Vector3f());
            var o=LockerModel.transform(panel,facing,true).transformPosition(new Vector3f());
            var inverse=new org.joml.Matrix4f(rotate).invert();inverse.transformPosition(c);inverse.transformPosition(o);
            near(c.x,.4375f,"closed panel right edge");near(c.z,-.5f,"closed panel front");
            near(o.x,-.4375f,"open panel aligned to hinge");near(o.z,-1.375f,"door swings outside cabinet");
        }
        Gson gson=new Gson();
        var old=gson.fromJson("{\"mapId\":\"old\",\"worldName\":\"world\"}",RaidMap.class);
        check(old.getLootLockers().isEmpty(),"legacy map compatibility");
        var spec=new RaidMap.LootLocker("locker-one",-17,-34,208,3);spec.generation=42;
        old.getLootLockers().add(spec);
        var restored=gson.fromJson(gson.toJson(old),RaidMap.class);var saved=restored.getLootLockers().getFirst();
        check(saved.id.equals(spec.id)&&saved.x==-17&&saved.y==-34&&saved.z==208&&saved.facing==3&&saved.generation==42,"placement and refill generation survive restart");
        check(old.getLootContainers().isEmpty()&&old.getLootDrawers().isEmpty(),"existing furniture registrations remain independent");
        System.out.println("LockerModelTest OK");
    }
    private static void near(float a,float b,String reason){check(Math.abs(a-b)<.0001,reason);}
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}

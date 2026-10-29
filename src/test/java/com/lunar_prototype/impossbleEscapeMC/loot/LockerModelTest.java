package com.lunar_prototype.impossbleEscapeMC.loot;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import org.joml.Vector3f;

public final class LockerModelTest {
    public static void main(String[] args) {
        for(int facing=0;facing<4;facing++) {
            var closed=LockerModel.transform(true,facing,false);
            var open=LockerModel.transform(true,facing,true);
            check(closed.transformPosition(new Vector3f()).distance(open.transformPosition(new Vector3f()))<.0001,"hinge stays fixed");
            var handle=open.transformPosition(new Vector3f(.875f,0,0));
            var hinge=open.transformPosition(new Vector3f());
            var direction=handle.sub(hinge);
            near(direction.length(),.875f,"door does not stretch");near(direction.y,0,"door stays level");
            float[][] expected={{0,-1},{-1,0},{0,1},{1,0}};
            near(direction.x,expected[facing][0]*.875f,"handle swings out in rotated X direction");
            near(direction.z,expected[facing][1]*.875f,"handle swings out in rotated Z direction");
            check(LockerModel.transform(false,facing,false).equals(LockerModel.transform(false,facing,true)),"shell never moves");
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

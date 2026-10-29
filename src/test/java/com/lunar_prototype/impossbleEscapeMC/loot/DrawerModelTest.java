package com.lunar_prototype.impossbleEscapeMC.loot;

import org.joml.Vector3f;
import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;

public final class DrawerModelTest {
    public static void main(String[] args) {
        check(DrawerModel.PARTS.size()==15,"all command parts retained");
        check(DrawerModel.PARTS.stream().filter(p->p.drawer()==-1).count()==5,"shell remains fixed");
        for(int slot=0;slot<2;slot++) {
            int selected=slot;
            check(DrawerModel.PARTS.stream().filter(p->p.drawer()==selected).count()==5,"whole drawer moves, including handle");
        }
        for(int facing=0;facing<4;facing++) {
            var part=DrawerModel.PARTS.get(9);
            Vector3f closed=DrawerModel.transform(part,facing,false).transformPosition(new Vector3f());
            Vector3f open=DrawerModel.transform(part,facing,true).transformPosition(new Vector3f());
            Vector3f motion=open.sub(closed);
            near(motion.length(),.625f,"travel independent of furniture rotation");
            near(motion.y,0,"drawer stays level");
            float[][] directions={{0,-1},{-1,0},{0,1},{1,0}};
            near(motion.x,directions[facing][0]*.625f,"correct rotated X direction");
            near(motion.z,directions[facing][1]*.625f,"correct rotated Z direction");
        }
        check(DrawerModel.facing(0)==0&&DrawerModel.facing(90)==3&&DrawerModel.facing(180)==2&&DrawerModel.facing(-90)==1,"handles face placement player");
        Gson gson=new Gson();
        RaidMap old=gson.fromJson("{\"mapId\":\"old\",\"worldName\":\"world\",\"lootContainers\":[]}",RaidMap.class);
        check(old.getLootDrawers().isEmpty(),"old maps need no migration");
        var drawer=new RaidMap.LootDrawer("one","industrial",-17,-34,208,3);drawer.generation=42;
        old.getLootDrawers().add(drawer);
        RaidMap restored=gson.fromJson(gson.toJson(old),RaidMap.class);
        var saved=restored.getLootDrawers().getFirst();
        check(saved.id.equals("one")&&saved.crateId.equals("industrial")&&saved.x==-17&&saved.y==-34&&saved.z==208&&saved.facing==3&&saved.generation==42,"identity, negative coordinates, facing and refill generation survive restart");
        check(restored.getLootContainers().isEmpty(),"existing loot containers remain independent");
        System.out.println("DrawerModelTest OK");
    }
    private static void near(float a,float b,String why){check(Math.abs(a-b)<.0001f,why);}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

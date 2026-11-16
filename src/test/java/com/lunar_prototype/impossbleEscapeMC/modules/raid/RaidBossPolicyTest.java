package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBossPost;
import java.util.*;

public final class RaidBossPolicyTest {
    @Test
    @org.junit.jupiter.api.DisplayName("partial setup not eligible")
    void partialSetupNotEligible() throws Exception {
        var zone=new RaidRadioZone("plant","map","Plant",0,-40,0,40,-20,40);
        var group=new RaidBossGroup("plant",null,null,null);
        check(problem(group,zone)!=null,"partial setup not eligible");
        double[] boss={10,-34,10,0,0};
        group=group.withMember("boss",boss).withMember("guard1",new double[]{7,-34,10,0,0}).withMember("guard2",new double[]{13,-34,10,0,0});
        check(problem(group,zone)==null,"complete group inside zone");
        boss[0]=100;
        double[] copy=group.boss();
        copy[0]=100;
        check(group.boss()[0]==10,"coordinates are defensive copies");
        check(group.withMember("guard1",new double[]{10,-34,10,0,0}).placement().problem(definition(),RaidMap.bossTerritory(zone))!=null,"overlapping members rejected");
        check(group.withMember("guard1",new double[]{30,-34,10,0,0}).placement().problem(definition(),RaidMap.bossTerritory(zone))!=null,"distant guard rejected");
        check(group.withMember("guard1",new double[]{-1,-34,10,0,0}).placement().problem(definition(),RaidMap.bossTerritory(zone))!=null,"outside high-threat zone rejected");
        check(problem(group,null)!=null,"removed zone invalidates group");
        Gson gson=new Gson();
        check(gson.fromJson(gson.toJson(group),RaidBossGroup.class).placement().problem(definition(),RaidMap.bossTerritory(zone))==null,"map JSON round trip");
        RaidMap old=gson.fromJson("{\"mapId\":\"map\",\"worldName\":\"world\"}",RaidMap.class);
        check(old.bossGroups().isEmpty(),"old map JSON remains supported");
        org.bukkit.World world=(org.bukkit.World)java.lang.reflect.Proxy.newProxyInstance(org.bukkit.World.class.getClassLoader(),new Class<?>[]{org.bukkit.World.class},(p,m,a)->{
            if(m.getName().equals("getName"))return "world";throw new AssertionError(m.getName());
        });
        old.addHighThreatZone(zone);
        old.setBossPosition("plant","boss",new org.bukkit.Location(world,10,-34,10));
        old.setBossPosition("plant","guard1",new org.bukkit.Location(world,7,-34,10));
        old.setBossPosition("plant","guard2",new org.bukkit.Location(world,13,-34,10));
        old.setBossPosition("plant","guard2",new org.bukkit.Location(world,14,-34,10));
        check(old.bossGroups().size()==1&&problem(old.bossGroups().getFirst(),zone)==null,"registration replaces role without duplicating groups");
        check(gson.fromJson(gson.toJson(old),RaidMap.class).bossGroups().size()==1,"whole map round trip");
        check(old.removeHighThreatZone("plant")&&old.bossGroups().isEmpty(),"removing zone removes its boss positions");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("post radius bounds")
    void postRadiusBounds() throws Exception {
        UUID unit=UUID.randomUUID();
        var post=new ScavBossPost(0,-40,0,40,-20,40,10,-34,10,8,true,unit);
        check(post.contains(18,-34,10)&&!post.contains(18.001,-34,10),"post radius bounds");
        check(!post.contains(10,-27,10)&&!post.contains(Double.NaN,-34,10),"floor bounds and invalid positions");
        check(!post.contains(40,-34,10),"zone upper bound exclusive");
    }

    @Test void legacyArrayPlacementsMigrateWithoutLosingCoordinates() {
        var gson=new Gson();
        var legacy=gson.fromJson("""
                {"zoneId":"plant","boss":[10,-34,10,90,20],"guard1":[7,-34,10,0,0],"guard2":[13,-34,10,0,0]}
                """,RaidBossGroup.class);
        check(legacy.bossId().equals("spectre_commander"),"legacy default boss id");
        check(legacy.placement().positions().size()==3,"legacy roles");
        var edited=legacy.withMember("guard1",new double[]{8,-34,10,0,0});
        check(Arrays.equals(legacy.boss(),edited.boss()),"leader yaw/pitch preserved");
        check(Arrays.equals(legacy.guard2(),edited.guard2()),"other guard preserved");
        var restored=gson.fromJson(gson.toJson(edited),RaidBossGroup.class);
        check(Arrays.equals(edited.position("guard1"),restored.position("guard1")),"generic positions roundtrip");
        restored.positions().get("boss")[0]=999;
        check(restored.boss()[0]==10,"DTO arrays cannot be mutated externally");
    }
    @Test void distinctBossDefinitionsCanShareAZoneAndBeRemovedIndependently() {
        var map=new RaidMap("map");
        var zone=new RaidRadioZone("plant","map","Plant",0,-40,0,40,-20,40);map.addHighThreatZone(zone);
        org.bukkit.World world=(org.bukkit.World)java.lang.reflect.Proxy.newProxyInstance(org.bukkit.World.class.getClassLoader(),new Class<?>[]{org.bukkit.World.class},(p,m,a)->{
            if(m.getName().equals("getName"))return "world";throw new AssertionError(m.getName());
        });
        map.setBossPosition("plant","custom","leader",new org.bukkit.Location(world,10,-34,10));
        map.setBossPosition("plant","boss",new org.bukkit.Location(world,12,-34,10));
        check(map.bossGroups().size()==2,"boss ids isolate placements");
        check(map.removeBossGroup("plant","custom")&&map.bossGroups().size()==1,"remove only selected definition");
        map.removeHighThreatZone("plant");check(map.bossGroups().isEmpty(),"zone deletion removes all definitions");
    }

    private static com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition definition() {
        var members=new ArrayList<com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition.Member>();
        for(String role:List.of("boss","guard1","guard2"))members.add(new com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition.Member(
                role,role.equals("boss"),"gun",null,null,null,com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain.BrainLevel.MID,40,12,"best"));
        return new com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition("spectre_commander","Boss",true,Set.of("map"),.125,members);
    }
    private static String problem(RaidBossGroup group,RaidRadioZone zone){return group.placement().problem(definition(),RaidMap.bossTerritory(zone));}
    private static void check(boolean value,String reason){ org.junit.jupiter.api.Assertions.assertTrue(value, reason); }
}

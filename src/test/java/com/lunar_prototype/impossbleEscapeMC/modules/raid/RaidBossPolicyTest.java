package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBossPost;
import java.util.*;

public final class RaidBossPolicyTest {
    public static void main(String[] args) {
        var selected=new RaidBossRoll(.125,.124999,true,false);
        for(int i=0;i<500;i++)check(selected.waiting(),"unsafe/absent players do not reroll or consume");
        check(selected.consume()&&!selected.waiting()&&!selected.consume(),"one attempt only after spawn, death or failure");
        check(!new RaidBossRoll(.125,.125,true,false).waiting(),"upper chance bound excluded");
        for(double chance:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY})check(!new RaidBossRoll(chance,0,true,false).waiting(),"invalid chance disables spawning");
        check(!new RaidBossRoll(1,0,true,true).waiting(),"restored raids cannot reroll or duplicate gear");
        check(!new RaidBossRoll(1,0,false,false).waiting(),"disabled map or incomplete registration");
        check(!new RaidBossRoll(0,0,true,false).waiting(),"zero chance");
        int count=0;for(int i=0;i<800;i++)if(new RaidBossRoll(.125,i/800.0,true,false).waiting())count++;
        check(count==100,"12.5 percent threshold");
        var zone=new RaidRadioZone("plant","map","Plant",0,-40,0,40,-20,40);
        var group=new RaidBossGroup("plant",null,null,null);
        check(group.problem(zone)!=null,"partial setup not eligible");
        double[] boss={10,-34,10,0,0};group=group.with("boss",boss).with("guard1",new double[]{7,-34,10,0,0}).with("guard2",new double[]{13,-34,10,0,0});
        check(group.problem(zone)==null,"complete group inside zone");boss[0]=100;
        double[] copy=group.boss();copy[0]=100;check(group.boss()[0]==10,"coordinates are defensive copies");
        check(group.with("guard1",new double[]{10,-34,10,0,0}).problem(zone)!=null,"overlapping members rejected");
        check(group.with("guard1",new double[]{30,-34,10,0,0}).problem(zone)!=null,"distant guard rejected");
        check(group.with("guard1",new double[]{-1,-34,10,0,0}).problem(zone)!=null,"outside high-threat zone rejected");
        check(group.problem(null)!=null,"removed zone invalidates group");
        Gson gson=new Gson();check(gson.fromJson(gson.toJson(group),RaidBossGroup.class).problem(zone)==null,"map JSON round trip");
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
        check(old.bossGroups().size()==1&&old.bossGroups().getFirst().problem(zone)==null,"registration replaces role without duplicating groups");
        check(gson.fromJson(gson.toJson(old),RaidMap.class).bossGroups().size()==1,"whole map round trip");
        check(old.removeHighThreatZone("plant")&&old.bossGroups().isEmpty(),"removing zone removes its boss positions");
        UUID unit=UUID.randomUUID();var post=new ScavBossPost(0,-40,0,40,-20,40,10,-34,10,8,true,unit);
        check(post.contains(18,-34,10)&&!post.contains(18.001,-34,10),"post radius bounds");
        check(!post.contains(10,-27,10)&&!post.contains(Double.NaN,-34,10),"floor bounds and invalid positions");
        check(!post.contains(40,-34,10),"zone upper bound exclusive");
        for(int fail=0;fail<3;fail++) {
            final int failure=fail;List<UUID> made=new ArrayList<>(),removed=new ArrayList<>();
            try {
                RaidBossEncounter.spawnAll(i->{if(i==failure)return null;UUID id=UUID.randomUUID();made.add(id);return id;},removed::add);
                throw new AssertionError("missing equipment must fail the full group");
            }catch(IllegalStateException expected){check(made.equals(removed),"rollback every previously spawned member");}
        }
        var all=RaidBossEncounter.spawnAll(i->new UUID(0,i+1),id->{throw new AssertionError("success must not rollback");});
        check(all.size()==3&&new HashSet<>(all).size()==3,"one boss and two guards");
        System.out.println("RaidBossPolicyTest passed: one-time roll, restore, map compatibility, placement, territory and partial-spawn rollback");
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}

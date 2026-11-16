package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class RaidBossSpawnsTest {
    private final World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(p,m,a)-> {
        if(m.getName().equals("getName"))return "world";throw new AssertionError(m.getName());
    });
    private RaidMap map(String id) {
        var map=new RaidMap(id);map.setWorldName("world");
        map.addHighThreatZone(new RaidRadioZone("zone",id,"Zone",0,0,0,40,10,40));return map;
    }
    private int add(RaidMap map,double x) {
        return map.addBossSpawn("zone",BossDefinition.DEFAULT_ID,"boss",new Location(world,x,1,5,90,10));
    }
    @Test void candidateIdsAreStableAcrossRemovalAndJsonRoundTrips() {
        var map=map("one");assertEquals(1,add(map,5));assertEquals(2,add(map,10));
        assertEquals(2,map.bossPlacements().size());assertTrue(map.removeBossSpawn(1));assertEquals(3,add(map,15));
        var restored=new Gson().fromJson(new Gson().toJson(map),RaidMap.class);
        assertEquals(2,restored.bossGroups().getFirst().spawnId());assertEquals(4,add(restored,20));
        assertFalse(restored.removeBossSpawn(1));assertEquals(3,restored.bossGroups().size());
        assertArrayEquals(new double[]{10,1,5,90,10},restored.bossGroups().getFirst().position("boss"));
    }
    @Test void mapsAndBossIdsKeepIndependentCandidates() {
        var a=map("a");var b=map("b");assertEquals(1,add(a,5));assertEquals(1,add(b,10));
        a.addBossSpawn("zone","other","leader",new Location(world,12,1,5));
        assertEquals(2,a.bossGroups().size());assertEquals(1,b.bossGroups().size());
        a.setBossPosition("zone","boss",new Location(world,8,1,5));
        assertEquals(2,a.bossGroups().size());assertEquals(1,a.bossGroups().getFirst().spawnId());
        assertTrue(a.removeHighThreatZone("zone"));assertTrue(a.bossGroups().isEmpty());assertEquals(1,b.bossGroups().size());
    }
    @Test void legacySetUpdatesOneCandidateWithoutRemovingOtherAnchors() {
        var map=map("map");add(map,5);add(map,10);
        map.setBossPosition("zone","boss",new Location(world,8,1,5));
        assertEquals(2,map.bossGroups().size());assertEquals(1,map.bossGroups().getFirst().spawnId());
        assertEquals(2,map.bossGroups().getLast().spawnId());assertEquals(10,map.bossGroups().getLast().position("boss")[0]);
    }
    @Test void legacyAndDuplicateIdsMigrateAndDoNotChangeOnRepeatedReads() {
        var map=new Gson().fromJson("""
                {"mapId":"old","bossGroups":[
                 {"zoneId":"zone","boss":[1,1,1,0,0]},
                 {"zoneId":"zone","spawnId":7,"boss":[2,1,2,0,0]},
                 {"zoneId":"zone","spawnId":7,"boss":[3,1,3,0,0]}]}
                """,RaidMap.class);
        assertTrue(map.initializeBossSpawnIds());assertFalse(map.initializeBossSpawnIds());
        assertEquals(3,map.bossGroups().stream().map(RaidBossGroup::spawnId).distinct().count());
        assertEquals(7,map.bossGroups().get(1).spawnId());
        assertArrayEquals(new double[]{1,1,1,0,0},map.bossGroups().getFirst().position("boss"));
    }
    @Test void outsideZoneIsRejectedWithoutAddingCandidate() {
        var map=map("map");assertThrows(IllegalArgumentException.class,()->add(map,45));assertTrue(map.bossGroups().isEmpty());
    }
}

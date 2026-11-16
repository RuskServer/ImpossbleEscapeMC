package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class BossEncounterTest {
    private boolean loaded=true, visible, nearby=true, isolated;
    private double playerX=40;
    private World world;
    private final List<BossEncounter.SpawnRequest> spawned=new ArrayList<>();
    private final List<UUID> removed=new ArrayList<>();
    private int closed;
    @BeforeEach void setup() throws Exception {
        Player player=proxy(Player.class,(p,m,a)->switch(m.getName()) {
            case "getGameMode"->GameMode.SURVIVAL;
            case "getLocation"->new Location(world,playerX,1,1.5);
            default->throw new AssertionError(m.getName());
        });
        world=proxy(World.class,(p,m,a)->switch(m.getName()) {
            case "getMinHeight"->-64;case "getMaxHeight"->320;case "isChunkLoaded"->loaded;
            case "getPlayers"->nearby?List.of(player):List.of();
            case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);
            case "getBlockAt"->proxy(Block.class,(block,method,arguments)->switch(method.getName()) {
                case "isLiquid"->false;
                case "getCollisionShape"->proxy(VoxelShape.class,(shape,call,values)-> {
                    if(call.getName().equals("getBoundingBoxes"))return ((Integer)a[1])==0&&(!isolated||((Integer)a[0])==1&&((Integer)a[2])==1)?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
                    throw new AssertionError(call.getName());
                });
                default->throw new AssertionError(method.getName());
            });
            default->throw new AssertionError(m.getName());
        });
        Server server=proxy(Server.class,(p,m,a)-> {
            if(m.getName().equals("getWorld"))return world;
            throw new AssertionError(m.getName());
        });
        Field field=Bukkit.class.getDeclaredField("server");field.setAccessible(true);field.set(null,server);
    }
    private BossEncounter encounter(boolean restored,int failure) throws Exception {
        var d=BossDefinitionTest.defaults();var positions=new LinkedHashMap<String,double[]>();
        for(int i=0;i<d.members().size();i++)positions.put(d.members().get(i).role(),new double[]{1.5+3*i,1,1.5,0,0});
        return new BossEncounter(Logger.getAnonymousLogger(),request->{
            if(spawned.size()==failure)return null;
            spawned.add(request);return new UUID(0,spawned.size());
        },removed::add,"map","world",restored,new BossPlacement(d.id(),"zone",positions),d,
                new BossTerritory("zone",0,0,0,20,10,20),0,()->closed++);
    }
    @Test void waitsForSafeHiddenNearbyPositionsThenSpawnsOnce() throws Exception {
        var e=encounter(false,-1);
        loaded=false;e.tick((p,l)->false);assertTrue(spawned.isEmpty());
        loaded=true;nearby=false;e.tick((p,l)->false);assertTrue(spawned.isEmpty());
        nearby=true;visible=true;e.tick((p,l)->visible);assertTrue(spawned.isEmpty());
        visible=false;playerX=10;e.tick((p,l)->false);assertTrue(spawned.isEmpty());
        playerX=40;for(int i=0;i<100&&spawned.size()<3;i++)e.tick((p,l)->false);e.tick((p,l)->false);assertEquals(3,spawned.size());
        assertEquals(1,spawned.stream().filter(r->r.identity().leader()).count());
        assertEquals(1,spawned.stream().map(r->r.identity().encounterId()).distinct().count());
        e.memberDied(new UUID(0,1));e.cleanup();e.cleanup();e.tick((p,l)->false);
        assertEquals(List.of(new UUID(0,2),new UUID(0,3)),removed);assertEquals(1,closed);
    }
    @Test void failedGroupRollsBackAndNeverRetries() throws Exception {
        var e=encounter(false,2);assertNotNull(e.selectedBoss());for(int i=0;i<100&&closed==0;i++)e.tick((p,l)->false);assertEquals(2,spawned.size());assertEquals(2,removed.size());
        e.tick((p,l)->false);e.cleanup();assertEquals(2,spawned.size());assertEquals(1,closed);assertNotNull(e.selectedBoss(),"intel is tied to the draw, not successful generation");
    }
    @Test void restoredSessionAndClosedWaitingEncounterCannotSpawn() throws Exception {
        var restored=encounter(true,-1);assertNull(restored.selectedBoss());restored.tick((p,l)->false);restored.cleanup();
        var waiting=encounter(false,-1);waiting.cleanup();waiting.tick((p,l)->false);
        assertTrue(spawned.isEmpty());assertEquals(2,closed);
    }
    @Test void arbitraryCountRollbackContinuesAfterCleanupError() {
        List<UUID> calls=new ArrayList<>();
        var failure=assertThrows(IllegalStateException.class,()->BossEncounter.spawnAll(5,i->i==4?null:new UUID(0,i),id->{
            calls.add(id);if(id.getLeastSignificantBits()==0)throw new IllegalStateException("remove failed");
        }));
        assertEquals(4,calls.size());assertEquals(1,failure.getSuppressed().length);
        assertEquals(7,BossEncounter.spawnAll(7,i->new UUID(0,i),id->fail()).size());
    }
    @Test void missingGuardSpaceWaitsWithoutConsumingRollAndRetriesSameAnchor() throws Exception {
        isolated=true;var e=encounter(false,-1);
        for(int i=0;i<60;i++)e.tick((p,l)->false);
        assertTrue(spawned.isEmpty());assertEquals(0,closed);
        isolated=false;for(int i=0;i<100&&spawned.isEmpty();i++)e.tick((p,l)->false);
        assertEquals(3,spawned.size());assertEquals(1.5,spawned.getFirst().location().getX());
        assertEquals(1.5,spawned.getFirst().location().getZ());e.cleanup();
    }
    @Test void visibleGuardPreventsWholeGroupFromSpawning() throws Exception {
        var e=encounter(false,-1);
        for(int i=0;i<100;i++)e.tick((p,l)->l.getX()!=1.5||l.getZ()!=1.5);
        assertTrue(spawned.isEmpty());
        for(int i=0;i<100&&spawned.isEmpty();i++)e.tick((p,l)->false);
        assertEquals(3,spawned.size());e.cleanup();
    }
    private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler));
    }
}

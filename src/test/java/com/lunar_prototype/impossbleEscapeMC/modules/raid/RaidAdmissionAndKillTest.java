package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidKillTracker;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidReport;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class RaidAdmissionAndKillTest {
    @Test void cancelledTeleportDoesNotCreateParticipantOrResult() throws Exception {
        var server = MockBukkit.mock();
        try (var messages = MessageTestSupport.forwarding()) {
            var player = server.addPlayer(); var origin = player.getLocation().clone();
            server.getPluginManager().registerEvents(new Listener() {
                @EventHandler public void cancel(PlayerTeleportEvent event) { event.setCancelled(true); }
            }, MockBukkit.createMockPlugin());
            var raid = allocate(RaidInstance.class);
            set(RaidInstance.class, raid, "players", new HashSet<>());
            set(RaidInstance.class, raid, "raidMembers", new HashSet<>());
            set(RaidInstance.class, raid, "raidResults", new HashMap<>());
            var spawn = RaidInstance.class.getDeclaredMethod("spawnGroup", List.class, List.class, Set.class, Random.class);
            spawn.setAccessible(true);
            spawn.invoke(raid, List.of(player), List.of(origin.clone().add(100,0,100)), new HashSet<>(), new Random(1));
            assertFalse(raid.isParticipant(player.getUniqueId()));
            assertTrue(((Set<?>) get(RaidInstance.class, raid, "raidMembers")).isEmpty());
            assertTrue(((Map<?,?>) get(RaidInstance.class, raid, "raidResults")).isEmpty());
            assertEquals(origin, player.getLocation());
        } finally { MockBukkit.unmock(); }
    }

    @Test void pvpDeathCopiesHitToBothReportsBeforeForgetting() throws Exception {
        var server = MockBukkit.mock(); UUID victimId = null;
        try {
            var victim = server.addPlayer(); var killer = server.addPlayer(); victimId = victim.getUniqueId();
            victim.setKiller(killer);
            var raid = allocate(RaidInstance.class);
            set(RaidInstance.class, raid, "players", new HashSet<>(Set.of(victimId,killer.getUniqueId())));
            set(RaidInstance.class, raid, "raidResults", new HashMap<>());
            var hit = new RaidKillTracker.Hit(killer.getUniqueId(),killer.getName(),RaidReport.Kind.PMC,victim.getName(),null,"USP","9mm",24,"胴",System.currentTimeMillis());
            @SuppressWarnings("unchecked") var hits = (Map<UUID,Map<UUID,RaidKillTracker.Hit>>) get(RaidKillTracker.class,null,"HITS");
            hits.put(victimId,new HashMap<>(Map.of(killer.getUniqueId(),hit)));
            var resultType = Arrays.stream(RaidInstance.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("RaidResult")).findFirst().orElseThrow();
            var ctor = resultType.getDeclaredConstructor(); ctor.setAccessible(true); var victimResult = ctor.newInstance();
            var record = RaidInstance.class.getDeclaredMethod("recordDeathCombat",org.bukkit.entity.Player.class,resultType);
            record.setAccessible(true); record.invoke(raid,victim,victimResult);
            var death = (RaidReport.Kill) get(resultType,victimResult,"death");
            var results = (Map<?,?>) get(RaidInstance.class,raid,"raidResults");
            var killerResult = results.get(killer.getUniqueId());
            var kills = (List<?>) get(resultType,killerResult,"kills");
            var kill = (RaidReport.Kill) kills.getFirst();
            assertEquals("USP",kill.weaponName()); assertEquals("9mm",kill.ammoName());
            assertEquals(24,kill.distance()); assertEquals("胴",kill.part());
            assertEquals(death.weaponName(),kill.weaponName());
            assertEquals(1,get(resultType,killerResult,"pmcKills"));
            assertNull(RaidKillTracker.lastHitOn(victimId));
        } finally { if(victimId!=null) RaidKillTracker.forget(victimId); MockBukkit.unmock(); }
    }
}

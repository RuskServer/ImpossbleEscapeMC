package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidProgressTest {
    @TempDir Path directory;

    private RaidInstance session(String id) throws Exception {
        var raid = allocate(RaidInstance.class);
        set(RaidInstance.class, raid, "raidSessionId", id);
        set(RaidInstance.class, raid, "startTime", 1234567890123L);
        set(RaidInstance.class, raid, "players", new HashSet<UUID>());
        set(RaidInstance.class, raid, "raidMembers", new HashSet<UUID>());
        set(RaidInstance.class, raid, "raidResults", new HashMap<>());
        set(RaidInstance.class, raid, "extractionTimer", new HashMap<>());
        return raid;
    }

    private void restore(RaidInstance raid, RaidProgress progress) throws Exception {
        var method = RaidInstance.class.getDeclaredMethod("restoreProgress", RaidProgress.class);
        method.setAccessible(true); method.invoke(raid, progress);
    }

    @Test void lastDisconnectKeepsSessionAndItsEarnedExperience() throws Exception {
        UUID id = UUID.randomUUID(); var raid = session("original-boss-roll");
        restore(raid, new RaidProgress("original-boss-roll", 1234567890123L, Set.of(), Map.of(id,
                new RaidProgress.Reward("ACTIVE", 450, 3, 0, 0, false, null, 0, false, 0, 0,
                        Map.of("SCAV", new long[]{3,450})))));
        ((Set<UUID>) get(RaidInstance.class, raid, "players")).add(id);
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (p,m,a) -> switch (m.getName()) {
                    case "getUniqueId" -> id;
                    case "closeInventory", "getAttribute" -> null;
                    default -> throw new AssertionError(m.getName());
                });
        raid.onPlayerQuit(player);
        assertFalse((boolean) get(RaidInstance.class, raid, "ended"));
        var snapshot = raid.progressSnapshot();
        assertEquals("original-boss-roll", snapshot.sessionId());
        assertTrue(snapshot.participants().isEmpty());
        assertEquals("LEFT", snapshot.members().get(id).outcome());
        assertEquals(450, snapshot.members().get(id).experience());
    }

    @Test void saveStateAndRestoreIncludeDepartedMembersEvenWithoutOnlineParticipants() throws Exception {
        var owner = allocate(RaidModule.class);
        set(RaidModule.class, owner, "finishedThisCycle", new HashMap<>());
        var raid = session("persisted-session");
        set(RaidInstance.class, raid, "owner", owner);
        set(RaidInstance.class, raid, "map", new RaidMap("factory"));
        UUID survived = UUID.randomUUID(), left = UUID.randomUUID(), dead = UUID.randomUUID();
        var progress = new RaidProgress("persisted-session", 1234567890123L, Set.of(), Map.of(
                survived, new RaidProgress.Reward("SURVIVED", 900, 4, 1, 0, true, "exit", 1234567899999L, false, 2, 250,
                        Map.of("SCAV", new long[]{4,600}, "SURVIVED", new long[]{1,300})),
                left, new RaidProgress.Reward("LEFT", 450, 3, 0, 0, false, null, 0, false, 0, 0, Map.of("SCAV", new long[]{3,450})),
                dead, new RaidProgress.Reward("DEAD", 200, 1, 0, 0, false, null, 1234567899000L, false, 1, 0, Map.of())));
        restore(raid, progress);
        Path file = directory.resolve("raid_state.json");
        set(RaidModule.class, owner, "stateFile", file.toFile());
        set(RaidModule.class, owner, "gson", new Gson());
        set(RaidModule.class, owner, "mapTimers", Map.of("factory", 300));
        set(RaidModule.class, owner, "activeRaids", Map.of("factory", raid));
        set(RaidModule.class, owner, "lastCycleStartTime", Map.of("factory", 1234567890123L));
        set(RaidModule.class, owner, "disconnected", Map.of());
        var save = RaidModule.class.getDeclaredMethod("saveState"); save.setAccessible(true); save.invoke(owner);
        var tree = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        var decoded = new Gson().fromJson(tree.getAsJsonObject("raidProgress").get("factory"), RaidProgress.class);
        var restored = session(decoded.sessionId());
        set(RaidInstance.class, restored, "owner", owner);
        set(RaidInstance.class, restored, "map", new RaidMap("factory"));
        restore(restored, decoded);
        var result = restored.progressSnapshot();
        assertEquals(3, result.members().size()); assertTrue(result.participants().isEmpty());
        assertEquals(900, result.members().get(survived).experience());
        assertTrue(result.members().get(survived).extractionCountIncremented());
        assertEquals("exit", result.members().get(survived).extractName());
        assertArrayEquals(new long[]{4,600}, result.members().get(survived).breakdown().get("SCAV"));
        assertEquals(450, result.members().get(left).experience());
        assertEquals("DEAD", result.members().get(dead).outcome());
        assertEquals(Set.of(survived, dead), ((Map<?,?>) get(RaidModule.class, owner, "finishedThisCycle")).get("factory"));
        assertEquals(1234567890123L, result.startTime());
    }
}

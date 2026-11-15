package com.lunar_prototype.impossbleEscapeMC.effect;

import io.papermc.paper.entity.PlayerPostEffects;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PostEffectTransportTest {
    static final class Wire implements PlayerPostEffects {
        List<Key> values = new ArrayList<>();
        final List<List<Key>> writes = new ArrayList<>();
        final List<String> operations = new ArrayList<>();
        @Override public List<Key> values() { return List.copyOf(values); }
        @Override public boolean set(SequencedCollection<Key> next) {
            values = new ArrayList<>(next); writes.add(List.copyOf(next)); operations.add("set"); return true;
        }
        @Override public void update() { operations.add("update"); }
        @Override public boolean add(Key key) { throw new AssertionError("Use complete ordered stacks"); }
        @Override public boolean remove(Key key) { throw new AssertionError("Use complete ordered stacks"); }
        @Override public boolean clear() { throw new AssertionError("Cannot clear other owners' effects"); }
    }
    static Player player(Wire wire) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) -> {
            if (m.getName().equals("postEffects")) return wire;
            throw new AssertionError(m.getName());
        });
    }
    @Test void unchangedStacksDoNotSendAndExternalChangesArePreserved() {
        var wire = new Wire(); Player player = player(wire); var transport = new PostEffectTransport();
        var state = new ScreenEffectState(); state.graded = true;
        transport.apply(player, state.layers(0)); transport.apply(player, state.layers(1));
        assertEquals(1, wire.writes.size());
        wire.values = List.of(Key.key("other", "changed"), Key.key("iem", "stale"));
        transport.apply(player, state.layers(2));
        assertEquals(List.of(Key.key("other", "changed"), Key.key("iem", "grade")), wire.values);
        transport.apply(player, state.layers(3)); assertEquals(2, wire.writes.size());
    }
    @Test void worldLoadResendsEvenWhenUnchangedAndAppliesBeforeResending() {
        var wire = new Wire(); Player player = player(wire); var transport = new PostEffectTransport(); var state = new ScreenEffectState();
        transport.resend(player, state.layers(0)); assertEquals(List.of("set", "update"), wire.operations);
        transport.resend(player, state.layers(1)); assertEquals(List.of("set", "update", "update"), wire.operations);
    }
    @Test void cleanupForcesOnlyOwnedEffectsOff() {
        var wire = new Wire(); Player player = player(wire); var transport = new PostEffectTransport();
        Key other = Key.key("other", "keep"); wire.values = List.of(other, Key.key("iem", "grade"), other, Key.key("iem", "hit"));
        transport.clear(player); assertEquals(List.of(other, other), wire.values);
        transport.clear(player); assertEquals(2, wire.writes.size(), "Explicit join/quit cleanup remains a forced synchronization");
    }
    @Test void allProductionPostEffectWritesUseOneTransport() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.stripLeading().startsWith("*") || line.stripLeading().startsWith("//")) continue;
                    if (line.contains(".postEffects(")) assertEquals("PostEffectTransport.java", file.getFileName().toString(), file.toString());
                }
            }
        }
    }
}

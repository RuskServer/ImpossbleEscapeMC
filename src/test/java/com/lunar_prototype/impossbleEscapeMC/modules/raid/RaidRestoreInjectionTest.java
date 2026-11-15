package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.core.di.DIContainer;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class RaidRestoreInjectionTest {
    static class Target {}
    static class Restoring implements Startable {
        final JavaPlugin plugin;
        final Supplier<Target> target;
        final AtomicInteger shown = new AtomicInteger();
        Set<UUID> participants;
        boolean active = true;
        @Inject Restoring(JavaPlugin plugin, Supplier<Target> target) { this.plugin = plugin; this.target = target; }
        public void start() {
            RaidInstance.restorePresentation(plugin, participants, id -> active, player -> { assertNotNull(target.get()); shown.incrementAndGet(); });
        }
    }
    @Test void restoreDisplaysOnlyAfterContainerStartup() {
        var server = MockBukkit.mock(); var container = new DIContainer();
        try {
            container.registerInstance(JavaPlugin.class, MockBukkit.createMockPlugin());
            var restored = container.resolve(Restoring.class);
            restored.participants = Set.of(server.addPlayer().getUniqueId());
            container.startAll();
            assertEquals(0, restored.shown.get());
            server.getScheduler().performOneTick();
            assertEquals(1, restored.shown.get());
        } finally { container.stopAll(); MockBukkit.unmock(); }
    }
    @Test void cancelledRaidDoesNotAcquireStoppedDependencies() {
        var server = MockBukkit.mock(); var container = new DIContainer();
        try {
            container.registerInstance(JavaPlugin.class, MockBukkit.createMockPlugin());
            var restored = container.resolve(Restoring.class);
            restored.participants = Set.of(server.addPlayer().getUniqueId());
            container.startAll(); restored.active = false; container.stopAll();
            server.getScheduler().performOneTick();
            assertEquals(0, restored.shown.get());
        } finally { container.stopAll(); MockBukkit.unmock(); }
    }
}

package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.*;
import com.lunar_prototype.impossbleEscapeMC.core.command.*;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Component;
import com.lunar_prototype.impossbleEscapeMC.di_bukkit_fixture.ManagedListener;
import io.papermc.paper.command.brigadier.*;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class DIBukkitTest {
    @Component @Command(name="di-test")
    static class ManagedCommand implements BasicCommand {
        public void execute(CommandSourceStack stack, String[] args) {}
    }
    @Test void lifecycleAndRegistration() {
        var server = MockBukkit.mock();
        ServiceContainer c = new ServiceContainer();
        try {
            var plugin = MockBukkit.createMockPlugin();
            c.register(JavaPlugin.class, plugin);
            var commands = new CommandRegistry();
            c.register(CommandRegistry.class, commands);
            c.scan(getClass().getClassLoader(), "com.lunar_prototype.impossbleEscapeMC.di_bukkit_fixture");
            c.registerTypes(ManagedCommand.class);
            c.startAll();
            var listener = c.get(ManagedListener.class);
            assertEquals(1, listener.starts);
            assertSame(listener, c.resolve(ManagedListener.class));
            assertEquals(1, commands.commands().size());
            var player = server.addPlayer();
            server.getPluginManager().callEvent(new PlayerToggleSneakEvent(player, true));
            assertEquals(1, listener.events);
            c.stopAll();
            server.getPluginManager().callEvent(new PlayerToggleSneakEvent(player, false));
            assertEquals(1, listener.events);
            assertEquals(1, listener.stops);
        } finally { c.stopAll(); MockBukkit.unmock(); }
    }
    @Test void discoverBeforeModuleConstruction() {
        MockBukkit.mock();
        ServiceContainer c = new ServiceContainer();
        try {
            var plugin = MockBukkit.createMockPlugin();
            var order = new com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.Order();
            c.register(com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.Order.class, order);
            var bootstrap = new ModuleBootstrap(plugin, c, "com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture");
            com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.First.constructed = 0;
            bootstrap.registerModule(com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.First.class);
            bootstrap.registerModule(com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.Second.class);
            assertEquals(0, com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.First.constructed);
            bootstrap.enableModules();
            var first = c.get(com.lunar_prototype.impossbleEscapeMC.di_bootstrap_fixture.BootstrapFixtures.First.class);
            assertEquals(1, first.parts.size()); assertSame(first.part, first.parts.getFirst());
            assertEquals(java.util.List.of("first+", "second+"), order.values);
            bootstrap.disableModules();
            assertEquals(java.util.List.of("first+", "second+", "second-", "first-"), order.values);
        } finally { c.stopAll(); MockBukkit.unmock(); }
    }

}

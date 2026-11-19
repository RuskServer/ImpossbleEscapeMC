package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketPersistenceTest {
    @TempDir Path directory;
    private MarketListing listing() {
        return new MarketListing(UUID.randomUUID(), UUID.randomUUID(), "seller", "encoded-item", 1000, 123);
    }
    private MarketModule module(Path file) throws Exception {
        var module = allocate(MarketModule.class);
        set(MarketModule.class, module, "dataFile", file.toFile());
        set(MarketModule.class, module, "gson", new Gson());
        set(MarketModule.class, module, "listings", new ConcurrentHashMap<>());
        var plugin = allocate(ImpossbleEscapeMC.class);
        var logger = Logger.getLogger("MarketPersistenceTest"); logger.setLevel(Level.OFF);
        set(org.bukkit.plugin.java.JavaPlugin.class, plugin, "logger", logger);
        set(MarketModule.class, module, "plugin", plugin);
        set(MarketModule.class, module, "commands", new com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry());
        return module;
    }
    @Test void corruptLoadAndNormalOrFailedStartupCleanupNeverOverwriteSource() throws Exception {
        Path file = directory.resolve("market.json"); String broken = "[{broken-json";
        Files.writeString(file, broken);
        var market = module(file);
        var container = new ServiceContainer(); container.register(MarketModule.class, market);
        container.startAll(); container.stopAll();
        assertEquals(broken, Files.readString(file));
        assertFalse(market.addListing(listing()));
        assertEquals(broken, Files.readString(file));
    }
    @Test void backupRecoversValidEntriesAndKeepsCorruptOriginal() throws Exception {
        Path file = directory.resolve("market.json"); var storage = new MarketStorage(file, new Gson());
        var entry = listing(); storage.save(List.of(entry)); storage.save(List.of(entry));
        Files.writeString(file, "broken");
        assertEquals(entry.getId(), storage.load().getFirst().getId());
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("market.json.corrupt-")));
        }
        assertEquals("broken", Files.readString(file));
    }
    @Test void failedAtomicWritePreservesDiskAndRejectsListingMutation() throws Exception {
        Path file = directory.resolve("market.json"); var storage = new MarketStorage(file, new Gson());
        var entry = listing(); storage.save(List.of(entry)); String original = Files.readString(file);
        var market = module(file); market.onEnable(new ServiceContainer());
        Files.createDirectory(directory.resolve("market.json.tmp"));
        assertFalse(market.addListing(listing()));
        assertEquals(1, market.getAllListings().size()); assertEquals(original, Files.readString(file));
        Files.createDirectory(directory.resolve("market.json.tmp"));
        assertFalse(market.removeListing(entry.getId()));
        assertNotNull(market.getListing(entry.getId())); assertEquals(original, Files.readString(file));
    }
}

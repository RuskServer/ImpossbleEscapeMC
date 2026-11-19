package com.lunar_prototype.impossbleEscapeMC.modules.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

class FailedPlayerSaveTest {
    @TempDir Path directory;

    private PlayerDataModule module(PlayerData data, Map<UUID, PlayerData> cache, Map<UUID, CompletableFuture<Void>> pending) throws Exception {
        var module = allocate(PlayerDataModule.class);
        var logger = Logger.getLogger("FailedPlayerSaveTest"); logger.setLevel(Level.OFF);
        set(PlayerDataModule.class, module, "storage", new PlayerDataStorage(directory.toFile(), logger));
        set(PlayerDataModule.class, module, "dataCache", cache);
        set(PlayerDataModule.class, module, "loadFailed", ConcurrentHashMap.newKeySet());
        set(PlayerDataModule.class, module, "pendingWrites", pending);
        set(PlayerDataModule.class, module, "snapshotCounter", new AtomicLong());
        cache.put(data.getUuid(), data);
        return module;
    }

    private void release(PlayerDataModule module, UUID id) throws Exception {
        var method = PlayerDataModule.class.getDeclaredMethod("release", UUID.class);
        method.setAccessible(true); method.invoke(module, id);
    }

    @Test void failedWriteRetainsLatestDataAndRetriesBeforeEviction() throws Exception {
        var data = new PlayerData(UUID.randomUUID()); data.setBalance(5000);
        Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
        var module = module(data, cache, new ConcurrentHashMap<>());
        Files.createDirectory(directory.resolve(data.getUuid() + ".json.tmp"));
        release(module, data.getUuid());
        assertSame(data, module.getPlayerData(data.getUuid()));
        assertTrue(data.isDirty()); assertTrue(cache.containsKey(data.getUuid()));
        release(module, data.getUuid());
        assertFalse(data.isDirty()); assertFalse(cache.containsKey(data.getUuid()));
        var stored = (PlayerDataStorage) get(PlayerDataModule.class, module, "storage");
        assertEquals(5000, stored.load(data.getUuid()).data().getBalance());
    }

    @Test void asyncFailureObservedWhileWaitingAlsoPreventsEviction() throws Exception {
        var data = new PlayerData(UUID.randomUUID()); data.setDirty(false);
        Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
        Map<UUID, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();
        pending.put(data.getUuid(), new CompletableFuture<>() {
            @Override public Void join() { data.setDirty(true); return null; }
        });
        var module = module(data, cache, pending);
        release(module, data.getUuid());
        assertTrue(data.isDirty()); assertSame(data, cache.get(data.getUuid()));
    }
}

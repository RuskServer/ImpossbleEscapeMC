package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectPlayback.Step;
import net.kyori.adventure.key.Key;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.Messenger;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 実サービスの公開入口から再生し、クライアントへ送る順序とコールバックの時点を検証する。 */
class ScreenEffectServiceTest {
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static final class Fixture implements AutoCloseable {
        int now;
        boolean nativeMuzzle;
        final List<Integer> muzzlePackets = new ArrayList<>();
        final UUID id = UUID.randomUUID();
        final PostEffectTransportTest.Wire wire = new PostEffectTransportTest.Wire();
        final Map<UUID, ScreenEffectState> states = new HashMap<>();
        final Object oldServer, oldInstance;
        final ScreenEffectService service;
        final Player player;
        final Location location;
        final Method advance;
        Fixture() throws Exception {
            oldServer = field(Bukkit.class, "server").get(null);
            oldInstance = field(ScreenEffectService.class, "instance").get(null);
            UUID worldId = UUID.randomUUID();
            World world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
                case "getGameTime" -> (long) now;
                case "getUID" -> worldId;
                case "equals" -> p == a[0];
                case "hashCode" -> worldId.hashCode();
                default -> throw new AssertionError(m.getName());
            });
            location = new Location(world, 19.25, -33.0, 87.75, 42f, -17f);
            player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
                case "getUniqueId" -> id;
                case "postEffects" -> wire;
                case "getWorld" -> world;
                case "getLocation" -> location.clone();
                case "getListeningPluginChannels" -> nativeMuzzle ? Set.of(MuzzleFlashSignal.CHANNEL) : Set.of();
                case "sendPluginMessage" -> { muzzlePackets.add((int) ((byte[]) a[2])[0]); yield null; }
                case "getGameMode" -> GameMode.SURVIVAL;
                case "playSound" -> null;
                default -> throw new AssertionError(m.getName());
            });
            Messenger messenger = proxy(Messenger.class, (p, m, a) -> null);
            Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
                case "getCurrentTick" -> now;
                case "getMessenger" -> messenger;
                case "getPlayer" -> player;
                default -> throw new AssertionError(m.getName());
            });
            field(Bukkit.class, "server").set(null, server);
            // タスクとPacketEventsは起動せず、サービスの実メソッドを1フレームずつ進める。
            var unsafeField = field(sun.misc.Unsafe.class, "theUnsafe");
            service = (ScreenEffectService) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(ScreenEffectService.class);
            field(ScreenEffectService.class, "states").set(service, states);
            field(ScreenEffectService.class, "transport").set(service, new PostEffectTransport());
            field(ScreenEffectService.class, "muzzleSignal").set(service, new MuzzleFlashSignal(null));
            field(ScreenEffectService.class, "instance").set(null, service);
            advance = ScreenEffectService.class.getDeclaredMethod("advancePlayback", Player.class, ScreenEffectState.class, ScreenEffectPlayback.class, int.class);
            advance.setAccessible(true);
        }
        ScreenEffectState state() { return states.computeIfAbsent(id, ignored -> new ScreenEffectState()); }
        void frame() throws Exception {
            now++;
            ScreenEffectState state = states.get(id);
            if (state != null && state.playback != null) advance.invoke(service, player, state, state.playback, now);
        }
        void until(int tick) throws Exception { while (now < tick) frame(); }
        void start(List<Step> steps, String name) throws Exception {
            var start = ScreenEffectService.class.getDeclaredMethod("start", Player.class, List.class, boolean.class, String.class);
            start.setAccessible(true); start.invoke(service, player, steps, false, name);
        }
        void worldLoaded() throws Exception {
            var method = ScreenEffectService.class.getDeclaredMethod("onWorldLoaded", Player.class); method.setAccessible(true); method.invoke(service, player);
        }
        void forget() {
            try {
                var method = ScreenEffectService.class.getDeclaredMethod("forget", UUID.class); method.setAccessible(true); method.invoke(service, id);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        @Override public void close() throws Exception {
            states.values().forEach(ScreenEffectState::discard);
            field(ScreenEffectService.class, "instance").set(null, oldInstance);
            field(Bukkit.class, "server").set(null, oldServer);
        }
    }
    private static Key key(String value) { return Key.key("iem", value); }
    @Test void pendingRaidRespawnBlocksOtherTeleportsBeforeRespawnButAllowsOwnReturn() throws Exception {
        try (var fixture = new Fixture()) {
            var scene = new DeathScene(fixture.location, true);
            fixture.state().deathScene = scene;
            var event = new org.bukkit.event.player.PlayerTeleportEvent(fixture.player,
                    fixture.location.clone(), fixture.location.clone().add(1000, 0, 1000));
            fixture.service.onDeathSceneTeleport(event);
            assertTrue(event.isCancelled());
            scene.moving = true;
            var internal = new org.bukkit.event.player.PlayerTeleportEvent(fixture.player,
                    fixture.location.clone(), fixture.location.clone().add(1000, 0, 1000));
            fixture.service.onDeathSceneTeleport(internal);
            assertFalse(internal.isCancelled());
        }
    }

    @Test void raidRespawnUsesCapturedSiteEvenWithoutReturnMarkerAndNeverTeleports() throws Exception {
        try (var fixture = new Fixture()) {
            Location death = fixture.location.clone();
            var scene = new DeathScene(death, true);
            fixture.state().deathScene = scene;
            fixture.state().awaitingRespawn = true;
            death.add(800, 0, 800); // 元のLocationの変更からも独立させる。
            var event = new org.bukkit.event.player.PlayerRespawnEvent(fixture.player, death, false);
            fixture.service.onRespawn(event);
            assertEquals(fixture.location, event.getRespawnLocation());
            assertEquals(DeathScene.Phase.WAIT_FIELD, scene.phase);
            assertTrue(scene.locksPlayer());
            assertFalse(fixture.state().awaitingRespawn);
            // このフィクスチャはPDC取得・teleportを許可しない。帰還印と後付けTPに依存しない。
        }
    }

    @Test void deathSnapshotSurvivesLaterPositionChangesAndCancelledDeathClearsIt() throws Exception {
        try (var fixture = new Fixture()) {
            var damage = proxy(org.bukkit.damage.DamageSource.class, (p, m, a) -> null);
            var event = new org.bukkit.event.entity.PlayerDeathEvent(fixture.player, damage,
                    new ArrayList<>(), 0, (net.kyori.adventure.text.Component) null, true);
            Location original = fixture.location.clone();
            fixture.service.captureDeathOrigin(event);
            fixture.location.add(300, 0, 300);
            assertEquals(original, fixture.state().pendingDeathOrigin);
            event.setCancelled(true);
            fixture.service.onDeath(event);
            assertNull(fixture.state().pendingDeathOrigin);
            assertFalse(fixture.state().pendingRaidDeath);
            assertNull(fixture.state().deathScene);
        }
    }

    @Test void adrenalineRetainsTwentyTickDurationAndNaturalRecovery() throws Exception {
        try (var fixture = new Fixture()) {
            ScreenEffectService.playAdrenaline(fixture.player, 1000);
            assertEquals(key("adrenaline_onset"), fixture.wire.values.getLast());
            fixture.until(4); assertEquals(key("adrenaline"), fixture.wire.values.getLast());
            fixture.until(8); assertEquals(key("adrenaline_fade"), fixture.wire.values.getLast());
            fixture.until(20); assertNull(fixture.state().playback); assertNotNull(fixture.state().recovery);
            assertEquals(List.of("adrenaline_onset", "adrenaline", "adrenaline_fade", "control"), fixture.wire.writes.stream().map(keys -> keys.getLast().value()).toList());
        }
    }
    @Test void initialCallbackRunsAfterSendAndTransitionsRunBeforeSend() throws Exception {
        try (var fixture = new Fixture()) {
            List<String> callbacks = new ArrayList<>();
            fixture.start(List.of(new Step(key("first"), 2, false, () -> { assertEquals(key("first"), fixture.wire.values.getLast()); callbacks.add("start:" + fixture.now); }),
                    new Step(key("second"), 1, false, () -> { assertEquals(key("first"), fixture.wire.values.getLast()); callbacks.add("step:" + fixture.now); }),
                    new Step(null, 1, false, () -> { assertEquals(key("second"), fixture.wire.values.getLast()); callbacks.add("finish:" + fixture.now); })), "test");
            fixture.until(4);
            assertEquals(List.of("start:0", "step:2", "finish:3"), callbacks); assertNull(fixture.state().playback);
        }
    }
    @Test void callbackCanReplacePlaybackWithoutOldCompletionOrStateRestoration() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.start(List.of(new Step(key("old"), 1, false), new Step(key("discarded"), 1, false, () -> ScreenEffectService.playEmpFlash(fixture.player)),
                    new Step(null, 1, false, () -> fail("Old completion cannot run"))), "old");
            fixture.frame(); assertFalse(ScreenEffectService.isPlaying(fixture.player, "old")); assertEquals(key("emp_flash"), fixture.wire.values.getLast());
            fixture.until(24); assertNull(fixture.state().playback); assertNull(fixture.state().recovery);
            assertFalse(fixture.wire.writes.stream().flatMap(List::stream).anyMatch(key("discarded")::equals));
        }
    }
    @Test void callbackCanForgetPlayerWithoutRecreatingState() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.start(List.of(new Step(key("first"), 1, false), new Step(key("second"), 1, false, fixture::forget)), null);
            fixture.frame(); assertTrue(fixture.states.isEmpty()); assertEquals(1, fixture.wire.writes.size());
        }
    }
    @Test void raidIntroWaitsForLoadThenSettlesWithoutUsingFirstStepDuration() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.state().awaitingLoadSince = 0;
            ScreenEffectService.playRaidIntro(fixture.player);
            fixture.until(20); assertEquals(key("intro_0"), fixture.wire.values.getLast());
            fixture.worldLoaded(); fixture.worldLoaded(); assertEquals(1, Collections.frequency(fixture.wire.operations, "update"));
            fixture.until(49); assertEquals(key("intro_0"), fixture.wire.values.getLast());
            fixture.frame(); assertEquals(key("intro_1"), fixture.wire.values.getLast());
        }
    }
    @Test void hitChangesAtTwoTicksAndClearsAtFiveTicks() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.state().hitTick = 0;
            var refresh = ScreenEffectService.class.getDeclaredMethod("refresh", Player.class); refresh.setAccessible(true);
            var tickHits = ScreenEffectService.class.getDeclaredMethod("tickHits", int.class); tickHits.setAccessible(true);
            refresh.invoke(fixture.service, fixture.player); assertEquals(key("hit"), fixture.wire.values.getLast());
            fixture.now = 2; tickHits.invoke(fixture.service, 2); assertEquals(key("hit_fade"), fixture.wire.values.getLast());
            fixture.now = 4; tickHits.invoke(fixture.service, 4); assertEquals(2, fixture.wire.writes.size());
            fixture.now = 5; tickHits.invoke(fixture.service, 5); assertNull(fixture.state().hitTick); assertEquals(key("control"), fixture.wire.values.getLast());
        }
    }
    @Test void forgetDiscardsLoadingPlaybackAndOverlayStateAndClearsMuzzleSignal() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.nativeMuzzle = true;
            ScreenEffectService.playMuzzleFlash(fixture.player, true, false);
            ScreenEffectState old = fixture.state(); old.cover = old.controlDebug = old.menuClear = old.awaitingRespawn = true;
            old.awaitingLoadSince = 1; old.settlingUntil = 10; old.visorWet = 30; old.sentWet = .5f; old.probe = key("probe");
            ScreenEffectService.playLoreIntro(fixture.player, () -> fail("Discarded lore completion"));
            ScreenEffectPlayback playback = old.playback; fixture.forget();
            assertTrue(fixture.states.isEmpty()); assertNull(old.playback); assertNull(old.recovery);
            assertNull(playback.visibleEffect()); assertEquals(List.of(2, 0), fixture.muzzlePackets);
            ScreenEffectState fresh = fixture.state();
            assertFalse(fresh.cover || fresh.controlDebug || fresh.menuClear || fresh.awaitingRespawn);
            assertNull(fresh.awaitingLoadSince); assertNull(fresh.settlingUntil); assertNull(fresh.probe); assertNull(fresh.sentWet); assertEquals(0, fresh.visorWet);
        }
    }
    @Test void pdaCallbacksKeepExistingTimesAndFinalVisibleName() throws Exception {
        try (var fixture = new Fixture()) {
            List<String> callbacks = new ArrayList<>();
            ScreenEffectService.playPdaIntro(fixture.player, () -> callbacks.add("switch:" + fixture.now), () -> callbacks.add("power:" + fixture.now),
                    () -> callbacks.add("boot:" + fixture.now), () -> callbacks.add("map:" + fixture.now), () -> callbacks.add("close:" + fixture.now),
                    () -> callbacks.add("rise:" + fixture.now), () -> { assertTrue(ScreenEffectService.isPlaying(fixture.player, ScreenEffectService.PDA_INTRO)); callbacks.add("finish:" + fixture.now); });
            fixture.until(143);
            assertEquals(List.of("switch:12", "power:16", "boot:24", "map:46", "close:110", "rise:118", "finish:142"), callbacks);
            assertFalse(ScreenEffectService.isPlaying(fixture.player, ScreenEffectService.PDA_INTRO));
        }
    }
    @Test void loreReturnsAtExistingTimeAndCallsCompletionOnce() throws Exception {
        try (var fixture = new Fixture()) {
            List<Integer> callback = new ArrayList<>();
            ScreenEffectService.playLoreIntro(fixture.player, () -> callback.add(fixture.now));
            fixture.until(2050); assertEquals(List.of(2050), callback); assertEquals(key("intro_1"), fixture.wire.values.getLast());
            fixture.until(2098); assertNull(fixture.state().playback); assertEquals(List.of(2050), callback);
        }
    }
}

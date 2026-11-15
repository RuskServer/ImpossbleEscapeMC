package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Stoppable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 実際の画面をコンテナで構築し、登録・停止・再起動と依存の受け渡しを検証する。 */
class GuiDependencyMigrationTest {
    private static final String BASE = "com.lunar_prototype.impossbleEscapeMC.";
    private static final List<String> SCREENS = List.of(
            "gui.PdaHomeScreen", "modules.quest.QuestDialog", "modules.quest.QuestHandInDialog",
            "modules.trader.TraderDialog", "modules.trader.RepairDialog", "modules.craft.CraftDialog",
            "modules.hideout.HideoutDialog", "modules.raid.RaidSelectionGUI", "modules.raid.result.RaidResultDialog",
            "gui.GunSettingsDialog", "modules.skill.SkillTreeDialog", "modules.quest.HandInPlaces",
            "modules.quest.QuestDeliveryAccess", "modules.raid.result.RaidResults", "modules.inventory.CostSlotManager");

    @Test void allGuiSourcesAndSharedOperationsHaveNoGlobalServiceLookup() throws Exception {
        Path root = Path.of("src/main/java", BASE.replace('.', '/'));
        List<String> helpers = List.of("HandInPlaces.java", "QuestDeliveryAccess.java", "QuestAutoHandIn.java",
                "QuestItemHighlighter.java", "FreeLoadoutPresentation.java", "SupplyLoadout.java", "RaidResults.java", "CostSlotManager.java", "CapacityGauge.java", "CraftingGridButtons.java");
        int checked = 0;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!(name.endsWith("GUI.java") || name.endsWith("Dialog.java") || name.endsWith("Screen.java") || helpers.contains(name))) continue;
                String source = Files.readString(path);
                for (String forbidden : List.of("getServiceContainer()", "ImpossbleEscapeMC.getInstance()", "PartyModule.get()", "SkillModule.value(player", "plugin.getRaidModule()"))
                    assertFalse(source.contains(forbidden), path + ": " + forbidden);
                checked++;
            }
        }
        assertTrue(checked >= 35, "All screen files and shared operations are covered: " + checked);
    }

    // モジュールのファイル/ワールド初期化をせず、画面の実ライフサイクルだけを動かす。
    private static final class ScreensOnly extends ServiceContainer {
        @Override protected void startInstance(Object value) { if (!(value instanceof IModule)) super.startInstance(value); }
        @Override protected void stopInstance(Object value) { if (!(value instanceof IModule)) super.stopInstance(value); }
    }

    @Test void noHiddenLookupOrManualListenerRegistration() throws Exception {
        for (String name : SCREENS) {
            Class<?> type = Class.forName(BASE + name);
            assertTrue(type.isAnnotationPresent(Service.class), name);
            assertEquals(1, type.getDeclaredConstructors().length);
            assertTrue(type.getDeclaredConstructors()[0].isAnnotationPresent(Inject.class));
            assertTrue(Startable.class.isAssignableFrom(type));
            assertTrue(Stoppable.class.isAssignableFrom(type));
            String source = Files.readString(Path.of("src/main/java", (BASE + name).replace('.', '/') + ".java"));
            for (String forbidden : List.of("getServiceContainer()", "ImpossbleEscapeMC.getInstance()", "registerEvents("))
                assertFalse(source.contains(forbidden), name + ": " + forbidden);
            for (Class<?> dependency : type.getDeclaredConstructors()[0].getParameterTypes())
                assertFalse(DIContainer.class.isAssignableFrom(dependency), name);
            for (Field field : type.getDeclaredFields())
                if (IModule.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers()))
                    assertTrue(Modifier.isFinal(field.getModifiers()), field.toString());
        }
        String trader = Files.readString(Path.of("src/main/java", BASE.replace('.', '/'), "modules/trader/TraderModule.java"));
        String hideout = Files.readString(Path.of("src/main/java", BASE.replace('.', '/'), "modules/hideout/HideoutModule.java"));
        assertFalse(trader.contains("TraderDialog.init("));
        assertFalse(hideout.contains("HideoutDialog.init("));
    }

    @Test void actualScreensRegisterOnceAndReleaseStateAcrossRestarts() throws Exception {
        MockBukkit.mock();
        try {
            var plugin = MockBukkit.createMockPlugin();
            for (int cycle = 0; cycle < 2; cycle++) {
                var container = new ScreensOnly();
                List<BukkitTask> tasks = new ArrayList<>();
                try {
                    container.register(JavaPlugin.class, plugin);
                    List<Class<?>> types = new ArrayList<>();
                    for (String name : SCREENS) {
                        Class<?> type = Class.forName(BASE + name);
                        types.add(type);
                        for (Class<?> dependency : type.getDeclaredConstructors()[0].getParameterTypes())
                            if (dependency != JavaPlugin.class && container.get(dependency) == null)
                                registerProbe(container, dependency);
                    }
                    container.registerTypes(types.toArray(Class<?>[]::new));
                    container.startAll();
                    for (Class<?> type : types) {
                        Object screen = container.get(type);
                        assertSame(screen, container.resolve(type));
                        assertSame(screen, field(type, "instance").get(null));
                        for (Field dependency : type.getDeclaredFields()) {
                            if (Modifier.isStatic(dependency.getModifiers()) || !IModule.class.isAssignableFrom(dependency.getType())) continue;
                            dependency.setAccessible(true);
                            assertSame(container.get(dependency.getType()), dependency.get(screen), dependency.toString());
                        }
                        long handlers = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(EventHandler.class)).count();
                        long registered = HandlerList.getRegisteredListeners(plugin).stream().filter(r -> r.getListener() == screen).count();
                        assertEquals(handlers, registered, "Each handler is registered exactly once: " + type);
                        if (type.getSimpleName().equals("CraftDialog")) {
                            Method ensure = type.getDeclaredMethod("ensureStarted"); ensure.setAccessible(true);
                            ensure.invoke(null);
                            tasks.add((BukkitTask) field(type, "refresher").get(null));
                        }
                        if (type.getSimpleName().equals("HideoutDialog")) tasks.add((BukkitTask) field(type, "watcher").get(screen));
                        if (type.getSimpleName().equals("GunSettingsDialog")) tasks.add((BukkitTask) field(type, "lockTask").get(screen));
                        // セッションは実アイテムを預からない。停止で古い操作先と表示キャッシュを捨てる。
                        for (String mapName : List.of("SESSIONS", "sessions")) {
                            try {
                                Field map = field(type, mapName);
                                @SuppressWarnings("unchecked") Map<UUID, Object> sessions = (Map<UUID, Object>) map.get(Modifier.isStatic(map.getModifiers()) ? null : screen);
                                if (!type.getSimpleName().equals("RaidResultDialog")) sessions.put(UUID.randomUUID(), new Object());
                            } catch (NoSuchFieldException ignored) { }
                        }
                    }
                    Class<?> result = Class.forName(BASE + "modules.raid.result.RaidResultDialog");
                    Class<?> sessionType = Class.forName(result.getName() + "$Session");
                    var report = new com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidReport("test", true, "ok", null,
                            0, List.of(), 1, 0, false, List.of(), 0, List.of(), null);
                    Constructor<?> ctor = sessionType.getDeclaredConstructor(report.getClass()); ctor.setAccessible(true);
                    Object session = ctor.newInstance(report);
                    BukkitTask animation = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {}, 1L, 1L);
                    field(sessionType, "anim").set(session, animation);
                    @SuppressWarnings("unchecked") Map<UUID, Object> results = (Map<UUID, Object>) field(result, "SESSIONS").get(null);
                    results.put(UUID.randomUUID(), session);
                    tasks.add(animation);
                    container.stopAll();
                    container.stopAll();
                    for (BukkitTask task : tasks) assertTrue(task.isCancelled(), "Screen timer is cancelled");
                    assertEquals(0, HandlerList.getRegisteredListeners(plugin).size());
                    for (Class<?> type : types) {
                        assertNull(field(type, "instance").get(null));
                        for (String mapName : List.of("SESSIONS", "sessions")) {
                            try {
                                Field map = field(type, mapName);
                                assertTrue(((Map<?, ?>) map.get(Modifier.isStatic(map.getModifiers()) ? null : container.get(type))).isEmpty());
                            } catch (NoSuchFieldException ignored) { }
                        }
                    }
                } finally { container.stopAll(); }
            }
        } finally { MockBukkit.unmock(); }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerProbe(ServiceContainer container, Class<?> dependency) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Object probe = ((sun.misc.Unsafe) field.get(null)).allocateInstance(dependency);
        container.register((Class) dependency, probe);
    }
}

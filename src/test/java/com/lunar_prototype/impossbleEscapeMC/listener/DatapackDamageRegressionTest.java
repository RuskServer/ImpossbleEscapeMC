package com.lunar_prototype.impossbleEscapeMC.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.*;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.*;
import java.util.*;

/** 発射監視前の初弾・発射情報の独立・二重命中・耐久イベントを実ハンドラーで検証する。 */
public final class DatapackDamageRegressionTest {
    private static int tick = 100;
    private static Marker activeBullet;
    private static EntityDamageEvent lastDamage;
    private static int notifications;
    private static final Map<String, Map<String, Integer>> scores = new HashMap<>();
    private static final List<Player> players = new ArrayList<>();
    private static final List<ItemStack> worn = new ArrayList<>();
    private static final class TestItem extends ItemStack {
        @Override public boolean equals(Object other) { return this == other; }
        @Override public int hashCode() { return System.identityHashCode(this); }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> switch (m.getName()) {
            case "equals" -> p == a[0];
            case "hashCode" -> System.identityHashCode(p);
            case "toString" -> type.getSimpleName();
            default -> handler.invoke(p, m, a);
        });
    }
    private static PersistentDataContainer data() {
        Map<NamespacedKey, Object> values = new HashMap<>();
        return proxy(PersistentDataContainer.class, (p, m, a) -> switch (m.getName()) {
            case "set" -> { values.put((NamespacedKey) a[0], a[2]); yield null; }
            case "get" -> values.get(a[0]);
            case "getOrDefault" -> values.getOrDefault(a[0], a[2]);
            default -> throw new AssertionError(m.getName());
        });
    }
    private static Objective objective(String name) {
        if (!scores.containsKey(name)) return null;
        return proxy(Objective.class, (p, m, a) -> {
            if (!m.getName().equals("getScore")) throw new AssertionError(m.getName());
            return proxy(Score.class, (q, n, b) -> switch (n.getName()) {
                case "isScoreSet" -> scores.get(name).containsKey(a[0]);
                case "getScore" -> scores.get(name).getOrDefault(a[0], 0);
                default -> throw new AssertionError(n.getName());
            });
        });
    }
    private static final World world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
        case "getPlayers" -> players;
        case "getNearbyEntitiesByType" -> activeBullet == null ? List.of() : List.of(activeBullet);
        default -> throw new AssertionError(m.getName());
    });
    private static Marker bullet(Set<String> tags) {
        UUID id = UUID.randomUUID();
        PersistentDataContainer data = data();
        return proxy(Marker.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "getPersistentDataContainer" -> data;
            case "getScoreboardTags" -> tags;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 1.1, .5, 180, 0);
            default -> throw new AssertionError(m.getName());
        });
    }
    private static final PlayerInventory inventory = proxy(PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
        case "getArmorContents" -> worn.toArray(ItemStack[]::new);
        default -> throw new AssertionError(m.getName());
    });
    private static Player player(UUID id, String name, Set<String> tags) {
        return proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "getScoreboardTags" -> tags;
            case "getWorld" -> world;
            case "getInventory" -> inventory;
            case "getLastDamageCause" -> lastDamage;
            case "getEquipment" -> null;
            case "getLocation" -> new Location(world, 0, 0, 0);
            case "getEyeLocation" -> new Location(world, 0, 1.62, 0);
            case "getWidth" -> .6;
            case "getBodyYaw" -> 0f;
            case "getBoundingBox" -> new BoundingBox(-.3, 0, -.3, .3, 1.8, .3);
            default -> throw new AssertionError(m.getName());
        });
    }
    private static DamageSource source(String namespace, String key) {
        DamageType type = proxy(DamageType.class, (p, m, a) -> {
            if (m.getName().equals("getKey")) return new NamespacedKey(namespace, key);
            throw new AssertionError(m.getName());
        });
        return proxy(DamageSource.class, (p, m, a) -> {
            if (m.getName().equals("getDamageType")) return type;
            throw new AssertionError(m.getName());
        });
    }
    @SuppressWarnings("removal")
    private static EntityDamageByEntityEvent hit(Entity shooter, Entity victim, DamageSource source) {
        return new EntityDamageByEntityEvent(shooter, victim, EntityDamageEvent.DamageCause.CUSTOM, source, 100);
    }
    private static AmmoDefinition ammo(String id, double damage, int ammoClass) {
        var ammo = new AmmoDefinition(); ammo.id = id; ammo.damage = damage; ammo.ammoClass = ammoClass;
        return ammo;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Scoreboard board = proxy(Scoreboard.class, (p, m, a) -> objective((String) a[0]));
        ScoreboardManager scoreboardManager = proxy(ScoreboardManager.class, (p, m, a) -> board);
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> {
            if (m.getName().equals("callEvent")) { notifications++; return null; }
            throw new AssertionError(m.getName());
        });
        Field server = Bukkit.class.getDeclaredField("server"); server.setAccessible(true);
        server.set(null, proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getCurrentTick" -> tick;
            case "getScoreboardManager" -> scoreboardManager;
            case "getPluginManager" -> plugins;
            case "getLogger" -> java.util.logging.Logger.getLogger("DatapackDamageRegressionTest");
            case "getName", "getVersion", "getBukkitVersion" -> "test";
            default -> throw new AssertionError(m.getName());
        }));
        var bridge = new DatapackBulletHitBridge();
        var shooter = player(UUID.randomUUID(), "shooter", new HashSet<>(Set.of("PLAYER")));
        var victim = player(UUID.randomUUID(), "victim", new HashSet<>());
        players.add(shooter);
        // 別の射手の情報を近さやコレクションの順序で拾わない。
        players.addFirst(player(UUID.randomUUID(), "other", Set.of("PLAYER")));
        scores.put("_", new HashMap<>(Map.of("#id", 7)));
        scores.put("toisarm.id", new HashMap<>(Map.of("shooter", 7, "other", 8)));
        scores.put("toisarm.state.projectiles_per_shot", new HashMap<>(Map.of("shooter", 1)));
        var first = ammo("review_first", 12, 3);
        var next = ammo("review_next", 16.5, 6);
        Field definitions = ItemRegistry.class.getDeclaredField("AMMO_MAP"); definitions.setAccessible(true);
        Map<String, AmmoDefinition> definitionsMap = (Map<String, AmmoDefinition>) definitions.get(null);
        definitionsMap.put(first.id, first); definitionsMap.put(next.id, next);
        // 供給元だけを差し替え、実際の追加ハンドラーとMarkerへの保存を通す。
        Field supplies = ScavAmmoSupply.class.getDeclaredField("SUPPLIES"); supplies.setAccessible(true);
        Map<UUID, ScavAmmoSupply> supplyMap = (Map<UUID, ScavAmmoSupply>) supplies.get(null);
        Constructor<ScavAmmoSupply> supply = ScavAmmoSupply.class.getDeclaredConstructor(String.class, int.class);
        supply.setAccessible(true);
        supplyMap.put(shooter.getUniqueId(), supply.newInstance(first.id, 100));
        Set<String> tags = new HashSet<>(Set.of("toisarm.bullet", "BULLET_INIT"));
        activeBullet = bullet(tags);
        bridge.onBulletAdded(new EntityAddToWorldEvent(activeBullet, world));
        var shot = DatapackBulletState.read(activeBullet);
        check(shot != null && shot.damage() == 12 && shot.ammoClass() == 3, "normal first bullet captured before polling/owner initialization");
        check(shot.shooter().equals(shooter.getUniqueId()), "exact executing player ID selected");
        tags.remove("BULLET_INIT"); tags.addAll(Set.of("BULLET", "HIT_ENTITY"));
        DamageSource bulletSource = source("toisarm", "bullet");
        var original = hit(shooter, victim, bulletSource);
        bridge.onDamageCalculate(original);
        check(original.getFinalDamage() == 12, "first same-tick normal impact uses loaded ammunition, not fallback5");
        // 入れ子の別被弾が起きても、外側の結果・通知を失わない。
        var otherVictim = player(UUID.randomUUID(), "otherVictim", Set.of());
        var nested = hit(shooter, otherVictim, bulletSource);
        bridge.onDamageCalculate(nested); bridge.onDamage(nested); bridge.onDamage(original);
        check(notifications == 2, "nested events each complete exactly once");
        lastDamage = original;
        var helmet = new TestItem(); var vest = new TestItem(); var rig = new TestItem(); var tool = new TestItem();
        worn.addAll(List.of(helmet, vest, rig));
        for (var armor : worn) {
            var itemDamage = new PlayerItemDamageEvent(victim, armor, 3);
            bridge.onVanillaArmorDamage(itemDamage);
            check(itemDamage.isCancelled(), "vanilla wear suppressed for all slots; selected piece is manually handled");
        }
        var toolDamage = new PlayerItemDamageEvent(victim, tool, 1); bridge.onVanillaArmorDamage(toolDamage);
        check(!toolDamage.isCancelled(), "held tool wear is preserved");
        lastDamage = hit(shooter, victim, source("minecraft", "mob_attack"));
        var meleeWear = new PlayerItemDamageEvent(victim, helmet, 1); bridge.onVanillaArmorDamage(meleeWear);
        check(!meleeWear.isCancelled(), "unrelated melee armor wear is preserved");
        lastDamage = original; tick++;
        var laterWear = new PlayerItemDamageEvent(victim, helmet, 1); bridge.onVanillaArmorDamage(laterWear);
        check(!laterWear.isCancelled(), "old bullet damage does not suppress future durability events");
        var duplicate = hit(shooter, victim, bulletSource); bridge.onDamageCalculate(duplicate);
        check(duplicate.isCancelled(), "same marker plus real-body UUID is canceled across direct/forwarded events and ticks");
        bridge.onDamage(duplicate); check(notifications == 2, "duplicate does not notify or wear armor twice");
        Marker oldBullet = activeBullet;
        supplyMap.put(shooter.getUniqueId(), supply.newInstance(next.id, 100));
        // 定義オブジェクト自体が変わっても発射済みの数値は変わらない。
        first.damage = 99; first.ammoClass = 6;
        check(DatapackBulletState.read(oldBullet).damage() == 12 && DatapackBulletState.read(oldBullet).ammoClass() == 3, "in-flight shot frozen despite next shot/ammo-definition change");
        tags = new HashSet<>(Set.of("toisarm.bullet", "BULLET_INIT"));
        activeBullet = bullet(tags); bridge.onBulletAdded(new EntityAddToWorldEvent(activeBullet, world));
        check(DatapackBulletState.read(activeBullet).damage() == 16.5, "next marker independently captures new ammo");
        tags.remove("BULLET_INIT"); tags.addAll(Set.of("BULLET", "HIT_ENTITY"));
        var nextHit = hit(shooter, victim, bulletSource); bridge.onDamageCalculate(nextHit);
        check(!nextHit.isCancelled() && nextHit.getFinalDamage() == 16.5, "distinct bullet can hit same victim");
        // 12粒はそれぞれ一度命中し、総量9。二重転送だけを抑制する。
        double total = 0;
        for (int pellet = 0; pellet < 12; pellet++) {
            activeBullet = bullet(new HashSet<>(Set.of("toisarm.bullet", "BULLET", "HIT_ENTITY")));
            DatapackBulletState.capture(activeBullet, shooter.getUniqueId(), ammo("shell", 9, 2), 12);
            var pelletHit = hit(shooter, victim, bulletSource); bridge.onDamageCalculate(pelletHit);
            check(!pelletHit.isCancelled(), "separate pellet preserved"); total += pelletHit.getFinalDamage();
            var pelletDuplicate = hit(shooter, victim, bulletSource); bridge.onDamageCalculate(pelletDuplicate);
            check(pelletDuplicate.isCancelled(), "duplicate of each pellet rejected");
        }
        check(total == 9, "twelve distinct pellets retain shell theory damage9");
        var empty = bullet(Set.of()); DatapackBulletState.capture(empty, shooter.getUniqueId(), null, 1);
        DatapackBulletState.capture(empty, shooter.getUniqueId(), next, 12);
        check(DatapackBulletState.read(empty).damage() == 5 && DatapackBulletState.read(empty).pellets() == 1, "unknown shot is explicitly frozen and cannot inherit later ammo");
        activeBullet = bullet(Set.of("BULLET", "HIT_ENTITY"));
        var legacy = hit(shooter, victim, bulletSource); bridge.onDamageCalculate(legacy);
        check(legacy.getFinalDamage() == 5, "pre-update untracked marker never reads current or last-shot ammo");
        check(DatapackBulletHitBridge.armorWear(false, 0) == 3, "blocked armor wear remains3");
        check(DatapackBulletHitBridge.armorWear(true, 12) == 3, "penetrated selected armor wear matches vanilla amount");
        check(DatapackBulletHitBridge.armorWear(true, .75) == 1, "penetrating pellet wears selected armor once");
        check(DatapackBulletHitBridge.armorWear(true, 0) == 0, "zeroed penetrating damage wears nothing");
        supplyMap.clear();
        System.out.println("DatapackDamageRegressionTest passed: spawn snapshot, initial shot, ammo switching, independent pellets/victims, nested events, duplicate rejection and scoped armor wear");
    }
}

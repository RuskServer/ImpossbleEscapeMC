package com.lunar_prototype.impossbleEscapeMC.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Objective;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Toi's Armory の弾Markerを追い、地形への着弾とプレイヤーの近くを通る弾を鳴らす。 */
public final class DatapackBulletAudio implements Listener {
    private static final String BULLET_TAG = "toisarm.bullet";
    private static final String HIT_GROUND_TAG = "HIT_GROUND";
    private static final String OWNER_OBJECTIVE = "toisarm.bullet.owner";
    private static final String PLAYER_ID_OBJECTIVE = "toisarm.id";
    private static final String IMPACT_SOUND = "iem:bullet.impact";
    private static final String BODY_IMPACT_SOUND = "iem:bullet.impact.body";
    private static final NamespacedKey BULLET_DAMAGE = new NamespacedKey("toisarm", "bullet");
    private static final String WHIZZ_SOUND = "iem:bullet.whizz";
    private static final double WHIZZ_RADIUS = 2.75;
    private static final double WHIZZ_RADIUS_SQUARED = WHIZZ_RADIUS * WHIZZ_RADIUS;
    private static final double MAX_SEGMENT_SQUARED = 128.0 * 128.0;
    private static final int WHIZZ_COOLDOWN_TICKS = 6;
    private static final int IMPACT_COOLDOWN_TICKS = 2;

    private final Map<UUID, Flight> flights = new HashMap<>();
    private final Map<UUID, Integer> lastWhizzTick = new HashMap<>();
    private final Map<UUID, Integer> lastImpactTick = new HashMap<>();

    private static final class Flight {
        final Marker bullet;
        World world;
        Vector position;
        int ownerId = Integer.MIN_VALUE;

        Flight(Marker bullet) {
            this.bullet = bullet;
            this.world = bullet.getWorld();
            this.position = bullet.getLocation().toVector();
        }
    }

    public DatapackBulletAudio(ImpossbleEscapeMC plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) {
            for (Marker bullet : world.getEntitiesByClass(Marker.class)) track(bullet);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        // summon 直後はデータパックのタグがまだ無い。生成位置だけ先に控える。
        if (event.getEntity() instanceof Marker marker) flights.putIfAbsent(marker.getUniqueId(), new Flight(marker));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Marker bullet)) return;
        Flight flight = flights.remove(bullet.getUniqueId());
        if (!bullet.getScoreboardTags().contains(BULLET_TAG)) return;

        // データパックは着弾位置へ移動して HIT_GROUND を付け、その場で弾Markerを kill する。
        if (flight != null) advance(flight, bullet.getLocation());
        if (bullet.getScoreboardTags().contains(HIT_GROUND_TAG)) playImpact(bullet.getLocation(), IMPACT_SOUND);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBulletHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)
                || !BULLET_DAMAGE.equals(event.getDamageSource().getDamageType().getKey())) return;
        Location hit = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
        playImpact(hit, BODY_IMPACT_SOUND);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastWhizzTick.remove(id);
        lastImpactTick.remove(id);
    }

    private void track(Marker bullet) {
        if (!bullet.getScoreboardTags().contains(BULLET_TAG)) return;
        flights.putIfAbsent(bullet.getUniqueId(), new Flight(bullet));
    }

    private void tick() {
        Iterator<Flight> iterator = flights.values().iterator();
        while (iterator.hasNext()) {
            Flight flight = iterator.next();
            if (!flight.bullet.isValid()) {
                iterator.remove();
                continue;
            }
            if (!flight.bullet.getScoreboardTags().contains(BULLET_TAG)) {
                iterator.remove();
                continue;
            }
            advance(flight, flight.bullet.getLocation());
        }
    }

    private void advance(Flight flight, Location nextLocation) {
        if (flight.world != nextLocation.getWorld()) {
            flight.world = nextLocation.getWorld();
            flight.position = nextLocation.toVector();
            return;
        }
        flight.ownerId = bulletOwner(flight.bullet, flight.ownerId);
        Vector next = nextLocation.toVector();
        Vector start = flight.position;
        flight.position = next;
        double lengthSquared = start.distanceSquared(next);
        if (lengthSquared < 0.01 || lengthSquared > MAX_SEGMENT_SQUARED) return;
        playNearMiss(flight.world, start, next, flight.ownerId);
    }

    private void playNearMiss(World world, Vector start, Vector end, int ownerId) {
        BoundingBox area = BoundingBox.of(start, end).expand(WHIZZ_RADIUS);
        Vector movement = end.clone().subtract(start);
        double length = movement.length();
        Vector direction = movement.clone().multiply(1.0 / length);
        int now = Bukkit.getCurrentTick();
        for (Entity entity : world.getNearbyEntities(area)) {
            if (!(entity instanceof Player player) || player.getGameMode() == GameMode.SPECTATOR || player.isDead()) continue;
            if (isOwner(player, ownerId)) continue;
            if (ownerId == Integer.MIN_VALUE && player.getEyeLocation().toVector().distanceSquared(start) < 9.0) continue;
            Integer last = lastWhizzTick.get(player.getUniqueId());
            if (last != null && now - last < WHIZZ_COOLDOWN_TICKS) continue;

            Vector eye = player.getEyeLocation().toVector();
            Vector closest = closestPoint(start, end, eye);
            double distanceSquared = closest.distanceSquared(eye);
            if (distanceSquared > WHIZZ_RADIUS_SQUARED) continue;
            BoundingBox body = player.getBoundingBox().clone().expand(0.15);
            if (body.contains(start) || body.contains(end) || body.rayTrace(start, direction, length) != null) continue;
            Vector toEye = eye.clone().subtract(closest);
            double distance = Math.sqrt(distanceSquared);
            if (distance > 0.01 && world.rayTraceBlocks(new Location(world, closest.getX(), closest.getY(), closest.getZ()),
                    toEye, distance, FluidCollisionMode.NEVER, true) != null) continue;

            float volume = (float) (0.9 - Math.min(distance, WHIZZ_RADIUS) * 0.12);
            player.playSound(new Location(world, closest.getX(), closest.getY(), closest.getZ()),
                    WHIZZ_SOUND, SoundCategory.PLAYERS, volume, 1.0f);
            lastWhizzTick.put(player.getUniqueId(), now);
        }
    }

    static Vector closestPoint(Vector start, Vector end, Vector point) {
        Vector segment = end.clone().subtract(start);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1e-9) return start.clone();
        double ratio = Math.max(0.0, Math.min(1.0, point.clone().subtract(start).dot(segment) / lengthSquared));
        return start.clone().add(segment.multiply(ratio));
    }

    private void playImpact(Location location, String sound) {
        int now = Bukkit.getCurrentTick();
        for (Player player : location.getNearbyPlayers(24.0)) {
            Integer last = lastImpactTick.get(player.getUniqueId());
            if (last != null && now - last < IMPACT_COOLDOWN_TICKS) continue;
            player.playSound(location, sound, SoundCategory.BLOCKS, 0.9f, 1.0f);
            lastImpactTick.put(player.getUniqueId(), now);
        }
    }

    private static int bulletOwner(Marker bullet, int known) {
        if (known != Integer.MIN_VALUE) return known;
        var manager = Bukkit.getScoreboardManager();
        Objective objective = manager != null ? manager.getMainScoreboard().getObjective(OWNER_OBJECTIVE) : null;
        if (objective == null) return known;
        var score = objective.getScoreFor(bullet);
        return score.isScoreSet() ? score.getScore() : known;
    }

    private static boolean isOwner(Player player, int ownerId) {
        if (ownerId == Integer.MIN_VALUE) return false;
        var manager = Bukkit.getScoreboardManager();
        Objective objective = manager != null ? manager.getMainScoreboard().getObjective(PLAYER_ID_OBJECTIVE) : null;
        if (objective == null) return false;
        var score = objective.getScore(player.getName());
        return score.isScoreSet() && score.getScore() == ownerId;
    }
}

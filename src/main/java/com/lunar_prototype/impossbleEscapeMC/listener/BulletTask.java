package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.CombatHeatmapManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.BloodEffect;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map;

/**
 * 弾丸の物理挙動、当たり判定、ダメージ計算を担当するタスク
 */
public class BulletTask extends BukkitRunnable {
    private final ImpossbleEscapeMC plugin;
    private final LivingEntity shooter;
    private final double damage;
    private final Vector origin;
    private Location currentLoc;
    private Vector velocity;
    private int ticksAlive = 0;
    private final int shooterPing;
    private boolean active = true;
    private final int ammoClass;
    private final java.util.Set<java.util.UUID> whizzedPlayers = new java.util.HashSet<>();

    private static final double GRAVITY = 0.015;
    private static final double SPEED = 20.0;

    public BulletTask(ImpossbleEscapeMC plugin, LivingEntity shooter, double damage, int ammoClass, double inaccuracy) {
        this.plugin = plugin;
        this.shooter = shooter;
        this.damage = damage;
        this.currentLoc = shooter.getEyeLocation();
        this.origin = this.currentLoc.toVector();
        this.shooterPing = (shooter instanceof Player p) ? PacketEvents.getAPI().getPlayerManager().getPing(p) : 0;
        this.ammoClass = ammoClass;

        Vector dir = shooter.getEyeLocation().getDirection();
        if (inaccuracy > 0) {
            dir.add(new Vector(
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy,
                    (Math.random() - 0.5) * inaccuracy));
            dir.normalize();
        }
        this.velocity = dir.multiply(SPEED);
    }

    public void start() {
        this.run();
        if (active) {
            this.runTaskTimer(plugin, 1, 1);
        }
    }

    @Override
    public void cancel() {
        if (!active) return;
        active = false;
        try {
            super.cancel();
        } catch (IllegalStateException ignored) {}
    }

    @Override
    public void run() {
        if (!active) return;

        ticksAlive++;
        if (ticksAlive > 100) {
            this.cancel();
            return;
        }

        Location prevLoc = currentLoc.clone();
        velocity.add(new Vector(0, -GRAVITY, 0));
        currentLoc.add(velocity);

        if (shooter instanceof Player) {
            CombatHeatmapManager.recordLine(prevLoc, velocity.clone().normalize(), SPEED, 1.0f);
        }

        // ニアミス判定 (プレイヤーへの音とAIへの影響)
        for (Entity nearby : currentLoc.getWorld().getNearbyEntities(currentLoc, 2.0, 2.0, 2.0)) {
            if (nearby.equals(shooter)) continue;

            // 1. プレイヤーへのかすり音 (minecraft:fx.whiz)
            if (nearby instanceof Player p) {
                if (whizzedPlayers.add(p.getUniqueId())) {
                    p.playSound(p.getLocation(), "minecraft:fx.whiz", 0.8f, 1.0f);
                }
            }

            // 2. スカブ（AI）への影響
            if (nearby instanceof Mob scav) {
                ScavController controller = ScavSpawner.getController(scav.getUniqueId());
                if (controller != null) {
                    controller.getBrain().reward(0.05f);
                    controller.addSuppression(0.1f);
                }
            }
        }

        spawnTracer(prevLoc, currentLoc);

        double distanceRemaining = SPEED;
        Location rayStart = prevLoc.clone();
        Vector rayDir = velocity.clone().normalize();
        long targetTime = System.currentTimeMillis() - shooterPing;

        while (distanceRemaining > 0.01) {
            var blockTrace = currentLoc.getWorld().rayTraceBlocks(rayStart, rayDir, distanceRemaining, FluidCollisionMode.NEVER, true);
            double blockDist = (blockTrace != null) ? blockTrace.getHitPosition().distance(rayStart.toVector()) : Double.MAX_VALUE;

            Entity victim = null;
            Vector hitPos = null;
            double bestDist = blockDist;

            Collection<Entity> potentialVictims = currentLoc.getWorld().getNearbyEntities(rayStart, distanceRemaining + 2, distanceRemaining + 2, distanceRemaining + 2);
            for (Entity e : potentialVictims) {
                if (!(e instanceof LivingEntity le) || e.equals(shooter)) continue;

                // ラグ補填 (GunListenerから移植されたロジックが必要だが、一旦シンプルに判定)
                BoundingBox box = le.getBoundingBox(); 
                // 本来は GunListener.getCompensatedBox を使うべきだが、BulletTaskに履歴管理を移すか、
                // あるいは履歴をStaticにアクセス可能にする必要がある。
                
                var entityTrace = box.rayTrace(rayStart.toVector(), rayDir, distanceRemaining);
                if (entityTrace != null) {
                    double dist = entityTrace.getHitPosition().distance(rayStart.toVector());
                    if (dist < bestDist) {
                        bestDist = dist;
                        victim = e;
                        hitPos = entityTrace.getHitPosition();
                    }
                }
            }

            if (victim != null) {
                Vector bulletDir = velocity.clone().normalize();
                handleDamage((LivingEntity) victim, shooter, damage, ammoClass, hitPos.toLocation(currentLoc.getWorld()));
                BloodEffect.spawn(hitPos.toLocation(currentLoc.getWorld()), bulletDir, damage);
                this.cancel();
                return;
            } else if (blockTrace != null) {
                Block block = blockTrace.getHitBlock();
                Location hLoc = blockTrace.getHitPosition().toLocation(currentLoc.getWorld());
                if (isPenetrable(block.getType()) && Math.random() < 0.9) {
                    block.getWorld().playEffect(hLoc, Effect.STEP_SOUND, block.getType());
                    double hitDist = blockTrace.getHitPosition().distance(rayStart.toVector());
                    rayStart = hLoc.clone().add(rayDir.clone().multiply(0.01));
                    distanceRemaining -= (hitDist + 0.01);
                    continue;
                } else {
                    spawnBulletHole(hLoc, blockTrace.getHitBlockFace());
                    this.cancel();
                    return;
                }
            } else {
                break;
            }
        }
    }

    private void handleDamage(LivingEntity victim, LivingEntity shooter, double baseDamage, int ammoClass, Location hitLoc) {
        double finalDamage = baseDamage;
        double footY = victim.getLocation().getY();
        double headY = victim.getEyeLocation().getY();
        double hitY = hitLoc.getY();
        double height = headY - footY;

        boolean isHeadshot = hitY >= (headY - 0.25);
        boolean isLegShot = hitY <= (footY + (height * 0.45));

        int armorClass = 0;
        if (isHeadshot) {
            finalDamage *= 1.2;
            armorClass = getArmorClassFromSlot(victim, EquipmentSlot.HEAD);
        } else if (isLegShot) {
            finalDamage *= 0.6;
        } else {
            armorClass = getArmorClassFromSlot(victim, EquipmentSlot.CHEST);
        }

        boolean isPenetrated = calculatePenetration(ammoClass, armorClass);
        Player shooterPlayer = (shooter instanceof Player p) ? p : null;

        if (!isPenetrated) {
            finalDamage *= 0.15;
            playHitSound(shooterPlayer, hitLoc, "hit-sounds.impact", "ENTITY_ARROW_HIT_PLAYER", 1.0f, 1.0f);
        } else {
            playHitSound(shooterPlayer, hitLoc, "hit-sounds.headshot", "ENTITY_ARROW_HIT_PLAYER", 1.0f, 1.0f);
        }

        // AI報酬
        if (shooter instanceof Player) {
            ScavController controller = ScavSpawner.getController(victim.getUniqueId());
            // 被弾したのがSCAVの場合の制圧効果
            if (controller != null) {
                controller.addSuppression(0.5f);
            }
        }

        // 独自イベント発行
        String hitLocation = isHeadshot ? "head" : (isLegShot ? "legs" : (hitY > (footY + (height * 0.45)) && hitY < (headY - 0.25) ? "arms" : "body"));
        BulletHitEvent bulletEvent = new BulletHitEvent(victim, shooter, finalDamage, hitLocation, isPenetrated, ammoClass);
        Bukkit.getPluginManager().callEvent(bulletEvent);

        victim.setMetadata("no_knockback", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        victim.setMetadata("bypass_armor", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        victim.setNoDamageTicks(0);
        victim.damage(finalDamage, shooter);
    }

    private boolean calculatePenetration(int ammo, int armor) {
        if (armor <= 0) return true;
        double chance = (ammo > armor) ? 0.95 : (ammo == armor ? 0.70 : 0.15);
        return Math.random() < chance;
    }

    private int getArmorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    private void playHitSound(Player player, Location loc, String configKey, String defaultSound, float vol, float pitch) {
        String soundName = plugin.getConfig().getString(configKey, defaultSound);
        Location playAt = (player != null) ? player.getLocation() : loc;
        try {
            Sound s = Sound.valueOf(soundName.toUpperCase());
            if (player != null) player.playSound(playAt, s, vol, pitch);
            else playAt.getWorld().playSound(playAt, s, vol, pitch);
        } catch (Exception e) {
            if (player != null) player.playSound(playAt, soundName, vol, pitch);
            else playAt.getWorld().playSound(playAt, soundName, vol, pitch);
        }
    }

    private boolean isPenetrable(Material m) {
        String n = m.name();
        return n.contains("GLASS") || n.contains("PANE") || n.contains("BARS") || n.contains("LEAVES");
    }

    private void spawnBulletHole(Location loc, org.bukkit.block.BlockFace face) {
        if (face == null) return;
        Vector offset = face.getDirection().multiply(0.2);
        Location holeLoc = loc.clone().add(offset);
        Particle.Trail trailData = new Particle.Trail(holeLoc, Color.fromRGB(10, 10, 10), 600);
        loc.getWorld().spawnParticle(Particle.TRAIL, holeLoc, 1, 0, 0, 0, 0, trailData);
    }

    private void spawnTracer(Location from, Location to) {
        final World world = from.getWorld();
        final Vector start = from.toVector();
        final Vector direction = to.toVector().subtract(start);
        final double distance = direction.length();
        if (distance < 0.05) return;
        direction.normalize();

        new BukkitRunnable() {
            @Override
            public void run() {
                Particle.DustOptions dustOption = new Particle.DustOptions(Color.fromRGB(255, 180, 50), 0.2f);
                for (double d = 0; d < distance; d += 0.25) {
                    Vector pos = start.clone().add(direction.clone().multiply(d));
                    if (pos.distanceSquared(origin) < 9.0) continue;
                    world.spawnParticle(Particle.DUST, pos.getX(), pos.getY(), pos.getZ(), 1, 0, 0, 0, 0, dustOption);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}

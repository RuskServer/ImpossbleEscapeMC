package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SCAVごとの {@link DatapackGunner} (データパック銃を撃つFakePlayer) を管理する。
 * FakePlayerがゲームやプラグインの他の処理に干渉しないための処理もここで行う。
 */
public final class DatapackGunnerManager implements Listener {

    private static DatapackGunnerManager instance;

    private final Plugin plugin;
    private final Map<UUID, DatapackGunner> byOwner = new HashMap<>();
    private final Map<UUID, DatapackGunner> byGunner = new HashMap<>();
    private final BitSet usedSlots = new BitSet();
    private final BukkitTask tickTask;

    private DatapackGunnerManager(Plugin plugin) {
        this.plugin = plugin;
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public static void init(Plugin plugin) {
        if (instance != null) return;
        instance = new DatapackGunnerManager(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    /** 全FakePlayerを取り除く。サーバー停止時にプレイヤーとして保存・退出処理されないよう、プラグイン無効化時に呼ぶ */
    public static void shutdown() {
        if (instance == null) return;
        instance.tickTask.cancel();
        for (DatapackGunner gunner : new ArrayList<>(instance.byOwner.values())) {
            instance.removeGunner(gunner);
        }
        instance = null;
    }

    public static boolean isGunner(Entity entity) {
        return entity instanceof Player && instance != null && instance.byGunner.containsKey(entity.getUniqueId());
    }

    /** FakePlayerなら持ち主のSCAVを、それ以外ならそのまま返す (攻撃者・キラーの解決用) */
    public static Entity resolveShooter(Entity entity) {
        if (entity == null || instance == null) return entity;
        DatapackGunner gunner = instance.byGunner.get(entity.getUniqueId());
        return gunner != null ? gunner.getOwner() : entity;
    }

    /** SCAVのFakePlayer。まだ作られていなければnull */
    public static DatapackGunner get(UUID ownerId) {
        return instance != null ? instance.byOwner.get(ownerId) : null;
    }

    /** SCAVのFakePlayerを返す。無ければ作る */
    public static DatapackGunner getOrCreate(Mob owner, DatapackGunProfile profile) {
        if (instance == null) throw new IllegalStateException("DatapackGunnerManager is not initialized");
        DatapackGunner gunner = instance.byOwner.get(owner.getUniqueId());
        return gunner != null ? gunner : instance.createGunner(owner, profile);
    }

    /** SCAVの終了時に呼ぶ */
    public static void remove(UUID ownerId) {
        if (instance == null) return;
        DatapackGunner gunner = instance.byOwner.get(ownerId);
        if (gunner != null) instance.removeGunner(gunner);
    }

    private DatapackGunner createGunner(Mob owner, DatapackGunProfile profile) {
        int slot = usedSlots.nextClearBit(0);
        usedSlots.set(slot);
        DatapackGunner gunner = DatapackGunner.spawn(plugin, owner, profile, slot);
        byOwner.put(owner.getUniqueId(), gunner);
        byGunner.put(gunner.getUniqueId(), gunner);
        return gunner;
    }

    private void removeGunner(DatapackGunner gunner) {
        byOwner.remove(gunner.getOwner().getUniqueId());
        byGunner.remove(gunner.getUniqueId());
        usedSlots.clear(gunner.getSlot());
        try {
            gunner.remove();
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to remove SCAV gunner " + gunner.getUniqueId(), t);
        }
    }

    private void tick() {
        for (DatapackGunner gunner : new ArrayList<>(byOwner.values())) {
            Mob owner = gunner.getOwner();
            if (!owner.isValid() || owner.isDead() || owner.getWorld() != gunner.getBukkitPlayer().getWorld()) {
                removeGunner(gunner);
                continue;
            }
            try {
                gunner.tick();
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "SCAV gunner tick failed; removing " + gunner.getUniqueId(), t);
                removeGunner(gunner);
            }
        }
    }

    // --- FakePlayerがゲームに干渉しないための処理 ---

    /** FakePlayerは無敵。SCAVの位置に重なっているため、他者からの攻撃はSCAV本体へ転送する */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onGunnerDamaged(EntityDamageEvent event) {
        DatapackGunner gunner = byGunner.get(event.getEntity().getUniqueId());
        if (gunner == null) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity attacker = resolveShooter(byEntity.getDamager());
            if (attacker != null && !attacker.equals(gunner.getOwner()) && gunner.getOwner().isValid()) {
                gunner.getOwner().damage(event.getDamage(), event.getDamageSource());
            }
        }
    }

    /** SCAVの目の位置から撃つため、自分の弾が自分のSCAVに当たらないようにする */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onOwnerHitByOwnGunner(EntityDamageByEntityEvent event) {
        DatapackGunner gunner = byGunner.get(event.getDamager().getUniqueId());
        if (gunner != null && event.getEntity().equals(gunner.getOwner())) {
            event.setCancelled(true);
        }
    }

    /**
     * データパック銃の命中を、プラグイン銃の命中 (BulletHitEvent) と同じくSCAVのAIとレイドのAIログに伝える
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGunnerHit(EntityDamageByEntityEvent event) {
        DatapackGunner gunner = byGunner.get(event.getDamager().getUniqueId());
        if (gunner == null || !(event.getEntity() instanceof org.bukkit.entity.LivingEntity victim)) return;
        if (victim.equals(gunner.getOwner()) || byGunner.containsKey(victim.getUniqueId())) return;

        UUID ownerId = gunner.getOwner().getUniqueId();
        ScavController controller = ScavSpawner.getController(ownerId);
        // データパック銃は部位・貫通の情報を持たないため、胴体への貫通弾として扱う
        if (controller != null) {
            controller.onBulletHitDealt(victim, event.getFinalDamage(), true, "BODY");
        }
        String raidSessionId = ScavSpawner.getRaidSessionId(ownerId);
        AiRaidLogger logger = com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.getInstance().getAiRaidLogger();
        if (raidSessionId != null && logger != null && logger.isEnabled()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("victimId", victim.getUniqueId().toString());
            payload.put("damage", event.getFinalDamage());
            payload.put("datapack", true);
            logger.logEvent(raidSessionId, ownerId, "SHOT_HIT", payload);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (DatapackGunner gunner : byOwner.values()) {
            event.getPlayer().hidePlayer(plugin, gunner.getBukkitPlayer());
        }
    }

    @EventHandler
    public void onGunnerAdvancement(PlayerAdvancementDoneEvent event) {
        if (isGunner(event.getPlayer())) {
            event.message(null);
        }
    }
}

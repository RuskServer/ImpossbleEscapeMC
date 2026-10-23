package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.listener.BulletDamageModel;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弾が当たった時の様子 (誰が・何で・どの弾で・どこに・何メートルから) を覚えておき、倒した時のキルログに使う
 * ({@link BulletHitEvent})。倒れた相手ごとに、撃った相手ごとの最後の 1 発だけ覚える
 */
public final class RaidKillTracker implements Listener {

    /** 倒れた時に、この時間より前の当たりは使わない */
    private static final long HIT_VALID_MILLIS = 15_000;

    /** 1 発の当たり */
    public record Hit(UUID shooter, String shooterName, RaidReport.Kind shooterKind, String victimName,
                      ItemStack weapon, String weaponName, String ammoName, int distance, String part, long at) {
    }

    private static final Map<UUID, Map<UUID, Hit>> HITS = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBulletHit(BulletHitEvent event) {
        LivingEntity victim = event.getVictim();
        LivingEntity shooter = event.getShooter();
        if (victim == null || shooter == null) return;
        long now = System.currentTimeMillis();
        HITS.values().forEach(m -> m.values().removeIf(h -> now - h.at() > HIT_VALID_MILLIS));
        HITS.values().removeIf(Map::isEmpty);

        ItemStack held = shooter.getEquipment() != null ? shooter.getEquipment().getItemInMainHand() : null;
        ItemStack weapon = held != null && !held.getType().isAir() ? held.asOne() : null;
        String weaponName = weapon != null ? PlainTextComponentSerializer.plainText().serialize(weapon.effectiveName()) : null;
        int distance = victim.getWorld().equals(shooter.getWorld())
                ? (int) Math.round(victim.getLocation().distance(shooter.getLocation())) : -1;
        Hit hit = new Hit(shooter.getUniqueId(), nameOf(shooter), kindOf(shooter), nameOf(victim), weapon, weaponName,
                ammoName(shooter), distance, partName(event.getHitLocation()), now);
        HITS.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>()).put(shooter.getUniqueId(), hit);
    }

    /** victim に shooter が最後に当てた 1 発 (無い・古ければ null) */
    public static Hit lastHit(UUID victim, UUID shooter) {
        Map<UUID, Hit> byShooter = HITS.get(victim);
        Hit hit = byShooter != null ? byShooter.get(shooter) : null;
        return hit != null && System.currentTimeMillis() - hit.at() <= HIT_VALID_MILLIS ? hit : null;
    }

    /** victim に誰かが最後に当てた 1 発 (無い・古ければ null) */
    public static Hit lastHitOn(UUID victim) {
        Map<UUID, Hit> byShooter = HITS.get(victim);
        if (byShooter == null) return null;
        Hit latest = null;
        for (Hit hit : byShooter.values()) {
            if (latest == null || hit.at() > latest.at()) latest = hit;
        }
        return latest != null && System.currentTimeMillis() - latest.at() <= HIT_VALID_MILLIS ? latest : null;
    }

    public static void forget(UUID victim) {
        HITS.remove(victim);
    }

    static RaidReport.Kind kindOf(LivingEntity entity) {
        if (entity instanceof Player) return RaidReport.Kind.PMC;
        ScavController controller = ScavSpawner.getController(entity.getUniqueId());
        return controller != null && controller.getBrain().getBrainLevel() == ScavBrain.BrainLevel.HIGH
                ? RaidReport.Kind.BOSS : RaidReport.Kind.SCAV;
    }

    static String nameOf(LivingEntity entity) {
        if (entity instanceof Player player) return player.getName();
        if (entity.customName() != null) return PlainTextComponentSerializer.plainText().serialize(entity.customName());
        return kindOf(entity) == RaidReport.Kind.BOSS ? "BOSS" : "SCAV";
    }

    private static String ammoName(LivingEntity shooter) {
        AmmoDefinition ammo;
        ScavAmmoSupply supply = ScavAmmoSupply.of(shooter.getUniqueId());
        if (supply != null) {
            ammo = supply.ammo();
        } else {
            ammo = shooter instanceof Player ? DatapackAmmo.lastShotAmmo(shooter.getUniqueId()) : null;
        }
        if (ammo == null || ammo.displayName == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(ammo.displayName));
    }

    private static String partName(String hitLocation) {
        if (hitLocation == null) return null;
        return switch (hitLocation) {
            case BulletDamageModel.HEAD -> "頭";
            case BulletDamageModel.FACE -> "顔";
            case BulletDamageModel.BODY -> "胴";
            case BulletDamageModel.ARMS -> "腕";
            case BulletDamageModel.LEGS -> "脚";
            default -> hitLocation;
        };
    }
}

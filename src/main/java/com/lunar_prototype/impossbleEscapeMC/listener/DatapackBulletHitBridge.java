package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.item.ArmorDurability;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackAmmo;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/**
 * Toi's Armoryデータパック銃の命中に、プラグイン銃と同じ部位判定・防具の貫通判定を入れ、{@link BulletHitEvent} として通知する。
 *
 * データパックの {@code damage} コマンドで起きるダメージイベントの中で判断する。
 * <ul>
 *   <li>頭: データパックは当たり判定の上0.45ブロックへの命中に CRIT_HIT タグを付け、頭用のダメージで撃つ。
 *       タグは damage コマンドの実行中も付いたままなので、それで判断する (頭の倍率はデータパック側で入っている)</li>
 *   <li>胴・腕・足: ダメージを与えている最中の弾 (BULLET と HIT_ENTITY タグの付いた marker) は、その tick の移動先に
 *       進行方向を向いて置かれている。その位置と向きから当たり判定の箱への入射点を逆算する</li>
 * </ul>
 * 基礎ダメージは装填弾の定義値 (散弾は1粒分)、足は×0.6、防具を貫通しなければ
 * ダメージなし (打撲なし) にし、バニラの防具による軽減は外す (プラグイン銃と同じく防具クラスで判断するため)。
 */
public class DatapackBulletHitBridge implements Listener {

    private static final NamespacedKey DATAPACK_BULLET_DAMAGE = new NamespacedKey("toisarm", "bullet");
    /** データパックが頭への命中に付けるタグ */
    private static final String CRIT_HIT_TAG = "CRIT_HIT";
    /** ダメージを与えている最中の弾に付いているタグ */
    private static final String BULLET_TAG = "BULLET";
    private static final String HIT_ENTITY_TAG = "HIT_ENTITY";
    private static final String MULTI_PROJECTILE_TAG = "toisarm.bullet.shots_divide_damage";
    private static final String FIRING_OBJECTIVE = "toisarm.timer.firing";
    /** 弾を探す範囲。弾は1tickにこれより長くは進まない */
    private static final double BULLET_SEARCH_RADIUS = 64.0;
    /** データパックの当たり判定は当たり判定の箱を0.1広げた範囲 */
    private static final double HITBOX_MARGIN = 0.1;
    /** 撃った弾が分からない時 (弾を込めていない銃・弾の無い口径のSCAV) の貫通クラス */
    private static final int DATAPACK_AMMO_CLASS = 1;

    /** プレイヤーの弾がSCAVに当たった時の制圧 */
    private static final float HIT_SUPPRESSION = 0.5f;

    private record HitResult(String hitLocation, boolean penetrated, double rawDamage, int ammoClass,
                             BulletDamageModel.ArmorPiece armor) {
    }

    /** LOW で決めた部位・貫通を MONITOR の通知で使う (ダメージイベントは入れ子にならないため1件で足りる) */
    private EntityDamageEvent pendingEvent;
    private HitResult pendingResult;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageCalculate(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;

        // 撃った弾の貫通クラスと定義威力。プレイヤーは撃った時に銃に込めていた弾、
        // SCAVは割り当てられた弾。分からない時 (弾を込めていない銃・弾の無い口径のSCAV) は貫通クラス1
        BulletContact contact = bulletContact(victim);
        AmmoDefinition ammo = shooterAmmo(event.getDamager(), contact);
        int ammoClass = ammo != null ? ammo.ammoClass : DATAPACK_AMMO_CLASS;
        double rawDamage = BulletDamageModel.baseDamage(ammo);
        String hitLocation = hitLocation(victim, contact != null ? contact.entry() : null);
        // 処理中の弾Markerから入射方向を読む。
        Vector incoming = contact != null ? contact.direction() : null;
        if (BulletDamageModel.visorRicochets(victim, hitLocation, incoming)) {
            event.setCancelled(true);
            victim.getWorld().playSound(victim.getEyeLocation(), org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.3f);
            return;
        }
        BulletDamageModel.ArmorPiece armor = BulletDamageModel.armorPiece(victim, hitLocation);
        int armorClass = armor != null ? armor.armorClass() : 0;
        Entity shooter = DatapackGunnerManager.resolveShooter(event.getDamager());
        UUID shooterId = shooter != null ? shooter.getUniqueId() : event.getDamager().getUniqueId();
        boolean penetrated = BulletDamageModel.penetrates(ammoClass, armorClass, shooterId,
                victim.getUniqueId(), hitLocation);

        double damage = BulletDamageModel.damageForHit(rawDamage, hitLocation, penetrated, armorClass);

        event.setDamage(damage);
        // バニラの防具・耐性などによる軽減を外す (ダメージは BulletDamageModel が防具を考えて決めている)
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }

        pendingEvent = event;
        pendingResult = new HitResult(hitLocation, penetrated, rawDamage, ammoClass, armor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;
        HitResult result = event == pendingEvent ? pendingResult : null;
        pendingEvent = null;
        pendingResult = null;
        if (result == null) return;

        // 止められた弾はダメージ0なので、止めた防具の耐久をここで減らす。
        // 貫通した弾はバニラの防具耐久 (damageOnHurt) に任せる。
        if (!result.penetrated() && result.armor() != null) {
            wearArmor(victim, result.armor());
        }

        // SCAVのFakePlayerが撃った場合は、撃ったSCAV本体を射手とする
        Entity shooter = DatapackGunnerManager.resolveShooter(event.getDamager());
        if (!(shooter instanceof LivingEntity livingShooter)) return;

        if (shooter instanceof Player) {
            ScavController controller = ScavSpawner.getController(victim.getUniqueId());
            if (controller != null) controller.addSuppression(HIT_SUPPRESSION);
        }

        Bukkit.getPluginManager().callEvent(new BulletHitEvent(
                victim, livingShooter, event.getFinalDamage(), result.hitLocation(), result.penetrated(),
                result.ammoClass(), result.rawDamage()));
    }

    private static void wearArmor(LivingEntity victim, BulletDamageModel.ArmorPiece armor) {
        ItemStack worn = ArmorDurability.wear(armor.item(), ArmorDurability.BLOCKED_WEAR, victim instanceof Player);
        if (worn != null && victim.getEquipment() != null) {
            victim.getEquipment().setItem(armor.slot(), worn);
        }
    }

    private static AmmoDefinition shooterAmmo(Entity damager, BulletContact contact) {
        Entity shooter = DatapackGunnerManager.resolveShooter(damager);
        if (shooter == null) return null;
        ScavAmmoSupply supply = ScavAmmoSupply.of(shooter.getUniqueId());
        if (supply != null) return supply.ammo();
        if (!(shooter instanceof Player player) || DatapackGunnerManager.isGunner(player)) return null;
        AmmoDefinition immediate = immediateScatterAmmo(player, contact);
        return immediate != null ? immediate : DatapackAmmo.lastShotAmmo(player.getUniqueId());
    }

    /** 同tickで当たる散弾は発射監視がまだ走っていないため、手にある銃の装填弾を使う。 */
    private static AmmoDefinition immediateScatterAmmo(Player player, BulletContact contact) {
        if (contact == null || !contact.bullet().getScoreboardTags().contains(MULTI_PROJECTILE_TAG)) return null;
        var manager = Bukkit.getScoreboardManager();
        var firing = manager != null ? manager.getMainScoreboard().getObjective(FIRING_OBJECTIVE) : null;
        if (firing == null) return null;
        var score = firing.getScore(player.getName());
        if (!score.isScoreSet() || score.getScore() < 0) return null;

        ItemStack gun = player.getInventory().getItemInMainHand();
        DatapackGunProfile profile = DatapackGunCatalog.get(DatapackGunCatalog.gunIdOf(gun));
        if (profile == null || profile.projectilesPerShot() <= 1) return null;
        return ItemRegistry.getAmmo(DatapackAmmo.loadedAmmoId(gun));
    }

    private static boolean isDatapackBullet(EntityDamageEvent event) {
        return DATAPACK_BULLET_DAMAGE.equals(event.getDamageSource().getDamageType().getKey());
    }

    private static String hitLocation(LivingEntity victim, Vector entry) {
        if (victim.getScoreboardTags().contains(CRIT_HIT_TAG)) {
            if (entry != null && BulletDamageModel.FACE.equals(BulletDamageModel.hitLocation(victim, entry))) {
                return BulletDamageModel.FACE;
            }
            return BulletDamageModel.HEAD;
        }
        if (entry == null) return BulletDamageModel.BODY;
        String location = BulletDamageModel.hitLocation(victim, entry);
        // 頭はデータパックの判定 (CRIT_HIT) に合わせる。付いていなければ胴として扱う
        return BulletDamageModel.HEAD.equals(location) ? BulletDamageModel.BODY : location;
    }

    private record BulletContact(Vector entry, Vector direction, Marker bullet) {
    }

    /** 処理中の弾の箱への入射点と進行方向。軌道が箱を通らない場合は部位だけを推定する。 */
    private static BulletContact bulletContact(LivingEntity victim) {
        Collection<Marker> bullets = victim.getWorld().getNearbyEntitiesByType(Marker.class, victim.getLocation(), BULLET_SEARCH_RADIUS,
                marker -> marker.getScoreboardTags().contains(BULLET_TAG) && marker.getScoreboardTags().contains(HIT_ENTITY_TAG));
        if (bullets.isEmpty()) return null;

        BoundingBox box = victim.getBoundingBox().clone().expand(HITBOX_MARGIN);
        Vector center = box.getCenter();
        // 処理中の弾は1発のはずだが、念のため弾道が箱を通る弾のうち一番近いものを使う
        BulletContact bestContact = null;
        double bestDistance = Double.MAX_VALUE;
        Marker nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Marker bullet : bullets) {
            Vector position = bullet.getLocation().toVector();
            double distance = position.distanceSquared(center);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = bullet;
            }
            Vector direction = bullet.getLocation().getDirection();
            // 弾はこの tick に箱を通り抜けているので、手前から進行方向へ線を引いて最初に箱に入った点を取る
            Vector origin = position.clone().subtract(direction.clone().multiply(BULLET_SEARCH_RADIUS * 2));
            RayTraceResult trace = box.rayTrace(origin, direction, BULLET_SEARCH_RADIUS * 4);
            if (trace != null && distance < bestDistance) {
                bestDistance = distance;
                bestContact = new BulletContact(trace.getHitPosition(), direction, bullet);
            }
        }
        if (bestContact != null) return bestContact;
        // 弾道が曲がっていて線が箱を外れた時は、一番近い弾に一番近い箱の上の点
        Vector position = nearest.getLocation().toVector();
        return new BulletContact(new Vector(
                Math.max(box.getMinX(), Math.min(box.getMaxX(), position.getX())),
                Math.max(box.getMinY(), Math.min(box.getMaxY(), position.getY())),
                Math.max(box.getMinZ(), Math.min(box.getMaxZ(), position.getZ()))), null, nearest);
    }
}

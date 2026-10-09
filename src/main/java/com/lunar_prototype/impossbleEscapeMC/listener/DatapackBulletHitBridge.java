package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Toi's Armoryデータパック銃の命中に、プラグイン銃と同じ部位判定・防具の貫通判定を入れ、{@link BulletHitEvent} として通知する。
 *
 * データパックには手を入れず、データパックの {@code damage} コマンドで起きるダメージイベントの中で判断する。
 * <ul>
 *   <li>頭: データパックは当たり判定の上0.45ブロックへの命中に CRIT_HIT タグを付け、頭用のダメージで撃つ。
 *       タグは damage コマンドの実行中も付いたままなので、それで判断する (頭の倍率はデータパック側で入っている)</li>
 *   <li>胴・腕・足: ダメージを与えている最中の弾 (BULLET と HIT_ENTITY タグの付いた marker) は、その tick の移動先に
 *       進行方向を向いて置かれている。その位置と向きから当たり判定の箱への入射点を逆算する</li>
 * </ul>
 * 基礎ダメージはデータパックの値 (距離による減衰・頭の倍率込み) のまま、足は×0.6、防具を貫通しなければ×0.15 にし、
 * バニラの防具による軽減は外す (プラグイン銃と同じく防具クラスで判断するため)。
 */
public class DatapackBulletHitBridge implements Listener {

    private static final NamespacedKey DATAPACK_BULLET_DAMAGE = new NamespacedKey("toisarm", "bullet");
    /** データパックが頭への命中に付けるタグ */
    private static final String CRIT_HIT_TAG = "CRIT_HIT";
    /** ダメージを与えている最中の弾に付いているタグ */
    private static final String BULLET_TAG = "BULLET";
    private static final String HIT_ENTITY_TAG = "HIT_ENTITY";
    /** 弾を探す範囲。弾は1tickにこれより長くは進まない */
    private static final double BULLET_SEARCH_RADIUS = 64.0;
    /** データパックの当たり判定は当たり判定の箱を0.1広げた範囲 */
    private static final double HITBOX_MARGIN = 0.1;
    /** データパック銃は弾薬の種類を持たないため、種類が入るまではクラス1の弾として扱う */
    private static final int DATAPACK_AMMO_CLASS = 1;
    private static final double LEGS_DAMAGE_MULTIPLIER = 0.6;
    private static final double NOT_PENETRATED_DAMAGE_MULTIPLIER = 0.15;
    /** プレイヤーの弾がSCAVに当たった時の制圧 (プラグイン銃の BulletTask と同じ値) */
    private static final float HIT_SUPPRESSION = 0.5f;

    private record HitResult(String hitLocation, boolean penetrated, double rawDamage) {
    }

    /** LOW で決めた部位・貫通を MONITOR の通知で使う (ダメージイベントは入れ子にならないため1件で足りる) */
    private EntityDamageEvent pendingEvent;
    private HitResult pendingResult;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageCalculate(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;

        double rawDamage = event.getDamage();
        String hitLocation = hitLocation(victim);
        int armorClass = BulletDamageModel.armorClass(victim, hitLocation);
        boolean penetrated = BulletDamageModel.penetrates(DATAPACK_AMMO_CLASS, armorClass);

        double damage = rawDamage;
        if (BulletDamageModel.LEGS.equals(hitLocation)) damage *= LEGS_DAMAGE_MULTIPLIER;
        if (!penetrated) damage *= NOT_PENETRATED_DAMAGE_MULTIPLIER;

        event.setDamage(damage);
        // バニラの防具・耐性などによる軽減を外す (GunListener#onEntityDamage の bypass_armor と同じ)
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }

        pendingEvent = event;
        pendingResult = new HitResult(hitLocation, penetrated, rawDamage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;
        HitResult result = event == pendingEvent ? pendingResult : null;
        pendingEvent = null;
        pendingResult = null;
        if (result == null) return;

        // SCAVのFakePlayerが撃った場合は、撃ったSCAV本体を射手とする
        Entity shooter = DatapackGunnerManager.resolveShooter(event.getDamager());
        if (!(shooter instanceof LivingEntity livingShooter)) return;

        if (shooter instanceof Player) {
            ScavController controller = ScavSpawner.getController(victim.getUniqueId());
            if (controller != null) controller.addSuppression(HIT_SUPPRESSION);
        }

        Bukkit.getPluginManager().callEvent(new BulletHitEvent(
                victim, livingShooter, event.getFinalDamage(), result.hitLocation(), result.penetrated(),
                DATAPACK_AMMO_CLASS, result.rawDamage()));
    }

    private static boolean isDatapackBullet(EntityDamageEvent event) {
        return DATAPACK_BULLET_DAMAGE.equals(event.getDamageSource().getDamageType().getKey());
    }

    private static String hitLocation(LivingEntity victim) {
        if (victim.getScoreboardTags().contains(CRIT_HIT_TAG)) return BulletDamageModel.HEAD;
        Vector entry = bulletEntryPoint(victim);
        if (entry == null) return BulletDamageModel.BODY;
        String location = BulletDamageModel.hitLocation(victim, entry);
        // 頭はデータパックの判定 (CRIT_HIT) に合わせる。付いていなければ胴として扱う
        return BulletDamageModel.HEAD.equals(location) ? BulletDamageModel.BODY : location;
    }

    /** ダメージを与えている最中の弾の位置と進行方向から、当たり判定の箱への入射点を求める */
    private static Vector bulletEntryPoint(LivingEntity victim) {
        Collection<Marker> bullets = victim.getWorld().getNearbyEntitiesByType(Marker.class, victim.getLocation(), BULLET_SEARCH_RADIUS,
                marker -> marker.getScoreboardTags().contains(BULLET_TAG) && marker.getScoreboardTags().contains(HIT_ENTITY_TAG));
        if (bullets.isEmpty()) return null;

        BoundingBox box = victim.getBoundingBox().clone().expand(HITBOX_MARGIN);
        Vector center = box.getCenter();
        // 処理中の弾は1発のはずだが、念のため弾道が箱を通る弾のうち一番近いものを使う
        Vector bestEntry = null;
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
                bestEntry = trace.getHitPosition();
            }
        }
        if (bestEntry != null) return bestEntry;
        // 弾道が曲がっていて線が箱を外れた時は、一番近い弾に一番近い箱の上の点
        Vector position = nearest.getLocation().toVector();
        return new Vector(
                Math.max(box.getMinX(), Math.min(box.getMaxX(), position.getX())),
                Math.max(box.getMinY(), Math.min(box.getMaxY(), position.getY())),
                Math.max(box.getMinZ(), Math.min(box.getMaxZ(), position.getZ())));
    }
}

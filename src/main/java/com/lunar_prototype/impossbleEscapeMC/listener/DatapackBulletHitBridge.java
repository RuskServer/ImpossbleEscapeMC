package com.lunar_prototype.impossbleEscapeMC.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.ScavAmmoSupply;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.item.ArmorDurability;
import com.lunar_prototype.impossbleEscapeMC.item.factory.HelmetVisors;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Toi's Armoryデータパック銃の命中に、プラグイン銃と同じ部位判定・防具の貫通判定を入れ、{@link BulletHitEvent} として通知する。
 *
 * データパックの {@code damage} コマンドで起きるダメージイベントの中で判断する。
 * <ul>
 *   <li>頭: データパックは当たり判定の上0.45ブロックへの命中に CRIT_HIT タグを付け、頭用のダメージで撃つ。
 *       タグは damage コマンドの実行中も付いたままなので、それで判断する (倍率はプラグイン側で一度だけ掛ける)</li>
 *   <li>胴・腕・足: ダメージを与えている最中の弾 (BULLET と HIT_ENTITY タグの付いた marker) は、その tick の移動先に
 *       進行方向を向いて置かれている。その位置と向きから当たり判定の箱への入射点を逆算する。
 *       腕と胴は、入射点だけでなく胴の中心領域を弾道が横切るかで分ける</li>
 * </ul>
 * 基礎ダメージは装填弾の定義値 (散弾は弾Markerの粒数で割る)、足は×0.6、防具を貫通しなければ
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
    private static final String PLAYER_ID_OBJECTIVE = "toisarm.id";
    private static final String PROJECTILES_OBJECTIVE = "toisarm.bullet.shots";
    private static final int DEFAULT_SHOTGUN_PROJECTILES = 12;
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

    // TODO(ai-collab): 再入するFakePlayer転送でも結果を混ぜず、弾単位の命中と耐久処理を一度に制限する。
    private final Map<EntityDamageEvent, HitResult> pending = new WeakHashMap<>();
    /** PaperがlastDamageCauseを設定してから行う防具消耗だけを識別する。イベントを強参照しない。 */
    private final Map<EntityDamageEvent, Integer> completed = new WeakHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBulletAdded(EntityAddToWorldEvent event) {
        if (!(event.getEntity() instanceof Marker bullet)
                || !bullet.getScoreboardTags().contains("toisarm.bullet")
                || !bullet.getScoreboardTags().contains("BULLET_INIT")) return;
        // summonのNBTは追加イベントより前に読み込まれる。ownerのスコア転記より前なので、
        // 実行中のPLAYERと#idを照合する。距離や「直前に撃った人」からは推定しない。
        var manager = Bukkit.getScoreboardManager();
        var ids = manager != null ? manager.getMainScoreboard().getObjective(PLAYER_ID_OBJECTIVE) : null;
        var temporary = manager != null ? manager.getMainScoreboard().getObjective("_") : null;
        if (ids == null || temporary == null || !temporary.getScore("#id").isScoreSet()) return;
        int id = temporary.getScore("#id").getScore();
        for (Player player : bullet.getWorld().getPlayers()) {
            if (!player.getScoreboardTags().contains("PLAYER")) continue;
            var playerId = ids.getScore(player.getName());
            if (!playerId.isScoreSet() || playerId.getScore() != id) continue;
            Entity shooter = DatapackGunnerManager.resolveShooter(player);
            ScavAmmoSupply supply = ScavAmmoSupply.of(shooter.getUniqueId());
            AmmoDefinition ammo = supply != null ? supply.ammo()
                    : ItemRegistry.getAmmo(DatapackAmmo.loadedAmmoId(player.getInventory().getItemInMainHand()));
            var shots = manager.getMainScoreboard().getObjective("toisarm.state.projectiles_per_shot");
            var score = shots != null ? shots.getScore(player.getName()) : null;
            int pellets = score != null && score.isScoreSet() ? Math.max(1, score.getScore()) : 1;
            DatapackBulletState.capture(bullet, shooter.getUniqueId(), ammo, pellets);
            return;
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageCalculate(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;

        BulletContact contact = bulletContact(victim);
        // 本体への直撃とFakePlayerからの転送が来ても、同じ弾・本体は一度しか処理しない。
        if (contact != null && !DatapackBulletState.claim(contact.bullet(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        DatapackBulletState.Shot shot = contact != null ? DatapackBulletState.read(contact.bullet()) : null;
        int ammoClass = shot != null ? shot.ammoClass() : DATAPACK_AMMO_CLASS;
        // 更新前から飛んでいた弾は発射情報を復元できない。射手の現在の弾を流用しない。
        double rawDamage = shot != null ? shot.pelletDamage()
                : BulletDamageModel.baseDamage(null, projectilesPerShot(contact));
        // 処理中の弾Markerから入射方向を読み、腕と胴の区別にも使う。
        Vector incoming = contact != null ? contact.direction() : null;
        String hitLocation = hitLocation(victim, contact != null ? contact.entry() : null, incoming);
        BulletDamageModel.ArmorPiece armor = BulletDamageModel.armorPiece(victim, hitLocation);
        int armorClass = armor != null ? armor.armorClass() : 0;
        Entity shooter = DatapackGunnerManager.resolveShooter(event.getDamager());
        UUID shooterId = shot != null ? shot.shooter()
                : shooter != null ? shooter.getUniqueId() : event.getDamager().getUniqueId();
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

        pending.put(event, new HitResult(hitLocation, penetrated, rawDamage, ammoClass, armor));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!isDatapackBullet(event)) return;
        HitResult result = pending.remove(event);
        if (result == null || event.isCancelled()) return;
        completed.put(event, Bukkit.getCurrentTick());

        // 防護を判定した一着だけを消耗させる。プレイヤーとSCAVで同じ規則を使う。
        int wear = armorWear(result.penetrated(), event.getFinalDamage());
        if (result.armor() != null && wear > 0) wearArmor(victim, result.armor(), wear);

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

    private static void wearArmor(LivingEntity victim, BulletDamageModel.ArmorPiece armor, int amount) {
        ItemStack worn = armor.visor() ? HelmetVisors.wear(armor.item(), amount, victim instanceof Player)
                : ArmorDurability.wear(armor.item(), amount, victim instanceof Player);
        if (worn != null && victim.getEquipment() != null) {
            victim.getEquipment().setItem(armor.slot(), worn);
        }
    }

    /** 貫通は従来のバニラ相当、防いだ弾は従来の固定消耗。ゼロダメージの貫通は消耗なし。 */
    static int armorWear(boolean penetrated, double damage) {
        if (!penetrated) return ArmorDurability.BLOCKED_WEAR;
        return Double.isFinite(damage) && damage > 0 ? Math.max(1, (int) (damage / 4.0)) : 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaArmorDamage(PlayerItemDamageEvent event) {
        EntityDamageEvent cause = event.getPlayer().getLastDamageCause();
        Integer tick = completed.get(cause);
        if (tick == null || tick != Bukkit.getCurrentTick() || cause.isCancelled() || !isDatapackBullet(cause)) return;
        // Paperは元のダメージイベントが戻った後に全防具を削る。選択した防具は既に手動消耗済み。
        // 手持ちの道具や、別のダメージ原因による耐久消費は取り消さない。
        for (ItemStack armor : event.getPlayer().getInventory().getArmorContents()) {
            if (armor != null && armor.equals(event.getItem())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** 発射時に弾Markerへ記録された粒数を使う。銃を持ち替えた後の着弾でも粒数は変わらない。 */
    private static int projectilesPerShot(BulletContact contact) {
        if (contact != null) {
            var manager = Bukkit.getScoreboardManager();
            var objective = manager != null ? manager.getMainScoreboard().getObjective(PROJECTILES_OBJECTIVE) : null;
            if (objective != null) {
                var score = objective.getScoreFor(contact.bullet());
                if (score.isScoreSet() && score.getScore() > 0) return score.getScore();
            }
            if (contact.bullet().getScoreboardTags().contains(MULTI_PROJECTILE_TAG)) return DEFAULT_SHOTGUN_PROJECTILES;
        }
        return 1;
    }

    private static boolean isDatapackBullet(EntityDamageEvent event) {
        return DATAPACK_BULLET_DAMAGE.equals(event.getDamageSource().getDamageType().getKey());
    }

    private static String hitLocation(LivingEntity victim, Vector entry, Vector incoming) {
        if (victim.getScoreboardTags().contains(CRIT_HIT_TAG)) {
            if (entry != null && BulletDamageModel.FACE.equals(BulletDamageModel.hitLocation(victim, entry))) {
                return BulletDamageModel.FACE;
            }
            return BulletDamageModel.HEAD;
        }
        if (entry == null) return BulletDamageModel.BODY;
        String location = BulletDamageModel.hitLocation(victim, entry, incoming);
        // 頭はデータパックの判定 (CRIT_HIT) に合わせる。付いていなければ胴として扱う
        return BulletDamageModel.HEAD.equals(location) || BulletDamageModel.FACE.equals(location)
                ? BulletDamageModel.BODY : location;
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

package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Toi's Armoryデータパック銃の命中を、プラグイン銃と同じ {@link BulletHitEvent} として通知する。
 *
 * データパック銃は {@link BulletTask} を通らないため、これが無いと被弾時の負傷・アドレナリン・
 * SCAVの命中フィードバックが一切動かない。データパック銃は部位・貫通の情報を持たないため、
 * 胴体への貫通弾として扱う。
 */
public class DatapackBulletHitBridge implements Listener {

    private static final NamespacedKey DATAPACK_BULLET_DAMAGE = new NamespacedKey("toisarm", "bullet");
    private static final String HIT_LOCATION = "body";
    /** データパック銃は弾薬クラスを持たない */
    private static final int UNKNOWN_AMMO_CLASS = 0;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!DATAPACK_BULLET_DAMAGE.equals(event.getDamageSource().getDamageType().getKey())) return;

        // SCAVのFakePlayerが撃った場合は、撃ったSCAV本体を射手とする
        Entity shooter = DatapackGunnerManager.resolveShooter(event.getDamager());
        if (!(shooter instanceof LivingEntity livingShooter)) return;

        Bukkit.getPluginManager().callEvent(new BulletHitEvent(
                victim, livingShooter, event.getFinalDamage(), HIT_LOCATION, true, UNKNOWN_AMMO_CLASS));
    }
}

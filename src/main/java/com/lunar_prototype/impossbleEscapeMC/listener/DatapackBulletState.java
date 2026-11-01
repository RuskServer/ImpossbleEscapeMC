package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Marker;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** 弾ごとの発射時情報と、実体に正規化した命中済み対象。Markerと一緒に破棄される。 */
final class DatapackBulletState {
    private static final NamespacedKey DAMAGE = key("shot_damage");
    private static final NamespacedKey AMMO_CLASS = key("shot_ammo_class");
    private static final NamespacedKey SHOOTER = key("shot_shooter");
    private static final NamespacedKey PELLETS = key("shot_pellets");
    private static final NamespacedKey VICTIMS = key("shot_victims");

    record Shot(UUID shooter, double damage, int ammoClass, int pellets) {
        double pelletDamage() { return damage / pellets; }
    }

    private DatapackBulletState() { }

    private static NamespacedKey key(String value) {
        return new NamespacedKey("impossbleescapemc", value);
    }

    /** 未装填も含め、生成時に一度だけ固定する。射手の持ち替えや供給弾の変更を追わない。 */
    static void capture(Marker bullet, UUID shooter, AmmoDefinition ammo, int pellets) {
        if (read(bullet) != null) return;
        var data = bullet.getPersistentDataContainer();
        data.set(DAMAGE, PersistentDataType.DOUBLE, BulletDamageModel.baseDamage(ammo));
        data.set(AMMO_CLASS, PersistentDataType.INTEGER, ammo != null ? ammo.ammoClass : 1);
        data.set(SHOOTER, PersistentDataType.STRING, shooter.toString());
        data.set(PELLETS, PersistentDataType.INTEGER, Math.max(1, pellets));
    }

    static Shot read(Marker bullet) {
        var data = bullet.getPersistentDataContainer();
        Double damage = data.get(DAMAGE, PersistentDataType.DOUBLE);
        Integer ammoClass = data.get(AMMO_CLASS, PersistentDataType.INTEGER);
        Integer pellets = data.get(PELLETS, PersistentDataType.INTEGER);
        String shooter = data.get(SHOOTER, PersistentDataType.STRING);
        if (damage == null || !Double.isFinite(damage) || damage <= 0 || ammoClass == null
                || pellets == null || pellets < 1 || shooter == null) return null;
        try {
            return new Shot(UUID.fromString(shooter), damage, ammoClass, pellets);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** 本体とFakePlayerの転送は同じvictim UUIDで呼ぶ。別Markerの散弾・貫通先は独立する。 */
    static boolean claim(Marker bullet, UUID victim) {
        var data = bullet.getPersistentDataContainer();
        String key = victim + ";";
        String victims = data.getOrDefault(VICTIMS, PersistentDataType.STRING, "");
        if (victims.contains(key)) return false;
        data.set(VICTIMS, PersistentDataType.STRING, victims + key);
        return true;
    }
}

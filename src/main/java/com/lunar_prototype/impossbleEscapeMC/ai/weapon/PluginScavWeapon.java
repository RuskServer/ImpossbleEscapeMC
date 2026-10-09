package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/** プラグイン独自の銃 (items/*.yml のGUN)。データパック銃を持っていないSCAV用 */
public final class PluginScavWeapon implements ScavWeapon {

    private final Mob scav;
    private final ItemDefinition def;
    private final GunListener gunListener;

    public PluginScavWeapon(Mob scav, ItemDefinition def, GunListener gunListener) {
        this.scav = scav;
        this.def = def;
        this.gunListener = gunListener;
    }

    @Override public String id() { return def.id; }
    @Override public double rpm() { return def.gunStats.rpm; }
    @Override public boolean isAutomatic() { return "AUTO".equalsIgnoreCase(def.gunStats.fireMode); }
    @Override public boolean isManualAction() { return "PUMP_ACTION".equalsIgnoreCase(def.gunStats.boltType); }
    @Override public int magazineSize() { return def.gunStats.magSize; }
    @Override public boolean isReloading() { return false; }

    @Override
    public double preferredRange() {
        if (def.gunStats.pelletCount > 1 || isManualAction()) return 8.0; // ショットガン・ポンプ
        if (!isAutomatic()) return 22.0;
        return def.gunStats.rpm >= 800 ? 10.0 : 16.0;
    }

    @Override
    public int ammo() {
        ItemStack gun = scav.getEquipment() != null ? scav.getEquipment().getItemInMainHand() : null;
        if (gun == null || !gun.hasItemMeta()) return 0;
        return gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
    }

    @Override
    public boolean fire(double inaccuracy) {
        gunListener.executeMobShoot(scav, def.gunStats, 1, inaccuracy);
        return true;
    }

    @Override
    public void release() {
    }
}

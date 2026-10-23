package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.datapack.DatapackGunCondition;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * トレーダーの修理: 何を直せるか・修理代・直し方 (修理の画面 {@link RepairDialog} が使う)。
 * <ul>
 *   <li>直せる物: 防具 (アイテム定義に耐久と防具の性能がある物) と銃 (データパック銃。耐久とジャム)。
 *       アーマーはアーマーを直せる店、銃は武器を直せる店で</li>
 *   <li>修理代: 完品の修理の基準額 × 減った耐久の割合 × スキルの倍率 (1₽ 単位)。防具の基準額はアーマークラス、
 *       データパック銃は Broker の買取の基準価格 (取り扱っていない銃は武器の標準額)</li>
 * </ul>
 */
final class Repairs {

    /**
     * 修理の見積もり。durability / max は今の耐久と最大、jammed はジャム (銃だけ)、allowed はこの店で直せるか
     * (直せなければ reason)、cost は修理代 (₽)
     */
    record Quote(int durability, int max, boolean jammed, boolean weapon, boolean allowed, String reason, long cost) {

        /** 耐久の残りの割合 (0..1) */
        double ratio() {
            return max > 0 ? Math.max(0, Math.min(1, (double) durability / max)) : 1;
        }
    }

    private Repairs() {
    }

    /** その物の修理の見積もり。直す物でない (耐久が無い・減っていない・ジャムでもない) 時は null */
    static Quote quote(TraderModule module, TraderDefinition trader, UUID player, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String gunId = DatapackGunCatalog.gunIdOf(item);
        if (gunId != null) {
            int durability = DatapackGunCondition.durability(item);
            int max = DatapackGunCondition.MAX_DURABILITY;
            boolean jammed = DatapackGunCondition.isJammed(item);
            if (durability >= max && !jammed) return null;
            double base = datapackGunBasePrice(module, gunId);
            return quote(trader, player, durability, max, jammed, true, base);
        }
        ItemMeta meta = item.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = itemId != null ? ItemRegistry.get(itemId) : null;
        if (def == null || def.maxDurability <= 0) return null;
        boolean weapon = "GUN".equalsIgnoreCase(def.type);
        if (def.armorStats == null && !weapon) return null;
        int durability = meta.getPersistentDataContainer().getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        if (durability >= def.maxDurability) return null;
        double base = def.armorStats != null ? TraderModule.getBaseArmorRepairCost(def.armorStats.armorClass)
                : TraderModule.getBaseWeaponRepairCost(def.rarity);
        return quote(trader, player, durability, def.maxDurability, false, weapon, base);
    }

    private static Quote quote(TraderDefinition trader, UUID player, int durability, int max, boolean jammed, boolean weapon, double base) {
        boolean allowed = weapon ? trader.canRepairWeapon : trader.canRepairArmor;
        String reason = allowed ? null : weapon ? "この店では武器を修理できません" : "この店ではアーマーを修理できません";
        double damage = 1.0 - Math.max(0, Math.min(1, (double) durability / max));
        long cost = Math.round(base * damage * TraderModule.repairMultiplier(player));   // スキルで安くなる
        return new Quote(durability, max, jammed, weapon, allowed, reason, cost);
    }

    /** 直した物 (耐久を最大に、ジャムを解く。説明の耐久の表示も直す) */
    static ItemStack repair(ItemStack item) {
        if (DatapackGunCatalog.gunIdOf(item) != null) return DatapackGunCondition.repair(item);
        ItemStack repaired = item.clone();
        ItemMeta meta = repaired.getItemMeta();
        String itemId = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = itemId != null ? ItemRegistry.get(itemId) : null;
        if (def == null) return repaired;
        meta.getPersistentDataContainer().set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        if ("GUN".equalsIgnoreCase(def.type)) meta.getPersistentDataContainer().set(PDCKeys.JAMMED, PDCKeys.BOOLEAN, (byte) 0);
        repaired.setItemMeta(meta);
        ItemFactory.updateLore(repaired);
        return repaired;
    }

    /** Broker の買取の基準価格を、データパック銃の完品の修理の基準額にする (取り扱っていない銃は武器の標準額) */
    private static double datapackGunBasePrice(TraderModule module, String gunId) {
        TraderDefinition broker = module.getTrader("broker");
        if (broker != null) {
            for (TraderItem offer : broker.items) {
                if (offer.itemId.equals(gunId) && offer.price > 0) return offer.price;
            }
        }
        return TraderModule.getBaseWeaponRepairCost(1);
    }
}

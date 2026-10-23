package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 防具の耐久値。PDC の値を基本にしつつ、バニラの耐久バーが減っている物 (古い防具・被弾) は
 * バーの方も見て、低い方を今の耐久とする。
 */
public final class ArmorDurability {

    /** 弾を1発止めるたびに減る耐久値 */
    public static final int BLOCKED_WEAR = 3;

    private ArmorDurability() {
    }

    public static ItemDefinition definition(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        return id != null ? ItemRegistry.get(id) : null;
    }

    /** 今の耐久。PDC とバニラの耐久バーの低い方。 */
    public static int current(ItemStack item, ItemDefinition def) {
        if (item == null || def == null || def.maxDurability <= 0) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        int durability = meta.getPersistentDataContainer()
                .getOrDefault(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        Integer barDamage = item.getData(DataComponentTypes.DAMAGE);
        if (barDamage != null) {
            durability = Math.min(durability, def.maxDurability - barDamage);
        }
        return Math.max(0, Math.min(def.maxDurability, durability));
    }

    /**
     * 耐久を amount 減らした防具を返す。減らない (防具でない・もう0) 時は null。
     * updateLore が true なら、耐久の割合が変わった時だけ説明の表示も直す。
     */
    public static ItemStack wear(ItemStack item, int amount, boolean updateLore) {
        ItemDefinition def = definition(item);
        if (def == null || def.armorStats == null || def.maxDurability <= 0 || amount <= 0) return null;
        int before = current(item, def);
        int after = Math.max(0, before - amount);
        if (after == before) return null;

        ItemStack updated = item.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(PDCKeys.DURABILITY, PDCKeys.INTEGER, after);
        updated.setItemMeta(meta);
        updated.setData(DataComponentTypes.MAX_DAMAGE, def.maxDurability);
        updated.setData(DataComponentTypes.DAMAGE, def.maxDurability - after);

        if (updateLore && (after == 0
                || before * 100 / def.maxDurability != after * 100 / def.maxDurability)) {
            return ItemFactory.updateLore(updated);
        }
        return updated;
    }

    /** 耐久を最大に戻した防具を返す。防具でなければ null。 */
    public static ItemStack repair(ItemStack item) {
        ItemDefinition def = definition(item);
        if (def == null || def.armorStats == null || def.maxDurability <= 0) return null;
        ItemStack repaired = item.clone();
        ItemMeta meta = repaired.getItemMeta();
        if (meta == null) return null;
        meta.getPersistentDataContainer().set(PDCKeys.DURABILITY, PDCKeys.INTEGER, def.maxDurability);
        repaired.setItemMeta(meta);
        repaired.setData(DataComponentTypes.MAX_DAMAGE, def.maxDurability);
        repaired.setData(DataComponentTypes.DAMAGE, 0);
        return ItemFactory.updateLore(repaired);
    }
}

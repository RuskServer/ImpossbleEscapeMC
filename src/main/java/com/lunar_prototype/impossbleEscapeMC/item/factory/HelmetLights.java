package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.Flashlight;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public final class HelmetLights {
    private HelmetLights() { }

    public static boolean fits(ItemDefinition helmet) {
        return helmet != null && helmet.armorStats != null && !helmet.armorStats.flashlightModels.isEmpty();
    }

    public static boolean isAttached(ItemStack helmet) {
        return helmet != null && helmet.hasItemMeta()
                && helmet.getItemMeta().getPersistentDataContainer().has(PDCKeys.HELMET_FLASHLIGHT, PDCKeys.BYTE_ARRAY)
                && fits(HelmetVisors.helmetDef(helmet));
    }

    static int weight() {
        ItemDefinition light = ItemRegistry.get(Flashlight.ITEM_ID);
        return light == null ? 0 : light.weight;
    }

    public static ItemStack attachedItem(ItemStack helmet) {
        if (!isAttached(helmet)) return null;
        byte[] data = helmet.getItemMeta().getPersistentDataContainer().get(PDCKeys.HELMET_FLASHLIGHT, PDCKeys.BYTE_ARRAY);
        try {
            ItemStack item = ItemStack.deserializeBytes(data);
            return Flashlight.isFlashlight(item) && item.getAmount() == 1 ? item : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static ItemStack withLight(ItemStack helmet, ItemStack light) {
        if (!fits(HelmetVisors.helmetDef(helmet)) || isAttached(helmet) || !Flashlight.isFlashlight(light)) return null;
        ItemStack result = helmet.clone();
        // 取り外した時も元のフラッシュライトのデータを引き継ぐ。
        saveAttachment(result, light.asOne());
        HelmetVisors.refreshAppearance(result);
        return result;
    }

    /** 装着中もレイドへの持ち込み印などを単品と同じように更新する。 */
    public static void updateAttachment(ItemStack helmet, Consumer<ItemStack> update) {
        ItemStack light = attachedItem(helmet);
        if (light == null) return;
        update.accept(light);
        saveAttachment(helmet, light);
    }

    private static void saveAttachment(ItemStack helmet, ItemStack light) {
        var meta = helmet.getItemMeta();
        meta.getPersistentDataContainer().set(PDCKeys.HELMET_FLASHLIGHT, PDCKeys.BYTE_ARRAY, light.serializeAsBytes());
        helmet.setItemMeta(meta);
    }

    public static ItemStack withoutLight(ItemStack helmet) {
        ItemStack result = helmet.clone();
        var meta = result.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.remove(PDCKeys.HELMET_FLASHLIGHT);
        result.setItemMeta(meta);
        HelmetVisors.refreshAppearance(result);
        return result;
    }
}

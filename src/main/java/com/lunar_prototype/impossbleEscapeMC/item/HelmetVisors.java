package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.key.Key;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * ヘルメットに付けるバイザー。付けられるバイザーはヘルメットの {@link ArmorStats#visors}、
 * 付けたバイザーはヘルメットの PDC (helmet_visor) に書く。バイザーを付けたヘルメットは
 * <ul>
 *   <li>見た目が {@link ArmorStats#visorCustomModelData} のモデルになる</li>
 *   <li>重さにバイザーの重さが足される</li>
 *   <li>かぶっている間、ScreenEffectService がバイザー越しの見え方 ({@link VisorStats#postEffect}) を画面にかける</li>
 * </ul>
 * 付け外しはアタッチメント画面 (gui.HelmetAttachmentGUI) で行う
 */
public final class HelmetVisors {

    private HelmetVisors() {
    }

    /** バイザーを付けられるヘルメットなら、その定義。違えばnull */
    public static ItemDefinition helmetDef(ItemStack item) {
        ItemDefinition def = ItemRegistry.get(itemIdOf(item));
        return def != null && def.armorStats != null && def.armorStats.visors != null && !def.armorStats.visors.isEmpty()
                ? def : null;
    }

    /** バイザーのアイテムなら、その定義。違えばnull */
    public static ItemDefinition visorDef(ItemStack item) {
        ItemDefinition def = ItemRegistry.get(itemIdOf(item));
        return def != null && def.visorStats != null ? def : null;
    }

    /** ヘルメットに付いているバイザーのアイテムID。付いていなければnull */
    public static String visorOf(ItemStack helmet) {
        if (helmet == null || !helmet.hasItemMeta()) return null;
        String id = helmet.getItemMeta().getPersistentDataContainer().get(PDCKeys.HELMET_VISOR, PDCKeys.STRING);
        return id != null && ItemRegistry.get(id) != null ? id : null;
    }

    /** そのヘルメットにそのバイザーを付けられるか */
    public static boolean fits(ItemDefinition helmet, String visorId) {
        return helmet != null && helmet.armorStats != null && helmet.armorStats.visors != null
                && helmet.armorStats.visors.contains(visorId);
    }

    /** バイザーを付けたヘルメット (元のヘルメットは変えない) */
    public static ItemStack withVisor(ItemStack helmet, String visorId) {
        ItemStack result = helmet.clone();
        apply(result, visorId);
        return result;
    }

    /** バイザーを外したヘルメット (元のヘルメットは変えない) */
    public static ItemStack withoutVisor(ItemStack helmet) {
        ItemStack result = helmet.clone();
        apply(result, null);
        return result;
    }

    /** かぶっているヘルメットのバイザーのポストエフェクト。バイザーが無ければnull */
    public static Key postEffectOf(ItemStack helmet) {
        String visorId = visorOf(helmet);
        ItemDefinition visor = visorId != null ? ItemRegistry.get(visorId) : null;
        if (visor == null || visor.visorStats == null || visor.visorStats.postEffect == null) return null;
        return Key.key(visor.visorStats.postEffect);
    }

    /** バイザーの有無に合わせて、モデル・重さ・説明を書き直す */
    private static void apply(ItemStack helmet, String visorId) {
        ItemDefinition def = helmetDef(helmet);
        if (def == null) return;
        ItemDefinition visor = visorId != null ? ItemRegistry.get(visorId) : null;
        ItemMeta meta = helmet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (visor != null) {
            pdc.set(PDCKeys.HELMET_VISOR, PDCKeys.STRING, visor.id);
        } else {
            pdc.remove(PDCKeys.HELMET_VISOR);
        }

        int model = visor != null ? def.armorStats.visorCustomModelData : def.armorStats.customModelData;
        meta.setCustomModelData(model != 0 ? model : null);
        pdc.set(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, def.weight + (visor != null ? visor.weight : 0));

        helmet.setItemMeta(meta);
        ItemFactory.updateLore(helmet);
    }

    private static String itemIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
    }
}

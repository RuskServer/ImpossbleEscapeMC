package com.lunar_prototype.impossbleEscapeMC.item.factory;

import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.stats.ArmorStats;
import com.lunar_prototype.impossbleEscapeMC.item.stats.VisorStats;

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

    /** 実物の残耐久を引き継いで装着する。 */
    public static ItemStack withVisor(ItemStack helmet, ItemStack visor) {
        ItemDefinition def = visorDef(visor);
        if (def == null || !fits(helmetDef(helmet), def.id)) return helmet.clone();
        ItemStack result = helmet.clone();
        apply(result, def.id, durability(visor, def));
        return result;
    }

    /** 同じキーを単体と装着先に保存する。旧アイテムにキーがなければ満タン。0は壊れた状態のまま保持する。 */
    public static int durability(ItemStack item, ItemDefinition visor) {
        if (item == null || visor == null || visor.visorStats == null || !item.hasItemMeta()) return 0;
        int max = Math.max(0, visor.visorStats.maxDurability);
        int value = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.VISOR_DURABILITY, PDCKeys.INTEGER, max);
        return Math.clamp(value, 0, max);
    }

    /** 取り外し・交換・画面表示用の実物。装着中の残耐久を戻す。 */
    public static ItemStack attachedItem(ItemStack helmet) {
        String id = visorOf(helmet);
        ItemDefinition def = id != null ? ItemRegistry.get(id) : null;
        if (def == null || !fits(helmetDef(helmet), id)) return null;
        ItemStack item = ItemFactory.create(id);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PDCKeys.VISOR_DURABILITY, PDCKeys.INTEGER, durability(helmet, def));
        item.setItemMeta(meta);
        return ItemFactory.updateLore(item);
    }

    /** 顔に当たった弾で消耗するのはバイザーだけ。本体の耐久・バーは変えない。 */
    public static ItemStack wear(ItemStack helmet, int amount, boolean updateLore) {
        String id = visorOf(helmet);
        ItemDefinition visor = id != null ? ItemRegistry.get(id) : null;
        if (visor == null || visor.visorStats == null || !fits(helmetDef(helmet), id) || amount <= 0) return null;
        int before = durability(helmet, visor), after = Math.max(0, before - amount);
        if (before == after) return null;
        ItemStack result = helmet.clone();
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().set(PDCKeys.VISOR_DURABILITY, PDCKeys.INTEGER, after);
        result.setItemMeta(meta);
        return updateLore ? ItemFactory.updateLore(result) : result;
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
        ItemDefinition visor = visorId != null ? ItemRegistry.get(visorId) : null;
        int durability = visor != null && visor.visorStats != null ? visor.visorStats.maxDurability : 0;
        if (java.util.Objects.equals(visorId, visorOf(helmet))) durability = durability(helmet, visor);
        apply(helmet, visorId, durability);
    }

    private static void apply(ItemStack helmet, String visorId, int durability) {
        ItemDefinition def = helmetDef(helmet);
        if (def == null) return;
        ItemDefinition visor = visorId != null ? ItemRegistry.get(visorId) : null;
        ItemMeta meta = helmet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (visor != null && visor.visorStats != null && fits(def, visor.id)) {
            pdc.set(PDCKeys.HELMET_VISOR, PDCKeys.STRING, visor.id);
            pdc.set(PDCKeys.VISOR_DURABILITY, PDCKeys.INTEGER, Math.clamp(durability, 0, Math.max(0, visor.visorStats.maxDurability)));
        } else {
            pdc.remove(PDCKeys.HELMET_VISOR);
            pdc.remove(PDCKeys.VISOR_DURABILITY);
            visor = null;
        }

        int model = visor == null ? def.armorStats.customModelData
                : visor.visorStats != null && visor.visorStats.helmetCustomModelData != 0
                ? visor.visorStats.helmetCustomModelData : def.armorStats.visorCustomModelData;
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

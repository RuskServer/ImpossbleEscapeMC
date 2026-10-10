package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * アイテム1個の重さとサイズ。重量・コストスロット・バックパック・リグは、すべてここから読む。
 * プラグインのアイテムはアイテムに書いた値 (PDC)、データパックの銃は {@link DatapackItemSpecs} の表。
 * アタッチメントは既存のプラグイン定義を優先し、未定義なら表の値を使う。
 * (データパック銃は、付いているアタッチメントの重さも足す)。どこで手に入れた物でも同じ値になる
 */
public final class ItemWeights {

    private ItemWeights() {
    }

    /** 1個の重さ (グラム) */
    public static int weightOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        String datapackAttachment = DatapackAttachments.attachmentIdOf(item);
        if (datapackAttachment != null) return AttachmentItems.weight(datapackAttachment);
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer weight = pdc.get(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER);
        if (weight != null) return weight;

        String gunId = DatapackGunCatalog.gunIdOf(item);
        if (gunId != null) {
            int total = DatapackItemSpecs.gun(gunId).weightGrams();
            for (String attachmentId : DatapackAttachments.attachedIds(item)) {
                total += AttachmentItems.weight(attachmentId);
            }
            return total;
        }
        return 0;
    }

    /** 全部の重さ (グラム、個数ぶん) */
    public static int totalWeightOf(ItemStack item) {
        return item == null ? 0 : weightOf(item) * item.getAmount();
    }

    /** インベントリで占めるスロット数 */
    public static int costOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 1;
        String attachmentId = DatapackAttachments.attachmentIdOf(item);
        if (attachmentId != null) return AttachmentItems.cost(attachmentId);
        Integer cost = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_COST, PDCKeys.INTEGER);
        if (cost != null) return cost;
        String gunId = DatapackGunCatalog.gunIdOf(item);
        return gunId != null ? DatapackItemSpecs.gun(gunId).cost() : 1;
    }
}

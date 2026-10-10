package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.util.DatapackFunctionUtil;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * IDからゲームのアイテムを作る (ルート表・トレーダーなど、IDだけで品物を書く所で使う)。
 * IDは次の順に探す: プラグインのアイテム・弾 → データパックの銃 → アタッチメント ({@link AttachmentItems}。データパックの物を優先)
 */
public final class GameItems {

    private GameItems() {
    }

    /** そのIDのアイテムがあるか */
    public static boolean exists(String id) {
        return id != null && (ItemRegistry.get(id) != null
                || ItemRegistry.getAmmo(id) != null
                || DatapackGunCatalog.get(id) != null
                || AttachmentItems.resolve(id) != null);
    }

    /**
     * アイテムを1個作る。無いIDならnull
     *
     * @param displayName データパック銃の表示名 (nullならデータパックに登録された名前)。他のアイテムでは使わない
     */
    public static ItemStack create(World world, String id, String displayName) {
        if (id == null) return null;
        if (ItemRegistry.get(id) != null || ItemRegistry.getAmmo(id) != null) return ItemFactory.create(id);
        DatapackGunProfile gun = DatapackGunCatalog.get(id);
        if (gun != null) {
            String name = displayName != null && !displayName.isEmpty() ? displayName : gun.displayName();
            return DatapackFunctionUtil.generateGunItem(world, id, name);
        }
        return ItemFactory.create(world, id);
    }
}

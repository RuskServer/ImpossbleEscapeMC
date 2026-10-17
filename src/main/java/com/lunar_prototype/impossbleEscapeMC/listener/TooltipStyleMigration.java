package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.TooltipStyle;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * 以前に作られたアイテムを今の見た目に直す。tooltip_style については、以前は minecraft:&lt;色&gt;_frame.png を付けていたが、
 * クライアントはそこから minecraft:tooltip/&lt;色&gt;_frame.png_background という無いスプライトを探すので説明の表示が壊れていた。
 * 弾のアイコン (iem:ammo/&lt;id&gt;) ができる前に作られた弾にも、アイコンを付ける。
 * 参加した時の手持ちとエンダーチェスト、開いたインベントリ (スタッシュ・コンテナなど) の中身を直す。
 * 旧金ヘルメット版 UNTAR も Diamond/CMD 9 に移し、元の耐久・FIR・支給品などのデータを保つ。
 */
public class TooltipStyleMigration implements Listener {
    private static final String LEGACY_SUFFIX = "_frame.png";

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        fix(event.getPlayer().getInventory());
        fix(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        fix(event.getInventory());
    }

    private static void fix(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (fix(item)) inventory.setItem(i, item);
        }
    }

    /** 古いアイテムの見た目や素材を更新したなら true */
    static boolean fix(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        boolean upgradedUntar = fixLegacyUntar(item);
        ItemMeta meta = item.getItemMeta();
        boolean changed = fixTooltipStyle(meta) | fixAmmoModel(meta);
        if (changed) item.setItemMeta(meta);
        return upgradedUntar || changed;
    }

    private static boolean fixLegacyUntar(ItemStack item) {
        if (item.getType() != Material.GOLDEN_HELMET) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (!"UNTAR".equals(id)) return false;
        item.setType(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(9);
        var equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        meta.setEquippable(equippable);
        item.setItemMeta(meta);
        return true;
    }

    private static boolean fixTooltipStyle(ItemMeta meta) {
        if (!meta.hasTooltipStyle()) return false;
        NamespacedKey style = meta.getTooltipStyle();
        if (!NamespacedKey.MINECRAFT.equals(style.getNamespace()) || !style.getKey().endsWith(LEGACY_SUFFIX)) return false;
        String color = style.getKey().substring(0, style.getKey().length() - LEGACY_SUFFIX.length());
        TooltipStyle fixed;
        try {
            fixed = TooltipStyle.valueOf(color.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            fixed = TooltipStyle.WHITE;
        }
        fixed.applyTo(meta);
        return true;
    }

    /** 弾のアイコンができる前に作られた弾 (棒の見た目) に iem:ammo/&lt;id&gt; を付ける */
    private static boolean fixAmmoModel(ItemMeta meta) {
        if (meta.hasItemModel()) return false;
        String id = meta.getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (id == null || ItemRegistry.getAmmo(id) == null) return false;
        meta.setItemModel(ItemFactory.ammoModel(id));
        return true;
    }
}

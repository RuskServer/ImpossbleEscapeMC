package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.TooltipStyle;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * 以前に作られたアイテムの tooltip_style を直す。以前は minecraft:&lt;色&gt;_frame.png を付けていたが、
 * クライアントはそこから minecraft:tooltip/&lt;色&gt;_frame.png_background という無いスプライトを探すので説明の表示が壊れていた。
 * 参加した時の手持ちとエンダーチェスト、開いたインベントリ (スタッシュ・コンテナなど) の中身を iem:&lt;色&gt; に直す
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

    /** 古い tooltip_style なら直して true */
    static boolean fix(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
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
        item.setItemMeta(meta);
        return true;
    }
}

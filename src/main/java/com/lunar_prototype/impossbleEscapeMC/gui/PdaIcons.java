package com.lunar_prototype.impossbleEscapeMC.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PDA のボタンの見た目: 端末の液晶に映る琥珀色のピクトグラム (リソースパックの iem:pda_&lt;名前&gt;。
 * IEM_shader の tools/pda_icons.py)。PDA のホームと、そこから開く画面 (パーティー・招待・トレーダー選択・マーケット) で使う。
 * 名前・説明 (ツールチップ) と、元のアイテムの種類はそのまま。見た目 (item_model) だけを差し替える。
 * 使えない時の絵は {@link #off} (暗い線 + 錠前)
 */
public final class PdaIcons {
    private PdaIcons() {
    }

    /** item の見た目を iem:pda_&lt;name&gt; にして返す (同じ item) */
    public static ItemStack of(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setItemModel(new NamespacedKey("iem", "pda_" + name));
        item.setItemMeta(meta);
        return item;
    }

    /** 使えない時の見た目 (iem:pda_&lt;name&gt;_off) */
    public static ItemStack off(ItemStack item, String name) {
        return of(item, name + "_off");
    }
}

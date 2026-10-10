package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * アイテムの説明 (ツールチップ) の背景と枠の色。リソースパックの
 * assets/iem/textures/gui/sprites/tooltip/&lt;色&gt;_background.png と &lt;色&gt;_frame.png を使う
 * (クライアントは tooltip_style が iem:&lt;色&gt; のとき iem:tooltip/&lt;色&gt;_background と _frame を描く)。
 * レア度の色 (名前の色) と同じ並び: 1 白 / 2 緑 / 3 青 / 4 紫 / 5 金。赤はサイズ制限の占有スロットなど警告用
 */
public enum TooltipStyle {
    WHITE, GREEN, BLUE, PURPLE, GOLD, RED;

    private final NamespacedKey key = new NamespacedKey("iem", name().toLowerCase(java.util.Locale.ROOT));

    public NamespacedKey key() {
        return key;
    }

    /** meta に付ける。item.setItemMeta(meta) より前に呼ぶ (後から付けると、古い meta で上書きされて消える) */
    public void applyTo(ItemMeta meta) {
        meta.setTooltipStyle(key);
    }

    public static TooltipStyle forRarity(int rarity) {
        return switch (rarity) {
            case 2 -> GREEN;
            case 3 -> BLUE;
            case 4 -> PURPLE;
            case 5 -> GOLD;
            default -> WHITE;
        };
    }
}

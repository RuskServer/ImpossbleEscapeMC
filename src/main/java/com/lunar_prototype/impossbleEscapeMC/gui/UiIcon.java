package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 画面で共通に使うアイコン (16x16)。同じ意味には、どの画面でも同じアイコンを使う。
 * 1 つの絵を 2 通りで使える:
 * <ul>
 *   <li>{@link #item()}: 品物 iem:icon/&lt;名前&gt;。ダイアログのアイテム欄・チェストの画面のボタンに置く</li>
 *   <li>{@link #glyph()}: フォント iem:icon の字 (高さ 8 = バニラの字と同じ)。文の中に置く</li>
 * </ul>
 * 絵と字の番号は IEM_shader/tools/ui_icons.py の ICONS と同じ並び (字は U+E900 + 並びの番号)。足す時は末尾に足す
 */
public enum UiIcon {
    KILL("kill", "排除"),
    EXTRACT("extract", "脱出"),
    REACH("reach", "到達"),
    HAND_IN("hand_in", "納品"),
    FOUND_IN_RAID("found_in_raid", "レイド内で入手 (FIR)"),
    MONEY("money", "お金"),
    EXP("exp", "経験値"),
    UNLOCK("unlock", "解放"),
    LOCKED("locked", "ロック"),
    LEVEL("level", "レベル"),
    TRADE("trade", "取引の品"),
    QUEST("quest", "クエスト"),
    REPORT("report", "報告可"),
    AVAILABLE("available", "受領可"),
    DONE("done", "済み"),
    FAILED("failed", "失敗・閉鎖"),
    TIMER("timer", "時間"),
    TRADER("trader", "トレーダー"),
    RAID("raid", "出撃"),
    WEIGHT("weight", "重さ・容量");

    private static final Key FONT = Key.key("iem", "icon");
    private static final char BASE = '';
    /** 字の送り (幅 8 + 1) */
    public static final int GLYPH_ADVANCE = 9;

    private final String id;
    private final String label;

    UiIcon(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** 品物の見た目 (iem:icon/&lt;名前&gt;)。名前は {@link #label()} */
    public ItemStack item() {
        return item(Component.text(label));
    }

    /** 品物の見た目で、名前 (ホバーの 1 行目) を指定する */
    public ItemStack item(Component name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("iem", "icon/" + id));
        meta.itemName(name.colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** 文の中に置く字 (白・影なし。色は絵のまま) */
    public Component glyph() {
        return Component.text(String.valueOf((char) (BASE + ordinal()))).font(FONT)
                .color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }
}

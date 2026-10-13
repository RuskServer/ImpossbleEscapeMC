package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResultFont.*;

/**
 * レイド結果の 2 ページ目: 持ち帰った物をチェストの画面に並べる (本物の品物なので、3D のモデルも個数もホバーの説明もそのまま)。
 * <ul>
 *   <li>タイトルに背景を描く (フォント iem:result): 上の 2 段は液晶 (評価ランク・持ち帰り・失った装備・総利益)、
 *       品物の後ろに価値の段階の枠 (金・紫・青・灰。クエストに使える物は左上に三角)</li>
 *   <li>3〜5 段目に品物 (価値の高い順 / 種類順)、6 段目にボタン (経験値へ・ページ・並び・クエスト納品へ)</li>
 *   <li>品物は見るだけ (取り出せない。持ち帰った物は持ち物・スタッシュにある)</li>
 * </ul>
 */
public final class RaidResultLootGUI implements InventoryHolder {

    private static final int PER_PAGE = 27, FIRST_SLOT = 18;
    private static final int BACK_SLOT = 45, PREV_SLOT = 47, SORT_SLOT = 49, NEXT_PAGE_SLOT = 51, NEXT_SLOT = 53;
    private static final TextColor AMBER = TextColor.color(0xDEAC4E);
    private static final TextColor OK = TextColor.color(0x78CD60);
    private static final TextColor NG = TextColor.color(0xD64A34);
    private static final TextColor NOTE = TextColor.color(0xAAA48C);
    private static final TextColor AMBER_DIM = TextColor.color(0x96763C);

    private static Listener listener;

    private final RaidReport report;
    private final int page;
    private final boolean byType;
    private final Inventory inventory;

    private RaidResultLootGUI(RaidReport report, int page, boolean byType) {
        this.report = report;
        this.byType = byType;
        List<RaidReport.LootStack> loot = sorted(report.loot(), byType);
        int pages = Math.max(1, (loot.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        List<RaidReport.LootStack> shown = loot.subList(this.page * PER_PAGE, Math.min(loot.size(), (this.page + 1) * PER_PAGE));
        this.inventory = Bukkit.createInventory(this, 54, title(report, shown));
        for (int i = 0; i < shown.size(); i++) inventory.setItem(FIRST_SLOT + i, display(shown.get(i)));
        inventory.setItem(BACK_SLOT, UiIcon.EXP.item(Component.text("戻る: 経験値")));
        inventory.setItem(NEXT_SLOT, UiIcon.QUEST.item(Component.text("次へ: クエスト納品")));
        inventory.setItem(SORT_SLOT, UiIcon.TRADE.item(Component.text(byType ? "並び: 種類順 (押すと価値順)" : "並び: 価値順 (押すと種類順)")));
        if (this.page > 0) inventory.setItem(PREV_SLOT, button(Material.ARROW, "前のページ (" + this.page + "/" + pages + ")"));
        if (this.page < pages - 1) inventory.setItem(NEXT_PAGE_SLOT, button(Material.ARROW, "次のページ (" + (this.page + 2) + "/" + pages + ")"));
    }

    static void open(Player player, RaidReport report) {
        open(player, report, 0, false);
    }

    private static void open(Player player, RaidReport report, int page, boolean byType) {
        if (listener == null) {
            listener = new ClickListener();
            Bukkit.getPluginManager().registerEvents(listener, ImpossbleEscapeMC.getInstance());
        }
        player.openInventory(new RaidResultLootGUI(report, page, byType).inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static List<RaidReport.LootStack> sorted(List<RaidReport.LootStack> loot, boolean byType) {
        List<RaidReport.LootStack> out = new ArrayList<>(loot);
        Comparator<RaidReport.LootStack> byValue = Comparator.comparingLong(RaidReport.LootStack::value).reversed();
        if (byType) {
            out.sort(Comparator.comparing((RaidReport.LootStack s) -> String.valueOf(QuestAutoHandIn.itemIdOf(s.item()))).thenComparing(byValue));
        } else {
            out.sort(byValue);
        }
        return out;
    }

    /** タイトル: 背景のタイル・ランクの札・液晶の数字 3 行・品物の後ろの枠。タイトルは画面の左から 8 の所から始まる */
    static Component title(RaidReport report, List<RaidReport.LootStack> shown) {
        DialogLine line = line();
        int origin = 8;
        for (int row = 0; row < TILE_ROWS; row++) {
            for (int col = 0; col < TILE_COLS; col++) {
                line.stamp(FONT, (char) (CHEST_BASE + row * TILE_COLS + col), TILE_W, col * TILE_W - origin);
            }
        }
        line.stamp(FONT, (char) (RANK_BASE + RANKS.indexOf(report.rank())), RANK_W, RANK_X - origin);
        // 液晶の 3 行: バニラの字を縦にずらしたフォントで置く (絵に字を焼き込まない)
        String[] labels = {"持ち帰り", "失った装備", "総利益"};
        String[] values = {fmt(report.foundValue()) + "₽", (report.lostValue() > 0 ? "-" : "") + fmt(report.lostValue()) + "₽",
                (report.profit() >= 0 ? "+" : "-") + fmt(Math.abs(report.profit())) + "₽"};
        TextColor[] colors = {AMBER, report.lostValue() > 0 ? NG : NOTE, report.profit() >= 0 ? OK : NG};
        for (int l = 0; l < 3; l++) {
            line.moveTo(LCD_LABEL_X - origin).text(shift(LCD_SHIFTS[l]), labels[l], AMBER_DIM, null, null);
            line.moveTo(LCD_VALUE_RIGHT - origin - com.lunar_prototype.impossbleEscapeMC.util.FontWidths.width(values[l]))
                    .text(shift(LCD_SHIFTS[l]), values[l], colors[l], null, null);
        }
        for (int k = 0; k < shown.size(); k++) {
            RaidReport.LootStack s = shown.get(k);
            int tier = 0;
            while (tier < TIER_VALUES.length - 1 && s.value() < TIER_VALUES[tier]) tier++;
            int row = k / 9, col = k % 9;
            line.moveTo(7 + col * 18 - origin).glyph(FONT, (char) (TIER_BASE + row * 8 + tier * 2 + (s.questUsable() ? 1 : 0)), TIER_W, null, null);
        }
        return line.build();
    }

    /** 見せる品物 (写し): 説明の最後に価値と、クエストに使えるか */
    private static ItemStack display(RaidReport.LootStack s) {
        ItemStack item = s.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("価値 " + fmt(s.value()) + "₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        if (s.questUsable()) {
            lore.add(Component.text("進行中のクエストの納品に使える", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static String fmt(long v) {
        return String.format("%,d", v);
    }

    /** 品物は動かせない。ボタンでページ・並び・前後のページへ */
    private static final class ClickListener implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getInventory().getHolder() instanceof RaidResultLootGUI gui)) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) return;
            int slot = event.getRawSlot();
            if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;
            Runnable next = switch (slot) {
                case BACK_SLOT -> () -> RaidResultDialog.showPage(player, gui.report, RaidResultDialog.Page.EXP);
                case NEXT_SLOT -> () -> RaidResultDialog.showPage(player, gui.report, RaidResultDialog.Page.HANDIN);
                case SORT_SLOT -> () -> open(player, gui.report, 0, !gui.byType);
                case PREV_SLOT -> gui.page > 0 ? () -> open(player, gui.report, gui.page - 1, gui.byType) : null;
                case NEXT_PAGE_SLOT -> () -> open(player, gui.report, gui.page + 1, gui.byType);
                default -> null;
            };
            if (next == null) return;
            player.playSound(player, "iem:pda.click", org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
            if (slot == BACK_SLOT || slot == NEXT_SLOT) {
                // ダイアログはチェストの画面を閉じてから出す (開いたままだとサーバーは画面が開いているとみなす)
                player.closeInventory();
                Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), next);
            } else {
                next.run();
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getInventory().getHolder() instanceof RaidResultLootGUI) event.setCancelled(true);
        }
    }
}

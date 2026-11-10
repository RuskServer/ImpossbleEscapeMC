package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.tooltip.TooltipStyle;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.*;

/** PDAの端末背景とNPCの顔で、取引先を選ぶ。 */
public class TraderSelectionGUI implements Listener {
    private static final int PAGE_SIZE = 7;
    private static final int PREVIOUS = 20, BACK = 22, NEXT = 24;
    private static final List<String> ORDER = List.of("kovacs", "pharmakon", "bastion", "broker");
    private final Player player;
    private final Inventory inventory;
    private final TraderModule traderModule;
    private final List<TraderDefinition> traderList;
    private final Map<Integer, TraderDefinition> buttons = new HashMap<>();
    private int page;

    public TraderSelectionGUI(Player player, TraderModule traderModule) {
        this.player = player;
        this.traderModule = traderModule;
        traderList = new ArrayList<>(traderModule.getAllTraders());
        traderList.sort(Comparator.comparingInt((TraderDefinition trader) -> {
            int rank = ORDER.indexOf(trader.id);
            return rank < 0 ? ORDER.size() : rank;
        }).thenComparing(trader -> trader.id));
        inventory = Bukkit.createInventory(null, 27, GuiBackground.TRADERS.title());
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();
        buttons.clear();
        int pages = Math.max(1, (traderList.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        inventory.setItem(4, PdaIcons.of(item(Material.WRITABLE_BOOK, "取引先", AMBER,
                List.of(text("連絡先  " + traderList.size() + "人", NOTE),
                        text("ページ  " + (page + 1) + " / " + pages, NOTE),
                        text("顔にカーソルを合わせて詳細を確認", NOTE))), "contacts"));
        int start = page * PAGE_SIZE;
        int count = Math.min(PAGE_SIZE, traderList.size() - start);
        for (int i = 0; i < count; i++) {
            TraderDefinition trader = traderList.get(start + i);
            // 現在の4人は10,12,14,16。増えた場合も中央欄だけを使い、次ページへ送る。
            int slot = count <= 4 ? 13 - (count - 1) + i * 2 : 13 - count / 2 + i;
            ItemStack head = TraderPortraits.icon(trader);
            var meta = head.getItemMeta();
            meta.displayName(text(name(trader), NAME));
            List<Component> lore = new ArrayList<>();
            lore.add(text(specialty(trader), NOTE));
            lore.add(Component.empty());
            lore.add(text(trader.type == TraderType.BUY ? "購入・交換" : "アイテムの売却", AMBER));
            if (trader.canRepairWeapon) lore.add(text("武器修理", NOTE));
            if (trader.canRepairArmor) lore.add(text("防具修理", NOTE));
            lore.add(Component.empty());
            lore.add(text("クリックして取引を開く", AMBER));
            meta.lore(lore);
            TooltipStyle.GOLD.applyTo(meta);
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            buttons.put(slot, trader);
        }
        if (traderList.isEmpty()) inventory.setItem(13,
                PdaIcons.of(item(Material.PAPER, "取引先がありません", NOTE, List.of()), "empty"));
        inventory.setItem(BACK, PdaIcons.of(item(Material.ARROW, "PDAへ戻る", AMBER, List.of()), "back"));
        if (page > 0) inventory.setItem(PREVIOUS, PdaIcons.of(item(Material.ARROW, "前のページ", AMBER, List.of()), "prev"));
        if (page + 1 < pages) inventory.setItem(NEXT, PdaIcons.of(item(Material.ARROW, "次のページ", AMBER, List.of()), "next"));
    }

    static String name(TraderDefinition trader) {
        String display = trader.displayName == null ? trader.id : trader.displayName;
        return PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(display))
                .replace(" (Buy Only)", "").replace(" (Sell Only)", "");
    }

    private static String specialty(TraderDefinition trader) {
        return switch (trader.id) {
            case "kovacs" -> "武器・弾薬・アタッチメント";
            case "pharmakon" -> "医療品・医療物資";
            case "bastion" -> "防具・装備・携行品";
            case "broker" -> "装備・物資の買い取り";
            default -> trader.type == TraderType.BUY ? "装備・物資の取引" : "物資の買い取り";
        };
    }

    private static Component text(String value, TextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, TextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(text(name, color));
        meta.lore(lore);
        TooltipStyle.GOLD.applyTo(meta);
        item.setItemMeta(meta);
        return item;
    }

    private void switchScreen(Runnable next) {
        // InventoryClickEventの処理中に画面を閉じない。連打後の古い遷移もここで止める。
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) return;
            player.closeInventory();
            next.run();
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory) return;
        event.setCancelled(true);
        if (!event.getWhoClicked().getUniqueId().equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        TraderDefinition trader = buttons.get(slot);
        if (trader != null) {
            switchScreen(() -> TraderDialog.open(traderModule, trader, player));
        } else if (slot == BACK) {
            switchScreen(() -> new PDAGUI(player).open());
        } else if (slot == PREVIOUS && page > 0) {
            page--;
            setupGUI();
        } else if (slot == NEXT && (page + 1) * PAGE_SIZE < traderList.size()) {
            page++;
            setupGUI();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() == inventory) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == inventory && event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            HandlerList.unregisterAll(this);
        }
    }
}

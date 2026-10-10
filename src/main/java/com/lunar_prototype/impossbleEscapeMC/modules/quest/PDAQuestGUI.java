package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.GuiBackground;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

/**
 * PDAから開ける全クエスト進捗確認GUI。3段で、上の2段に進行中→受領可能の順に並べ、ページで送る
 */
public class PDAQuestGUI implements Listener {
    private static final int SIZE = 27;
    private static final int PAGE_SIZE = 18;
    private static final int PREV_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int NEXT_SLOT = 26;

    private final Player player;
    private final QuestModule questModule;
    private final PlayerDataModule dataModule;
    /** タイトルにページ数を出すので、開くときに作る */
    private Inventory inventory;
    private int page;
    private int pageCount = 1;

    public PDAQuestGUI(Player player, QuestModule questModule) {
        this(player, questModule, 0);
    }

    public PDAQuestGUI(Player player, QuestModule questModule, int page) {
        this.player = player;
        this.questModule = questModule;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.page = page;
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        List<ItemStack> entries = entries(dataModule.getPlayerData(player.getUniqueId()));
        pageCount = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pageCount - 1));
        this.inventory = Bukkit.createInventory(null, SIZE, GuiBackground.QUEST.pagedTitle(page, pageCount));
        setupGUI(entries);
        player.openInventory(inventory);
    }

    /** 進行中のクエスト、続いて受領可能なクエストのアイコン */
    private List<ItemStack> entries(PlayerData data) {
        List<ItemStack> entries = new ArrayList<>();
        for (ActiveQuest active : data.getActiveQuests().values()) {
            QuestDefinition q = questModule.getQuest(active.getQuestId());
            if (q == null) continue;

            ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(q.getDisplayName(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("依頼主: " + q.getTraderId(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("進捗状況:", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            for (int i = 0; i < q.getObjectives().size(); i++) {
                QuestObjective obj = q.getObjectives().get(i);
                boolean done = obj.isCompleted(active, i);
                lore.add(Component.text("- " + obj.getDescription() + " (" + obj.getProgressText(active, i) + ")",
                        done ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            entries.add(item);
        }

        for (QuestDefinition q : questModule.getStartableQuests(data)) {
            ItemStack item = new ItemStack(Material.BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(q.getDisplayName() + " [受領可能]", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("依頼主: " + q.getTraderId(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(q.getDescription(), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("▶ クリックして受領", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.QUEST_ID, PDCKeys.STRING, q.getId());
            item.setItemMeta(meta);
            entries.add(item);
        }
        return entries;
    }

    private void setupGUI(List<ItemStack> entries) {
        // 背景はタイトルの画像で描くので、空きスロットは空のままにする
        inventory.clear();

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < entries.size(); i++) {
            inventory.setItem(i, entries.get(start + i));
        }
        if (entries.isEmpty()) {
            inventory.setItem(4, button(Material.PAPER, "進行中・受領可能なクエストはありません", NamedTextColor.GRAY));
        }

        if (page > 0) inventory.setItem(PREV_SLOT, button(Material.ARROW, "前のページ", NamedTextColor.WHITE));
        if (page < pageCount - 1) inventory.setItem(NEXT_SLOT, button(Material.ARROW, "次のページ", NamedTextColor.WHITE));
        inventory.setItem(BACK_SLOT, button(Material.BARRIER, "PDAに戻る", NamedTextColor.RED));
    }

    private static ItemStack button(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot == BACK_SLOT) {
            player.closeInventory();
            new com.lunar_prototype.impossbleEscapeMC.gui.PDAGUI(player).open();
            return;
        }
        // ページ数はタイトルに出ているので、ページを変えるときは開き直す
        if (rawSlot == PREV_SLOT && page > 0) {
            new PDAQuestGUI(player, questModule, page - 1).open();
            return;
        }
        if (rawSlot == NEXT_SLOT && page < pageCount - 1) {
            new PDAQuestGUI(player, questModule, page + 1).open();
            return;
        }
        if (rawSlot < 0 || rawSlot >= PAGE_SIZE) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        String questId = clicked.getItemMeta().getPersistentDataContainer().get(PDCKeys.QUEST_ID, PDCKeys.STRING);
        if (questId != null) {
            QuestDefinition q = questModule.getQuest(questId);
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (q != null && data != null && questModule.canStart(data, q)) {
                // クエスト受領処理
                data.getActiveQuests().put(questId, new ActiveQuest(questId));
                data.setDirty(true);
                player.sendMessage(Component.text("クエストを受領しました: " + q.getDisplayName(), NamedTextColor.GREEN));
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                new PDAQuestGUI(player, questModule, page).open(); // 再描画 (並びとページ数が変わる)
            }
        }
    }

    /** 背景の画像を見せるため空きスロットを残しているので、ドラッグで物を置けないようにする */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().equals(inventory)
                && event.getRawSlots().stream().anyMatch(slot -> slot < inventory.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}

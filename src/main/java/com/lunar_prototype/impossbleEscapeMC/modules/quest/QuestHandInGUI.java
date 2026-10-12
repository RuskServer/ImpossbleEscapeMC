package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * クエストの納品画面。納品する物をプレイヤーが選んで中段の欄に入れ、「納品する」で目標に充てる。
 * インベントリから自動で取らないので、弾種や防具を選べる目標でも、手放す物をプレイヤーが決められる
 * (貫通クラスの高い弾や着ている防具を勝手に取らない)。
 * 目標に足りた分だけ減らし、余り・条件に合わない物は欄に残す。画面を閉じると欄の物はすべて持ち主に返す。
 * <p>
 * 1 つのクエストの画面と、トレーダーの進行中のクエストをまとめて納品する画面 ({@link #forTrader}) がある。
 * まとめての時は、入れた物をクエストの並び順 (トレーダーのクエスト一覧と同じ)・目標の順に割り当てる
 */
public class QuestHandInGUI implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 36;
    private static final int CONFIRM_SLOT = 44;
    /** 納品する物を入れる欄 (2〜4段目) */
    private static final int FIRST_INPUT_SLOT = 9;
    private static final int LAST_INPUT_SLOT = 35;

    private final Player player;
    private final TraderDefinition trader;
    private final QuestModule questModule;
    private final PlayerDataModule dataModule;
    /** 納品先のクエスト (まとめての時は複数、トレーダーのクエスト一覧の順) */
    private final List<QuestDefinition> quests;
    private final boolean bulk;
    private final Inventory inventory;

    /** 割り当て先: クエストと、その目標の番号 */
    private record Target(QuestDefinition quest, int objective) {
    }

    public QuestHandInGUI(Player player, TraderDefinition trader, QuestModule questModule, QuestDefinition quest) {
        this(player, trader, questModule, List.of(quest), false, "納品 - " + quest.getDisplayName());
    }

    private QuestHandInGUI(Player player, TraderDefinition trader, QuestModule questModule, List<QuestDefinition> quests,
                           boolean bulk, String title) {
        this.player = player;
        this.trader = trader;
        this.questModule = questModule;
        this.quests = quests;
        this.bulk = bulk;
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.inventory = Bukkit.createInventory(null, SIZE, Component.text(title).decoration(TextDecoration.ITALIC, false));
    }

    /** トレーダーの進行中のクエストのうち、まだ終わっていない納品の目標があるもの (一覧の順) */
    public static List<QuestDefinition> openHandIns(PlayerData data, TraderDefinition trader, QuestModule questModule) {
        List<QuestDefinition> result = new ArrayList<>();
        if (data == null) return result;
        for (QuestDefinition quest : questModule.getQuestsByTrader(trader.id)) {
            ActiveQuest active = data.getActiveQuests().get(quest.getId());
            if (active != null && hasOpenHandIn(quest, active)) result.add(quest);
        }
        return result;
    }

    /** トレーダーの進行中のクエストにまとめて納品する画面。納品できるクエストが無ければ null */
    public static QuestHandInGUI forTrader(Player player, TraderDefinition trader, QuestModule questModule) {
        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        List<QuestDefinition> quests = openHandIns(dataModule.getPlayerData(player.getUniqueId()), trader, questModule);
        if (quests.isEmpty()) return null;
        return new QuestHandInGUI(player, trader, questModule, quests, true, "まとめて納品 - " + trader.displayName);
    }

    /** 納品の目標があり、まだ終わっていないものがあるか */
    public static boolean hasOpenHandIn(QuestDefinition quest, ActiveQuest active) {
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i) instanceof HandInObjective && !objectives.get(i).isCompleted(active, i)) return true;
        }
        return false;
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isInputSlot(slot)) inventory.setItem(slot, pane);
        }
        inventory.setItem(BACK_SLOT, button(Material.ARROW, "§7クエストの画面に戻る", List.of("§8入れた物は返却されます")));
        refresh();
        player.openInventory(inventory);
    }

    private static boolean isInputSlot(int slot) {
        return slot >= FIRST_INPUT_SLOT && slot <= LAST_INPUT_SLOT;
    }

    /** 目標の進み具合と、今の欄の中身で納品できる数を表示し直す */
    private void refresh() {
        List<String> info = new ArrayList<>();
        for (QuestDefinition quest : quests) {
            ActiveQuest active = activeQuest(quest);
            if (active == null) continue;
            if (bulk) info.add("§6" + quest.getDisplayName());
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                // まとめての時は納品の目標だけを並べる (長くなりすぎないように)
                if (bulk && !(objective instanceof HandInObjective)) continue;
                boolean done = objective.isCompleted(active, i);
                info.add((bulk ? " " : "") + (done ? "§a✔ " : objective instanceof HandInObjective ? "§e- " : "§7- ")
                        + objective.getDescription() + " §8(" + objective.getProgressText(active, i) + ")");
            }
        }
        inventory.setItem(INFO_SLOT, button(Material.WRITABLE_BOOK,
                bulk ? "§6" + trader.displayName + " の納品 (" + quests.size() + " 件)" : "§6" + quests.get(0).getDisplayName(), info));

        Map<Target, Integer> accepted = allocate(false);
        List<String> lore = new ArrayList<>();
        if (accepted.isEmpty()) {
            lore.add("§7納品する物を上の欄に入れてください");
            lore.add("§8(条件に合わない物・FIRでない物は受け付けません)");
        } else {
            accepted.forEach((target, amount) -> lore.add("§f" + describe(target) + " §a+" + amount));
            lore.add("");
            lore.add("§eクリックで納品する §8(余った分は欄に残ります)");
        }
        inventory.setItem(CONFIRM_SLOT, button(accepted.isEmpty() ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                accepted.isEmpty() ? "§c納品できる物がありません" : "§a§l納品する", lore));
    }

    /** 割り当て先の説明 (まとめての時はクエストの名前を付ける) */
    private String describe(Target target) {
        String objective = target.quest().getObjectives().get(target.objective()).getDescription();
        return bulk ? "[" + target.quest().getDisplayName() + "] " + objective : objective;
    }

    /**
     * 欄の物を目標に割り当てる (クエストの順・目標の順、欄の左上から順)。割り当て先ごとに受け付ける数を返す。
     * apply なら進捗を進め、受け付けた分を欄から減らす
     */
    private Map<Target, Integer> allocate(boolean apply) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        Map<Target, Integer> accepted = new LinkedHashMap<>();
        int[] remaining = new int[SIZE];
        for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT; slot++) {
            ItemStack item = inventory.getItem(slot);
            remaining[slot] = item == null || item.getType().isAir() ? 0 : item.getAmount();
        }

        for (QuestDefinition quest : quests) {
            ActiveQuest active = activeQuest(quest);
            if (active == null) continue;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!(objectives.get(i) instanceof HandInObjective handIn) || handIn.isCompleted(active, i)) continue;
                int needed = handIn.getTargetAmount() - active.getProgress(i);
                for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT && needed > 0; slot++) {
                    if (remaining[slot] <= 0) continue;
                    ItemStack item = inventory.getItem(slot);
                    String itemId = handInIdOf(item);
                    QuestItems.Definition def = QuestItems.resolve(itemId);
                    boolean fir = isFir(item);
                    if (def == null || !handIn.matches(itemId, def.type(), fir)) continue;

                    int take = Math.min(remaining[slot], needed);
                    if (apply) {
                        Map<String, Object> params = Map.of("itemId", itemId, "itemType", def.type(), "isFIR", fir, "amount", take);
                        if (!handIn.updateProgress(player, data, active, i, QuestTrigger.HAND_IN, params)) continue;
                        item.setAmount(item.getAmount() - take);
                        inventory.setItem(slot, item.getAmount() > 0 ? item : null);
                    }
                    remaining[slot] -= take;
                    needed -= take;
                    accepted.merge(new Target(quest, i), take, Integer::sum);
                }
            }
        }
        return accepted;
    }

    private void handIn() {
        if (quests.stream().allMatch(quest -> activeQuest(quest) == null)) {
            player.sendMessage(Component.text("このクエストは進行中ではありません。", NamedTextColor.RED));
            player.closeInventory();
            return;
        }
        Map<Target, Integer> accepted = allocate(true);
        if (accepted.isEmpty()) {
            player.sendMessage(Component.text("納品できる物が入っていません。", NamedTextColor.RED));
            return;
        }
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        data.setDirty(true);
        accepted.forEach((target, amount) -> player.sendMessage(Component.text(
                "納品しました: " + describe(target) + " +" + amount, NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
        accepted.keySet().stream().map(Target::quest).distinct().forEach(quest -> {
            ActiveQuest active = activeQuest(quest);
            if (active != null && questModule.isAllObjectivesMet(quest, active)) {
                player.sendMessage(Component.text((bulk ? "「" + quest.getDisplayName() + "」の" : "")
                        + "すべての目標を達成しました。クエスト一覧で報告できます。", NamedTextColor.YELLOW));
            }
        });
        refresh();
    }

    private ActiveQuest activeQuest(QuestDefinition quest) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        return data == null ? null : data.getActiveQuests().get(quest.getId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < SIZE && !isInputSlot(slot)) {
            event.setCancelled(true);
            if (slot == CONFIRM_SLOT) {
                handIn();
            } else if (slot == BACK_SLOT) {
                player.closeInventory();
                QuestDialog.resume(player, trader);   // 開いていたクエストの画面 (一覧・詳細) に戻る
                return;
            }
        }
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), this::refreshIfOpen);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < SIZE && !isInputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), this::refreshIfOpen);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        for (int slot = FIRST_INPUT_SLOT; slot <= LAST_INPUT_SLOT; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            player.getInventory().addItem(item).values().forEach(rest ->
                    player.getWorld().dropItemNaturally(player.getLocation(), rest));
        }
        HandlerList.unregisterAll(this);
    }

    private void refreshIfOpen() {
        if (player.getOpenInventory().getTopInventory().equals(inventory)) refresh();
    }

    private static boolean isFir(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
    }

    /** 納品で使うアイテムのID: データパックのアタッチメント → プラグインのアイテム・弾 → データパックの銃 */
    static String handInIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        // 支給品は納品できない (クエストの強調表示もしない)
        if (com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout.isSupplied(item)) return null;
        String id = DatapackAttachments.attachmentIdOf(item);
        if (id != null) return id;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        id = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        return id != null ? id : datapackGunId(pdc);
    }

    private static String datapackGunId(PersistentDataContainer pdc) {
        for (String namespace : new String[]{"minecraft", "toisarm"}) {
            PersistentDataContainer sub = pdc.get(new NamespacedKey(namespace, "toisarm"), PersistentDataType.TAG_CONTAINER);
            if (sub == null) continue;
            for (String subNamespace : new String[]{"minecraft", "toisarm"}) {
                String id = sub.get(new NamespacedKey(subNamespace, "id"), PersistentDataType.STRING);
                if (id != null) return id;
            }
        }
        return null;
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}

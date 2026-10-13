package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 持ち物とスタッシュの FIR (レイド内で入手) の物を、進行中のクエストの納品の目標に自動で充てる (チェストの画面を開かない)。
 * 割り当ての決まりはチェストの納品画面 ({@link QuestHandInGUI}) と同じ: クエストの順・目標の順に、持ち物 → スタッシュの順で。
 * 支給品は使わない ({@link QuestHandInGUI#handInIdOf})
 */
public final class QuestAutoHandIn {

    /** 物のある場所: 持ち物の欄、またはスタッシュのページと欄 */
    public record Where(int stashPage, int slot) {
        boolean inventory() {
            return stashPage == 0;
        }
    }

    /** 1 つの割り当て: どの物 (場所) を、どのクエストのどの目標に、いくつ */
    public record Allocation(QuestDefinition quest, int objective, Where where, String itemId, String itemType, int amount) {
    }

    /** 目標ごとの見通し: 今の数・納品した後の数・必要な数、使う物の見本、納品後にクエストを報告できるか */
    public record Target(QuestDefinition quest, int objective, int before, int after, int needed, ItemStack sample,
                         boolean reportableAfter, List<Allocation> allocations) {
    }

    private final Player player;
    private final PlayerData data;
    private final QuestModule questModule;
    /** スタッシュのページ (読んだもの)。割り当ての数を減らしながら使う */
    private final Map<Integer, Inventory> stash = new LinkedHashMap<>();

    public QuestAutoHandIn(Player player, PlayerData data, QuestModule questModule) {
        this.player = player;
        this.data = data;
        this.questModule = questModule;
        for (Map.Entry<Integer, String> page : data.getStashPages().entrySet()) {
            if (page.getValue() == null) continue;
            try {
                stash.put(page.getKey(), SerializationUtil.deserializeInventory(page.getValue(), Component.empty()));
            } catch (Exception e) {
                ImpossbleEscapeMC.getInstance().getLogger().log(Level.WARNING, "スタッシュのページを読めませんでした: " + page.getKey(), e);
            }
        }
    }

    /** 物の ID (支給品・ID の無い物は null) */
    public static String itemIdOf(ItemStack item) {
        return QuestHandInGUI.handInIdOf(item);
    }

    public static boolean isFir(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
    }

    /** その物を、進行中のクエストのまだ終わっていない納品の目標に使えるか */
    public static boolean usable(PlayerData data, QuestModule questModule, ItemStack item) {
        String id = itemIdOf(item);
        QuestItems.Definition def = QuestItems.resolve(id);
        if (def == null) return false;
        boolean fir = isFir(item);
        for (Map.Entry<String, ActiveQuest> entry : data.getActiveQuests().entrySet()) {
            QuestDefinition quest = questModule.getQuest(entry.getKey());
            if (quest == null) continue;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (objectives.get(i) instanceof HandInObjective h && !h.isCompleted(entry.getValue(), i) && h.matches(id, def.type(), fir)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 目標ごとの割り当ての見通し (物はまだ動かさない) */
    public List<Target> plan() {
        Map<Where, Integer> remaining = new LinkedHashMap<>();
        List<Target> targets = new ArrayList<>();
        for (QuestDefinition quest : questModule.getAllQuests()) {
            ActiveQuest active = data.getActiveQuests().get(quest.getId());
            if (active == null) continue;
            List<QuestObjective> objectives = quest.getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (!(objectives.get(i) instanceof HandInObjective handIn) || handIn.isCompleted(active, i)) continue;
                int before = active.getProgress(i);
                int needed = handIn.getTargetAmount() - before;
                List<Allocation> allocations = new ArrayList<>();
                ItemStack sample = null;
                for (Map.Entry<Where, ItemStack> c : candidates().entrySet()) {
                    if (needed <= 0) break;
                    ItemStack item = c.getValue();
                    int left = remaining.getOrDefault(c.getKey(), item.getAmount());
                    if (left <= 0 || !isFir(item)) continue;
                    String id = itemIdOf(item);
                    QuestItems.Definition def = QuestItems.resolve(id);
                    if (def == null || !handIn.matches(id, def.type(), true)) continue;
                    int take = Math.min(left, needed);
                    remaining.put(c.getKey(), left - take);
                    needed -= take;
                    allocations.add(new Allocation(quest, i, c.getKey(), id, def.type(), take));
                    if (sample == null) sample = item.asOne();
                }
                if (allocations.isEmpty()) continue;
                int after = before + allocations.stream().mapToInt(Allocation::amount).sum();
                targets.add(new Target(quest, i, before, after, handIn.getTargetAmount(), sample, false, allocations));
            }
        }
        // 納品した後にクエストの目標がすべて揃うか (同じクエストの別の目標の割り当ても足して見る)
        List<Target> result = new ArrayList<>();
        for (Target t : targets) {
            ActiveQuest active = data.getActiveQuests().get(t.quest().getId());
            boolean all = true;
            List<QuestObjective> objectives = t.quest().getObjectives();
            for (int i = 0; i < objectives.size(); i++) {
                if (objectives.get(i).isCompleted(active, i)) continue;
                int idx = i;
                Target planned = targets.stream().filter(o -> o.quest() == t.quest() && o.objective() == idx).findFirst().orElse(null);
                if (planned == null || planned.after() < planned.needed()) {
                    all = false;
                    break;
                }
            }
            result.add(new Target(t.quest(), t.objective(), t.before(), t.after(), t.needed(), t.sample(), all, t.allocations()));
        }
        return result;
    }

    /**
     * 割り当てどおりに物を減らして進捗を進める。物が動いていた (数が足りない・別の物になった) 割り当ては飛ばす。
     * 納品できた数を返す
     */
    public int apply(List<Allocation> allocations) {
        int done = 0;
        java.util.Set<Integer> changedPages = new java.util.HashSet<>();
        for (Allocation a : allocations) {
            ItemStack item = itemAt(a.where());
            if (item == null || item.getAmount() < a.amount() || !a.itemId().equals(itemIdOf(item)) || !isFir(item)) continue;
            ActiveQuest active = data.getActiveQuests().get(a.quest().getId());
            if (active == null || !(a.quest().getObjectives().get(a.objective()) instanceof HandInObjective handIn)) continue;
            Map<String, Object> params = Map.of("itemId", a.itemId(), "itemType", a.itemType(), "isFIR", true, "amount", a.amount());
            if (!handIn.updateProgress(player, data, active, a.objective(), QuestTrigger.HAND_IN, params)) continue;
            item.setAmount(item.getAmount() - a.amount());
            setItemAt(a.where(), item.getAmount() > 0 ? item : null);
            if (!a.where().inventory()) changedPages.add(a.where().stashPage());
            done += a.amount();
        }
        for (int page : changedPages) {
            try {
                data.setStashPage(page, SerializationUtil.serializeInventory(stash.get(page)));
            } catch (Exception e) {
                ImpossbleEscapeMC.getInstance().getLogger().log(Level.WARNING, "スタッシュのページを書けませんでした: " + page, e);
            }
        }
        if (done > 0) data.setDirty(true);
        return done;
    }

    /** 使える物の候補: 持ち物 (ホットバー・持ち物の欄)、続いてスタッシュのページ順 */
    private Map<Where, ItemStack> candidates() {
        Map<Where, ItemStack> out = new LinkedHashMap<>();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] != null && !contents[slot].getType().isAir()) out.put(new Where(0, slot), contents[slot]);
        }
        for (Map.Entry<Integer, Inventory> page : stash.entrySet()) {
            ItemStack[] items = page.getValue().getContents();
            for (int slot = 0; slot < items.length; slot++) {
                if (items[slot] != null && !items[slot].getType().isAir()) out.put(new Where(page.getKey(), slot), items[slot]);
            }
        }
        return out;
    }

    private ItemStack itemAt(Where where) {
        if (where.inventory()) return player.getInventory().getItem(where.slot());
        Inventory page = stash.get(where.stashPage());
        return page == null ? null : page.getItem(where.slot());
    }

    private void setItemAt(Where where, ItemStack item) {
        if (where.inventory()) {
            player.getInventory().setItem(where.slot(), item);
        } else {
            Inventory page = stash.get(where.stashPage());
            if (page != null) page.setItem(where.slot(), item);
        }
    }
}

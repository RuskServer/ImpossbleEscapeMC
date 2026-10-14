package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持ち物・バックパックの中・スタッシュの FIR (レイド内で入手) の物を、進行中のクエストの納品の目標に自動で充てる (レイド結果の画面から)。
 * 割り当ての決まり: クエストの順・目標の順に、物の並びの順 ({@link HandInPlaces#scan}: 持ち物 → バックパックの中 → スタッシュ) で
 * (選ばずに使う。選べる納品はトレーダーから開く {@link QuestHandInDialog})。支給品は使わない ({@link QuestHandInDialog#handInIdOf})
 */
public final class QuestAutoHandIn {

    /** 1 つの割り当て: どの物 (場所と、画面に出す場所の名前) を、どのクエストのどの目標に、いくつ */
    public record Allocation(QuestDefinition quest, int objective, HandInPlaces.Source where, String place, String itemId,
                             String itemType, int amount) {
    }

    /** 目標ごとの見通し: 今の数・納品した後の数・必要な数、使う物の見本、納品後にクエストを報告できるか */
    public record Target(QuestDefinition quest, int objective, int before, int after, int needed, ItemStack sample,
                         boolean reportableAfter, List<Allocation> allocations) {
    }

    private final Player player;
    private final PlayerData data;
    private final QuestModule questModule;

    public QuestAutoHandIn(Player player, PlayerData data, QuestModule questModule) {
        this.player = player;
        this.data = data;
        this.questModule = questModule;
    }

    /** 物の ID (支給品・ID の無い物は null) */
    public static String itemIdOf(ItemStack item) {
        return QuestHandInDialog.handInIdOf(item);
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
        Map<HandInPlaces.Source, Integer> remaining = new LinkedHashMap<>();
        List<HandInPlaces.Entry> candidates = HandInPlaces.scan(player, data);
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
                for (HandInPlaces.Entry c : candidates) {
                    if (needed <= 0) break;
                    ItemStack item = c.item();
                    int left = remaining.getOrDefault(c.source(), item.getAmount());
                    if (left <= 0 || !isFir(item)) continue;
                    String id = itemIdOf(item);
                    QuestItems.Definition def = QuestItems.resolve(id);
                    if (def == null || !handIn.matches(id, def.type(), true)) continue;
                    int take = Math.min(left, needed);
                    remaining.put(c.source(), left - take);
                    needed -= take;
                    allocations.add(new Allocation(quest, i, c.source(), c.place(), id, def.type(), take));
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
        HandInPlaces.Store store = new HandInPlaces.Store(player, data);
        for (Allocation a : allocations) {
            ItemStack item = store.get(a.where());
            if (item == null || item.getAmount() < a.amount() || !a.itemId().equals(itemIdOf(item)) || !isFir(item)) continue;
            ActiveQuest active = data.getActiveQuests().get(a.quest().getId());
            if (active == null || !(a.quest().getObjectives().get(a.objective()) instanceof HandInObjective handIn)) continue;
            Map<String, Object> params = Map.of("itemId", a.itemId(), "itemType", a.itemType(), "isFIR", true, "amount", a.amount());
            if (!handIn.updateProgress(player, data, active, a.objective(), QuestTrigger.HAND_IN, params)) continue;
            item.setAmount(item.getAmount() - a.amount());
            store.set(a.where(), item.getAmount() > 0 ? item : null);
            done += a.amount();
        }
        store.save();
        if (done > 0) data.setDirty(true);
        return done;
    }
}

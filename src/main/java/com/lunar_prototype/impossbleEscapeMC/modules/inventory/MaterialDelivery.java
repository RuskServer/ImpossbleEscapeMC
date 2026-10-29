package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.HandInPlaces;
import com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 隠れ家・製作・トレーダー交換で共用する素材納品。場所の探索はクエスト納品と共通。 */
public final class MaterialDelivery {
    private MaterialDelivery() {}
    public record Stock<T>(T source, String id, int amount) {}

    /** 足りなければnull。同じ場所を二重に使わず、所持品→バッグ→スタッシュの順を保つ。 */
    public static <T> Map<T, Integer> plan(List<Stock<T>> stock, Map<String, Integer> requirements) {
        Map<String, Integer> left = new LinkedHashMap<>(requirements);
        if (left.values().stream().anyMatch(n -> n == null || n < 0)) throw new IllegalArgumentException("invalid requirements");
        Map<T, Integer> result = new LinkedHashMap<>();
        java.util.Set<T> seen = new java.util.HashSet<>();
        for (Stock<T> item : stock) {
            if (!seen.add(item.source())) continue;
            int take = Math.min(Math.max(0, item.amount()), left.getOrDefault(item.id(), 0));
            if (take <= 0) continue;
            result.put(item.source(), take);left.put(item.id(), left.get(item.id())-take);
        }
        return left.values().stream().anyMatch(n -> n > 0) ? null : result;
    }

    private static String id(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta() || SupplyLoadout.isSupplied(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
    }

    public static Map<String, Integer> count(Player player, PlayerData data, java.util.Collection<String> ids) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : ids) counts.put(id,0);
        if (counts.isEmpty()) return counts;
        for (var entry : HandInPlaces.scanForCount(player,data)) {
            String id = id(entry.item());
            if (id != null && counts.containsKey(id)) counts.merge(id,entry.item().getAmount(),Integer::sum);
        }
        return counts;
    }

    public static boolean consume(Player player, PlayerData data, Map<String, Integer> requirements) throws IOException {
        // TODO(ai-collab): 数える場所と消費する場所を納品元の共通表へ統一し、保存前に不足を検査する。
        List<HandInPlaces.Entry> entries = HandInPlaces.scan(player,data,false,true);
        List<Stock<HandInPlaces.Source>> stock = entries.stream().filter(e -> id(e.item()) != null)
                .map(e -> new Stock<>(e.source(),id(e.item()),e.item().getAmount())).toList();
        Map<HandInPlaces.Source,Integer> plan = plan(stock,requirements);
        if (plan == null) return false;
        HandInPlaces.Store store = new HandInPlaces.Store(player,data,true);
        for (var entry : entries) {
            int take = plan.getOrDefault(entry.source(),0);
            if (take <= 0) continue;
            ItemStack current = store.get(entry.source());
            if (current == null || !current.isSimilar(entry.item()) || current.getAmount()<take) return false;
            ItemStack next = current.clone();next.setAmount(current.getAmount()-take);
            store.set(entry.source(),next.getAmount()>0?next:null);
        }
        store.commit();
        return true;
    }
}

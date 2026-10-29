package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.inventory.MaterialDelivery;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** 各設備の次の強化に必要な素材。所持数の集計と表示は、納品の判定と同じ場所を使う。 */
public final class HideoutMaterialNeeds {
    private HideoutMaterialNeeds() {}
    public record Use(String facility, int level, int amount, boolean prerequisiteMissing) {}
    public record Need(List<Use> uses, int required, int have) {
        public Need { uses = List.copyOf(uses); }
        public boolean missing() { return have < required; }
        public int shortage() { return Math.max(0,required-have); }
    }

    /** 同じ所持品を設備ごとに重複計上せず、次段階の必要数を合算する。 */
    public static Map<String,List<Use>> demands(ToIntFunction<HideoutFacility> levels, ToIntFunction<HideoutFacility> maximum) {
        Map<String,List<Use>> result = new LinkedHashMap<>();
        for (HideoutFacility facility : HideoutFacility.values()) {
            int level = levels.applyAsInt(facility);
            if (level >= maximum.applyAsInt(facility)) continue;
            int next = level+1;
            if (!facility.hasRequirements(next)) continue;
            boolean blocked = prerequisiteMissing(facility,next,levels);
            Map<String,Integer> amounts = new LinkedHashMap<>();
            for (var cost : facility.itemCosts(next)) if (cost.amount()>0) amounts.merge(cost.itemId(),cost.amount(),Math::addExact);
            amounts.forEach((id,amount) -> result.computeIfAbsent(id,key -> new ArrayList<>())
                    .add(new Use(facility.label,next,amount,blocked)));
        }
        Map<String,List<Use>> immutable = new LinkedHashMap<>();
        result.forEach((id,uses) -> immutable.put(id,List.copyOf(uses)));
        return java.util.Collections.unmodifiableMap(immutable);
    }

    static boolean prerequisiteMissing(HideoutFacility facility, int next, ToIntFunction<HideoutFacility> levels) {
        return facility.prerequisites(next).stream().anyMatch(p -> levels.applyAsInt(p.facility()) < p.level());
    }

    public static Map<String,Need> withStock(Map<String,List<Use>> demands, Map<String,Integer> counts) {
        Map<String,Need> result = new LinkedHashMap<>();
        demands.forEach((id,uses) -> result.put(id,new Need(uses,uses.stream().mapToInt(Use::amount).sum(),
                Math.max(0,counts.getOrDefault(id,0)))));
        return Map.copyOf(result);
    }

    public static Map<String,Need> collect(Player player, PlayerData data, HideoutModule hideout) {
        Map<String,List<Use>> demands = demands(f -> data.getHideoutLevel(f.id),f -> f.maxLevel(hideout.getLayout()));
        if (demands.isEmpty()) return Map.of();
        // 全アイテムIDをまとめて一度だけ数える。パケットのスレッドではバッグを読み込まない。
        return withStock(demands,MaterialDelivery.count(player,data,demands.keySet()));
    }
}

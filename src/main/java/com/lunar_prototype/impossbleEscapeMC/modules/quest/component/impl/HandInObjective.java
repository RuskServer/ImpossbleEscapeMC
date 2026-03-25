package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * アイテムの納品を目標とするコンポーネント。
 * 特定のID、または特定のカテゴリー(med, gun等)を指定可能。
 */
public class HandInObjective implements QuestObjective {
    private final String itemId;   // null if type-based
    private final String itemType; // null if id-based
    private final int targetAmount;
    private final boolean requireFIR;

    public HandInObjective(String itemId, String itemType, int targetAmount, boolean requireFIR) {
        this.itemId = itemId;
        this.itemType = itemType;
        this.targetAmount = targetAmount;
        this.requireFIR = requireFIR;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.HAND_IN) return false;

        // 納品されたアイテムの情報
        String handedItemId = (String) params.get("itemId");
        String handedItemType = (String) params.get("itemType");
        boolean isFIR = params.containsKey("isFIR") && (boolean) params.get("isFIR");
        int amount = params.containsKey("amount") ? ((Number) params.get("amount")).intValue() : 1;

        if (requireFIR && !isFIR) return false;

        boolean matches = false;
        if (itemId != null) {
            matches = itemId.equalsIgnoreCase(handedItemId);
        } else if (itemType != null) {
            matches = itemType.equalsIgnoreCase(handedItemType);
        }

        if (matches) {
            int current = activeQuest.getProgress(index);
            if (current < targetAmount) {
                activeQuest.setProgress(index, Math.min(targetAmount, current + amount));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isCompleted(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) >= targetAmount;
    }

    @Override
    public String getProgressText(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) + " / " + targetAmount;
    }

    @Override
    public String getDescription() {
        String targetName = itemId;
        if (itemId != null) {
            ItemDefinition def = ItemRegistry.get(itemId);
            if (def != null && def.displayName != null) {
                targetName = def.displayName;
            }
        } else if (itemType != null) {
            targetName = "カテゴリー: " + itemType;
        }

        String target = (itemId != null) ? "アイテム: " + targetName : targetName;
        String firSuffix = requireFIR ? " (FIR品のみ)" : "";
        return target + " を納品する (" + targetAmount + "個)" + firSuffix;
    }

    public boolean isRequireFIR() {
        return requireFIR;
    }
}

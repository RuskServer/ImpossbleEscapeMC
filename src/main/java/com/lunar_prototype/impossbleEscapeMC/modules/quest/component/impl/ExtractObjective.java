package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * 特定のマップ、または任意のマップからの脱出を目標とするコンポーネント
 */
public class ExtractObjective implements QuestObjective {
    private final String mapId; // null or "any" if any map
    private final int targetAmount;

    public ExtractObjective(String mapId, int targetAmount) {
        this.mapId = (mapId == null || mapId.equalsIgnoreCase("any")) ? null : mapId;
        this.targetAmount = targetAmount;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.RAID_EXTRACT) return false;

        if (mapId != null) {
            String extractedMap = (String) params.get("mapId");
            if (extractedMap == null || !extractedMap.equalsIgnoreCase(mapId)) return false;
        }

        int current = activeQuest.getProgress(index);
        if (current < targetAmount) {
            activeQuest.setProgress(index, current + 1);
            return true;
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
        String mapName = (mapId == null) ? "任意のマップ" : mapId;
        return mapName + " から脱出する (" + targetAmount + "回)";
    }
}

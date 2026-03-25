package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * 特定のエンティティを一定数殺害する目標
 */
public class KillEntityObjective implements QuestObjective {
    private final String entityType; // "SCAV", "PLAYER", "BOSS" 等
    private final int targetAmount;

    public KillEntityObjective(String entityType, int targetAmount) {
        this.entityType = entityType;
        this.targetAmount = targetAmount;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.KILL_ENTITY) return false;
        
        String killedType = (String) params.get("entityType");
        if (killedType == null || !killedType.equalsIgnoreCase(entityType)) return false;

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
        return entityType + " を " + targetAmount + " 体排除する";
    }
}

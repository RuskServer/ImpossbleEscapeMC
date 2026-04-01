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
    private final Double minDistance;
    private final Double maxDistance;

    public KillEntityObjective(String entityType, int targetAmount) {
        this(entityType, targetAmount, null, null);
    }

    public KillEntityObjective(String entityType, int targetAmount, Double minDistance, Double maxDistance) {
        this.entityType = entityType;
        this.targetAmount = targetAmount;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.KILL_ENTITY) return false;
        
        String killedType = (String) params.get("entityType");
        if (killedType == null || !killedType.equalsIgnoreCase(entityType)) return false;

        if (!isDistanceInRange(params)) return false;

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
        StringBuilder sb = new StringBuilder();
        sb.append(entityType).append(" を ").append(targetAmount).append(" 体排除する");
        if (minDistance != null || maxDistance != null) {
            sb.append(" (距離: ").append(formatDistanceRange()).append(")");
        }
        return sb.toString();
    }

    private boolean isDistanceInRange(Map<String, Object> params) {
        if (minDistance == null && maxDistance == null) return true;

        Object rawDistance = params.get("killDistance");
        if (!(rawDistance instanceof Number)) return false;

        double distance = ((Number) rawDistance).doubleValue();
        if (minDistance != null && distance < minDistance) return false;
        if (maxDistance != null && distance > maxDistance) return false;
        return true;
    }

    private String formatDistanceRange() {
        if (minDistance != null && maxDistance != null) {
            return minDistance + "m - " + maxDistance + "m";
        }
        if (minDistance != null) {
            return minDistance + "m 以上";
        }
        return maxDistance + "m 以下";
    }
}

package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * 特定の座標地点への到達を目標とするコンポーネント
 */
public class ReachLocationObjective implements QuestObjective {
    private final String worldName;
    private final double x, y, z;
    private final double radiusSquared;
    private final String locationName;

    public ReachLocationObjective(String worldName, double x, double y, double z, double radius, String locationName) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radiusSquared = radius * radius;
        this.locationName = locationName;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.LOCATION_REACHED) return false;

        // すでに完了している場合は無視
        if (isCompleted(activeQuest, index)) return false;

        // トリガーパラメータから位置情報を取得
        String targetName = (String) params.get("locationName");
        if (targetName != null && targetName.equals(locationName)) {
            activeQuest.setProgress(index, 1); // 1 = 到達済み
            return true;
        }
        return false;
    }

    @Override
    public boolean isCompleted(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) >= 1;
    }

    @Override
    public String getProgressText(ActiveQuest activeQuest, int index) {
        return isCompleted(activeQuest, index) ? "到達済み" : "未到達";
    }

    @Override
    public String getDescription() {
        return locationName + " を探索する";
    }

    // マップ連携用のゲッター
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getRadiusSquared() { return radiusSquared; }
    public String getLocationName() { return locationName; }
}

package com.lunar_prototype.impossbleEscapeMC.modules.quest.component;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * クエストの目標を定義するコンポーネント
 */
public interface QuestObjective {
    /**
     * トリガー発生時に進捗を更新
     * @return 進捗に変更があった場合 true
     */
    boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int objectiveIndex, QuestTrigger trigger, Map<String, Object> params);

    /**
     * 目標が完了しているか判定
     */
    boolean isCompleted(ActiveQuest activeQuest, int objectiveIndex);

    /**
     * GUI表示用の進捗テキスト
     */
    String getProgressText(ActiveQuest activeQuest, int objectiveIndex);

    /**
     * 目標の説明文
     */
    String getDescription();
}

package com.lunar_prototype.impossbleEscapeMC.modules.quest.component;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;

/**
 * クエストの受領条件などを定義するコンポーネント
 */
public interface QuestCondition {
    /**
     * 条件を満たしているか判定
     */
    boolean isMet(PlayerData data);

    /**
     * GUI表示用の説明文
     */
    String getDescription();
}

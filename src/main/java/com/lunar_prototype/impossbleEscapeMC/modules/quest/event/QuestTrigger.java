package com.lunar_prototype.impossbleEscapeMC.modules.quest.event;

/**
 * クエスト進行のトリガー
 */
public enum QuestTrigger {
    KILL_ENTITY,        // エンティティ殺害
    COLLECT_ITEM,      // アイテム入手
    RAID_EXTRACT,      // レイド脱出
    INTERACT_NPC,      // NPCとの会話
    LOCATION_REACHED,  // 特定地点への到達
    LEVEL_UP,          // レベルアップ
    HAND_IN            // アイテムの納品
}

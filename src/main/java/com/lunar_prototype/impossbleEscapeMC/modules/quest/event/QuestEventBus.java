package com.lunar_prototype.impossbleEscapeMC.modules.quest.event;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;

/**
 * クエストシステム専用のイベントバス。
 * プレイヤーごとのトリガーを処理し、アクティブなクエストの進行状況を更新する。
 */
public class QuestEventBus {
    
    public interface QuestEventListener {
        void onTrigger(Player player, PlayerData data, QuestTrigger trigger, Map<String, Object> params);
    }

    private final Map<QuestTrigger, java.util.List<QuestEventListener>> listeners = new HashMap<>();

    public void register(QuestTrigger trigger, QuestEventListener listener) {
        listeners.computeIfAbsent(trigger, k -> new java.util.ArrayList<>()).add(listener);
    }

    public void fire(Player player, PlayerData data, QuestTrigger trigger, Map<String, Object> params) {
        java.util.List<QuestEventListener> list = listeners.get(trigger);
        if (list != null) {
            for (QuestEventListener listener : list) {
                listener.onTrigger(player, data, trigger, params);
            }
        }
    }
}

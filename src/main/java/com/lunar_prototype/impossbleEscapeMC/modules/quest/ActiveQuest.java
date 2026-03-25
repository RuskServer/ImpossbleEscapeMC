package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * プレイヤーごとの進行中のクエストデータ
 */
public class ActiveQuest {
    private final String questId;
    private final Map<Integer, Integer> progress; // Objective index -> Current count
    private boolean completed; // 報告可能状態かどうか

    public ActiveQuest(String questId) {
        this.questId = questId;
        this.progress = new HashMap<>();
        this.completed = false;
    }

    public String getQuestId() { return questId; }
    public Map<Integer, Integer> getProgress() { return progress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getProgress(int index) {
        return progress.getOrDefault(index, 0);
    }

    public void setProgress(int index, int value) {
        progress.put(index, value);
    }
}

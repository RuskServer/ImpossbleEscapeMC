package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * プレイヤーごとの進行中のクエストデータ
 */
public class ActiveQuest {
    private final String questId;
    private Map<String, Integer> progress; // Objective index (as string) -> Current count
    private boolean completed; // 報告可能状態かどうか

    public ActiveQuest(String questId) {
        this.questId = questId;
        this.progress = new HashMap<>();
        this.completed = false;
    }

    public String getQuestId() { return questId; }
    public Map<String, Integer> getProgress() {
        if (progress == null) progress = new HashMap<>();
        return progress;
    }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public int getProgress(int index) {
        return getProgress().getOrDefault(String.valueOf(index), 0);
    }

    public void setProgress(int index, int value) {
        getProgress().put(String.valueOf(index), value);
    }
}

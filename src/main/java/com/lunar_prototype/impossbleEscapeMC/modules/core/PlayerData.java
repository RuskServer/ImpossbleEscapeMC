package com.lunar_prototype.impossbleEscapeMC.modules.core;

import java.util.UUID;

/**
 * プレイヤーのデータを保持するデータクラス (POJO)
 * GSONによってJSONにシリアライズされる
 */
public class PlayerData {
    private final UUID uuid;
    private double balance;
    private int level;
    private long experience;

    // トレーダーの購入制限用: キーは "traderId_itemId"
    private java.util.Map<String, Integer> dailyPurchases;
    private long lastResetTimestamp;

    // 非表示フラグ: メモリ上での変更があった場合にtrueにする
    private transient boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.balance = 0.0;
        this.level = 1;
        this.experience = 0;
        this.dailyPurchases = new java.util.HashMap<>();
        this.lastResetTimestamp = System.currentTimeMillis();
        this.dirty = false;
    }

    public java.util.Map<String, Integer> getDailyPurchases() {
        if (dailyPurchases == null) dailyPurchases = new java.util.HashMap<>();
        return dailyPurchases;
    }

    public long getLastResetTimestamp() {
        return lastResetTimestamp;
    }

    public void setLastResetTimestamp(long lastResetTimestamp) {
        this.lastResetTimestamp = lastResetTimestamp;
        this.dirty = true;
    }

    public void incrementPurchase(String key) {
        int current = getDailyPurchases().getOrDefault(key, 0);
        getDailyPurchases().put(key, current + 1);
        this.dirty = true;
    }

    public void clearDailyPurchases() {
        getDailyPurchases().clear();
        this.lastResetTimestamp = System.currentTimeMillis();
        this.dirty = true;
    }

    public UUID getUuid() {
        return uuid;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
        this.dirty = true;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        this.dirty = true;
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = experience;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}

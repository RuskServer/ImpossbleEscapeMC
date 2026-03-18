package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightStage;
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
    private int extractions;

    // 重量システム
    private int currentWeight; // グラム単位
    private WeightStage weightStage;

    // Stash
    private int stashLevel;
    private java.util.Map<Integer, String> stashPages; // Page index (1-based) to Base64 serialized inventory

    // トレーダーの購入制限用: キーは "traderId_itemId"
    private java.util.Map<String, Integer> dailyPurchases;
    private long lastResetTimestamp;

    // 非表示フラグ: メモリ上での変更があった場合にtrueにする
    private transient boolean dirty;

    // 状態異常システム
    private boolean legFracture;
    private boolean armFracture;
    private int bleedingLevel; // 0: なし, 1以上: 出血中
    private long painkillerUntil; // 鎮痛効果終了時間 (ms)
    private long lastPainkillerTrigger; // 最後の自動鎮痛発動時間 (ms)

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.balance = 0.0;
        this.level = 1;
        this.experience = 0;
        this.currentWeight = 0;
        this.weightStage = WeightStage.LIGHT;
        this.stashLevel = 1;
        this.stashPages = new java.util.HashMap<>();
        this.dailyPurchases = new java.util.HashMap<>();
        this.lastResetTimestamp = System.currentTimeMillis();
        this.dirty = false;
        
        this.legFracture = false;
        this.armFracture = false;
        this.bleedingLevel = 0;
        this.painkillerUntil = 0;
        this.lastPainkillerTrigger = 0;
    }

    public int getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(int currentWeight) {
        if (this.currentWeight != currentWeight) {
            this.currentWeight = currentWeight;
            this.dirty = true;
            // ステージも合わせて更新
            this.weightStage = WeightStage.getFromWeight(currentWeight);
        }
    }

    public WeightStage getWeightStage() {
        if (weightStage == null) {
            weightStage = WeightStage.getFromWeight(currentWeight);
        }
        return weightStage;
    }

    public void setWeightStage(WeightStage weightStage) {
        if (this.weightStage != weightStage) {
            this.weightStage = weightStage;
            this.dirty = true;
        }
    }

    public int getStashLevel() {
        return Math.max(1, stashLevel);
    }

    public void setStashLevel(int stashLevel) {
        if (this.stashLevel != stashLevel) {
            this.stashLevel = stashLevel;
            this.dirty = true;
        }
    }

    public java.util.Map<Integer, String> getStashPages() {
        if (stashPages == null) stashPages = new java.util.HashMap<>();
        return stashPages;
    }

    public void setStashPage(int page, String data) {
        getStashPages().put(page, data);
        this.dirty = true;
    }

    public boolean hasLegFracture() {
        return legFracture;
    }

    public void setLegFracture(boolean legFracture) {
        if (this.legFracture != legFracture) {
            this.legFracture = legFracture;
            this.dirty = true;
        }
    }

    public boolean hasArmFracture() {
        return armFracture;
    }

    public void setArmFracture(boolean armFracture) {
        if (this.armFracture != armFracture) {
            this.armFracture = armFracture;
            this.dirty = true;
        }
    }

    public int getBleedingLevel() {
        return bleedingLevel;
    }

    public void setBleedingLevel(int bleedingLevel) {
        if (this.bleedingLevel != bleedingLevel) {
            this.bleedingLevel = bleedingLevel;
            this.dirty = true;
        }
    }

    public long getPainkillerUntil() {
        return painkillerUntil;
    }

    public void setPainkillerUntil(long painkillerUntil) {
        if (this.painkillerUntil != painkillerUntil) {
            this.painkillerUntil = painkillerUntil;
            this.dirty = true;
        }
    }

    public boolean isPainkillerActive() {
        return System.currentTimeMillis() < painkillerUntil;
    }

    public long getLastPainkillerTrigger() {
        return lastPainkillerTrigger;
    }

    public void setLastPainkillerTrigger(long lastPainkillerTrigger) {
        this.lastPainkillerTrigger = lastPainkillerTrigger;
        this.dirty = true;
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

    public int getExtractions() {
        return extractions;
    }

    public void setExtractions(int extractions) {
        this.extractions = extractions;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}

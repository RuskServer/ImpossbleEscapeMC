package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.AbstractQuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * アイテムの納品を目標とするコンポーネント。
 * 特定のID、または特定のカテゴリー(med, gun等)を指定可能。
 */
public class HandInObjective extends AbstractQuestObjective {
    private final String itemId;   // null if type-based
    private final String itemType; // null if id-based
    private final int targetAmount;
    private final boolean requireFIR;
    private final String caliber;
    private final Integer minAmmoClass;
    private final Integer minArmorClass;

    public HandInObjective(String itemId, String itemType, int targetAmount, boolean requireFIR) {
        this(itemId, itemType, targetAmount, requireFIR, null, null, null);
    }

    public HandInObjective(String itemId, String itemType, int targetAmount, boolean requireFIR,
                           String caliber, Integer minAmmoClass, Integer minArmorClass) {
        if (targetAmount <= 0) throw new IllegalArgumentException("納品数は正の値が必要です");
        this.caliber = caliber;
        this.minAmmoClass = minAmmoClass;
        this.minArmorClass = minArmorClass;
        this.itemId = itemId;
        this.itemType = itemType;
        this.targetAmount = targetAmount;
        this.requireFIR = requireFIR;
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.HAND_IN) return false;

        String handedItemId = (String) params.get("itemId");
        String handedItemType = (String) params.get("itemType");
        boolean isFIR = Boolean.TRUE.equals(params.get("isFIR"));
        int amount = params.get("amount") instanceof Number number ? number.intValue() : 1;
        if (amount <= 0 || !matches(handedItemId, handedItemType, isFIR)) return false;
        int current = activeQuest.getProgress(index);
        if (current < targetAmount) {
            activeQuest.setProgress(index, current + Math.min(amount, targetAmount - current));
            return true;
        }
        return false;
    }

    /** GUIと進捗更新で共通の納品条件。性能は登録済み定義から読む。 */
    public boolean matches(String handedItemId, String handedItemType, boolean isFIR) {
        if (requireFIR && !isFIR) return false;
        if (itemId != null) {
            if (!itemId.equalsIgnoreCase(handedItemId)) return false;
        } else if (itemType == null || !itemType.equalsIgnoreCase(handedItemType)) return false;
        if (caliber != null || minAmmoClass != null) {
            var ammo = ItemRegistry.getAmmo(handedItemId);
            if (ammo == null || (caliber != null && !caliber.equalsIgnoreCase(ammo.caliber))
                    || (minAmmoClass != null && ammo.ammoClass < minAmmoClass)) return false;
        }
        if (minArmorClass != null) {
            var armor = ItemRegistry.get(handedItemId);
            if (armor == null || armor.armorStats == null || armor.armorStats.armorClass < minArmorClass) return false;
        }
        return true;
    }

    public String getCaliber() { return caliber; }
    public Integer getMinAmmoClass() { return minAmmoClass; }
    public Integer getMinArmorClass() { return minArmorClass; }

    @Override
    public boolean isCompleted(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) >= targetAmount;
    }

    @Override
    public String getProgressText(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) + " / " + targetAmount;
    }

    @Override
    protected String defaultDescription() {
        String targetName = itemId;
        if (itemId != null) {
            var def = com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestItems.resolve(itemId);
            if (def != null && def.name() != null) {
                targetName = def.name();
            }
        } else if (itemType != null) {
            targetName = switch (itemType.toUpperCase(java.util.Locale.ROOT)) {
                case "AMMO" -> "弾薬";
                case "ARMOR" -> "防具";
                case "MED" -> "医療品";
                case "GUN" -> "銃";
                case "ATTACHMENT" -> "アタッチメント";
                case "BACKPACK" -> "バックパック";
                case "RIG" -> "リグ";
                default -> "カテゴリー: " + itemType;
            };
        }

        String target = (itemId != null) ? "アイテム: " + targetName : targetName;
        if (caliber != null) target += " / " + caliber;
        if (minAmmoClass != null) target += " / 貫通クラス" + minAmmoClass + "以上";
        if (minArmorClass != null) target += " / 防具クラス" + minArmorClass + "以上";
        String firSuffix = requireFIR ? " (FIR品のみ)" : "";
        return target + " を納品する (" + targetAmount + ("AMMO".equalsIgnoreCase(itemType) ? "発)" : "個)") + firSuffix;
    }

    public boolean isRequireFIR() {
        return requireFIR;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemType() {
        return itemType;
    }

    public int getTargetAmount() {
        return targetAmount;
    }
}

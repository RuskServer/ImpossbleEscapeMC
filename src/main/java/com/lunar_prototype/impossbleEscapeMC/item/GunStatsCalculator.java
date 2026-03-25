package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GunStatsCalculator {

    /**
     * アイテムスタックから現在のアタッチメントを考慮した実効ステータスを計算します。
     * @param item 銃アイテム
     * @param baseStats 銃の基本ステータス
     * @return 修正適用後のステータス（新しいGunStatsオブジェクト）
     */
    public static GunStats calculateEffectiveStats(ItemStack item, GunStats baseStats) {
        if (item == null || baseStats == null) return baseStats;

        // クローンを作成して基本値で初期化
        GunStats effective = cloneStats(baseStats);
        
        // 1. PDCにある "affix" (恒久的な修正) を適用
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            effective.damage = pdc.getOrDefault(PDCKeys.affix("damage"), PDCKeys.DOUBLE, effective.damage);
            effective.recoil = pdc.getOrDefault(PDCKeys.affix("recoil"), PDCKeys.DOUBLE, effective.recoil);
            effective.adsTime = pdc.getOrDefault(PDCKeys.affix("adsTime"), PDCKeys.INTEGER, effective.adsTime);
        }

        // 2. 装着されているアタッチメントの修正を適用
        List<AttachmentDefinition> attachments = getEquippedAttachments(item);
        
        double damageMult = 1.0;
        double recoilMult = 1.0;
        int adsTimeAdd = 0;

        for (AttachmentDefinition att : attachments) {
            if (att.modifiers != null) {
                damageMult += att.modifiers.getOrDefault("damage", 0.0);
                recoilMult += att.modifiers.getOrDefault("recoil", 0.0);
                adsTimeAdd += att.modifiers.getOrDefault("adsTime", 0.0).intValue();
            }
            
            // エイムアニメーションのオーバーライド (最後に定義されていたものが優先)
            if (att.aimAnimation != null) {
                effective.aimAnimation = att.aimAnimation;
            }
            // スコープ設定のオーバーライド
            if (att.scope != null) {
                effective.scope = att.scope;
            }
        }

        effective.damage *= damageMult;
        effective.recoil *= recoilMult;
        effective.adsTime += adsTimeAdd;

        // 最小値ガード
        effective.damage = Math.max(0, effective.damage);
        effective.recoil = Math.max(0, effective.recoil);
        effective.adsTime = Math.max(0, effective.adsTime);

        return effective;
    }

    private static GunStats cloneStats(GunStats base) {
        GunStats copy = new GunStats();
        copy.damage = base.damage;
        copy.recoil = base.recoil;
        copy.rpm = base.rpm;
        copy.magSize = base.magSize;
        copy.fireMode = base.fireMode;
        copy.pelletCount = base.pelletCount;
        copy.customModelData = base.customModelData;
        copy.adsTime = base.adsTime;
        copy.boltingTime = base.boltingTime;
        copy.shotSound = base.shotSound;
        copy.caliber = base.caliber;
        copy.boltType = base.boltType;
        copy.defaultAttachments = base.defaultAttachments;
        copy.reloadAnimation = base.reloadAnimation;
        copy.tacticalReloadAnimation = base.tacticalReloadAnimation;
        copy.reloadLoopAnimation = base.reloadLoopAnimation;
        copy.boltingAnimation = base.boltingAnimation;
        copy.independentAnimation = base.independentAnimation;
        copy.validIndependentAnimStates = base.validIndependentAnimStates;
        copy.aimAnimation = base.aimAnimation;
        copy.sprintAnimation = base.sprintAnimation;
        copy.idleAnimation = base.idleAnimation;
        copy.scope = base.scope;
        return copy;
    }

    private static List<AttachmentDefinition> getEquippedAttachments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Collections.emptyList();
        
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
        if (joined == null || joined.isEmpty()) return Collections.emptyList();
        
        List<AttachmentDefinition> list = new ArrayList<>();
        for (String id : joined.split(",")) {
            if (id.isEmpty()) continue;
            AttachmentDefinition def = ItemRegistry.getAttachment(id);
            if (def != null) {
                list.add(def);
            }
        }
        return list;
    }
}

package com.lunar_prototype.impossbleEscapeMC.modules.level;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * レベルシステムを担当するモジュール
 */
public class LevelModule implements IModule {
    public static final long SCAV_BASE_EXP = 150L;

    private PlayerDataModule dataModule;

    @Override
    public void onEnable(ServiceContainer container) {
        // DIコンテナからPlayerDataModuleを取得
        this.dataModule = container.get(PlayerDataModule.class);
    }

    @Override
    public void onDisable() {
    }

    /**
     * 現在のレベルを取得
     */
    public int getLevel(UUID uuid) {
        return dataModule.getPlayerData(uuid).getLevel();
    }

    /**
     * 現在の経験値を取得
     */
    public long getExperience(UUID uuid) {
        return dataModule.getPlayerData(uuid).getExperience();
    }

    /**
     * 次のレベルに必要な経験値を計算 (例: level * 1000)
     */
    public long getRequiredExperience(int level) {
        return (long) level * 1000;
    }

    /**
     * レイドの結末に応じたベース報酬を計算
     * @param level プレイヤーの現在のレベル
     * @param outcome 結末 (SURVIVED, DEAD, MIA, LEFT)
     * @return 経験値量
     */
    public long getRaidOutcomeReward(int level, String outcome) {
        long required = getRequiredExperience(level);
        return switch (outcome.toUpperCase()) {
            case "SURVIVED" -> (long) (required * 0.05); // 5%
            case "DEAD" -> (long) (required * 0.01);     // 1%
            case "MIA", "LEFT" -> 0L; // 参加だけでは経験値を与えない
            default -> 0L;
        };
    }

    /**
     * レベルに応じてスケーリングされたキル報酬を計算
     * @param level プレイヤーの現在のレベル
     * @param baseAmount 基礎経験値量 (例: SCAV=150, BOSS=250)
     * @return スケーリング後の経験値量
     */
    public long getScaledKillReward(int level, long baseAmount) {
        // レベル1を基準(100%)とし、1レベルごとに10%増加
        double multiplier = 1.0 + (Math.max(0, level - 1) * 0.1);
        return (long) (baseAmount * multiplier);
    }

    /**
     * 経験値を追加。レベルアップした場合は再帰的に判定。
     */
    public void addExperience(UUID uuid, long amount) {
        if (amount <= 0) return;
        PlayerData data = dataModule.getPlayerData(uuid);
        long newExp = data.getExperience() + amount;
        data.setExperience(newExp);

        checkLevelUp(data);
        dataModule.saveAsync(uuid);
    }

    /**
     * レベルアップ判定
     */
    private void checkLevelUp(PlayerData data) {
        int currentLevel = data.getLevel();
        long required = getRequiredExperience(currentLevel);

        if (data.getExperience() >= required) {
            // レベルアップ
            data.setExperience(data.getExperience() - required);
            data.setLevel(currentLevel + 1);

            // メッセージ表示などの通知 (Playerがオンラインの場合)
            Player player = Bukkit.getPlayer(data.getUuid());
            if (player != null) {
                Messages.chat(player, "iem.progression.levelmodule.checklevelup.notice_001", MessageText.of("iem.progression.levelmodule.checklevelup.notice_001", MessageText.payload(MessageText.of("iem.progression.levelmodule.checklevelup.notice_001.text_01", currentLevel, (currentLevel + 1)))));
                // TODO: 必要に応じてLevelUpEventを配布
            }

            // さらに次のレベルに達しているか確認 (再帰)
            checkLevelUp(data);
        }
    }
}

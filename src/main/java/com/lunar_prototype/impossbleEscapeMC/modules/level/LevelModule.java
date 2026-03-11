package com.lunar_prototype.impossbleEscapeMC.modules.level;

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
                player.sendMessage("§a§lLEVEL UP! §f" + currentLevel + " -> §e" + (currentLevel + 1));
                // TODO: 必要に応じてLevelUpEventを配布
            }

            // さらに次のレベルに達しているか確認 (再帰)
            checkLevelUp(data);
        }
    }
}

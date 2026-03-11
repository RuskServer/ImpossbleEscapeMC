package com.lunar_prototype.impossbleEscapeMC.modules.economy;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;

import java.util.UUID;

/**
 * 経済システムを担当するモジュール
 */
public class EconomyModule implements IModule {
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
     * 所持金を取得
     */
    public double getBalance(UUID uuid) {
        return dataModule.getPlayerData(uuid).getBalance();
    }

    /**
     * 入金
     */
    public void deposit(UUID uuid, double amount) {
        if (amount <= 0) return;
        PlayerData data = dataModule.getPlayerData(uuid);
        data.setBalance(data.getBalance() + amount);
        dataModule.saveAsync(uuid);
    }

    /**
     * 出金
     */
    public boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0) return false;
        PlayerData data = dataModule.getPlayerData(uuid);
        double current = data.getBalance();
        if (current < amount) return false;

        data.setBalance(current - amount);
        dataModule.saveAsync(uuid);
        return true;
    }

    /**
     * 指定額以上の所持金があるか確認
     */
    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }
}

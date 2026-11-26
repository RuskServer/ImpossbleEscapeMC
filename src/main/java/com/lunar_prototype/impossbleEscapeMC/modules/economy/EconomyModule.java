package com.lunar_prototype.impossbleEscapeMC.modules.economy;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;

import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;

import java.util.UUID;

/**
 * 経済システムを担当するモジュール
 */
@Service
public class EconomyModule implements IModule {
    private final PlayerDataModule dataModule;

    @Inject
    public EconomyModule(PlayerDataModule dataModule) {

        this.dataModule = dataModule;
    }

    @Override
    public void onEnable(ServiceContainer container) {
    }

    @Override
    public void onDisable() {
    }

    public boolean canTransact(UUID uuid) {
        return dataModule.isDataAvailable(uuid);
    }

    /**
     * 所持金を取得
     */
    public double getBalance(UUID uuid) {
        return canTransact(uuid) ? dataModule.getPlayerData(uuid).getBalance() : 0;
    }

    /**
     * 入金
     */
    public void deposit(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        if (!canTransact(uuid)) throw new IllegalStateException("Player data is unavailable for deposit: " + uuid);
        PlayerData data = dataModule.getPlayerData(uuid);
        data.setBalance(data.getBalance() + amount);
        dataModule.saveAsync(uuid);
    }

    /**
     * 出金
     */
    public boolean withdraw(UUID uuid, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !canTransact(uuid)) return false;
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
        return Double.isFinite(amount) && amount >= 0 && canTransact(uuid) && getBalance(uuid) >= amount;
    }
}

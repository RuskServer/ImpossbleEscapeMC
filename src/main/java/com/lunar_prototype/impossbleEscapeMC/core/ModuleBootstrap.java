package com.lunar_prototype.impossbleEscapeMC.core;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import java.util.ArrayList;
import java.util.List;

/**
 * モジュールの登録とライフサイクルの一括管理を担当
 */
public class ModuleBootstrap {
    private final ImpossbleEscapeMC plugin;
    private final ServiceContainer container;
    private final List<IModule> modules = new ArrayList<>();

    public ModuleBootstrap(ImpossbleEscapeMC plugin, ServiceContainer container) {
        this.plugin = plugin;
        this.container = container;
    }

    /**
     * モジュールを登録。登録順に起動される。
     */
    public void registerModule(IModule module) {
        modules.add(module);
    }

    /**
     * 全モジュールを有効化
     */
    public void enableModules() {
        for (IModule module : modules) {
            try {
                plugin.getLogger().info("Enabling module: " + module.getClass().getSimpleName());
                module.onEnable(container);
                // モジュール自身もサービスとして登録（他のモジュールから取得可能にする）
                // Raw Typeキャストを使用してジェネリクスの不変性によるエラーを回避
                container.register((Class) module.getClass(), module);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to enable module: " + module.getClass().getSimpleName());
                e.printStackTrace();
            }
        }
    }

    /**
     * 全モジュールを無効化
     */
    public void disableModules() {
        for (int i = modules.size() - 1; i >= 0; i--) { // 起動とは逆順に停止
            try {
                modules.get(i).onDisable();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to disable module: " + modules.get(i).getClass().getSimpleName());
                e.printStackTrace();
            }
        }
    }
}

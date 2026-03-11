package com.lunar_prototype.impossbleEscapeMC.core;

/**
 * モジュールのライフサイクルを定義するインターフェース
 */
public interface IModule {
    /**
     * モジュール有効化時の処理
     * @param container DIコンテナ。他のモジュールやサービスを取得するために使用
     */
    void onEnable(ServiceContainer container);

    /**
     * モジュール無効化時の処理
     */
    void onDisable();
}

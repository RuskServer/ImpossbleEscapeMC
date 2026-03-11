package com.lunar_prototype.impossbleEscapeMC.core;

import java.util.HashMap;
import java.util.Map;

/**
 * サービスやマネージャーのインスタンスを管理するシンプルなDIコンテナ
 */
public class ServiceContainer {
    private final Map<Class<?>, Object> services = new HashMap<>();

    /**
     * インスタンスを登録
     */
    public <T> void register(Class<T> clazz, T instance) {
        services.put(clazz, instance);
    }

    /**
     * インスタンスを取得
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz) {
        Object service = services.get(clazz);
        if (service == null) {
            // インターフェースや親クラスでの登録も考慮して線形探索
            for (Map.Entry<Class<?>, Object> entry : services.entrySet()) {
                if (clazz.isAssignableFrom(entry.getKey())) {
                    return (T) entry.getValue();
                }
            }
        }
        return (T) service;
    }
}

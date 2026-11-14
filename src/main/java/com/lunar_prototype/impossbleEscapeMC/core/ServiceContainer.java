package com.lunar_prototype.impossbleEscapeMC.core;

import com.lunar_prototype.impossbleEscapeMC.core.di.DIContainer;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Component;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Module;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Repository;
import com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry;
import io.papermc.paper.command.brigadier.BasicCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** 既存get/registerを保ったDIコンテナ。同じレジストリを使い、二重生成を防ぐ。 */
public class ServiceContainer extends DIContainer {
    public ServiceContainer() { registerInstance(ServiceContainer.class, this); }
    public <T> void register(Class<T> type, T instance) { registerInstance(type, instance); }
    public <T> T get(Class<T> type) { return findInstance(type); }

    @Override protected void startInstance(Object instance) {
        if (instance instanceof IModule module) {
            // 既存モジュール自身がリスナーとコマンドを登録するので自動登録は重ねない。
            module.onEnable(this);
        } else {
            super.startInstance(instance);
            if (!automatic(instance)) return;
            if (instance instanceof Listener listener) {
                JavaPlugin plugin = get(JavaPlugin.class);
                if (plugin == null) throw new IllegalStateException("JavaPlugin is required for a managed Listener");
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            }
            if (instance instanceof BasicCommand command) {
                CommandRegistry registry = get(CommandRegistry.class);
                if (registry == null) throw new IllegalStateException("CommandRegistry is required for a managed command");
                registry.register(command);
            }
        }
    }
    @Override protected void stopInstance(Object instance) {
        if (instance instanceof IModule module) module.onDisable();
        else {
            try {
                if (automatic(instance) && instance instanceof Listener listener) HandlerList.unregisterAll(listener);
            } finally { super.stopInstance(instance); }
        }
    }
    private static boolean automatic(Object instance) {
        Class<?> type = instance.getClass();
        return type.isAnnotationPresent(Component.class) || type.isAnnotationPresent(Service.class)
                || type.isAnnotationPresent(Module.class) || type.isAnnotationPresent(Repository.class);
    }
}

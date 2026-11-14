package com.lunar_prototype.impossbleEscapeMC.core;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.LinkedHashSet;
import java.util.Set;

/** 既存モジュールの順序を保ち、検出・登録が揃ってからDIで構築する。 */
public class ModuleBootstrap {
    private final ServiceContainer container;
    private final JavaPlugin plugin;
    private final String basePackage;
    private final Set<Class<?>> modules = new LinkedHashSet<>();
    private Class<?> previous;

    public ModuleBootstrap(JavaPlugin plugin, ServiceContainer container) {
        this(plugin, container, "com.lunar_prototype.impossbleEscapeMC");
    }
    public ModuleBootstrap(JavaPlugin plugin, ServiceContainer container, String basePackage) {
        this.container = container;
        this.plugin = plugin;
        this.basePackage = basePackage;
        container.setLogger(plugin.getLogger());
    }
    public <T extends IModule> void registerModule(Class<T> type) {
        container.registerTypes(type);
        orderModule(type);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerModule(IModule module) {
        Class<?> type = module.getClass();
        container.register((Class) type, module);
        orderModule(type);
    }
    private void orderModule(Class<?> type) {
        if (!modules.add(type)) throw new IllegalArgumentException("Module already registered: " + type.getName());
        if (previous != null) container.startAfter(type, previous);
        previous = type;
    }
    public void enableModules() {
        container.scan(getClass().getClassLoader(), basePackage);
        container.startAll();
        if (plugin.getConfig().getBoolean("di.debug", false)) container.printDependencyTree();
    }
    public void disableModules() { container.stopAll(); }
}

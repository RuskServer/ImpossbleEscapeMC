package com.lunar_prototype.impossbleEscapeMC.testing;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.extension.*;
import java.lang.reflect.Field;

/** プロキシを使う検証で差し替えたBukkit/プラグイン参照を、失敗した場合も戻す。 */
public final class BukkitState implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NS = ExtensionContext.Namespace.create(BukkitState.class);
    @Override public void beforeEach(ExtensionContext context) throws Exception {
        context.getStore(NS).put("server", new Saved(field(Bukkit.class, "server")));
        context.getStore(NS).put("plugin", new Saved(field(ImpossbleEscapeMC.class, "instance")));
    }
    @Override public void afterEach(ExtensionContext context) throws Exception {
        try { context.getStore(NS).remove("plugin", Saved.class).restore(); }
        finally { context.getStore(NS).remove("server", Saved.class).restore(); }
    }
    private record Saved(Field field, Object value) {
        Saved(Field field) throws IllegalAccessException { this(field, field.get(null)); }
        void restore() throws IllegalAccessException { field.set(null, value); }
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }
}

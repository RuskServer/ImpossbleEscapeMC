package com.lunar_prototype.impossbleEscapeMC.testing;

/** Bukkitの起動やファイル生成を伴うコンストラクタを避け、実ハンドラーだけを検証する。 */
public final class FixtureObjects {
    private FixtureObjects() { }
    public static <T> T allocate(Class<T> type) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
    public static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    public static Object get(Class<?> owner, Object target, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
}

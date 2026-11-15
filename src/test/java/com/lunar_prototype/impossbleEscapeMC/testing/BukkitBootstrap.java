package com.lunar_prototype.impossbleEscapeMC.testing;

import org.junit.jupiter.api.extension.*;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Bukkitの静的レジストリを、テスト用プロキシが使われる前に正規のMockBukkitで初期化する。 */
public final class BukkitBootstrap implements BeforeAllCallback {
    private static boolean initialized;
    @Override public void beforeAll(ExtensionContext context) {
        synchronized (BukkitBootstrap.class) {
            if (initialized) return;
            // この後のテストにはサーバーを残さない。各フィクスチャが自分の環境を作る。
            try { MockBukkit.mock(); }
            finally { MockBukkit.unmock(); }
            initialized = true;
        }
    }
}

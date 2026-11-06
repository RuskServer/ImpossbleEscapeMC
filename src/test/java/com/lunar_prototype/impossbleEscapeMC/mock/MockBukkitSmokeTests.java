package com.lunar_prototype.impossbleEscapeMC.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** MockBukkit がこのプロジェクトのクラスパス (paper-nms など) で起動できるかの確認 */
class MockBukkitSmokeTests {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void serverStartsAndPlayerJoins() {
        PlayerMock player = server.addPlayer();
        assertNotNull(player);
        assertEquals(1, server.getOnlinePlayers().size());
    }
}

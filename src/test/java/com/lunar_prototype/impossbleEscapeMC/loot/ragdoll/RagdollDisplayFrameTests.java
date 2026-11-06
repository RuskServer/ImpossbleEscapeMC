package com.lunar_prototype.impossbleEscapeMC.loot.ragdoll;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * 見た目の Display は向き (yaw・pitch) が 0 の位置に置く。向きが付いていると変換 (translation) が回り、
 * ワールド座標に置いた操作箱と見た目がずれて、右クリックが当たらなくなる。
 */
class RagdollDisplayFrameTests {

    @Test
    void yawAndPitchAreZeroedWhilePositionIsKept() {
        Location frame = RagdollDisplayFrame.at(new Location(null, 1.5, 64.0, -3.25, 137f, -42f));

        assertEquals(0f, frame.getYaw());
        assertEquals(0f, frame.getPitch());
        assertEquals(1.5, frame.getX());
        assertEquals(64.0, frame.getY());
        assertEquals(-3.25, frame.getZ());
    }

    @Test
    void originKeepsItsRotationForThePhysics() {
        Location origin = new Location(null, 0, 70, 0, -90f, 25f);

        Location frame = RagdollDisplayFrame.at(origin);

        assertNotSame(origin, frame);
        assertEquals(-90f, origin.getYaw(), "物理は origin の向きを初期姿勢に使うので、変えてはいけない");
        assertEquals(25f, origin.getPitch());
    }
}

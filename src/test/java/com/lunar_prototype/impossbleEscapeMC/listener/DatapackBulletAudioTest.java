package com.lunar_prototype.impossbleEscapeMC.listener;

import org.bukkit.util.Vector;

/** 高速な弾の1tick分の移動でも、通過した位置を線分上で拾えることを確認する。 */
public final class DatapackBulletAudioTest {
    public static void main(String[] args) {
        Vector start = new Vector(0, 0, 0);
        Vector end = new Vector(40, 0, 0);
        assertAt(new Vector(20, 0, 0), DatapackBulletAudio.closestPoint(start, end, new Vector(20, 2, 0)));
        assertAt(start, DatapackBulletAudio.closestPoint(start, end, new Vector(-3, 1, 0)));
        assertAt(end, DatapackBulletAudio.closestPoint(start, end, new Vector(43, 1, 0)));
        System.out.println("DatapackBulletAudioTest OK");
    }

    private static void assertAt(Vector expected, Vector actual) {
        if (expected.distanceSquared(actual) > 1e-9) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}

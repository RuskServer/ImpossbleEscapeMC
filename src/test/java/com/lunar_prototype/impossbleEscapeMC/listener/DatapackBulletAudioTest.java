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
        check(!DatapackBulletAudio.canHearWhizz(12, 12), "shooter never hears own bullet");
        check(DatapackBulletAudio.canHearWhizz(12, 34), "another player's bullet remains audible");
        check(!DatapackBulletAudio.canHearWhizz(Integer.MIN_VALUE, 12), "unknown owner must not fall back to distance");
        check(!DatapackBulletAudio.canHearWhizz(12, Integer.MIN_VALUE), "unknown listener must not hear a possible own bullet");
        check(!DatapackBulletAudio.canHearWhizz(Integer.MIN_VALUE, Integer.MIN_VALUE), "missing scoreboard IDs stay silent");
        System.out.println("DatapackBulletAudioTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertAt(Vector expected, Vector actual) {
        if (expected.distanceSquared(actual) > 1e-9) {
            throw new AssertionError("expected " + expected + " but got " + actual);
        }
    }
}

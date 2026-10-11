package com.lunar_prototype.impossbleEscapeMC.listener;

import org.bukkit.util.Vector;

/** Deterministic direction/probability boundary checks; no running Bukkit server. */
public final class VisorRicochetTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        Vector front = new Vector(0, 0, 1);
        Vector incoming = new Vector(0, 0, -10);
        check(BulletDamageModel.rollVisorRicochet("head", .1, front, incoming, .099), "front below threshold");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, incoming, .1), "threshold excluded");
        check(!BulletDamageModel.rollVisorRicochet("body", .1, front, incoming, 0), "body excluded");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, new Vector(0, 0, 1), 0), "rear excluded");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, new Vector(1, 0, 0), 0), "side excluded");
        check(BulletDamageModel.rollVisorRicochet("head", .1, front, new Vector(.8, 0, -.6), 0), "front oblique");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, new Vector(.95, 0, -.3), 0), "outside front cone");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, new Vector(0, -1, 0), 0), "vertical excluded");
        check(!BulletDamageModel.rollVisorRicochet("head", 0, front, incoming, 0), "unconfigured visor");
        check(!BulletDamageModel.rollVisorRicochet("head", Double.NaN, front, incoming, 0), "invalid chance");
        check(!BulletDamageModel.rollVisorRicochet("head", .1, front, null, 0), "unknown direction");
        check(incoming.getZ() == -10 && front.getZ() == 1, "vectors unchanged");
        System.out.println("VisorRicochetTest: front/rear/side/body/vertical and probability boundaries passed");
    }
}

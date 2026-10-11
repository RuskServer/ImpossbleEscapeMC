package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;

/** Deterministic common damage calculation checks; no running server. */
public final class AmmoDamageTest {
    private static void equal(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1e-9) throw new AssertionError(message + ": " + actual);
    }
    public static void main(String[] args) {
        AmmoDefinition ps = new AmmoDefinition(); ps.damage = 12;
        AmmoDefinition hp = new AmmoDefinition(); hp.damage = 15;
        AmmoDefinition pellet = new AmmoDefinition(); pellet.damage = 4;
        equal(BulletDamageModel.baseDamage(ps), 12, "PS definition used directly");
        equal(BulletDamageModel.baseDamage(hp), 15, "HP definition used directly");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "body", true), 12, "body");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "head", true), 14.4, "head");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "legs", true), 7.2, "legs");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "arms", true), 12, "arms");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "body", false), 1.8, "armor stops");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "head", false), 2.16, "head and armor compose once");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(pellet), "body", true), 4, "per pellet");
        equal(BulletDamageModel.baseDamage(null), 5, "unknown ammo fixed fallback");
        System.out.println("AmmoDamageTest: direct ammo damage, hit locations, armor and per-pellet damage passed");
    }
}

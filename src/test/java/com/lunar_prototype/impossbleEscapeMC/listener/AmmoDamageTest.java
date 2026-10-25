package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoCatalog;

/** Deterministic common damage calculation checks; no running server. */
public final class AmmoDamageTest {
    private static void equal(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1e-9) throw new AssertionError(message + ": " + actual);
    }
    public static void main(String[] args) {
        AmmoDefinition ps = new AmmoDefinition(); ps.damage = 12;
        AmmoDefinition hp = new AmmoDefinition(); hp.damage = 15;
        AmmoDefinition shell = new AmmoDefinition(); shell.damage = 9;
        equal(BulletDamageModel.baseDamage(ps), 12, "PS definition used directly");
        equal(BulletDamageModel.baseDamage(hp), 15, "HP definition used directly");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "body", true, 0), 12, "body");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "head", true, 0), 14.4, "head");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "legs", true, 0), 7.2, "legs");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "arms", true, 0), 12, "arms");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "body", false, 3), 0.0, "armor stops all damage");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(ps), "head", false, 3), 0.0, "head and armor stop all damage");
        equal(BulletDamageModel.baseDamage(shell, 12), 0.75, "one of twelve pellets");
        equal(BulletDamageModel.damageForHit(BulletDamageModel.baseDamage(shell, 12), "body", true, 0) * 12,
                9, "all pellets equal shell theory damage");
        equal(BulletDamageModel.damageForHit(12, "body", true, 3), 12 * 0.7, "penetrated class 3 armor still absorbs 30%");
        equal(BulletDamageModel.damageForHit(12, "head", true, 4), 14.4 * 0.6, "penetrated class 4 helmet");
        equal(BulletDamageModel.damageForHit(12, "body", true, 9), 12 * 0.4, "armor class capped at 6");
        equal(BulletDamageModel.damageForHit(12, "body", true, -1), 12, "negative class treated as none");
        equal(BulletDamageModel.baseDamage(null), 5, "unknown ammo fixed fallback");
        equal(BulletDamageModel.baseDamage(null, 12), 5.0 / 12, "unknown shotgun ammo split over pellets");

        var shells = AmmoCatalog.create().stream().filter(a -> "12x70mm".equals(a.caliber)).toList();
        if (shells.size() != 6 || shells.stream().anyMatch(a -> a.damage <= 0 || a.damage >= 10)) {
            throw new AssertionError("all six 12x70 shells must have single-digit theoretical damage");
        }
        equal(shells.stream().filter(a -> "12x70_steel_buckshot".equals(a.id)).findFirst().orElseThrow().damage,
                9, "standard buckshot theory damage");

        equal(BulletDamageModel.effectiveArmorClass(4, 600, 600), 4, "full durability keeps class");
        equal(BulletDamageModel.effectiveArmorClass(4, 300, 600), 3, "half durability drops class 4 to 3");
        equal(BulletDamageModel.effectiveArmorClass(4, 180, 600), 3, "30% durability keeps class 4 at 3");
        equal(BulletDamageModel.effectiveArmorClass(4, 60, 600), 2, "10% durability drops class 4 to 2");
        equal(BulletDamageModel.effectiveArmorClass(4, 0, 600), 0, "broken armor protects nothing");
        equal(BulletDamageModel.effectiveArmorClass(3, 250, 500), 2, "half durability drops class 3 to 2");
        equal(BulletDamageModel.effectiveArmorClass(5, 450, 900), 4, "half durability drops class 5 to 4");
        equal(BulletDamageModel.effectiveArmorClass(2, 0, 0), 2, "armor without durability keeps class");
        equal(BulletDamageModel.effectiveArmorClass(0, 0, 0), 0, "no armor class stays none");
        System.out.println("AmmoDamageTest: direct ammo damage, hit locations, no blunt damage, durability class and shell damage passed");
    }
}

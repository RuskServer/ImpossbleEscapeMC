package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

import org.junit.jupiter.api.Test;

import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import java.util.List;

public final class ScavBossAmmoTest {
    @Test
    @org.junit.jupiter.api.DisplayName("SCAR-H top cartridge")
    void scarHTopCartridge() throws Exception {
        var ammo=AmmoCatalog.create();
        check(ScavAmmoSupply.bestForCaliber(ammo,"7.62x51mm").id.equals("762x51_lancer"),"SCAR-H top cartridge");
        check(ScavAmmoSupply.bestForCaliber(ammo,"5.56x45mm").id.equals("556x45_lancer"),"M4A1 top cartridge");
        check(ScavAmmoSupply.bestForCaliber(ammo,"9x39mm").id.equals("9x39_7n56"),"AS VAL top cartridge");
        check(ScavAmmoSupply.bestForCaliber(ammo,"missing")==null,"missing caliber must prevent spawning");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("empty ammo definitions")
    void emptyAmmoDefinitions() throws Exception {
        check(ScavAmmoSupply.bestForCaliber(List.of(),"9x39mm")==null,"empty ammo definitions");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("penetration outranks raw damage; case insensitive")
    void penetrationOutranksRawDamageCaseInsensitive() throws Exception {
        AmmoDefinition low=new AmmoDefinition();
        low.id="low";
        low.caliber="test";
        low.ammoClass=1;
        low.damage=99;
        AmmoDefinition top=new AmmoDefinition();
        top.id="top";
        top.caliber="test";
        top.ammoClass=6;
        top.damage=1;
        check(ScavAmmoSupply.bestForCaliber(List.of(low,top),"TEST")==top,"penetration outranks raw damage; case insensitive");
    }
    private static void check(boolean value,String reason){ org.junit.jupiter.api.Assertions.assertTrue(value, reason); }
}

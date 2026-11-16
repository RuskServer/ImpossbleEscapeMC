package com.lunar_prototype.impossbleEscapeMC.modules.medical;

import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemCatalog;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillCatalog;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedicalKitBalanceTest {
    private ItemDefinition item(String id) {
        return ItemCatalog.create().stream().filter(d -> d.id.equals(id)).findFirst().orElseThrow();
    }

    @Test
    void ifakHasMoreSuppliesWithTheSameSpeedAndInventoryCost() {
        var ai2 = item("ai2"); var ifak = item("ifak");
        assertEquals(25, ai2.maxDurability);
        assertEquals(2.0, ai2.medStats.healPerUse);
        assertEquals(2, ai2.medStats.durabilityPerUse);
        assertEquals(ai2.medStats.healPerUse, ifak.medStats.healPerUse);
        assertEquals(ai2.medStats.durabilityPerUse, ifak.medStats.durabilityPerUse);
        assertEquals(120, ifak.maxDurability);
        assertEquals(ai2.weight, ifak.weight);
        assertEquals(ai2.cost, ifak.cost);
        assertTrue(ifak.medStats.continuous);
        assertFalse(ifak.medStats.cureBleeding);
        assertFalse(ifak.medStats.cureArmFracture);
        assertFalse(ifak.medStats.cureLegFracture);
        assertEquals(29, ifak.customModelData);
        assertEquals(ifak.customModelData, ifak.medStats.usingCustomModelData);
        assertEquals("iem:med.ifak", ifak.medStats.soundPrefix);
        assertEquals(9, ai2.customModelData);
        assertEquals(10, ai2.medStats.usingCustomModelData);
    }

    @Test
    void supplyBonusDoesNotPromiseDurabilityBeyondAi2Capacity() {
        var bonus = SkillCatalog.get("trade.supply_extra").stats().get(SkillStat.SUPPLY_AI2);
        assertEquals(25, 20 + bonus.intValue());
        assertEquals(item("ai2").maxDurability, 20 + bonus.intValue());
    }

    @Test
    void eachKitUsesItsOwnTreatmentFoley() {
        assertEquals("pills", MedicalKitSounds.step("ai2", 1));
        assertEquals("blister", MedicalKitSounds.step("ai2", 2));
        assertEquals("unwrap", MedicalKitSounds.step("ifak", 1));
        assertEquals("dress", MedicalKitSounds.step("ifak", 2));
        assertEquals("unwrap", MedicalKitSounds.step("ifak", 3));
    }
}

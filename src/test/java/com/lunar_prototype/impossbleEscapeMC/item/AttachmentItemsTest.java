package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.item.parser.AttachmentDefinitionParser;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.configuration.MemoryConfiguration;
import java.util.List;
import java.util.Map;

public final class AttachmentItemsTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var config = new MemoryConfiguration();
        config.set("displayName", "Old name"); config.set("slot", "BARREL");
        config.set("weight", 450); config.set("cost", 2); config.set("rarity", 3);
        config.set("description", List.of("&7Existing description"));
        var legacy = AttachmentDefinitionParser.parse("eotech_xps2", config);
        var nativeInfo = new DatapackAttachments.Info("eotech_xps2", "sight", "item.toisarm.eotech_xps2", "Eotech XPS2");
        var resolved = AttachmentItems.definition("eotech_xps2", nativeInfo, legacy);
        check(resolved.datapack() && resolved.slot().equals("sight"), "datapack slot wins collision");
        check(resolved.name() instanceof TranslatableComponent name && name.fallback().equals("Eotech XPS2"), "datapack name wins collision");
        check(resolved.weight() == 450 && resolved.cost() == 2 && resolved.rarity() == 3, "plugin metadata retained");
        check(resolved.description().equals(List.of("&7Existing description")), "description retained");
        var nativeOnly = AttachmentItems.definition("eotech_xps2", nativeInfo, null);
        check(nativeOnly.weight() == 320 && nativeOnly.cost() == 1, "known datapack uses existing specs");
        var unknownNative = AttachmentItems.definition("new_optic", new DatapackAttachments.Info("new_optic", "sight", "", "New optic"), null);
        check(unknownNative.weight() == 200 && unknownNative.cost() == 1, "new native item has defaults");
        check(!AttachmentItems.definition("eotech_xps2", null, legacy).datapack(), "legacy fallback works");
        check(AttachmentItems.definition("missing", null, null) == null, "unknown id rejected");
        var objective = new HandInObjective("eotech_xps2", null, 1, true);
        var active = new ActiveQuest("optic_calibration");
        check(!objective.updateProgress(null, null, active, 0, QuestTrigger.HAND_IN,
                Map.of("itemId", "eotech_xps2", "isFIR", false, "amount", 1)), "non-FIR rejected");
        check(!objective.updateProgress(null, null, active, 0, QuestTrigger.HAND_IN,
                Map.of("itemId", "pso_1", "isFIR", true, "amount", 1)), "wrong optic rejected");
        check(objective.updateProgress(null, null, active, 0, QuestTrigger.HAND_IN,
                Map.of("itemId", "eotech_xps2", "itemType", "ATTACHMENT", "isFIR", true, "amount", 1)), "native id accepted");
        check(objective.isCompleted(active, 0), "optic objective completed");
        check(!objective.updateProgress(null, null, active, 0, QuestTrigger.HAND_IN,
                Map.of("itemId", "eotech_xps2", "isFIR", true, "amount", 1)), "completed objective cannot consume more");
        System.out.println("AttachmentItemsTest: all checks passed");
    }
}

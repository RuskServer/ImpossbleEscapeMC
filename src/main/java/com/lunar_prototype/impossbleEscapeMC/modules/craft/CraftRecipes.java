package com.lunar_prototype.impossbleEscapeMC.modules.craft;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility.ItemCost;

import java.util.List;

/**
 * 作る設備のレシピ (仮の値)。素材は隠れ家の設備と同じく、持ち物とスタッシュから取る (支給品は使えない)。
 * level は作れるようになる設備のレベル、category は製作の画面の種類のタブ (null ならタブを出さない)
 */
public final class CraftRecipes {

    public record Recipe(String id, HideoutFacility facility, String category, String productId, int count, int seconds, int level,
                         List<ItemCost> materials) {
    }

    private static final List<Recipe> ALL = List.of(
            // ワークベンチ: 部品と装備
            new Recipe("wb_bolts", HideoutFacility.WORKBENCH, "部品", "bolts", 4, 90, 1,
                    List.of(new ItemCost("scraps", 3))),
            new Recipe("wb_tool_box", HideoutFacility.WORKBENCH, "部品", "tool_box", 1, 300, 1,
                    List.of(new ItemCost("wrench", 1), new ItemCost("screwdriver", 1), new ItemCost("bolts", 2))),
            new Recipe("wb_mini_battery", HideoutFacility.WORKBENCH, "部品", "mini_battery", 1, 360, 2,
                    List.of(new ItemCost("copper_wire", 2), new ItemCost("scraps", 2))),
            new Recipe("wb_flashlight", HideoutFacility.WORKBENCH, "装備", "flashlight", 1, 180, 1,
                    List.of(new ItemCost("mini_battery", 1), new ItemCost("copper_wire", 1), new ItemCost("scraps", 1))),
            new Recipe("wb_micro_rig", HideoutFacility.WORKBENCH, "装備", "micro_rig", 1, 480, 2,
                    List.of(new ItemCost("scraps", 4), new ItemCost("bolts", 4), new ItemCost("tool_box", 1))),
            new Recipe("wb_sling_bag", HideoutFacility.WORKBENCH, "装備", "tactical_sling_bag", 1, 600, 3,
                    List.of(new ItemCost("toilet_paper", 6), new ItemCost("bolts", 4), new ItemCost("tool_box", 1))),
            // 弾薬製造
            new Recipe("am_9x19_pst", HideoutFacility.AMMO, null, "9x19_pst_gzh", 60, 180, 1,
                    List.of(new ItemCost("scraps", 1), new ItemCost("dry_fuel", 1))),
            new Recipe("am_545_ps", HideoutFacility.AMMO, null, "545x39_ps", 60, 240, 1,
                    List.of(new ItemCost("scraps", 2), new ItemCost("dry_fuel", 1))),
            new Recipe("am_556_m855", HideoutFacility.AMMO, null, "556x45_m855", 60, 300, 1,
                    List.of(new ItemCost("scraps", 3), new ItemCost("dry_fuel", 1), new ItemCost("bolts", 1))),
            new Recipe("am_762x51_m80", HideoutFacility.AMMO, null, "762x51_m80", 40, 360, 1,
                    List.of(new ItemCost("scraps", 3), new ItemCost("dry_fuel", 2))),
            // 医療設備
            new Recipe("md_cat", HideoutFacility.MEDICAL, null, "CAT", 1, 120, 1,
                    List.of(new ItemCost("toilet_paper", 2), new ItemCost("bolts", 1))),
            new Recipe("md_ai2", HideoutFacility.MEDICAL, null, "ai2", 1, 240, 1,
                    List.of(new ItemCost("toilet_paper", 2), new ItemCost("water_filter", 1))),
            new Recipe("md_ai2_batch", HideoutFacility.MEDICAL, null, "ai2", 2, 360, 2,
                    List.of(new ItemCost("toilet_paper", 3), new ItemCost("water_filter", 1), new ItemCost("cigarettes", 1))));

    private CraftRecipes() {
    }

    /** 製作の画面を持つ設備か */
    public static boolean crafts(HideoutFacility facility) {
        return ALL.stream().anyMatch(r -> r.facility() == facility);
    }

    public static List<Recipe> of(HideoutFacility facility) {
        return ALL.stream().filter(r -> r.facility() == facility).toList();
    }

    public static Recipe byId(String id) {
        return ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null);
    }
}

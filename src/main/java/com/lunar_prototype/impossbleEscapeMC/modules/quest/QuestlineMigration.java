package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import java.util.List;

/** テスト段階の再構築。旧進捗は捨て、獲得済みの取引資格だけを独立して残す。 */
public final class QuestlineMigration {
    public static final int CURRENT_VERSION = 1;
    private record LegacyUnlock(String trader,String item,boolean barter,String quest) {}
    // 変更前の販売条件を固定。新カタログの条件を使って旧取引を余分に解放しない。
    private static final List<LegacyUnlock> LEGACY = List.of(
            new LegacyUnlock("kovacs", "mossberg_590", false, "kovacs_close_supply"),
            new LegacyUnlock("kovacs", "uzi", false, "kovacs_close_supply"),
            new LegacyUnlock("kovacs", "svd", false, "kovacs_marksman_02"),
            new LegacyUnlock("kovacs", "m700", false, "kovacs_headhunter"),
            new LegacyUnlock("kovacs", "mp5", false, "pharmakon_quiet_escort"),
            new LegacyUnlock("kovacs", "m4a1", false, "bastion_coastal_clearance"),
            new LegacyUnlock("kovacs", "d3rcx", false, "bastion_carrying_capacity"),
            new LegacyUnlock("kovacs", "762x54r_ps", false, "kovacs_eastern_supply"),
            new LegacyUnlock("kovacs", "9x39_sp5", false, "kovacs_eastern_supply"),
            new LegacyUnlock("kovacs", "556x45_hp", false, "kovacs_western_supply"),
            new LegacyUnlock("kovacs", "762x51_fmj_28", false, "kovacs_western_supply"),
            new LegacyUnlock("kovacs", "9x19_pst_gzh", false, "kovacs_close_supply"),
            new LegacyUnlock("kovacs", "45acp_match_fmj_26", false, "kovacs_close_supply"),
            new LegacyUnlock("kovacs", "12x70_flechette", false, "kovacs_shotgun_trial"),
            new LegacyUnlock("kovacs", "pso_1", false, "kovacs_optic_reserve"),
            new LegacyUnlock("kovacs", "uzi_short_magazine", false, "kovacs_uzi_parts"),
            new LegacyUnlock("kovacs", "uzi_folded_stock", false, "kovacs_uzi_parts"),
            new LegacyUnlock("kovacs", "2_stage_suppressor", false, "kovacs_suppressor_supply"),
            new LegacyUnlock("kovacs", "9x39mm_30round_magazine", false, "kovacs_val_magazines"),
            new LegacyUnlock("kovacs", "eotech_xps2", false, "kovacs_coastal_marksman"),
            new LegacyUnlock("kovacs", "unrg7k_ekho", false, "kovacs_recon_supply"),
            new LegacyUnlock("kovacs", "545x39_pp", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "556x45_m855", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "762x51_m62", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "762x54r_lps_gzh", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "9x39_sp6", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "9x19_7n21", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "45acp_ap", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "12x70_tss", false, "kovacs_penetration"),
            new LegacyUnlock("kovacs", "545x39_bs", false, "bastion_rifle_drill"),
            new LegacyUnlock("kovacs", "556x45_m855", true, "kovacs_rewiring"),
            new LegacyUnlock("pharmakon", "water_filter", false, "pharmakon_factory_sweep"),
            new LegacyUnlock("bastion", "mbss", false, "bastion_logistics_02"),
            new LegacyUnlock("bastion", "pilgrim_tourist_backpack", false, "bastion_capacity_expansion"),
            new LegacyUnlock("bastion", "FAST-MT_visor", false, "bastion_face_protection"),
            new LegacyUnlock("bastion", "PVS-14", false, "bastion_night_optics_supply"),
            new LegacyUnlock("bastion", "Trooper", false, "bastion_protection_selection"),
            new LegacyUnlock("bastion", "Altyn", false, "bastion_close_quarters"),
            new LegacyUnlock("bastion", "PVS-14", true, "bastion_night_optics_trial"),
            new LegacyUnlock("bastion", "sp_v", true, "bastion_monolith_prototype"));
    private QuestlineMigration() {}

    public static String tradeKey(String trader,String item,boolean barter) {
        return trader+":"+(barter?"barter":"cash")+":"+item;
    }

    /** 読込時に一度だけ実行。資格・リセット・版は同じPlayerDataの原子的な保存に含める。 */
    public static boolean migrate(PlayerData data) {
        synchronized(data) {
            if(data.getQuestlineVersion()>=CURRENT_VERSION)return false;
            for(LegacyUnlock rule:LEGACY)if(data.isQuestCompleted(rule.quest))
                data.retainTradeUnlock(tradeKey(rule.trader,rule.item,rule.barter));
            data.getActiveQuests().clear();
            data.getCompletedQuests().clear();
            data.setQuestlineVersion(CURRENT_VERSION);
            return true;
        }
    }
}

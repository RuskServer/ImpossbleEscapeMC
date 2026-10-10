package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * クエストの定義。
 * <p>
 * クエストIDはプレイヤーの進捗 (完了・進行中) とトレーダー設定の {@code quest:} が参照するため変えない
 * (以前の quests/*.yml のファイル名と同じ)。新しいクエストは、トレーダーごとの並び (受領の順) に足す。
 * 起動時に {@link QuestModule} がIDの重複・前提クエスト・アイテムIDを確かめる。
 */
final class QuestCatalog {

    private static final String KOVACS = "kovacs";
    private static final String PHARMAKON = "pharmakon";
    private static final String BASTION = "bastion";

    /** 脱出の目標に渡るマップ名 */
    private static final String FACTORY = "ノヴォザリエ薬品工場";

    private QuestCatalog() {
    }

    static List<QuestDefinition> create(QuestModule module) {
        List<QuestDefinition> quests = new ArrayList<>();

        // --- Kovacs ---
        quests.add(new QuestBuilder(module, "supply_route")
                .trader(KOVACS)
                .name("補給路の確認")
                .description("戦場での基本は生きて帰ることだ。まずはノヴォザリエ薬品工場から脱出し、拾ったボルトを届けてくれ。機材の修理に必要でな。")
                .requiresLevel(1)
                .extract(FACTORY)
                .handInFir("bolts", 3)
                .money(3000)
                .exp(820)
                .build());
        quests.add(new QuestBuilder(module, "field_deployment")
                .trader(KOVACS)
                .name("実戦配備")
                .description("お前が使える奴か試させてもらう。SCAVを掃除し、戦場から貴重な電子部品を持ち帰れ。報酬にAK-74の販売を許可しよう。")
                .requiresLevel(1)
                .requiresQuest("supply_route")
                .kill("SCAV", 3)
                .handInFir("mini_battery", 1)
                .unlockTrade(KOVACS, "ak74")
                .money(15000)
                .exp(820)
                .build());
        quests.add(new QuestBuilder(module, "optic_calibration")
                .trader(KOVACS)
                .name("照準の微調整")
                .description("いい働きだった。次はこいつを試してくれ。仕入れた光学機器の精度を実戦で確かめたい。ノヴォザリエ薬品工場へ向かい、正確な射撃でSCAVを排除、無事に持ち帰るんだ。")
                .requiresQuest("field_deployment")
                .kill("SCAV", 5)
                .handInFir("eotech_xps2", 1)
                .extract(FACTORY)
                .money(22000)
                .exp(1200)
                .build());
        quests.add(new QuestBuilder(module, "kovacs_marksman_01")
                .trader(KOVACS)
                .name("至近距離の洗礼")
                .description("銃の性能に頼る前に、まずは自分の度胸を試せ。暗がりで、敵の息遣いが聞こえるほどの至近距離からSCAVを仕留めてこい。")
                .requiresLevel(10)
                .killAtDistance("SCAV", 5, null, 10.0)
                .label("10m以内の至近距離でSCAVを5体排除する")
                .money(8000)
                .exp(5600)
                .build());
        quests.add(new QuestBuilder(module, "kovacs_marksman_02")
                .trader(KOVACS)
                .name("精密な中間点")
                .description("近すぎず、遠すぎない。この距離が一番腕の差が出る。30メートル以上離れた位置から、正確にターゲットを撃ち抜いて見せろ。")
                .requiresQuest("kovacs_marksman_01")
                .killAtDistance("SCAV", 7, 30.0, 70.0)
                .label("30m以上70m以下の距離からSCAVを7体排除する")
                .money(15000)
                .exp(8200)
                .build());

        // --- Pharmakon ---
        quests.add(new QuestBuilder(module, "first_aid_basics")
                .trader(PHARMAKON)
                .name("応急処置の心得")
                .description("戦場では弾丸よりも感染症が恐ろしいわ。まずは基本的な治療キットをいくつか集めてきてちょうだい。あなたの実力、期待しているわよ。")
                .requiresLevel(1)
                .handInFir("ai2", 3)
                .money(5000)
                .exp(820)
                .build());
        quests.add(new QuestBuilder(module, "stop_the_bleed")
                .trader(PHARMAKON)
                .name("出血を止めろ")
                .description("ノヴォザリエ薬品工場付近で負傷者が増えているの。止血帯が全然足りないわ。いくつか納品してくれる？ついでに、あの場所が今どうなっているかその目で確かめてきて。")
                .requiresLevel(1)
                .requiresQuest("first_aid_basics")
                .handInFir("CAT", 3)
                .money(8000)
                .exp(820)
                .build());
        quests.add(new QuestBuilder(module, "sanitary_maintenance")
                .trader(PHARMAKON)
                .name("衛生環境の維持")
                .description("薬だけあっても、泥水を飲んでいたら病気は治らないわ。居住区の浄水システムを動かすために、フィルターと燃料が必要なの。どちらもかさばるし重いけれど、生存者のために工面してちょうだい。")
                .requiresQuest("stop_the_bleed")
                .handInFir("water_filter", 2)
                .label("浄水フィルター(FIR)を2個納品する")
                .handInFir("dry_fuel", 4)
                .label("固形燃料(FIR)を4個納品する")
                .money(24000)
                .exp(1800)
                .build());

        // --- Bastion ---
        quests.add(new QuestBuilder(module, "bastion_logistics_01")
                .trader(BASTION)
                .name("工場の遺産 - 調査")
                .description("ノヴォザリエ薬品工場の物流ラインが生きてるか確認したい。あそこのフォークリフトがまだ動くなら、沿岸都市への資材搬送に使えるはずだ。まずは現物を見て、無事に帰ってきてくれ。")
                .reach("factory", 100, -33, 98, 5, "工場内のフォークリフト")
                .extract(FACTORY)
                .label("レイドから生還する")
                .money(12000)
                .exp(2500)
                .build());
        quests.add(new QuestBuilder(module, "bastion_logistics_02")
                .trader(BASTION)
                .name("防衛ラインの再構築 - 納品")
                .description("フォークリフトは使えそうだな。だが、資材を運び出すには護衛が必要だ。現地の警備に回すためのUNTARヘルメットをいくつか調達してこい。現場で拾った、状態の良いもの（FIR）に限るぞ。")
                .requiresQuest("bastion_logistics_01")
                .handInFir("UNTAR", 2)
                .label("新品(FIR)のUNTARヘルメットを2個納品する")
                .money(18000)
                .exp(3500)
                .unlockTrade(BASTION, "mbss")
                .build());
        quests.add(new QuestBuilder(module, "bastion_logistics_03")
                .trader(BASTION)
                .name("防衛ラインの再構築 - 掃討")
                .description("準備は整った。納品させたUNTARヘルメットを着た連中を現場へ送り込む。お前の仕事は、運び出しの邪魔になる付近の脅威を排除し、脱出路を確保することだ。死ぬなよ。")
                .requiresQuest("bastion_logistics_02")
                .requiresLevel(8)
                .kill("SCAV", 8)
                .extract(FACTORY)
                .money(25000)
                .exp(5200)
                .build());

        addIndependentQuests(module, quests);
        return quests;
    }

    /** どのマップでも進められる独立系列。工場限定の既存系列を前提にしない。 */
    private static void addIndependentQuests(QuestModule module, List<QuestDefinition> quests) {
        quests.add(new QuestBuilder(module, "kovacs_workbench")
                .trader(KOVACS).name("整備台の再稼働")
                .description("整備台を使える状態に戻す。拾った金属と工具を届けてくれ。")
                .requiresLevel(1)
                .handInFir("scraps", 5).handInFir("wrench", 1).handInFir("screwdriver", 1)
                .money(8000).exp(1200).build());
        quests.add(new QuestBuilder(module, "kovacs_eastern_supply")
                .trader(KOVACS).name("東側の補給線")
                .description("東側の装備に使う弾を集めたい。同じ口径なら弾種は混ざっていて構わない。")
                .requiresLevel(3)
                .requiresQuest("kovacs_workbench")
                .handInAmmoFir("5.45x39mm", null, 60).handInAmmoFir("7.62x54mmR", null, 20).handInAmmoFir("9x39mm", null, 20)
                .unlockTrade(KOVACS, "762x54r_ps")
                .unlockTrade(KOVACS, "9x39_sp5")
                .money(15000).exp(2200).build());
        quests.add(new QuestBuilder(module, "kovacs_western_supply")
                .trader(KOVACS).name("西側の補給線")
                .description("西側の銃も補給が必要だ。小銃弾を揃えてくれ。標準弾の仕入れ先を紹介しよう。")
                .requiresLevel(3)
                .requiresQuest("kovacs_workbench")
                .handInAmmoFir("5.56x45mm", null, 60).handInAmmoFir("7.62x51mm", null, 20)
                .unlockTrade(KOVACS, "556x45_hp")
                .unlockTrade(KOVACS, "762x51_fmj_28")
                .money(12000).exp(2000).build());
        quests.add(new QuestBuilder(module, "kovacs_close_supply")
                .trader(KOVACS).name("近接装備の備蓄")
                .description("拳銃と散弾銃の予備弾を備蓄する。遠出のついでに回収してくれ。")
                .requiresLevel(4)
                .handInAmmoFir("9x19mm", null, 30).handInAmmoFir(".45ACP", null, 30).handInAmmoFir("12x70mm", null, 12)
                .unlockTrade(KOVACS, "9x19_pst_gzh")
                .unlockTrade(KOVACS, "45acp_match_fmj_26")
                .unlockTrade(KOVACS, "12x70_steel_buckshot")
                .money(10000).exp(1800).build());
        quests.add(new QuestBuilder(module, "kovacs_optic_reserve")
                .trader(KOVACS).name("照準器の予備")
                .description("予備のEKP-8を回収し、中距離の敵を仕留めてこい。仕入れた照準器も販売に回す。")
                .requiresLevel(5)
                .requiresQuest("kovacs_eastern_supply")
                .handInFir("ekp_8", 1).killAtDistance("SCAV", 5, 15.0, 40.0)
                .unlockTrade(KOVACS, "ekp_8")
                .money(16000).exp(2800).build());
        quests.add(new QuestBuilder(module, "kovacs_penetration")
                .trader(KOVACS).name("貫通力の確保")
                .description("防具を着た相手には弾の選択が効く。貫通クラス4以上の弾を回収し、SCAVを排除してくれ。")
                .requiresLevel(8)
                .requiresQuest("kovacs_eastern_supply")
                .requiresQuest("kovacs_western_supply")
                .handInAmmoFir(null, 4, 30).kill("SCAV", 8)
                .unlockTrade(KOVACS, "545x39_pp")
                .unlockTrade(KOVACS, "556x45_m855")
                .unlockTrade(KOVACS, "762x51_m62")
                .unlockTrade(KOVACS, "762x54r_lps_gzh")
                .unlockTrade(KOVACS, "9x39_sp6")
                .unlockTrade(KOVACS, "9x19_7n21")
                .unlockTrade(KOVACS, "45acp_ap")
                .unlockTrade(KOVACS, "12x70_tss")
                .money(24000).exp(3800).build());
        quests.add(new QuestBuilder(module, "kovacs_small_deal")
                .trader(KOVACS).name("小さな取引")
                .description("協力者への手土産と修理用のボルトが必要だ。少量だが、戦場で回収した物を頼む。")
                .requiresLevel(1)
                .handInFir("cigarettes", 3).handInFir("bolts", 2)
                .money(6000).exp(900).build());
        quests.add(new QuestBuilder(module, "kovacs_power_supply")
                .trader(KOVACS).name("電源の確保")
                .description("通信設備の電源を直す。小型バッテリーと予備の金属部品を集めてくれ。")
                .requiresLevel(3)
                .requiresQuest("kovacs_small_deal")
                .handInFir("mini_battery", 2).handInFir("scraps", 3)
                .money(10000).exp(1600).build());
        quests.add(new QuestBuilder(module, "kovacs_data_recovery")
                .trader(KOVACS).name("記録媒体の回収")
                .description("暗号化された記録媒体を探している。回収に加え、二度の生還で運搬を任せられることを示してくれ。")
                .requiresLevel(8)
                .requiresQuest("kovacs_power_supply")
                .handInFir("usb", 1).extractAny(2)
                .money(42000).exp(3500).build());
        quests.add(new QuestBuilder(module, "pharmakon_field_clinic")
                .trader(PHARMAKON).name("仮設診療所")
                .description("仮設診療所の衛生用品と暖房用の燃料が不足しているの。戦場で見つけた物を届けて。")
                .requiresLevel(1)
                .handInFir("toilet_paper", 4).handInFir("dry_fuel", 2)
                .money(7000).exp(1100).build());
        quests.add(new QuestBuilder(module, "pharmakon_transport_security")
                .trader(PHARMAKON).name("搬送路の安全確保")
                .description("負傷者を運ぶ隊員が狙われているわ。SCAVを排除し、二度生還して安全な運搬を支えて。")
                .requiresLevel(3)
                .requiresQuest("pharmakon_field_clinic")
                .kill("SCAV", 4).extractAny(2)
                .money(12000).exp(2200).build());
        quests.add(new QuestBuilder(module, "pharmakon_emergency_stock")
                .trader(PHARMAKON).name("救急備蓄の更新")
                .description("治療キットも止血帯も受け付けるわ。医療品と浄水フィルターを集めて備蓄を補充して。")
                .requiresLevel(5)
                .requiresQuest("pharmakon_transport_security")
                .handInCategory("MED", 6, true).handInFir("water_filter", 1)
                .money(23000).exp(3000).build());
        quests.add(new QuestBuilder(module, "bastion_light_transport")
                .trader(BASTION).name("軽装輸送の準備")
                .description("小さな輸送隊を編成する。軽量バッグとリグを回収してくれ。")
                .requiresLevel(1)
                .handInFir("tactical_sling_bag", 1).handInFir("micro_rig", 1)
                .money(15000).exp(1700).build());
        quests.add(new QuestBuilder(module, "bastion_escort_equipment")
                .trader(BASTION).name("護衛隊の装備")
                .description("護衛に回す防具が足りない。MF-UNTARを調達し、邪魔なSCAVも片付けてくれ。")
                .requiresLevel(4)
                .requiresQuest("bastion_light_transport")
                .handInFir("MF-UNTAR", 1).kill("SCAV", 5)
                .money(15000).exp(2500).build());
        quests.add(new QuestBuilder(module, "bastion_carrying_capacity")
                .trader(BASTION).name("積載能力の拡張")
                .description("輸送量を増やす。大型バッグと容量のあるリグを回収し、二度生還してくれ。D3CRXの販売も許可する。")
                .requiresLevel(8)
                .requiresQuest("bastion_escort_equipment")
                .handInFir("mbss", 1).handInFir("d3rcx", 1).extractAny(2)
                .unlockTrade(KOVACS, "d3rcx")
                .money(28000).exp(3500).build());
        quests.add(new QuestBuilder(module, "bastion_protection_selection")
                .trader(BASTION).name("防護装備の選別")
                .description("クラス3以上の防具を一つ確保してくれ。SCAVの排除と三度の生還も頼む。完了後はTrooperを販売する。")
                .requiresLevel(12)
                .requiresQuest("bastion_carrying_capacity")
                .handInArmorFir(3, 1).kill("SCAV", 10).extractAny(3)
                .unlockTrade(BASTION, "Trooper")
                .money(30000).exp(4500).build());
        quests.add(new QuestBuilder(module, "bastion_workshop_gift")
                .trader(BASTION).name("職人への手土産")
                .description("輸送設備を直せる職人を見つけた。工具箱と手工具を揃えて作業を始めさせたい。")
                .requiresLevel(4)
                .requiresQuest("bastion_light_transport")
                .handInFir("tool_box", 1).handInFir("wrench", 1).handInFir("screwdriver", 1)
                .money(11000).exp(2000).build());
        quests.add(new QuestBuilder(module, "bastion_computing_resources")
                .trader(BASTION).name("演算資源の調達")
                .description("防衛設備の監視装置を復旧する。使えるグラフィックボードを回収してくれ。")
                .requiresLevel(12)
                .requiresQuest("bastion_workshop_gift")
                .handInFir("graphics_card", 1)
                .money(105000).exp(4200).build());
        quests.add(new QuestBuilder(module, "bastion_buying_time")
                .trader(BASTION).name("時間を買う")
                .description("部品の交換取引に使う金時計を探している。三度の生還も達成し、最後まで仕事を片付けてくれ。")
                .requiresLevel(15)
                .requiresQuest("bastion_computing_resources")
                .handInFir("mechanical_golden_watch", 1).extractAny(3)
                .money(145000).exp(5000).build());
    }
}

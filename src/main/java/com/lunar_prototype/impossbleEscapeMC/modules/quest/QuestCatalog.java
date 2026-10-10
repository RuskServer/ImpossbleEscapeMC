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
                .description("フォークリフトは使えそうだな。だが、資材を運び出すには護衛が必要だ。現地の警備に回すためのUNTARアーマーをいくつか調達してこい。現場で拾った、状態の良いもの（FIR）に限るぞ。")
                .requiresQuest("bastion_logistics_01")
                .handInFir("UNTAR", 2)
                .label("新品(FIR)のUNTARアーマーを2着納品する")
                .money(18000)
                .exp(3500)
                .unlockTrade(BASTION, "mbss")
                .build());
        quests.add(new QuestBuilder(module, "bastion_logistics_03")
                .trader(BASTION)
                .name("防衛ラインの再構築 - 掃討")
                .description("準備は整った。納品させたUNTARアーマーを着た連中を現場へ送り込む。お前の仕事は、運び出しの邪魔になる付近の脅威を排除し、脱出路を確保することだ。死ぬなよ。")
                .requiresQuest("bastion_logistics_02")
                .requiresLevel(8)
                .kill("SCAV", 8)
                .extract(FACTORY)
                .money(25000)
                .exp(5200)
                .build());

        return quests;
    }
}

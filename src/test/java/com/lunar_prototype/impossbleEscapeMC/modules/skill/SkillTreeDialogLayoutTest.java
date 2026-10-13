package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * スキルツリーの画面:
 * <ul>
 *   <li>どの行も行の幅を超えず、ちょうど行の幅で終わる (どの系統・どのノードを選んでも)</li>
 *   <li>詳細の行が液晶に収まる</li>
 *   <li>ツリーの線がノードを通らず、別の子への線と重ならない・交わらない</li>
 *   <li>前提のノードが同じ系統にあり、ティアが前</li>
 * </ul>
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す (IEM_shader/tools/dialog_render.py で絵にする)
 */
public final class SkillTreeDialogLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 習得した物・設備・レベルから、系統のノードの状態を作る (SkillModule.status と同じ規則) */
    private static SkillTreeDialog.View view(SkillCategory category, String selected, Set<String> learned, int facility, int level,
                                             long money, Map<String, Integer> have) {
        Map<String, SkillModule.Status> status = new HashMap<>();
        Map<SkillCategory, int[]> counts = new EnumMap<>(SkillCategory.class);
        for (SkillNode node : SkillCatalog.all()) {
            int[] c = counts.computeIfAbsent(node.category(), k -> new int[2]);
            c[1]++;
            if (learned.contains(node.id())) c[0]++;
            if (node.category() != category) continue;
            SkillModule.Status s;
            int met = 0;
            for (String r : node.requires()) if (learned.contains(r)) met++;
            boolean pre = node.requires().isEmpty() || (node.anyOf() ? met > 0 : met == node.requires().size());
            if (learned.contains(node.id())) s = SkillModule.Status.LEARNED;
            else if (facility < SkillCatalog.facilityLevel(node.tier())) s = SkillModule.Status.FACILITY;
            else if (!pre) s = SkillModule.Status.PREREQUISITE;
            else if (level < SkillCatalog.playerLevel(node.tier())) s = SkillModule.Status.LEVEL;
            else s = SkillModule.Status.AVAILABLE;
            status.put(node.id(), s);
        }
        SkillNode node = selected != null ? SkillCatalog.get(selected) : SkillTreeDialog.defaultNode(category, status);
        Map<String, String> names = new HashMap<>();
        for (HideoutFacility.ItemCost cost : node.materials()) names.put(cost.itemId(), NAMES.getOrDefault(cost.itemId(), cost.itemId()));
        return new SkillTreeDialog.View(category, node, status, counts, money, level, facility, have, names);
    }

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("bolts", "ボルト"), Map.entry("screwdriver", "ドライバー"), Map.entry("wrench", "レンチ"), Map.entry("tool_box", "工具箱"),
            Map.entry("dry_fuel", "固形燃料"), Map.entry("scraps", "スクラップ"), Map.entry("mini_battery", "小型バッテリー"),
            Map.entry("water_filter", "浄水フィルター"), Map.entry("toilet_paper", "トイレットペーパー"), Map.entry("cigarettes", "シガレット"),
            Map.entry("flashlight", "フラッシュライト"), Map.entry("usb", "暗号化USBメモリ"), Map.entry("graphics_card", "グラフィックボード"),
            Map.entry("mechanical_golden_watch", "機械式金時計"), Map.entry("CAT", "CAT"), Map.entry("ai2", "AI-2 Medical Kit"));

    private static Set<String> ids(String... ids) {
        return new HashSet<>(List.of(ids));
    }

    private static void checkLines(SkillTreeDialog.View v, String what) {
        List<DialogLine> lines = SkillTreeDialog.lines(v);
        check(lines.size() == SkillTreeDialog.LINES, what + " line count");
        for (int i = 0; i < lines.size(); i++) {
            DialogLine l = lines.get(i);
            check(l.max <= SkillTreeDialog.LINE_W && l.x == SkillTreeDialog.LINE_W, what + " line " + i + ": max " + l.max + " end " + l.x);
        }
        check(SkillTreeDialog.detailRows(v).size() <= SkillTreeDialog.DETAIL_ROWS, what + " detail rows " + SkillTreeDialog.detailRows(v).size());
    }

    /** ツリーの線の形: 親の下の隙間 (親の段) で横に、子の列を下りる */
    private static void checkTree(SkillCategory category) {
        List<SkillNode> nodes = SkillCatalog.of(category);
        Map<String, SkillNode> byId = new HashMap<>();
        Set<String> cells = new HashSet<>();
        for (SkillNode n : nodes) {
            byId.put(n.id(), n);
            check(cells.add(n.row() + ":" + n.col2()), n.id() + " overlaps another node");
            check(n.row() >= 1 && n.row() <= 5 && n.col2() >= 0 && n.col2() <= 8, n.id() + " out of the grid");
            check((n.row() % 2 == 1) == (n.col2() % 2 == 0), n.id() + " odd rows use whole columns, even rows half columns");
            int[] tierRows = {0, 1, 3, 5};
            check(n.row() >= tierRows[n.tier()] && n.row() <= (n.tier() == 3 ? 5 : tierRows[n.tier()] + 1), n.id() + " row outside its tier");
        }
        record Edge(SkillNode parent, SkillNode child) {
        }
        List<Edge> edges = new ArrayList<>();
        for (SkillNode child : nodes) {
            for (String r : child.requires()) {
                SkillNode parent = byId.get(r);
                check(parent != null, child.id() + " requires " + r + " outside its category");
                check(parent.tier() < child.tier() && parent.row() < child.row(), child.id() + " requires a later node " + r);
                edges.add(new Edge(parent, child));
            }
        }
        for (Edge e : edges) {
            int col = e.child().col2();
            // 下りる縦線 (親の段の隙間から子の上まで) が、間の段のノードを通らない
            for (SkillNode n : nodes) {
                if (n.row() > e.parent().row() && n.row() < e.child().row() && n.col2() == col) {
                    check(false, e.parent().id() + " -> " + e.child().id() + " passes through " + n.id());
                }
            }
            for (Edge o : edges) {
                if (o.child() == e.child()) continue;
                int lo = Math.min(o.parent().col2(), o.child().col2()), hi = Math.max(o.parent().col2(), o.child().col2());
                // 同じ隙間の横線が、別の子への横線と重ならない
                if (o.parent().row() == e.parent().row() && o.parent() != e.parent()) {
                    int elo = Math.min(e.parent().col2(), col), ehi = Math.max(e.parent().col2(), col);
                    check(ehi < lo || hi < elo,
                            e.parent().id() + " -> " + e.child().id() + " overlaps " + o.parent().id() + " -> " + o.child().id());
                }
                // 下りる縦線が、ほかの横線を横切らない
                if (o.parent().row() > e.parent().row() && o.parent().row() < e.child().row() && lo <= col && col <= hi) {
                    check(false, e.parent().id() + " -> " + e.child().id() + " crosses " + o.parent().id() + " -> " + o.child().id());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        check(SkillCatalog.all().size() == 69, "node count " + SkillCatalog.all().size());
        for (SkillCategory c : SkillCategory.values()) checkTree(c);

        Map<String, Integer> plenty = new HashMap<>();
        for (String id : NAMES.keySet()) plenty.put(id, 9);
        // どの系統・どのノード・どの設備のレベルでも行が収まる
        for (SkillCategory c : SkillCategory.values()) {
            for (SkillNode node : SkillCatalog.of(c)) {
                for (int facility = 0; facility <= 3; facility++) {
                    checkLines(view(c, node.id(), ids(), facility, 1, 0, Map.of()), node.id() + " f" + facility);
                    checkLines(view(c, node.id(), ids(), facility, 30, 12_345_678, plenty), node.id() + " rich f" + facility);
                }
            }
        }

        List<SkillTreeDialog.View> samples = List.of(
                // 射撃: 習得できるノード (空マガジン交換) を選んでいる
                view(SkillCategory.SHOOTING, "shooting.empty_reload",
                        ids("shooting.reload", "shooting.swap", "shooting.ads", "shooting.recoil_v", "shooting.recoil_h", "shooting.steady"),
                        2, 8, 184_500, Map.of("bolts", 6)),
                // 隠密: 熟練の探索 (小型バッテリーが足りない)
                view(SkillCategory.STEALTH, "stealth.search_master",
                        ids("stealth.quick_search", "stealth.quiet_feet", "stealth.sneak", "stealth.grass"),
                        2, 8, 184_500, Map.of("mini_battery", 1, "flashlight", 2)),
                // 射撃: 設備で閉じたティア III
                view(SkillCategory.SHOOTING, "shooting.still_aim",
                        ids("shooting.reload", "shooting.swap", "shooting.ads", "shooting.recoil_v", "shooting.recoil_h", "shooting.steady"),
                        2, 8, 184_500, Map.of("usb", 0, "mini_battery", 1)),
                // 交易: どれか 1 つの前提 (大口の客)
                view(SkillCategory.TRADE, "trade.bulk", ids("trade.pharmakon", "trade.pitch"), 2, 8, 52_000, Map.of("usb", 1)),
                // 体力: 習得済み
                view(SkillCategory.BODY, "body.endurance", ids("body.endurance", "body.breathing", "body.long_run"), 3, 12, 300_000, Map.of()),
                // 医療: 設備 Lv1 (ティア II・III が閉じている)
                view(SkillCategory.MEDICAL, null, ids("medical.tourniquet"), 1, 3, 20_000, Map.of("CAT", 3)));
        var plain = PlainTextComponentSerializer.plainText();
        for (SkillTreeDialog.View v : samples) {
            checkLines(v, v.selected().id());
            check(!plain.serialize(SkillTreeDialog.learnLabel(v)).isEmpty(), "learn label");
        }
        check(samples.get(0).canLearn(), "empty_reload is learnable");
        check(!samples.get(1).canLearn(), "search_master lacks batteries");
        check(SkillTreeDialog.statusText(samples.get(1), samples.get(1).selected(), true).equals("素材が足りません"), "short materials");
        check(SkillTreeDialog.statusText(samples.get(2), samples.get(2).selected(), true).startsWith("スキルツリーの設備 Lv3"), "facility");
        check(SkillTreeDialog.wrap("マガジンへの弾込めと残弾の確認が速くなる。", 60).stream().noneMatch(s -> s.startsWith("。")), "wrap");

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            var gson = GsonComponentSerializer.gson();
            List<String> screens = new ArrayList<>();
            for (SkillTreeDialog.View v : samples) {
                Component body = Component.join(JoinConfiguration.newlines(), SkillTreeDialog.lines(v).stream().map(DialogLine::build).toList());
                screens.add("{\"title\":\"隠れ家 ― スキルツリー\",\"footer\":[\"" + plain.serialize(SkillTreeDialog.learnLabel(v)) + "\",\"閉じる\"],"
                        + "\"elements\":[{\"kind\":\"text\",\"width\":" + SkillTreeDialog.BODY_W + ",\"component\":" + gson.serialize(body) + "}]}");
            }
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("SkillTreeDialogLayoutTest OK");
    }
}

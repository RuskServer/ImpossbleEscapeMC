package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule;
import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TRADER_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxArtWidth;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxGlyph;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.line;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER_DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.DARK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.INK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NAME;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NG;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NOTE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.OK;

/**
 * スキルツリーの画面 (ダイアログ)。隠れ家のスキルツリー設備のブロックを右クリックで開く。
 * 本文は 1 つの要素 (幅 {@link #BODY_W}) で、パネルの絵はフォント iem:skill の字 (IEM_shader/tools/skill_font.py。座標もそちらと同じ)。
 * <ul>
 *   <li>上の札: 所持金・レベル・設備のレベルと、系統のタブ</li>
 *   <li>左の液晶: ツリー。ノードを押すとそのノードを選んだ画面を送り直す。ホバーで短い説明</li>
 *   <li>右の液晶: 選んだノードの効果・条件・費用 (隠れ家の設備と同じく、素材はスタッシュと持ち物から)</li>
 *   <li>下の帯: 習得する / 閉じる</li>
 * </ul>
 * 行はすべて {@link #LINE_W} にそろえる (クライアントの中央揃えで位置がずれないように)。パネルの左は行の頭の {@link #PANEL} 右、上は行 0 の上端
 */
public final class SkillTreeDialog implements Listener {

    private static final String NS = "iem";
    private static final String PREFIX = "skill/";
    static final Key FONT = Key.key("iem", "skill");

    static final int BODY_W = 392;
    static final int LINE_W = 384;
    static final int LINES = 22;
    private static final int PANEL = 2;

    // ---- フォント iem:skill の字 (skill_font.py と同じ) ----
    private static final char BG = '';
    private static final int[] BG_COLS = {0, 127, 254, 380};
    private static final char BAND = '';
    private static final int[][] BAND_X = {{6, 98}, {98, 189}};
    private static final char NODE_LEARNED = '', NODE_AVAILABLE = '', NODE_LOCKED = '', NODE_CLOSED = '';
    private static final char SELECTED = '';
    private static final char STUB = '';
    private static final char DROP = '';   // + 段の差 (1〜4)
    private static final char HLINE = '';  // + i (幅 2^i)
    private static final char ICON = '';
    private static final int NODE = 18, ICON_W = 12, SELECTED_W = 24, LINE_GLYPH_W = 2;

    // ---- 置き場所 (パネルの左上から) ----
    private static final int COL_X0 = 40, COL_PITCH2 = 16;
    private static final int FIRST_NODE_LINE = 6, ROW_LINES = 3;
    /** ティアの数字の行 (ティア I・II・III) */
    private static final int[] TIER_LABEL_LINE = {8, 14, 19};
    private static final int STATS_X = 201, STATS_RIGHT = 371;
    private static final int DETAIL_X = 199, DETAIL_VALUE = 223, DETAIL_HAVE_RIGHT = 359, DETAIL_MARK = 363, DETAIL_RIGHT = 371;
    private static final int DETAIL_FIRST_ROW = 9, DETAIL_LAST_ROW = 19, DETAIL_STATUS_LINE = 20;
    static final int DETAIL_ROWS = DETAIL_LAST_ROW - DETAIL_FIRST_ROW + 1;

    private static final TextColor SUB = TextColor.color(0xBEB68C);
    private static final TextColor LINE_LIT = AMBER;
    private static final TextColor LINE_DIM = TextColor.color(0x3C3A2E);
    private static final TextColor LOCKED_TEXT = TextColor.color(0x3E3C30);
    private static final String[] ROMAN = {"I", "II", "III"};
    private static final int NOTICE_TICKS = 80;

    // ---- 表示に使う値 (サーバーなしで組み立てられる。SkillTreeDialogLayoutTest が幅を確かめる) ----

    /**
     * @param category  開いている系統
     * @param selected  選んでいるノード
     * @param status    この系統のノードの状態
     * @param counts    系統ごとの [習得した数, 全部の数]
     * @param have      選んでいるノードの素材の、持っている数
     * @param itemNames 選んでいるノードの素材の名前
     */
    record View(SkillCategory category, SkillNode selected, Map<String, SkillModule.Status> status,
                Map<SkillCategory, int[]> counts, long money, int level, int facility,
                Map<String, Integer> have, Map<String, String> itemNames) {

        boolean learned(String id) {
            return status.get(id) == SkillModule.Status.LEARNED;
        }

        boolean affordable() {
            if (money < selected.money()) return false;
            for (HideoutFacility.ItemCost cost : selected.materials()) {
                if (have.getOrDefault(cost.itemId(), 0) < cost.amount()) return false;
            }
            return true;
        }

        boolean canLearn() {
            return status.get(selected.id()) == SkillModule.Status.AVAILABLE && affordable();
        }
    }

    private static final class Session {
        SkillCategory category = SkillCategory.SHOOTING;
        String selected;
        Component notice;
        int noticeUntil;
    }

    private static SkillTreeDialog instance;

    private final SkillModule module;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private SkillTreeDialog(SkillModule module) {
        this.module = module;
    }

    static void init(SkillModule module) {
        if (instance != null) return;
        instance = new SkillTreeDialog(module);
        Bukkit.getPluginManager().registerEvents(instance, module.plugin());
    }

    /** 画面を開く (前に開いていた系統とノードのまま) */
    public static void open(Player player) {
        if (instance == null) return;
        player.closeInventory();
        Session session = instance.sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
        instance.show(player, session);
    }

    private View view(Player player, PlayerData data, Session session) {
        Map<String, SkillModule.Status> status = new HashMap<>();
        Map<SkillCategory, int[]> counts = new EnumMap<>(SkillCategory.class);
        for (SkillNode node : SkillCatalog.all()) {
            int[] c = counts.computeIfAbsent(node.category(), k -> new int[2]);
            c[1]++;
            if (data.hasSkill(node.id())) c[0]++;
            if (node.category() == session.category) status.put(node.id(), SkillModule.status(data, node));
        }
        SkillNode selected = session.selected != null ? SkillCatalog.get(session.selected) : null;
        if (selected == null || selected.category() != session.category) {
            selected = defaultNode(session.category, status);
            session.selected = selected.id();
        }
        EconomyModule economy = module.economy();
        long money = economy != null ? (long) economy.getBalance(player.getUniqueId()) : 0;
        HideoutModule hideout = module.hideout();
        Map<String, Integer> have = hideout != null ? hideout.countMaterials(player, data, selected.materials()) : Map.of();
        Map<String, String> names = new HashMap<>();
        for (HideoutFacility.ItemCost cost : selected.materials()) names.put(cost.itemId(), itemName(cost.itemId()));
        return new View(session.category, selected, status, counts, money, data.getLevel(),
                data.getHideoutLevel(HideoutFacility.SKILL_TREE.id), have, names);
    }

    /** 系統を開いた時に選ぶノード: 習得できる最初のノード (無ければ最初のノード) */
    static SkillNode defaultNode(SkillCategory category, Map<String, SkillModule.Status> status) {
        List<SkillNode> nodes = SkillCatalog.of(category);
        for (SkillNode node : nodes) {
            if (status.get(node.id()) == SkillModule.Status.AVAILABLE) return node;
        }
        return nodes.get(0);
    }

    private static String itemName(String itemId) {
        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null && def.displayName != null ? def.displayName.replaceAll("§.", "") : itemId;
    }

    private void show(Player player, Session session) {
        PlayerData data = module.dataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        View view = view(player, data, session);
        Component title = Component.text("隠れ家 ― スキルツリー");
        if (session.notice != null && Bukkit.getCurrentTick() < session.noticeUntil) {
            title = title.append(Component.text("   ")).append(session.notice);
        }
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(join(lines(view)), BODY_W)))
                .build();
        ActionButton learn = ActionButton.create(learnLabel(view), learnHover(view), 150, action("learn"));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(learn, close))));
    }

    static Component learnLabel(View v) {
        if (v.learned(v.selected().id())) return Component.text("習得済み", NamedTextColor.GRAY);
        return Component.text("習得する", v.canLearn() ? NamedTextColor.WHITE : NamedTextColor.GRAY);
    }

    private static Component learnHover(View v) {
        if (v.learned(v.selected().id())) return null;
        return Component.text(String.format("%s を %,d₽ と素材で習得する", v.selected().name(), v.selected().money()));
    }

    // ---- 本文の行 ----

    static List<DialogLine> lines(View v) {
        List<DialogLine> lines = new ArrayList<>();
        for (int i = 0; i < LINES; i++) lines.add(newLine());
        List<SkillNode> nodes = SkillCatalog.of(v.category());

        // 行 0: 背景と、閉じたティアの帯 (後の行の線とノードより先に描く)
        DialogLine l0 = lines.get(0);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                l0.stamp(FONT, (char) (BG + r * 3 + c), BG_COLS[c + 1] - BG_COLS[c], PANEL + BG_COLS[c]);
            }
        }
        for (int t = 1; t <= 3; t++) {
            if (v.facility() >= SkillCatalog.facilityLevel(t)) continue;
            for (int h = 0; h < 2; h++) {
                l0.stamp(FONT, (char) (BAND + (t - 1) * 2 + h), BAND_X[h][1] - BAND_X[h][0], PANEL + BAND_X[h][0]);
            }
        }

        header(lines, v);
        tree(lines, v, nodes);
        detail(lines, v);

        for (DialogLine line : lines) line.moveTo(LINE_W);
        return lines;
    }

    private static DialogLine newLine() {
        return line();
    }

    /** 上の札: 名前・所持金などの液晶 (行 1)、系統のタブ (行 3) */
    private static void header(List<DialogLine> lines, View v) {
        DialogLine l1 = lines.get(1);
        l1.moveTo(PANEL + 9).text("スキル", INK).shift(5).text("ПОДГОТОВКА", SUB);
        String money = String.format("%,d₽", v.money());
        String level = String.valueOf(v.level());
        String facility = "Lv" + v.facility();
        int full = FontWidths.width("所持金") + 3 + FontWidths.width(money) + 8 + FontWidths.width("レベル") + 3 + FontWidths.width(level)
                + 8 + FontWidths.width("設備") + 3 + FontWidths.width(facility);
        l1.moveTo(PANEL + STATS_X);
        if (STATS_X + full <= STATS_RIGHT) {
            l1.text("所持金", AMBER_DIM).shift(3).text(money, OK).shift(8).text("レベル", AMBER_DIM).shift(3).text(level, AMBER)
                    .shift(8).text("設備", AMBER_DIM).shift(3).text(facility, AMBER);
        } else {   // 所持金が大きい時は名前を外す
            l1.text(money, OK).shift(8).text("Lv" + level, AMBER).shift(8).text("設備" + facility, AMBER);
        }

        DialogLine l3 = lines.get(3);
        l3.moveTo(PANEL + 9);
        for (SkillCategory c : SkillCategory.values()) {
            int[] count = v.counts().getOrDefault(c, new int[2]);
            String label = c.label + " " + count[0] + "/" + count[1];
            boolean on = c == v.category();
            int w = boxArtWidth(label);
            int start = l3.x;
            l3.box(TRADER_FONT, boxGlyph(label, on ? BOX_TAB_ON : BOX_TAB), w, label, on ? DARK : AMBER, on,
                    click("tab/" + c.id), null);
            l3.moveTo(start + w + 4);
        }
    }

    static int nodeX(SkillNode node) {
        return PANEL + COL_X0 + COL_PITCH2 * node.col2();
    }

    static int nodeLine(SkillNode node) {
        return FIRST_NODE_LINE + ROW_LINES * (node.row() - 1);
    }

    /** ツリー: ティアの数字、線 (暗い線を先に、光る線を後に)、ノード */
    private static void tree(List<DialogLine> lines, View v, List<SkillNode> nodes) {
        for (int t = 1; t <= 3; t++) {
            boolean open = v.facility() >= SkillCatalog.facilityLevel(t);
            DialogLine label = lines.get(TIER_LABEL_LINE[t - 1]);
            label.moveTo(PANEL + 9).text(ROMAN[t - 1], open ? AMBER_DIM : LOCKED_TEXT);
            if (!open) lines.get(TIER_LABEL_LINE[t - 1] + 1).moveTo(PANEL + 8).icon(UiIcon.LOCKED);
        }
        Map<String, SkillNode> byId = new HashMap<>();
        for (SkillNode node : nodes) byId.put(node.id(), node);
        for (boolean lit : new boolean[]{false, true}) {
            for (SkillNode child : nodes) {
                for (String parentId : child.requires()) {
                    SkillNode parent = byId.get(parentId);
                    if (parent == null || v.learned(parentId) != lit) continue;
                    edge(lines.get(nodeLine(parent)), parent, child, lit ? LINE_LIT : LINE_DIM);
                }
            }
        }
        for (SkillNode node : nodes) {
            DialogLine line = lines.get(nodeLine(node));
            int cx = nodeX(node);
            SkillModule.Status status = v.status().get(node.id());
            ClickEvent click = click("node/" + node.id().replace('.', '/'));
            Component hover = hover(v, node);
            line.moveTo(cx - NODE / 2).glyph(FONT, frame(status), NODE, NamedTextColor.WHITE, click, hover);
            line.moveTo(cx - ICON_W / 2).glyph(FONT, (char) (ICON + node.icon()), ICON_W, iconColor(status), click, hover);
            if (node == v.selected()) line.stamp(FONT, SELECTED, SELECTED_W, cx - SELECTED_W / 2);
        }
    }

    /** 親から子への線: 親の下の短い縦線、隙間の横線、子の上端まで下りる縦線 */
    private static void edge(DialogLine line, SkillNode parent, SkillNode child, TextColor color) {
        int px = nodeX(parent), cx = nodeX(child);
        line.stamp(FONT, STUB, LINE_GLYPH_W, px - 1, color);
        int from = Math.min(px, cx), to = Math.max(px, cx);
        int x = from;
        for (int i = 7; i >= 0; i--) {
            int w = 1 << i;
            while (to - x >= w) {
                line.stamp(FONT, (char) (HLINE + i), w, x, color);
                x += w;
            }
        }
        line.stamp(FONT, (char) (DROP + (child.row() - parent.row())), LINE_GLYPH_W, cx - 1, color);
    }

    private static char frame(SkillModule.Status status) {
        return switch (status) {
            case LEARNED -> NODE_LEARNED;
            case AVAILABLE -> NODE_AVAILABLE;
            case FACILITY -> NODE_CLOSED;
            default -> NODE_LOCKED;
        };
    }

    private static TextColor iconColor(SkillModule.Status status) {
        return switch (status) {
            case LEARNED -> TextColor.color(0xFAD682);
            case AVAILABLE -> OK;
            case FACILITY -> TextColor.color(0x42403A);
            default -> TextColor.color(0x5C5A4C);
        };
    }

    /** ノードのホバー: 名前・効果・状態 */
    private static Component hover(View v, SkillNode node) {
        Component state = Component.text(statusText(v, node, false), statusColor(v, node, false));
        return Component.text(node.name(), NAME)
                .append(Component.newline()).append(Component.text(node.effect(), INK))
                .append(Component.newline()).append(state)
                .append(Component.newline()).append(Component.text("クリックで選ぶ", NamedTextColor.DARK_GRAY));
    }

    /** 状態の字。withCost なら、習得できる時に費用が足りるかも見る */
    static String statusText(View v, SkillNode node, boolean withCost) {
        SkillModule.Status status = v.status().get(node.id());
        return switch (status) {
            case LEARNED -> "習得済み";
            case FACILITY -> "スキルツリーの設備 Lv" + SkillCatalog.facilityLevel(node.tier()) + " が必要";
            case PREREQUISITE -> "前のスキルが必要";
            case LEVEL -> "レベル " + SkillCatalog.playerLevel(node.tier()) + " が必要";
            case AVAILABLE -> !withCost ? "習得できます"
                    : v.money() < node.money() ? "所持金が足りません" : v.affordable() ? "習得できます" : "素材が足りません";
        };
    }

    private static TextColor statusColor(View v, SkillNode node, boolean withCost) {
        SkillModule.Status status = v.status().get(node.id());
        if (status == SkillModule.Status.LEARNED) return AMBER;
        if (status == SkillModule.Status.AVAILABLE && (!withCost || v.affordable())) return OK;
        return NG;
    }

    /** 右の液晶: 選んだノードの名前 (行 6・7)、効果・条件・費用 (行 9〜19)、状態 (行 20) */
    private static void detail(List<DialogLine> lines, View v) {
        SkillNode node = v.selected();
        SkillModule.Status status = v.status().get(node.id());
        DialogLine l6 = lines.get(6);
        l6.stamp(FONT, frame(status), NODE, PANEL + DETAIL_X);
        l6.stamp(FONT, (char) (ICON + node.icon()), ICON_W, PANEL + DETAIL_X + 3, iconColor(status));
        l6.moveTo(PANEL + DETAIL_X + 23).text(node.name(), NAME);
        lines.get(7).moveTo(PANEL + DETAIL_X + 23).text(node.category().label + " · ティア " + ROMAN[node.tier() - 1], AMBER_DIM);

        List<Row> rows = detailRows(v);
        for (int i = 0; i < rows.size() && DETAIL_FIRST_ROW + i <= DETAIL_LAST_ROW; i++) {
            rows.get(i).draw(lines.get(DETAIL_FIRST_ROW + i));
        }
        lines.get(DETAIL_STATUS_LINE).moveTo(PANEL + DETAIL_X).text(statusText(v, node, true), statusColor(v, node, true));
    }

    /** 詳細の行 (効果・条件・費用)。{@link #DETAIL_ROWS} 行まで出る */
    static List<Row> detailRows(View v) {
        SkillNode node = v.selected();
        SkillModule.Status status = v.status().get(node.id());
        List<Row> rows = new ArrayList<>();
        List<String> effect = wrap(node.effect(), DETAIL_RIGHT - DETAIL_VALUE);
        for (int i = 0; i < effect.size(); i++) rows.add(new Row(i == 0 ? "効果" : null, effect.get(i), INK, null, null));
        if (status != SkillModule.Status.LEARNED) {
            List<Row> conditions = new ArrayList<>();
            int facility = SkillCatalog.facilityLevel(node.tier());
            conditions.add(Row.check("設備 Lv" + facility, null, v.facility() >= facility));
            int level = SkillCatalog.playerLevel(node.tier());
            if (level > 0) conditions.add(Row.check("レベル " + level, null, v.level() >= level));
            if (node.anyOf() && node.requires().size() > 1) conditions.add(new Row(null, "次のどれか 1 つ", NOTE, null, null));
            for (String id : node.requires()) {
                SkillNode parent = SkillCatalog.get(id);
                conditions.add(Row.check(parent != null ? parent.name() : id, null, v.learned(id)));
            }
            rows.addAll(labeled("条件", conditions));
            List<Row> costs = new ArrayList<>();
            costs.add(Row.check(String.format("₽ %,d", node.money()), null, v.money() >= node.money()));
            for (HideoutFacility.ItemCost cost : node.materials()) {
                int have = v.have().getOrDefault(cost.itemId(), 0);
                costs.add(Row.check(v.itemNames().getOrDefault(cost.itemId(), cost.itemId()), have + "/" + cost.amount(), have >= cost.amount()));
            }
            rows.addAll(labeled("費用", costs));
        }
        return rows;
    }

    /** 右の液晶の 1 行: 左に見出し (最初の行だけ)、値、右に 持っている数 と ✓ ✕ */
    record Row(String label, String value, TextColor color, String have, Boolean ok) {
        static Row check(String value, String have, boolean ok) {
            return new Row(null, value, ok ? INK : TextColor.color(0xE67864), have, ok);
        }

        Row withLabel(String l) {
            return new Row(l, value, color, have, ok);
        }

        void draw(DialogLine line) {
            if (label != null) line.moveTo(PANEL + DETAIL_X).text(label, AMBER_DIM);
            int valueRight = PANEL + (ok != null ? DETAIL_HAVE_RIGHT - (have != null ? FontWidths.width(have) + 4 : 0) : DETAIL_RIGHT);
            line.moveTo(PANEL + DETAIL_VALUE).text(FontWidths.truncate(value, valueRight - (PANEL + DETAIL_VALUE)), color);
            if (ok == null) return;
            TextColor mark = ok ? OK : NG;
            if (have != null) line.moveTo(PANEL + DETAIL_HAVE_RIGHT - FontWidths.width(have)).text(have, mark);
            line.moveTo(PANEL + DETAIL_MARK).text(ok ? "✓" : "✕", mark);
        }
    }

    private static List<Row> labeled(String label, List<Row> rows) {
        if (!rows.isEmpty()) rows.set(0, rows.get(0).withLabel(label));
        return rows;
    }

    /**
     * 幅 width に収まるように折り返す。空白で区切った語 (英数字の並び・「a → b」・「0.4 秒」のような数と短い単位) は切らない。
     * 語が 1 行より長い時と、日本語の続きは字ごとに切る (句読点は行の頭に置かない)
     */
    static List<String> wrap(String text, int width) {
        // 語に分ける: 空白は前の語に付ける。英数字の並びは 1 語。数の後の空白 + 短い語 (単位) は数とつなぐ
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int n = Character.charCount(cp);
            boolean ascii = (cp < 0x80 && cp != ' ') || cp == '×';
            if (cp == ' ') {
                word.append(' ');
                char before = word.length() > 1 ? word.charAt(word.length() - 2) : ' ';
                char after = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                // 「a → b」は 1 語。数の後の短い単位もつなぐ
                boolean unit = before == '→' || after == '→' || (Character.isDigit(before) && unitFollows(text, i + 1));
                if (!unit) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else if (ascii) {
                if (word.length() > 0 && word.charAt(word.length() - 1) >= 0x80 && word.charAt(word.length() - 1) != '×') {
                    words.add(word.toString());
                    word.setLength(0);
                }
                word.appendCodePoint(cp);
            } else {
                char last = word.length() > 0 ? word.charAt(word.length() - 1) : ' ';
                if (word.length() > 0 && last != ' ' && last != '(' && last != '「' && !"、。)」".contains(new String(Character.toChars(cp)))) {
                    words.add(word.toString());
                    word.setLength(0);
                }
                word.appendCodePoint(cp);
            }
            i += n;
        }
        if (word.length() > 0) words.add(word.toString());

        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            if (line.length() > 0 && FontWidths.width((line + w).stripTrailing()) > width) {
                out.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            line.append(w);
            while (FontWidths.width(line.toString().stripTrailing()) > width) {   // 1 語が長すぎる時は字で切る
                int cut = line.length() - 1;
                while (cut > 1 && FontWidths.width(line.substring(0, cut)) > width) cut--;
                out.add(line.substring(0, cut));
                line.delete(0, cut);
            }
        }
        if (!line.toString().isBlank()) out.add(line.toString().stripTrailing());
        return out;
    }

    /** 空白の後が短い単位 (秒・分・HP・tick など、次の空白か終わりまでが 4 字以内) か */
    private static boolean unitFollows(String text, int from) {
        int end = text.indexOf(' ', from);
        if (end < 0) end = text.length();
        return end > from && end - from <= 4 && !Character.isDigit(text.charAt(from)) && text.charAt(from) != '→';
    }

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    private static ClickEvent click(String action) {
        return ClickEvent.custom(Key.key(NS, PREFIX + action));
    }

    private static DialogAction action(String action) {
        return DialogAction.customClick(Key.key(NS, PREFIX + action), null);
    }

    // ---- 操作 ----

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!NS.equals(id.namespace()) || !id.value().startsWith(PREFIX)) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        String action = id.value().substring(PREFIX.length());
        Bukkit.getScheduler().runTask(module.plugin(), () -> handle(player, action));
    }

    private void handle(Player player, String action) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !player.isOnline()) return;
        PlayerData data = module.dataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        if (action.startsWith("tab/")) {
            SkillCategory category = SkillCategory.byId(action.substring(4));
            if (category == null) return;
            session.category = category;
            session.selected = null;
        } else if (action.startsWith("node/")) {
            SkillNode node = SkillCatalog.get(action.substring(5).replace('/', '.'));
            if (node == null) return;
            session.category = node.category();
            session.selected = node.id();
        } else if (action.equals("learn")) {
            SkillNode node = session.selected != null ? SkillCatalog.get(session.selected) : null;
            if (node == null) return;
            String blocking = module.learn(player, data, node);
            if (blocking != null) {
                notice(session, Component.text(blocking, NamedTextColor.RED));
            } else {
                notice(session, Component.text(node.name() + " を習得しました", NamedTextColor.GREEN));
                player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 1.4f);
            }
        } else if (action.equals("close")) {
            sessions.remove(player.getUniqueId());
            player.closeDialog();
            return;
        } else {
            return;
        }
        player.playSound(player, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.3f);
        show(player, session);
    }

    private static void notice(Session session, Component text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }
}

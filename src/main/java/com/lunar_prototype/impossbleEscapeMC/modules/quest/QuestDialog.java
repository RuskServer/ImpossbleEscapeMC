package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.PDAGUI;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.item.factory.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.AndCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ExtractObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.LevelCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.ExpReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.MoneyReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.QuestReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.UnlockTradeReward;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDialog;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_DONE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_STEPS;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_CHIP;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_OFF;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_ACTIVE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_DONE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_HAND_IN;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_REPORT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_START;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_TEXT_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.DETAIL_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.DETAIL_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_DONE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_REPORT_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.QUEST_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RULE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RULE_MAX;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RULE_MIN;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RULE_STEP;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.SEG_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.SEG_MAX;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.SEG_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TRADER_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxWidth;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.line;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER_DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.DARK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.INK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NAME;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NG;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NOTE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.OK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ITEM_BOX;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_BODY_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_LINE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TEXT_X;

/**
 * クエスト (タスク) の画面 (ダイアログ)。買うトレーダーの画面 ({@link TraderDialog}) と同じ作りで、本文の文字に
 * リソースパックのフォントの絵を並べて端末と札を描き、目標の品物はダイアログのアイテム欄 (本物の見た目。ホバーで詳細) に出す。
 * <ul>
 *   <li>一覧: トレーダーから開くとそのトレーダーのクエスト (状態のタブ)、PDA から開くとすべてのトレーダーの進行中・受領可
 *       (トレーダーのタブ)。行に状態の札と目標の目盛り。「報告」「受領」の札はその場で押せ、名前を押すと詳細</li>
 *   <li>詳細: 依頼文・目標ごとの進み具合 (納品の目標は「納品」の札)・報酬・(ロック中なら) 受領の条件</li>
 *   <li>納品は納品の画面 ({@link QuestHandInDialog}) で、持ち物・スタッシュ・バックパックから使う物を選ぶ。戻るとこの画面に戻る</li>
 * </ul>
 * フォント: 共通の部品 (端末・札・枠・錠・箱・空白) は iem:trader (IEM_shader/tools/trader_font.py)、クエストだけの部品は
 * iem:quest (quest_font.py)、目標・報酬のアイコンは iem:icon ({@link UiIcon}、ui_icons.py)。字の番号・寸法はそれぞれと同じにする。
 * 本文の文字は行ごとに中央揃えになるので、すべての行をちょうど決まった幅にする ({@link DialogLine})
 */
public final class QuestDialog implements Listener {

    // ---- 寸法 (GUI の画素。共通の部品の字・寸法・色は gui/TerminalFont・TerminalPalette) ----
    /** 上の端末の絵の幅と、要素の幅・行の幅。絵の左端は行の頭 */
    private static final int HEADER_ART_W = HEADER_TILES * HEADER_TILE_W;
    private static final int HEADER_BODY_W = 340;
    static final int HEADER_W = HEADER_BODY_W - 8;
    /** 一覧の上の端末の行の数 (1 行目: 名前と液晶、3 行目: 札、5 行目: タブ) */
    private static final int HEADER_LINES = 6;
    /** 詳細の上の端末の行の数 (1 行目: 名前と液晶、2 行目: 進み具合、3 行目: 依頼主) */
    private static final int DETAIL_LINES = 4;
    /** 液晶の字の頭 (一覧は x 145 から、詳細は x 196 から) */
    private static final int LCD_X = 150;
    private static final int DETAIL_LCD_X = 201;
    /** 目標の行の文字の頭 (済の印の右) */
    private static final int OBJECTIVE_X = TEXT_X + CHECK_W + 4;
    private static final int CHIP_X = PLATE_X + PLATE_TILES * PLATE_TILE_W - 4 - CHIP_W;
    private static final int VALUE_RIGHT = CHIP_X - 6;
    private static final int LOCKED_VALUE_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 5;

    // ---- 色 (TraderDialog と同じ) ----
    private static final TextColor OK_TEXT = TextColor.color(0xAADC96);

    private static final String NS = "iem";
    private static final String PREFIX = "quest/";
    private static final int NOTICE_TICKS = 80;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** クエストの状態 (一覧の並び順) */
    enum Status {
        REPORT("報告可", CHIP_REPORT, AMBER), ACTIVE("進行中", CHIP_ACTIVE, AMBER), START("受領可", CHIP_START, OK),
        LOCKED("ロック", -1, NG), DONE("完了", CHIP_DONE, DIM);

        final String label;
        final int chip;
        final TextColor color;

        Status(String label, int chip, TextColor color) {
            this.label = label;
            this.chip = chip;
            this.color = color;
        }
    }

    /** トレーダーから開いた一覧のタブ */
    private enum Filter {
        ALL("すべて"), ACTIVE("進行中"), START("受領可"), DONE("完了"), LOCKED("ロック");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        boolean accepts(Status status) {
            return switch (this) {
                case ALL -> true;
                case ACTIVE -> status == Status.REPORT || status == Status.ACTIVE;
                case START -> status == Status.START;
                case DONE -> status == Status.DONE;
                case LOCKED -> status == Status.LOCKED;
            };
        }
    }

    private static final class Session {
        /** トレーダーから開いた (そのトレーダーの一覧) か、PDA から開いた (すべてのトレーダーの一覧) か */
        final String traderId;
        /** 一覧のタブ: トレーダーの一覧は Filter の名前、PDA の一覧はトレーダーの ID (null は「すべて」) */
        String tab;
        /** PDA の一覧で、完了・ロックのクエストも並べるか */
        boolean showAll;
        /** 詳細を開いているクエスト (null は一覧) */
        String detail;
        String notice;
        int noticeUntil;

        Session(String traderId) {
            this.traderId = traderId;
        }

        boolean pda() {
            return traderId == null;
        }
    }

    /** 1 つのクエストと、今の状態 */
    private record Entry(QuestDefinition quest, Status status, ActiveQuest active, int done, int total) {
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 品物の見本 (データパックの銃は作るのに手間がかかるので覚えておく) */
    private static final Map<String, ItemStack> ITEMS = new ConcurrentHashMap<>();
    private static QuestDialog instance;

    private QuestDialog() {
    }

    private static void ensureRegistered() {
        if (instance == null) {
            instance = new QuestDialog();
            Bukkit.getPluginManager().registerEvents(instance, ImpossbleEscapeMC.getInstance());
        }
    }

    // ---- 開く ----

    /** トレーダーのクエスト一覧を開く */
    public static void openTrader(Player player, TraderDefinition trader) {
        open(player, new Session(trader.id));
    }

    /** PDA から、すべてのトレーダーのクエスト一覧を開く */
    public static void openPda(Player player) {
        open(player, new Session(null));
    }

    /**
     * 納品画面から戻る: 開いていた画面 (一覧・詳細) に戻る。覚えていなければトレーダーの一覧
     */
    public static void resume(Player player, TraderDefinition trader) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) {
            openTrader(player, trader);
            return;
        }
        ensureRegistered();
        show(player, session);
    }

    private static void open(Player player, Session session) {
        ensureRegistered();
        player.closeInventory();
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session);
    }

    // ---- 画面 ----

    private static void show(Player player, Session session) {
        QuestModule quests = questModule();
        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule == null ? null : dataModule.getPlayerData(player.getUniqueId());
        if (quests == null || data == null) return;
        if (session.detail != null) {
            QuestDefinition quest = quests.getQuest(session.detail);
            if (quest != null) {
                showDetail(player, session, quests, data, entry(quests, data, quest));
                return;
            }
            session.detail = null;
        }
        showList(player, session, quests, data);
    }

    private static void showList(Player player, Session session, QuestModule quests, PlayerData data) {
        TraderDefinition trader = session.pda() ? null : trader(session.traderId);
        List<Entry> all = new ArrayList<>();
        for (QuestDefinition quest : session.pda() ? quests.getAllQuests() : quests.getQuestsByTrader(session.traderId)) {
            Entry e = entry(quests, data, quest);
            if (session.pda() && !session.showAll && (e.status() == Status.DONE || e.status() == Status.LOCKED)) continue;
            all.add(e);
        }

        // タブ: トレーダーの一覧は状態、PDA の一覧はトレーダー (数が 0 のタブは出さない)
        List<Tab> tabs = new ArrayList<>();
        List<Entry> shown = new ArrayList<>();
        if (session.pda()) {
            Map<String, Integer> byTrader = new LinkedHashMap<>();
            for (Entry e : all) byTrader.merge(e.quest().getTraderId().toLowerCase(Locale.ROOT), 1, Integer::sum);
            if (session.tab != null && !byTrader.containsKey(session.tab)) session.tab = null;
            tabs.add(new Tab("すべて " + all.size(), session.tab == null, "tab/all"));
            if (byTrader.size() > 1) {
                byTrader.forEach((id, count) -> tabs.add(new Tab(traderName(trader(id), id) + " " + count, id.equals(session.tab), "tab/" + id)));
            }
            for (Entry e : all) {
                if (session.tab == null || e.quest().getTraderId().equalsIgnoreCase(session.tab)) shown.add(e);
            }
        } else {
            Filter filter = session.tab != null ? Filter.valueOf(session.tab.toUpperCase(Locale.ROOT)) : Filter.ALL;
            for (Filter f : Filter.values()) {
                long count = all.stream().filter(e -> f.accepts(e.status())).count();
                if (f == Filter.ALL || count > 0 || f == filter) tabs.add(new Tab(f.label + " " + count, f == filter, "tab/" + f.name().toLowerCase(Locale.ROOT)));
            }
            for (Entry e : all) {
                if (filter.accepts(e.status())) shown.add(e);
            }
        }
        shown.sort(Comparator.comparing((Entry e) -> e.status().ordinal()));

        int reportable = (int) all.stream().filter(e -> e.status() == Status.REPORT).count();
        int active = (int) all.stream().filter(e -> e.status() == Status.ACTIVE).count();
        int startable = (int) all.stream().filter(e -> e.status() == Status.START).count();
        List<Chip> chips = new ArrayList<>();
        if (session.pda()) {
            chips.add(new Chip(session.showAll ? "完了・ロック: 表示" : "完了・ロック: 隠す", NOTE, "all",
                    session.showAll ? "進行中・受領可だけにする" : "完了・ロック中のクエストも並べる"));
        } else if (trader != null) {
            int bulk = QuestHandInDialog.openHandIns(data, trader, quests).size();
            if (bulk > 0) chips.add(new Chip("まとめて納品  " + bulk + "件", AMBER, "bulk", "入れた物を、進行中のクエストの納品に順に充てる"));
        }
        String name = session.pda() ? "TASKS" : traderName(trader, session.traderId).toUpperCase(Locale.ROOT);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(listHeader(new ListHeaderView(name, reportable, active, startable, chips, tabs)), HEADER_BODY_W));
        for (Entry e : shown) {
            body.add(DialogBody.item(listIcon(player, e)).description(DialogBody.plainMessage(row(rowView(e, session.pda())), ROW_BODY_W))
                    .showTooltip(true).showDecorations(false).width(ITEM_BOX).height(ITEM_BOX).build());
        }
        if (shown.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(session.pda() && !session.showAll
                    ? "進行中・受領できるクエストはありません" : "クエストはありません", NOTE)));
        }

        Component title = Component.text(session.pda() ? "PDA ― タスク" : traderName(trader, session.traderId) + " ― 依頼")
                .append(noticeText(session));
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        DialogType type;
        if (session.pda()) {
            type = DialogType.confirmation(ActionButton.create(Component.text("PDA に戻る"), null, 150, action("pda")), close);
        } else if (trader != null && trader.type == TraderType.BUY) {
            type = DialogType.confirmation(ActionButton.create(Component.text("ショップ"), Component.text("このトレーダーの品物を見る"),
                    150, action("shop")), close);
        } else {
            type = DialogType.notice(close);
        }
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private static void showDetail(Player player, Session session, QuestModule quests, PlayerData data, Entry e) {
        QuestDefinition quest = e.quest();
        String traderName = traderName(trader(quest.getTraderId()), quest.getTraderId());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(detailHeader(new DetailView(quest.getDisplayName(), traderName, e.status(), e.done(), e.total())),
                HEADER_BODY_W));
        if (quest.getDescription() != null && !quest.getDescription().isBlank()) {
            body.add(DialogBody.plainMessage(Component.text("「" + quest.getDescription() + "」", NOTE), HEADER_BODY_W));
        }

        // 受領の条件 (ロック中だけ)
        if (e.status() == Status.LOCKED) {
            body.add(DialogBody.plainMessage(section(UiIcon.LOCKED, "受領の条件"), HEADER_BODY_W));
            for (QuestCondition c : flatten(quest.getConditions())) {
                boolean met = c.isMet(data);
                UiIcon icon = c instanceof LevelCondition ? UiIcon.LEVEL : UiIcon.QUEST;
                body.add(itemRow(icon.item(), row(conditionView(c, met, data))));
            }
        }

        // 目標
        body.add(DialogBody.plainMessage(section(UiIcon.QUEST, "目標"), HEADER_BODY_W));
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective o = objectives.get(i);
            int target = target(o);
            int progress = progress(o, e.active(), i);
            boolean done = e.status() == Status.DONE || (e.active() != null && o.isCompleted(e.active(), i));
            boolean handIn = o instanceof HandInObjective && e.active() != null && !done;
            String count = o instanceof ReachLocationObjective ? (done ? "到達済み" : "未到達") : (done ? target : progress) + "/" + target;
            body.add(itemRow(objectiveIcon(player, o), row(new ObjectiveView(o.getDescription(), count, done,
                    done ? 1.0 : (double) progress / Math.max(1, target), handIn ? "handin/" + quest.getId() : null))));
        }

        // 報酬
        if (!quest.getRewards().isEmpty()) {
            body.add(DialogBody.plainMessage(section(UiIcon.MONEY, "報酬"), HEADER_BODY_W));
            for (QuestReward reward : quest.getRewards()) {
                body.add(itemRow(rewardIcon(player, reward), row(rewardView(reward))));
            }
        }

        Component title = Component.text(traderName + " ― " + quest.getDisplayName()).append(noticeText(session));
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("back"));
        ActionButton main = switch (e.status()) {
            case START -> ActionButton.create(Component.text("受領する"), null, 150, action("accept/" + quest.getId()));
            case REPORT -> ActionButton.create(Component.text("報告して報酬を受け取る"), null, 150, action("report/" + quest.getId()));
            case ACTIVE -> QuestHandInDialog.hasOpenHandIn(quest, e.active())
                    ? ActionButton.create(Component.text("納品する"), Component.text("納品の画面を開く"), 150, action("handin/" + quest.getId()))
                    : null;
            default -> null;
        };
        DialogType type = main != null ? DialogType.confirmation(main, back) : DialogType.notice(back);
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private static DialogBody itemRow(ItemStack icon, Component description) {
        return DialogBody.item(icon).description(DialogBody.plainMessage(description, ROW_BODY_W))
                .showTooltip(true).showDecorations(false).width(ITEM_BOX).height(ITEM_BOX).build();
    }

    private static Component noticeText(Session session) {
        if (session.notice == null || Bukkit.getCurrentTick() > session.noticeUntil) return Component.empty();
        return Component.text("  " + session.notice, NamedTextColor.YELLOW);
    }

    // ---- 一覧の上の端末 ----

    /** タブ・札 (押すと action) */
    record Tab(String label, boolean on, String action) {
    }

    record Chip(String label, TextColor color, String action, String hover) {
    }

    record ListHeaderView(String name, int reportable, int active, int startable, List<Chip> chips, List<Tab> tabs) {
    }

    static Component listHeader(ListHeaderView v) {
        return join(listHeaderLines(v));
    }

    /** 一覧の上の端末の行 (テストで、どの行も途中で幅を超えないことを確かめる) */
    static List<DialogLine> listHeaderLines(ListHeaderView v) {
        DialogLine line1 = line();
        for (int i = 0; i < HEADER_TILES; i++) line1.stamp(TRADER_FONT, (char) (HEADER_BASE + i), HEADER_TILE_W, i * HEADER_TILE_W);
        line1.moveTo(8).text(FontWidths.truncate(v.name(), LCD_X - 21), INK);
        line1.moveTo(LCD_X).text("報告可 ", AMBER_DIM).text(String.valueOf(v.reportable()), v.reportable() > 0 ? AMBER : DIM);
        line1.shift(8).text("進行中 ", AMBER_DIM).text(String.valueOf(v.active()), v.active() > 0 ? AMBER : DIM);
        line1.shift(8).text("受領可 ", AMBER_DIM).text(String.valueOf(v.startable()), v.startable() > 0 ? OK : DIM);
        line1.moveTo(HEADER_W);

        DialogLine line3 = line().moveTo(8);
        for (Chip chip : v.chips()) {
            if (line3.x + boxWidth(chip.label()) > HEADER_ART_W - 6) break;
            line3.box(chip.label(), BOX_CHIP, chip.color(), click(chip.action()), Component.text(chip.hover(), NOTE)).shift(4);
        }
        line3.moveTo(HEADER_W);

        DialogLine line5 = line().moveTo(8);
        for (Tab tab : v.tabs()) {
            if (line5.x + boxWidth(tab.label()) > HEADER_ART_W - 6) break;   // 入りきらないタブは出さない (折り返させない)
            line5.box(tab.label(), tab.on() ? BOX_TAB_ON : BOX_TAB, tab.on() ? DARK : AMBER, click(tab.action()), null).shift(4);
        }
        line5.moveTo(HEADER_W);

        List<DialogLine> lines = new ArrayList<>(List.of(line1, line().moveTo(HEADER_W), line3, line().moveTo(HEADER_W), line5));
        while (lines.size() < HEADER_LINES) lines.add(line().moveTo(HEADER_W));
        return lines;
    }

    // ---- 一覧の行 ----

    record Part(String text, TextColor color) {
    }

    /**
     * 一覧の行に出す値。trader は PDA の一覧で名前の横に出す依頼主 (null なら出さない)、value は右の値 (進み具合・報酬金)、
     * segTotal・segDone は 2 行目の右の目盛り (segTotal 0 なら出さない)、parts は 2 行目 (目標の要約・ロックの条件)
     */
    record RowView(String name, Status status, String trader, String value, TextColor valueColor, int segTotal, int segDone,
                   List<Part> parts, String detailAction, String chipAction, Component hover) {
    }

    private static RowView rowView(Entry e, boolean pda) {
        QuestDefinition q = e.quest();
        String trader = pda ? traderName(trader(q.getTraderId()), q.getTraderId()) : null;
        List<Part> parts = new ArrayList<>();
        String value = null;
        TextColor valueColor = AMBER;
        int segTotal = 0;
        switch (e.status()) {
            case REPORT, ACTIVE -> {
                for (int i = 0; i < q.getObjectives().size(); i++) {
                    QuestObjective o = q.getObjectives().get(i);
                    boolean done = o.isCompleted(e.active(), i);
                    parts.add(new Part((done ? "✓ " : "") + summary(o, e.active(), i), done ? OK : NOTE));
                }
                value = e.done() + "/" + e.total();
                valueColor = e.status() == Status.REPORT ? OK : AMBER;
                segTotal = e.total();
            }
            case START -> {
                for (QuestObjective o : q.getObjectives()) parts.add(new Part(summary(o, null, -1), NOTE));
                double money = money(q);
                if (money > 0) value = price(money);
            }
            case LOCKED -> {
                for (QuestCondition c : flatten(q.getConditions())) parts.add(new Part(conditionShort(c), NG));
                for (QuestReward r : q.getRewards()) {
                    if (r instanceof UnlockTradeReward unlock) parts.add(new Part(itemName(unlock.getItemId()) + " 販売解放", AMBER_DIM));
                }
                double money = money(q);
                if (money > 0) {
                    value = price(money);
                    valueColor = DIM;
                }
            }
            case DONE -> parts.add(new Part("完了済み", DIM));
        }
        String chipAction = switch (e.status()) {
            case REPORT -> "report/" + q.getId();
            case START -> "accept/" + q.getId();
            default -> "detail/" + q.getId();
        };
        Component hover = Component.text(String.join("\n", QuestText.wrap(q.getDescription(), 30)), NOTE)
                .append(Component.text("\n\nクリックで詳細", AMBER));
        return new RowView(q.getDisplayName(), e.status(), trader, value, valueColor, segTotal, e.done(), parts,
                "detail/" + q.getId(), chipAction, hover);
    }

    static Component row(RowView v) {
        return join(rowLines(v));
    }

    static List<DialogLine> rowLines(RowView v) {
        boolean locked = v.status() == Status.LOCKED;
        boolean dim = locked || v.status() == Status.DONE;
        DialogLine line1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            if (v.status() == Status.REPORT) {
                line1.stamp(QUEST_FONT, (char) (PLATE_REPORT_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
            } else {
                line1.stamp(TRADER_FONT, (char) (PLATE_BASE + (dim ? PLATE_TILES : 0) + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
            }
        }
        line1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        int nameX = TEXT_X;
        if (locked) {
            line1.stamp(TRADER_FONT, LOCK, LOCK_W, TEXT_X);
            nameX = TEXT_X + LOCK_W + 3;
        }
        int right = locked ? LOCKED_VALUE_RIGHT : VALUE_RIGHT;
        int valueX = v.value() != null ? right - FontWidths.width(v.value()) : right;
        int tagW = v.trader() != null ? boxWidth(v.trader()) + 4 : 0;
        String name = FontWidths.truncate(v.name(), valueX - 6 - nameX - tagW);
        line1.moveTo(nameX).text(name, dim ? DIM : NAME, click(v.detailAction()), v.hover());
        if (v.trader() != null) line1.shift(4).box(v.trader(), BOX_CHIP, NOTE, null, null);
        if (v.value() != null) line1.moveTo(valueX).text(v.value(), v.valueColor());
        if (v.status().chip >= 0) {
            String label = switch (v.status()) {
                case REPORT -> "報告";
                case ACTIVE -> "進行中";
                case START -> "受領";
                default -> "完了";
            };
            TextColor labelColor = switch (v.status()) {
                case REPORT -> DARK;
                case START -> OK;
                case DONE -> DIM;
                default -> AMBER;
            };
            line1.moveTo(CHIP_X).chip(QUEST_FONT, (char) (CHIP_BASE + v.status().chip), CHIP_W, CHIP_TEXT_FONT, label, labelColor,
                    v.status() == Status.REPORT, click(v.chipAction()),
                    Component.text(switch (v.status()) {
                        case REPORT -> "報告して報酬を受け取る";
                        case START -> "このクエストを受領する";
                        default -> "詳細を見る";
                    }, NOTE));
        }
        line1.moveTo(ROW_LINE_W);

        DialogLine line2 = line().moveTo(TEXT_X);
        int limit = v.segTotal() > 0 ? VALUE_RIGHT - SEG_W - 6 : right;
        for (Part part : v.parts()) {
            if (line2.x >= limit - 6) break;
            line2.text(FontWidths.truncate(part.text(), limit - line2.x), part.color()).shift(6);
        }
        if (v.segTotal() > 0) {
            int total = Math.min(SEG_MAX, v.segTotal());
            int done = v.segTotal() <= SEG_MAX ? v.segDone() : (int) Math.floor((double) v.segDone() * SEG_MAX / v.segTotal());
            line2.moveTo(Math.max(line2.x, VALUE_RIGHT - SEG_W));
            line2.glyph(QUEST_FONT, (char) (SEG_BASE + (total - 1) * 6 + done), SEG_W, null, null);
        }
        line2.moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    // ---- 詳細 ----

    record DetailView(String name, String trader, Status status, int done, int total) {
    }

    static Component detailHeader(DetailView v) {
        return join(detailHeaderLines(v));
    }

    static List<DialogLine> detailHeaderLines(DetailView v) {
        DialogLine line1 = line();
        for (int i = 0; i < DETAIL_TILES; i++) line1.stamp(QUEST_FONT, (char) (DETAIL_BASE + i), HEADER_TILE_W, i * HEADER_TILE_W);
        line1.moveTo(8).text(FontWidths.truncate(v.name(), DETAIL_LCD_X - 21), INK);
        line1.moveTo(DETAIL_LCD_X).text(v.status().label, v.status().color);
        if (v.status() == Status.REPORT || v.status() == Status.ACTIVE) {
            line1.shift(8).text("目標 ", AMBER_DIM).text(v.done() + "/" + v.total(), v.status() == Status.REPORT ? OK : AMBER);
        }
        line1.moveTo(HEADER_W);

        DialogLine line2 = line();
        if (v.status() != Status.LOCKED) {
            double ratio = v.status() == Status.DONE ? 1.0 : v.status() == Status.START ? 0.0 : (double) v.done() / Math.max(1, v.total());
            line2.moveTo(DETAIL_LCD_X).glyph(QUEST_FONT, barGlyph(ratio, ratio >= 1.0), BAR_W, null, null);
        }
        line2.moveTo(HEADER_W);

        DialogLine line3 = line().moveTo(8).text("依頼主 ", NOTE).text(FontWidths.truncate(v.trader(), DETAIL_LCD_X - 60), INK).moveTo(HEADER_W);
        return List.of(line1, line2, line3, line().moveTo(HEADER_W));
    }

    /** 見出し: アイコン・名前・線 (行の終わりまで) */
    static Component section(UiIcon icon, String label) {
        return sectionLine(icon, label).build();
    }

    static DialogLine sectionLine(UiIcon icon, String label) {
        DialogLine line = line().icon(icon).shift(3).text(label, AMBER).shift(5);
        // 線の字は「幅 + 1」進むので、2 つ目からは 1 戻してつなげる (すき間を作らない)
        int end = HEADER_W - 2;
        boolean first = true;
        while (end - line.x >= RULE_MIN + 1) {
            if (!first) line.shift(-1);
            int w = Math.min(RULE_MAX, (end - line.x - 1) / RULE_STEP * RULE_STEP);
            if (w < RULE_MIN) break;
            line.glyph(QUEST_FONT, (char) (RULE_BASE + (w - RULE_MIN) / RULE_STEP), w, null, null);
            first = false;
        }
        return line.moveTo(HEADER_W);
    }

    /** 目標の行: 済の印・説明 (納品の目標は右に「納品」の札) / 数と進み具合の棒 */
    record ObjectiveView(String text, String count, boolean done, double ratio, String handInAction) {
    }

    static Component row(ObjectiveView v) {
        return join(objectiveLines(v));
    }

    static List<DialogLine> objectiveLines(ObjectiveView v) {
        DialogLine line1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            if (v.done()) line1.stamp(QUEST_FONT, (char) (PLATE_DONE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
            else line1.stamp(TRADER_FONT, (char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        line1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        line1.moveTo(TEXT_X).glyph(QUEST_FONT, v.done() ? CHECK_ON : CHECK_OFF, CHECK_W, null, null);
        int limit = (v.handInAction() != null ? CHIP_X - 6 : LOCKED_VALUE_RIGHT) - OBJECTIVE_X;
        line1.moveTo(OBJECTIVE_X).text(FontWidths.truncate(v.text(), limit), v.done() ? OK_TEXT : NAME);
        if (v.handInAction() != null) {
            line1.moveTo(CHIP_X).chip(QUEST_FONT, (char) (CHIP_BASE + CHIP_HAND_IN), CHIP_W, CHIP_TEXT_FONT, "納品", AMBER, false,
                    click(v.handInAction()), Component.text("納品の画面を開く", NOTE));
        }
        line1.moveTo(ROW_LINE_W);

        DialogLine line2 = line().moveTo(OBJECTIVE_X).text(v.count(), v.done() ? OK : AMBER).shift(5);
        line2.glyph(QUEST_FONT, barGlyph(v.ratio(), v.done()), BAR_W, null, null).moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    /** 報酬・受領の条件の行: 1 行目に名前、2 行目に補足 (札は敷かない) */
    record NoteView(String text, TextColor color, String note, TextColor noteColor) {
    }

    static Component row(NoteView v) {
        return join(noteLines(v));
    }

    static List<DialogLine> noteLines(NoteView v) {
        DialogLine line1 = line().stamp(TRADER_FONT, RING, RING_W, RING_X);
        line1.moveTo(TEXT_X).text(FontWidths.truncate(v.text(), ROW_LINE_W - TEXT_X - 4), v.color()).moveTo(ROW_LINE_W);
        DialogLine line2 = line().moveTo(TEXT_X).text(FontWidths.truncate(v.note(), ROW_LINE_W - TEXT_X - 4), v.noteColor()).moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    private static NoteView rewardView(QuestReward reward) {
        if (reward instanceof MoneyReward money) return new NoteView(price(money.getAmount()), AMBER, "所持金に加わる", NOTE);
        if (reward instanceof ExpReward exp) return new NoteView(String.format("EXP %,d", exp.getAmount()), OK, "経験値", NOTE);
        if (reward instanceof UnlockTradeReward unlock) {
            String trader = traderName(trader(unlock.getTraderId()), unlock.getTraderId());
            return new NoteView(itemName(unlock.getItemId()) + " 販売解放", INK, trader + " で買えるようになる", NOTE);
        }
        return new NoteView(reward.getDescription(), INK, "", NOTE);
    }

    private static NoteView conditionView(QuestCondition c, boolean met, PlayerData data) {
        String note;
        if (c instanceof LevelCondition level) {
            note = met ? "達成 (今 Lv." + data.getLevel() + ")" : "あと " + (level.getRequiredLevel() - data.getLevel()) + " レベル (今 Lv." + data.getLevel() + ")";
        } else {
            note = met ? "達成" : "未達成";
        }
        return new NoteView(c.getDescription(), met ? OK_TEXT : NAME, note, met ? OK : NG);
    }

    // ---- 文字の行 ----

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    private static char barGlyph(double ratio, boolean done) {
        int step = (int) Math.round(Math.max(0, Math.min(1, ratio)) * BAR_STEPS);
        return (char) ((done ? BAR_DONE_BASE : BAR_BASE) + step);
    }

    // ---- クエストの値 ----

    private static Entry entry(QuestModule quests, PlayerData data, QuestDefinition quest) {
        ActiveQuest active = data.getActiveQuests().get(quest.getId());
        int total = quest.getObjectives().size();
        int done = 0;
        if (active != null) {
            for (int i = 0; i < total; i++) {
                if (quest.getObjectives().get(i).isCompleted(active, i)) done++;
            }
        }
        Status status;
        if (data.isQuestCompleted(quest.getId())) {
            status = Status.DONE;
            done = total;
        } else if (active != null) {
            status = done >= total ? Status.REPORT : Status.ACTIVE;
        } else {
            status = quests.canStart(data, quest) ? Status.START : Status.LOCKED;
        }
        return new Entry(quest, status, active, done, total);
    }

    private static int target(QuestObjective o) {
        if (o instanceof HandInObjective h) return h.getTargetAmount();
        if (o instanceof KillEntityObjective k) return k.getTargetAmount();
        if (o instanceof ExtractObjective x) return x.getTargetAmount();
        return 1;
    }

    private static int progress(QuestObjective o, ActiveQuest active, int index) {
        return active == null ? 0 : Math.min(target(o), active.getProgress(index));
    }

    /** 一覧の 2 行目に出す目標の要約 (active が null なら数を出さない) */
    private static String summary(QuestObjective o, ActiveQuest active, int index) {
        String count = active != null ? " " + progress(o, active, index) + "/" + target(o) : target(o) > 1 ? " ×" + target(o) : "";
        if (o instanceof HandInObjective h) {
            String name = h.getItemId() != null ? itemName(h.getItemId()) : h.getItemType();
            return name + count + " 納品";
        }
        if (o instanceof KillEntityObjective k) {
            String conditions = k.shortConditions();
            return k.getEntityType() + " 排除" + (conditions.isEmpty() ? "" : " (" + conditions + ")") + count;
        }
        if (o instanceof ExtractObjective x) {
            return (x.getMapId() != null ? x.getMapId() : "任意のマップ") + " から脱出" + (x.getTargetAmount() > 1 ? count : "");
        }
        if (o instanceof ReachLocationObjective r) return (r.getLocationName() != null ? r.getLocationName() : "指定地点") + " へ到達";
        return o.getDescription();
    }

    private static List<QuestCondition> flatten(List<QuestCondition> conditions) {
        List<QuestCondition> out = new ArrayList<>();
        for (QuestCondition c : conditions) {
            if (c instanceof AndCondition and) out.addAll(flatten(and.getConditions()));
            else out.add(c);
        }
        return out;
    }

    private static String conditionShort(QuestCondition c) {
        if (c instanceof LevelCondition level) return "Lv." + level.getRequiredLevel();
        if (c instanceof CompletedQuestCondition done) {
            QuestModule quests = questModule();
            QuestDefinition q = quests != null ? quests.getQuest(done.getRequiredQuestId()) : null;
            return "「" + (q != null ? q.getDisplayName() : done.getRequiredQuestId()) + "」完了後";
        }
        return c.getDescription();
    }

    private static double money(QuestDefinition q) {
        double sum = 0;
        for (QuestReward r : q.getRewards()) {
            if (r instanceof MoneyReward m) sum += m.getAmount();
        }
        return sum;
    }

    // ---- 品物・アイコン ----

    /** 一覧の行の品物: ロック中は解放される品物 (無ければ最初の目標)、ほかはまだ済んでいない最初の目標 */
    private static ItemStack listIcon(Player player, Entry e) {
        List<QuestObjective> objectives = e.quest().getObjectives();
        if (e.status() == Status.LOCKED) {
            for (QuestReward r : e.quest().getRewards()) {
                if (r instanceof UnlockTradeReward unlock) {
                    ItemStack item = item(player, unlock.getItemId());
                    if (item != null) return item;
                }
            }
        }
        if (e.active() != null && e.status() == Status.ACTIVE) {
            for (int i = 0; i < objectives.size(); i++) {
                if (!objectives.get(i).isCompleted(e.active(), i)) return objectiveIcon(player, objectives.get(i));
            }
        }
        if (e.status() == Status.REPORT) return UiIcon.REPORT.item();
        return objectives.isEmpty() ? UiIcon.QUEST.item() : objectiveIcon(player, objectives.getFirst());
    }

    private static ItemStack objectiveIcon(Player player, QuestObjective o) {
        if (o instanceof HandInObjective h) {
            ItemStack item = h.getItemId() != null ? item(player, h.getItemId()) : null;
            return item != null ? item : UiIcon.HAND_IN.item();
        }
        if (o instanceof KillEntityObjective) return UiIcon.KILL.item();
        if (o instanceof ExtractObjective) return UiIcon.EXTRACT.item();
        if (o instanceof ReachLocationObjective) return UiIcon.REACH.item();
        return UiIcon.QUEST.item();
    }

    private static ItemStack rewardIcon(Player player, QuestReward reward) {
        if (reward instanceof MoneyReward) return UiIcon.MONEY.item();
        if (reward instanceof ExpReward) return UiIcon.EXP.item();
        if (reward instanceof UnlockTradeReward unlock) {
            ItemStack item = item(player, unlock.getItemId());
            return item != null ? item : UiIcon.UNLOCK.item();
        }
        return UiIcon.QUEST.item();
    }

    /** 品物の見本 (覚えておいたものの写し)。無い ID なら null */
    private static ItemStack item(Player player, String id) {
        ItemStack cached = ITEMS.get(id);
        if (cached == null) {
            cached = GameItems.create(player.getWorld(), id, null);
            if (cached == null) return null;
            ITEMS.put(id, cached);
        }
        return cached.clone();
    }

    private static String itemName(String id) {
        ItemStack cached = ITEMS.get(id);
        if (cached == null && !Bukkit.getWorlds().isEmpty()) {
            cached = GameItems.create(Bukkit.getWorlds().getFirst(), id, null);
            if (cached != null) ITEMS.put(id, cached);
        }
        return cached != null ? PlainTextComponentSerializer.plainText().serialize(cached.effectiveName()) : id;
    }

    // ---- トレーダー ----

    private static TraderModule traderModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(TraderModule.class);
    }

    private static QuestModule questModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(QuestModule.class);
    }

    private static TraderDefinition trader(String id) {
        TraderModule traders = traderModule();
        return traders == null || id == null ? null : traders.getTrader(id);
    }

    private static String traderName(TraderDefinition trader, String fallback) {
        if (trader == null) return fallback;
        return PlainTextComponentSerializer.plainText().serialize(LEGACY.deserialize(trader.displayName)).replace(" (Buy Only)", "");
    }

    private static String price(double value) {
        return String.format("%,d₽", Math.round(value));
    }

    /** 押した時の操作。キーは小文字しか使えないので、クエストの ID も小文字にする (押された時は大文字小文字を区別せずに引く) */
    private static ClickEvent click(String action) {
        return action == null ? null : ClickEvent.custom(Key.key(NS, (PREFIX + action).toLowerCase(Locale.ROOT)));
    }

    private static DialogAction action(String action) {
        return DialogAction.customClick(Key.key(NS, (PREFIX + action).toLowerCase(Locale.ROOT)), null);
    }

    private static QuestDefinition findQuest(QuestModule quests, String id) {
        if (id == null) return null;
        QuestDefinition quest = quests.getQuest(id);
        if (quest != null) return quest;
        for (QuestDefinition q : quests.getAllQuests()) {
            if (q.getId().equalsIgnoreCase(id)) return q;
        }
        return null;
    }

    // ---- 操作 ----

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!NS.equals(id.namespace()) || !id.value().startsWith(PREFIX)) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        String action = id.value().substring(PREFIX.length());
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> handle(player, action));
    }

    private static void handle(Player player, String action) {
        if (!player.isOnline()) return;
        Session session = SESSIONS.get(player.getUniqueId());
        if (action.equals("close") || session == null) {
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        QuestModule quests = questModule();
        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule == null ? null : dataModule.getPlayerData(player.getUniqueId());
        if (quests == null || data == null) return;
        String[] parts = action.split("/", 2);
        String arg = parts.length > 1 ? parts[1] : null;
        QuestDefinition quest = findQuest(quests, arg);
        switch (parts[0]) {
            case "tab" -> session.tab = arg == null || arg.equals("all") ? null : arg;
            case "all" -> session.showAll = !session.showAll;
            case "detail" -> {
                if (quest == null) return;
                session.detail = quest.getId();
            }
            case "back" -> session.detail = null;
            case "accept" -> {
                if (quest == null) return;
                if (quests.canStart(data, quest)) {
                    data.getActiveQuests().put(quest.getId(), new ActiveQuest(quest.getId()));
                    data.setDirty(true);
                    notice(session, "受領しました: " + quest.getDisplayName());
                    player.sendMessage(Component.text("クエストを受領しました: " + quest.getDisplayName(), NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                    show(player, session);
                    return;
                }
                notice(session, "まだ受領できません");
            }
            case "report" -> {
                if (quest == null) return;
                ActiveQuest active = data.getActiveQuests().get(quest.getId());
                if (active != null && quests.isAllObjectivesMet(quest, active)) {
                    quests.completeQuest(player, data, quest);   // 報酬・音・チャットは completeQuest
                    notice(session, "報告しました: " + quest.getDisplayName());
                    show(player, session);
                    return;
                }
                notice(session, "まだ目標が残っています");
            }
            case "handin", "bulk" -> {
                TraderDefinition trader = trader(quest != null ? quest.getTraderId() : session.traderId);
                if (trader == null) return;
                // 納品の画面に替わる。納品の画面の「戻る」で resume して、この画面に戻る
                if (quest != null) {
                    QuestHandInDialog.open(player, trader, quest);
                } else if (!QuestHandInDialog.openForTrader(player, trader, quests)) {
                    notice(session, "納品が残っているクエストはありません");
                    break;
                }
                return;
            }
            case "shop" -> {
                TraderDefinition trader = trader(session.traderId);
                TraderModule traders = traderModule();
                SESSIONS.remove(player.getUniqueId());
                if (trader != null && traders != null) TraderDialog.open(traders, trader, player);
                else player.closeDialog();
                return;
            }
            case "pda" -> {
                SESSIONS.remove(player.getUniqueId());
                player.closeDialog();
                new PDAGUI(player).open();
                return;
            }
            default -> {
                return;
            }
        }
        player.playSound(player, "iem:pda.click", org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);   // PDA のボタン (IEM_shader/tools/pda_sfx.py)
        show(player, session);
    }

    private static void notice(Session session, String text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }
}

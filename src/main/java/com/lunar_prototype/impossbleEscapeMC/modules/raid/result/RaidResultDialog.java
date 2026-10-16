package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResultFont.*;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_CHIP;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxArtWidth;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxGlyph;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxWidth;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
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
 * レイド結果の画面。4 ページを順に送る (2 ページ目の戦利品だけチェストの画面 {@link RaidResultLootGUI}):
 * <ol>
 *   <li>経験値: 内訳が 1 行ずつ足され、レベルの棒が伸びる (ダイアログを 2 tick ごとに送り直す。送り直しても
 *       クライアントは同じ描画の間に画面を差し替えるので、ちらつかない)</li>
 *   <li>戦利品 (チェストの画面)</li>
 *   <li>クエスト納品: 持ち物・バックパックの中・スタッシュの FIR の物で納品できる目標 ({@link QuestAutoHandIn})。行ごと・まとめて (確認を挟む)</li>
 *   <li>キルログ: 倒した相手を時刻順に (武器・弾・距離・部位)。倒された時はその相手も</li>
 * </ol>
 * 見た目の部品はクエストの画面と同じ (フォント iem:trader・iem:quest) と、iem:result ({@link RaidResultFont})。
 * 本文の文字は行ごとに中央揃えになるので、すべての行をちょうど決まった幅にする ({@link DialogLine})
 */
public final class RaidResultDialog implements Listener {

    // ---- 寸法 (GUI の画素。共通の部品の字・寸法・色は gui/TerminalFont・TerminalPalette) ----
    private static final int BODY_W = 340;
    static final int LINE_W = BODY_W - 8;
    private static final int CHIP_X = PLATE_X + PLATE_TILES * PLATE_TILE_W - 4 - CHIP_ACTION_W;
    private static final int ROW_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 5;

    private static final String NS = "iem";
    private static final String PREFIX = "result/";
    /** 経験値の演出の長さと、送り直す間隔 (tick) */
    private static final int ANIM_TICKS = 30, ANIM_STEP = 2;
    private static final int NOTICE_TICKS = 80;

    enum Page { EXP, HANDIN, CONFIRM, KILLS }

    private static final class Session {
        final RaidReport report;
        Page page = Page.EXP;
        int animTick;
        BukkitTask anim;
        /** キルログの絞り込み (null はすべて) */
        RaidReport.Kind killFilter;
        String notice;
        int noticeUntil;

        Session(RaidReport report) {
            this.report = report;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static RaidResultDialog instance;

    private RaidResultDialog() {
    }

    private static void ensureRegistered() {
        if (instance == null) {
            instance = new RaidResultDialog();
            Bukkit.getPluginManager().registerEvents(instance, ImpossbleEscapeMC.getInstance());
        }
    }

    /** 1 ページ目 (経験値の演出) から開く */
    static void open(Player player, RaidReport report) {
        ensureRegistered();
        stopAnim(SESSIONS.get(player.getUniqueId()));
        Session session = new Session(report);
        SESSIONS.put(player.getUniqueId(), session);
        player.closeInventory();
        show(player, session);
    }

    /** 戦利品のページ (チェストの画面) から戻る */
    static void showPage(Player player, RaidReport report, Page page) {
        ensureRegistered();
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || session.report != report) {
            session = new Session(report);
            session.animTick = ANIM_TICKS;
            SESSIONS.put(player.getUniqueId(), session);
        }
        session.page = page;
        show(player, session);
    }

    private static void show(Player player, Session session) {
        switch (session.page) {
            case EXP -> showExp(player, session);
            case HANDIN -> showHandIn(player, session);
            case CONFIRM -> showConfirm(player, session);
            case KILLS -> showKills(player, session);
        }
    }

    // ---- 1. 経験値 ----

    private static void showExp(Player player, Session session) {
        RaidReport r = session.report;
        double t = Math.min(1.0, session.animTick / (double) ANIM_TICKS);
        List<RaidReport.ExpLine> lines = r.exp();
        int n = Math.max(1, lines.size());
        double gained = 0;
        double[] shown = new double[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            shown[i] = Math.max(0, Math.min(1, t * n - i));
            gained += lines.get(i).amount() * shown[i];
        }
        LevelModule levels = ImpossbleEscapeMC.getInstance().getServiceContainer().get(LevelModule.class);
        int level = r.levelBefore();
        long exp = r.expBefore() + Math.round(gained);
        while (levels != null && exp >= levels.getRequiredExperience(level)) {
            exp -= levels.getRequiredExperience(level);
            level++;
        }
        long need = levels != null ? levels.getRequiredExperience(level) : Math.max(1, exp);
        boolean leveled = level > r.levelBefore();

        List<DialogBody> body = new ArrayList<>();
        body.add(header(r, null, null));
        body.add(DialogBody.plainMessage(join(levelLines(new LevelView(level, leveled, Math.round(gained), exp, need,
                leveled ? 0 : r.expBefore(), r.pending()))), BODY_W));
        List<ExpRow> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (shown[i] <= 0) break;
            RaidReport.ExpLine l = lines.get(i);
            rows.add(new ExpRow(l.icon(), l.label(), l.note(), Math.round(l.amount() * shown[i])));
        }
        body.add(DialogBody.plainMessage(join(expRowLines(rows, t >= 1.0 ? r.expTotal() : -1)), BODY_W));

        ActionButton next = ActionButton.create(Component.text("次へ: 戦利品"), null, 150, action("loot"));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        showDialog(player, session, body, DialogType.confirmation(next, close));

        if (t < 1.0 && session.anim == null) {
            UUID id = player.getUniqueId();
            session.anim = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || SESSIONS.get(id) != session || session.page != Page.EXP) {
                    stopAnim(session);
                    return;
                }
                session.animTick += ANIM_STEP;
                if (session.animTick >= ANIM_TICKS) {
                    session.animTick = ANIM_TICKS;
                    stopAnim(session);
                    p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                }
                showExp(p, session);
            }, ANIM_STEP, ANIM_STEP);
        }
    }

    private static void stopAnim(Session session) {
        if (session != null && session.anim != null) {
            session.anim.cancel();
            session.anim = null;
        }
    }

    /** レベルの板に出す値。base は棒のうちこのレイドより前の分 (明るい色で伸びるのはその先) */
    record LevelView(int level, boolean leveled, long gained, long exp, long need, long base, boolean pending) {
    }

    static List<DialogLine> levelLines(LevelView v) {
        DialogLine l1 = line();
        for (int i = 0; i < LEVEL_TILES; i++) l1.stamp(FONT, (char) (LEVEL_BASE + i), LEVEL_TILE_W, i * LEVEL_TILE_W);
        l1.moveTo(12).text("Lv " + v.level(), v.leveled() ? OK : AMBER);
        l1.moveTo(60).text("+" + fmt(v.gained()) + " EXP", OK);
        if (v.leveled()) l1.moveTo(LINE_W - 8 - 24 - CHIP_GREEN_W - 1).box(FONT, CHIP_GREEN, CHIP_GREEN_W, "LEVEL UP", DARK, true, null, null);
        l1.moveTo(LINE_W);

        DialogLine l2 = line().moveTo(12);
        l2.stamp(FONT, BAR_TRACK, BAR_TRACK_W, 12).stamp(FONT, (char) (BAR_TRACK + 1), BAR_TRACK_W, 12 + BAR_TRACK_W);
        int total = (int) Math.round(BAR_W * Math.min(1.0, v.exp() / (double) Math.max(1, v.need())));
        if (v.leveled()) {
            fill(l2, BAR_GREEN, total);
        } else {
            int base = (int) Math.round(BAR_W * Math.min(1.0, v.base() / (double) Math.max(1, v.need())));
            fill(l2, BAR_AMBER, Math.min(base, total));
            fill(l2, BAR_LIGHT, Math.max(0, total - base));
        }
        l2.moveTo(LINE_W);

        DialogLine l3 = line().moveTo(12).text(fmt(v.exp()) + " / " + fmt(v.need()), INK);
        l3.textRight("次のレベルまで " + fmt(Math.max(0, v.need() - v.exp())), NOTE, 12 + BAR_W).moveTo(LINE_W);
        DialogLine l4 = line();
        if (v.pending()) l4.moveTo(12).text("経験値はレイドの終了時に加算されます", NOTE);
        l4.moveTo(LINE_W);
        return List.of(l1, l2, l3, l4, line().moveTo(LINE_W));
    }

    /** 棒の中身 (幅 2^p の字を組み合わせる。字は幅 + 1 進むので 1 戻してつなげる) */
    private static void fill(DialogLine line, int color, int width) {
        for (int p = 7; p >= 0 && width > 0; p--) {
            while (width >= (1 << p)) {
                line.glyph(FONT, (char) (BAR_FILL + color * 8 + p), 1 << p, null, null).shift(-1);
                width -= 1 << p;
            }
        }
    }

    record ExpRow(UiIcon icon, String label, String note, long amount) {
    }

    /** 経験値の内訳: 1 行に 1 つ (1 行おきに縞)。total が 0 以上なら最後に合計 */
    static List<DialogLine> expRowLines(List<ExpRow> rows, long total) {
        List<DialogLine> lines = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ExpRow row = rows.get(i);
            DialogLine l = line();
            if (i % 2 == 0) l.stamp(FONT, STRIPE, STRIPE_W, 0).stamp(FONT, (char) (STRIPE + 1), STRIPE_W, STRIPE_W);
            l.moveTo(4).icon(row.icon()).shift(4).text(row.label(), NAME);
            if (row.note() != null && !row.note().isEmpty()) l.shift(6).text(row.note(), NOTE);
            l.textRight("+" + fmt(row.amount()), OK, 2 * STRIPE_W - 4).moveTo(LINE_W);
            lines.add(l);
        }
        if (total >= 0) {
            lines.add(line().textRight("合計  +" + fmt(total) + " EXP", OK, 2 * STRIPE_W - 4).moveTo(LINE_W));
        }
        if (lines.isEmpty()) lines.add(line().moveTo(LINE_W));
        return lines;
    }

    // ---- 3. クエスト納品 ----

    private static void showHandIn(Player player, Session session) {
        QuestAutoHandIn auto = autoHandIn(player);
        List<QuestAutoHandIn.Target> plan = auto != null ? auto.plan() : List.of();
        List<DialogBody> body = new ArrayList<>();
        body.add(header(session.report, "戻る: 戦利品", "loot"));
        for (QuestAutoHandIn.Target t : plan) {
            String item = t.sample() != null ? plain(t.sample()) : "";
            body.add(itemRow(t.sample(), join(handInLines(new HandInView(t.quest().getDisplayName(), traderName(t.quest().getTraderId()),
                    item, t.before(), t.after(), t.needed(), t.reportableAfter(), "handin/" + t.quest().getId() + "/" + t.objective())))));
        }
        if (plan.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("持ち帰った物で納品できる目標はありません", NOTE), BODY_W));
        } else {
            body.add(DialogBody.plainMessage(Component.text("納品する物は持ち物・バックパックの中・スタッシュから自動で選びます (FIR の物だけ。持ち込んだ物・支給品は使いません)", NOTE), BODY_W));
        }
        ActionButton next = ActionButton.create(Component.text("次へ: キルログ"), null, 150, action("page/kills"));
        ActionButton first = plan.isEmpty()
                ? ActionButton.create(Component.text("戻る: 戦利品"), null, 150, action("loot"))
                : ActionButton.create(Component.text("まとめて納品  (" + plan.size() + "件)"), Component.text("使う物を確かめてから納品する"), 150, action("bulk"));
        showDialog(player, session, body, DialogType.confirmation(first, next));
    }

    record HandInView(String quest, String trader, String item, int before, int after, int needed, boolean reportable, String action) {
    }

    static List<DialogLine> handInLines(HandInView v) {
        DialogLine l1 = line();
        for (int i = 0; i < PLATE_TILES; i++) l1.stamp(TRADER, (char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        l1.stamp(TRADER, RING, RING_W, RING_X);
        int tagW = boxWidth(v.trader());
        int right = v.reportable() ? CHIP_X - 6 - FontWidths.width("報告できる") - 6 : CHIP_X - 6;
        l1.moveTo(TEXT_X).text(FontWidths.truncate(v.quest(), right - TEXT_X - tagW - 4), NAME);
        l1.shift(4).box(TRADER, boxGlyph(v.trader(), BOX_CHIP),
                boxArtWidth(v.trader()), v.trader(), NOTE, false, null, null);
        if (v.reportable()) l1.textRight("報告できる", OK, CHIP_X - 6);
        // 押せる札 (絵) と、真ん中に「納品」(バニラの字を 5 ずらしたフォント)。札も字も押せる
        Component hover = Component.text("この目標に納品する", NOTE);
        l1.moveTo(CHIP_X).glyph(FONT, CHIP_ACTION, CHIP_ACTION_W, click(v.action()), hover);
        l1.moveTo(CHIP_X + (CHIP_ACTION_W - FontWidths.width("納品")) / 2).text(shift(CHIP_TEXT_SHIFT), "納品", AMBER, click(v.action()), hover);
        l1.moveTo(ROW_LINE_W);

        DialogLine l2 = line().moveTo(TEXT_X);
        String counts = v.before() + "/" + v.needed() + " → " + v.after() + "/" + v.needed();
        l2.text(FontWidths.truncate(v.item() + " (FIR)", CHIP_X - 12 - TEXT_X - FontWidths.width(counts)), NOTE).shift(6);
        l2.text(v.before() + "/" + v.needed(), DIM).text(" → ", AMBER).text(v.after() + "/" + v.needed(), v.after() >= v.needed() ? OK : AMBER);
        l2.moveTo(ROW_LINE_W);
        return List.of(l1, l2);
    }

    private static void showConfirm(Player player, Session session) {
        QuestAutoHandIn auto = autoHandIn(player);
        List<QuestAutoHandIn.Target> plan = auto != null ? auto.plan() : List.of();
        if (plan.isEmpty()) {
            session.page = Page.HANDIN;
            showHandIn(player, session);
            return;
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(header(session.report, null, null));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("次の物を使って納品します。", INK));
        for (QuestAutoHandIn.Target t : plan) {
            for (QuestAutoHandIn.Allocation a : t.allocations()) {
                String where = a.place();
                lines.add(Component.text("・" + (t.sample() != null ? plain(t.sample()) : a.itemId()) + " ×" + a.amount(), NAME)
                        .append(Component.text("  (" + where + ")  → ", NOTE))
                        .append(Component.text(t.quest().getDisplayName(), AMBER)));
            }
        }
        body.add(DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(), lines), BODY_W));
        ActionButton ok = ActionButton.create(Component.text("納品する"), null, 150, action("bulk_ok"));
        ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("page/handin"));
        showDialog(player, session, body, DialogType.confirmation(ok, back));
    }

    private static QuestAutoHandIn autoHandIn(Player player) {
        QuestModule quests = ImpossbleEscapeMC.getInstance().getServiceContainer().get(QuestModule.class);
        PlayerDataModule dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        return quests != null && data != null ? new QuestAutoHandIn(player, data, quests) : null;
    }

    // ---- 4. キルログ ----

    private static void showKills(Player player, Session session) {
        RaidReport r = session.report;
        List<RaidReport.Kill> kills = r.kills();
        List<DialogBody> body = new ArrayList<>();
        body.add(header(r, "戻る: クエスト納品", "page/handin"));

        DialogLine tabs = line();
        tabs.moveTo(8).box(TRADER, boxGlyph("すべて " + kills.size(), session.killFilter == null ? BOX_TAB_ON : BOX_TAB),
                boxArtWidth("すべて " + kills.size()), "すべて " + kills.size(), session.killFilter == null ? DARK : AMBER,
                session.killFilter == null, click("kills/all"), null).shift(4);
        for (RaidReport.Kind kind : RaidReport.Kind.values()) {
            long count = kills.stream().filter(k -> k.kind() == kind).count();
            if (count == 0 && session.killFilter != kind) continue;
            String label = kind.name() + " " + count;
            boolean on = session.killFilter == kind;
            tabs.box(TRADER, boxGlyph(label, on ? BOX_TAB_ON : BOX_TAB), boxArtWidth(label), label, on ? DARK : AMBER, on,
                    click("kills/" + kind.name().toLowerCase(Locale.ROOT)), null).shift(4);
        }
        body.add(DialogBody.plainMessage(tabs.moveTo(LINE_W).build(), BODY_W));

        for (RaidReport.Kill k : kills) {
            if (session.killFilter != null && k.kind() != session.killFilter) continue;
            body.add(itemRow(k.weapon() != null ? k.weapon() : UiIcon.KILL.item(), join(killLines(k, false))));
        }
        if (r.death() != null && (session.killFilter == null || session.killFilter == r.death().kind())) {
            RaidReport.Kill d = r.death();
            body.add(itemRow(d.weapon() != null ? d.weapon() : UiIcon.FAILED.item(), join(killLines(d, true))));
        }
        if (kills.isEmpty() && r.death() == null) {
            body.add(DialogBody.plainMessage(Component.text("倒した相手はいません", NOTE), BODY_W));
        }
        ActionButton back = ActionButton.create(Component.text("戻る: クエスト納品"), null, 150, action("page/handin"));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        showDialog(player, session, body, DialogType.confirmation(back, close));
    }

    /** キルログの行: 時刻・相手 (種類の札)・距離 / 武器・弾・部位 (頭は赤)。died なら自分を倒した相手 (赤い字) */
    static List<DialogLine> killLines(RaidReport.Kill k, boolean died) {
        DialogLine l1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            l1.stamp(TRADER, (char) (PLATE_BASE + (died ? PLATE_TILES : 0) + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        l1.stamp(TRADER, RING, RING_W, RING_X);
        l1.moveTo(TEXT_X).text(clock(k.atMillis()), DIM);
        String name = died ? "倒された ← " + k.name() : k.name();
        String dist = k.distance() >= 0 ? k.distance() + "m" : "";
        int kindW = k.kind() == RaidReport.Kind.SCAV ? 0 : CHIP_KIND_W + 5;
        l1.moveTo(40).text(FontWidths.truncate(name, ROW_RIGHT - 40 - FontWidths.width(dist) - 8 - kindW), died ? NG : NAME);
        if (k.kind() != RaidReport.Kind.SCAV) {
            l1.shift(4).box(FONT, k.kind() == RaidReport.Kind.PMC ? CHIP_KIND_RED : CHIP_KIND_AMBER, CHIP_KIND_W, k.kind().name(),
                    k.kind() == RaidReport.Kind.PMC ? TextColor.color(0xFAF0E6) : DARK, true, null, null);
        }
        if (!dist.isEmpty()) l1.textRight(dist, AMBER, ROW_RIGHT);
        l1.moveTo(ROW_LINE_W);

        DialogLine l2 = line().moveTo(TEXT_X);
        String weapon = k.weaponName() != null ? k.weaponName() : "不明";
        int partX = ROW_RIGHT - CHIP_PART_W;
        l2.text(FontWidths.truncate(weapon, partX - 8 - TEXT_X), INK);
        if (k.ammoName() != null && l2.x + 5 < partX - 8) {
            l2.shift(5).text(FontWidths.truncate(k.ammoName(), partX - 8 - l2.x), NOTE);
        }
        if (k.part() != null) {
            l2.moveTo(partX).box(FONT, k.headshot() ? CHIP_PART_HEAD : CHIP_PART, CHIP_PART_W, k.part(), k.headshot() ? DARK : INK,
                    k.headshot(), null, null);
        }
        l2.moveTo(ROW_LINE_W);
        return List.of(l1, l2);
    }

    // ---- 共通 ----

    /** 上の札: 1 行目に結果 (右端に前のページへ戻る字)、2 行目に脱出地点・マップ・時間 */
    private static DialogBody header(RaidReport r, String backLabel, String backAction) {
        return DialogBody.plainMessage(join(headerLines(new HeaderView(r.outcome(), r.survived(),
                (r.extractName() != null ? r.extractName() + " · " : "") + r.mapName() + " · " + clock(r.raidMillis()),
                backLabel, backAction))), BODY_W);
    }

    record HeaderView(String outcome, boolean survived, String where, String backLabel, String backAction) {
    }

    static List<DialogLine> headerLines(HeaderView v) {
        DialogLine l1 = line();
        for (int i = 0; i < HEADER_TILES; i++) l1.stamp(FONT, (char) (HEADER_BASE + i), HEADER_TILE_W, i * HEADER_TILE_W);
        l1.moveTo(8).text(v.outcome(), v.survived() ? OK : NG);
        if (v.backLabel() != null) {
            l1.moveTo(HEADER_TILES * HEADER_TILE_W - 8 - FontWidths.width(v.backLabel()))
                    .text(v.backLabel(), AMBER, click(v.backAction()), Component.text("前のページへ", NOTE));
        }
        l1.moveTo(LINE_W);
        DialogLine l2 = line().moveTo(8).text(FontWidths.truncate(v.where(), HEADER_TILES * HEADER_TILE_W - 16), NOTE).moveTo(LINE_W);
        return List.of(l1, l2, line().moveTo(LINE_W));
    }

    private static DialogBody itemRow(ItemStack icon, Component description) {
        return DialogBody.item(icon != null ? icon : UiIcon.QUEST.item()).description(DialogBody.plainMessage(description, ROW_BODY_W))
                .showTooltip(true).showDecorations(true).width(ITEM_BOX).height(ITEM_BOX).build();
    }

    private static void showDialog(Player player, Session session, List<DialogBody> body, DialogType type) {
        Component title = Component.text(session.report.mapName() + " ― レイド結果");
        if (session.notice != null && Bukkit.getCurrentTick() <= session.noticeUntil) {
            title = title.append(Component.text("  " + session.notice, NamedTextColor.YELLOW));
        }
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    static String fmt(long v) {
        return String.format("%,d", v);
    }

    static String clock(long millis) {
        long s = Math.max(0, millis / 1000);
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    private static String plain(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
    }

    private static String traderName(String id) {
        TraderModule traders = ImpossbleEscapeMC.getInstance().getServiceContainer().get(TraderModule.class);
        TraderDefinition trader = traders != null ? traders.getTrader(id) : null;
        if (trader == null) return id;
        return PlainTextComponentSerializer.plainText().serialize(LegacyComponentSerializer.legacySection().deserialize(trader.displayName))
                .replace(" (Buy Only)", "");
    }

    private static ClickEvent click(String action) {
        return action == null ? null : ClickEvent.custom(Key.key(NS, (PREFIX + action).toLowerCase(Locale.ROOT)));
    }

    private static DialogAction action(String action) {
        return DialogAction.customClick(Key.key(NS, (PREFIX + action).toLowerCase(Locale.ROOT)), null);
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
        if (session == null || action.equals("close")) {
            stopAnim(session);
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        String[] parts = action.split("/");
        switch (parts[0]) {
            case "page" -> {
                stopAnim(session);
                session.animTick = ANIM_TICKS;
                session.page = switch (parts.length > 1 ? parts[1] : "") {
                    case "exp" -> Page.EXP;
                    case "kills" -> Page.KILLS;
                    default -> Page.HANDIN;
                };
            }
            case "loot" -> {
                stopAnim(session);
                session.animTick = ANIM_TICKS;
                player.closeDialog();
                RaidResultLootGUI.open(player, session.report);
                return;
            }
            case "kills" -> session.killFilter = parts.length > 1 && !parts[1].equals("all")
                    ? RaidReport.Kind.valueOf(parts[1].toUpperCase(Locale.ROOT)) : null;
            case "bulk" -> session.page = Page.CONFIRM;
            case "bulk_ok" -> {
                QuestAutoHandIn auto = autoHandIn(player);
                if (auto == null) return;
                List<QuestAutoHandIn.Allocation> all = new ArrayList<>();
                auto.plan().forEach(t -> all.addAll(t.allocations()));
                int done = auto.apply(all);
                notice(session, done > 0 ? "納品しました (" + done + " 個)" : "納品できる物がありませんでした");
                if (done > 0) player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
                session.page = Page.HANDIN;
            }
            case "handin" -> {
                if (parts.length < 3) return;
                QuestAutoHandIn auto = autoHandIn(player);
                if (auto == null) return;
                QuestAutoHandIn.Target target = auto.plan().stream()
                        .filter(t -> t.quest().getId().equalsIgnoreCase(parts[1]) && String.valueOf(t.objective()).equals(parts[2]))
                        .findFirst().orElse(null);
                int done = target != null ? auto.apply(target.allocations()) : 0;
                notice(session, done > 0 ? "納品しました: " + target.quest().getDisplayName() : "納品できる物がありませんでした");
                if (done > 0) player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
            }
            default -> {
                return;
            }
        }
        player.playSound(player, "iem:pda.click", org.bukkit.SoundCategory.MASTER, 1.0f, 1.0f);
        show(player, session);
    }

    private static void notice(Session session, String text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = SESSIONS.remove(event.getPlayer().getUniqueId());
        stopAnim(session);
    }
}

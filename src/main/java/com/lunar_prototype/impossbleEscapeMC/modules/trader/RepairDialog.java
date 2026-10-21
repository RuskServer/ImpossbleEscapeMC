package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.HandInPlaces;
import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
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
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_STEPS;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_OFF;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ITEM_BOX;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.QUEST_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_BODY_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_LINE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TEXT_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TRADER_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.line;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.INK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NAME;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NG;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NOTE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.OK;

/**
 * トレーダーの修理の画面 (ダイアログ)。修理の要る物を並べ、どれを直すかを選んで修理する (クエストの納品の画面と同じ作り)。
 * <ul>
 *   <li>並べる物: 着ている防具・オフハンド・持ち物・持っているバックパックの中 ({@link HandInPlaces}。スタッシュは見ない)のうち、
 *       耐久が減っている・ジャムしている物 ({@link Repairs#quote})。この店で直せない物も暗くして理由を出す</li>
 *   <li>行のチェックで直す・直さないが切り替わる。開いた時は、この店で直せる物をすべて選んである</li>
 *   <li>修理する時に物を読み直し、まだそこにある同じ物だけ、修理代をまとめて払って直す (所持金が足りなければ何もしない)</li>
 * </ul>
 * レイドから帰った時の結果の画面からも開く ({@link #openAfterRaid})。その時は物ごとに、それを直せるトレーダー
 * (武器は Kovacs、アーマーは Bastion) の修理として出す。修理代はトレーダーで直すのと同じ
 */
public final class RepairDialog implements Listener {

    // ---- 寸法 (GUI の画素。共通の部品の字・寸法・色は gui/TerminalFont・TerminalPalette) ----
    private static final int BODY_W = 340;
    static final int LINE_W = BODY_W - 8;
    private static final int NAME_X = TEXT_X + CHECK_W + 5;
    private static final int ROW_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 6;

    private static final String NS = "iem";
    private static final String PREFIX = "repair/";
    private static final int NOTICE_TICKS = 80;

    // ---- 画面に出す値 (サーバーなしで組み立てられる。RepairDialogLayoutTest が幅を確かめる) ----

    record HeaderView(long money, int selected, long total) {
    }

    /** 修理の要る物 1 つの行。allowed でなければ reason を出し、選べない */
    record RowView(String name, String place, double ratio, boolean jammed, long cost, boolean selected, boolean allowed,
                   String reason, String action) {
    }

    static List<DialogLine> headerLines(HeaderView v) {
        DialogLine l = line();
        l.text("所持金 ", NOTE).text(price(v.money()), AMBER);
        String count = v.selected() + " 個  修理代 ";
        String total = price(v.total());
        int right = LINE_W - FontWidths.width(count) - FontWidths.width(total);
        l.moveTo(right).text(count, NOTE).text(total, v.total() > v.money() ? NG : v.selected() > 0 ? OK : DIM);
        l.moveTo(LINE_W);
        return List.of(l);
    }

    static List<DialogLine> rowLines(RowView v) {
        ClickEvent click = v.allowed() ? click(v.action()) : null;
        Component hover = v.allowed() ? Component.text(v.selected() ? "直さない" : "直す", NOTE) : Component.text(v.reason(), NG);
        DialogLine l1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            l1.stamp(TRADER_FONT, (char) (PLATE_BASE + (v.allowed() ? 0 : PLATE_TILES) + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        l1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        if (v.allowed()) l1.moveTo(TEXT_X).glyph(QUEST_FONT, v.selected() ? CHECK_ON : CHECK_OFF, CHECK_W, click, hover);
        else l1.stamp(TRADER_FONT, LOCK, LOCK_W, TEXT_X);
        String cost = price(v.cost());
        int costX = ROW_RIGHT - FontWidths.width(cost);
        l1.moveTo(NAME_X).text(FontWidths.truncate(v.name(), costX - 6 - NAME_X), !v.allowed() ? DIM : v.selected() ? NAME : INK, click, hover);
        l1.moveTo(costX).text(cost, v.allowed() ? AMBER : DIM);
        l1.moveTo(ROW_LINE_W);

        DialogLine l2 = line().moveTo(NAME_X);
        if (!v.allowed()) {
            int reasonX = ROW_RIGHT - FontWidths.width(v.reason());
            l2.text(FontWidths.truncate(v.place(), reasonX - 6 - NAME_X), NOTE);
            l2.moveTo(reasonX).text(v.reason(), NG);
        } else {
            // 右から: 耐久の割合、耐久の棒、ジャム
            String percent = Math.round(v.ratio() * 100) + "%";
            int percentX = ROW_RIGHT - FontWidths.width(percent);
            int barX = percentX - 4 - (BAR_W + 1);
            String jam = v.jammed() ? "ジャム" : "";
            int jamX = barX - (jam.isEmpty() ? 0 : FontWidths.width(jam) + 6);
            l2.text(FontWidths.truncate(v.place(), jamX - 6 - NAME_X), NOTE);
            if (!jam.isEmpty()) l2.moveTo(jamX).text(jam, NG);
            int step = (int) Math.round(v.ratio() * BAR_STEPS);
            l2.moveTo(barX).glyph(QUEST_FONT, (char) (BAR_BASE + step), BAR_W, null, null);
            l2.moveTo(percentX).text(percent, v.ratio() < 0.3 ? NG : NOTE);
        }
        l2.moveTo(ROW_LINE_W);
        return List.of(l1, l2);
    }

    // ---- 修理の要る物 ----

    /**
     * 修理の要る物 1 つ。key は場所と物の ID (押された時・修理する時に、同じ物か確かめる)。
     * traderName はレイドの後の修理の時の、それを直すトレーダー (トレーダーの画面では null)
     */
    private record Row(String key, HandInPlaces.Source source, String place, ItemStack item, Repairs.Quote quote, String traderName) {
    }

    /** trader が null なら、物ごとにそれを直せるトレーダーで見積もる (どこでも直せない物は出さない) */
    private static List<Row> collect(TraderModule module, TraderDefinition trader, Player player, PlayerData data) {
        List<Row> rows = new ArrayList<>();
        for (HandInPlaces.Entry entry : HandInPlaces.scan(player, data, true, false)) {
            Repairs.Quote quote = null;
            String traderName = null;
            if (trader != null) {
                quote = Repairs.quote(module, trader, player.getUniqueId(), entry.item());
            } else {
                for (TraderDefinition t : module.getAllTraders()) {
                    if (!t.canRepairWeapon && !t.canRepairArmor) continue;
                    Repairs.Quote q = Repairs.quote(module, t, player.getUniqueId(), entry.item());
                    if (q != null && q.allowed()) {
                        quote = q;
                        traderName = traderName(t);
                        break;
                    }
                }
            }
            if (quote == null) continue;
            HandInPlaces.Source s = entry.source();
            String key = s.kind() + ":" + s.page() + ":" + s.slot() + ":" + s.inner() + "|" + idOf(entry.item());
            rows.add(new Row(key, s, entry.place(), entry.item(), quote, traderName));
        }
        return rows;
    }

    /** トレーダーの名前 (色の記号と「(Buy Only)」のような添え書きを外す) */
    private static String traderName(TraderDefinition trader) {
        String name = trader.displayName != null ? trader.displayName.replaceAll("§.", "") : trader.id;
        int paren = name.indexOf(" (");
        return paren > 0 ? name.substring(0, paren) : name;
    }

    /** 同じ物か確かめるための ID (データパック銃の ID か、アイテムの ID) */
    private static String idOf(ItemStack item) {
        String gunId = DatapackGunCatalog.gunIdOf(item);
        if (gunId != null) return gunId;
        return item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
    }

    // ---- 開く ----

    private static final class Session {
        /** 修理するトレーダー (null はレイドの後の修理: 物ごとに直せるトレーダー) */
        final TraderDefinition trader;
        /** レイドの後の修理の時の、戻る・次へ (トレーダーの画面では null) */
        Runnable back, next;
        /** 選んでいる行の key (null は開いた時の選び方: この店で直せる物すべて) */
        Set<String> selected;
        /** 今出している行 (押された番号から引く) */
        List<Row> rows = List.of();
        String notice;
        int noticeUntil;

        Session(TraderDefinition trader) {
            this.trader = trader;
        }

        boolean isSelected(Row row) {
            return row.quote().allowed() && (selected == null || selected.contains(row.key()));
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static RepairDialog instance;

    private RepairDialog() {
    }

    private static void ensureRegistered() {
        if (instance == null) {
            instance = new RepairDialog();
            Bukkit.getPluginManager().registerEvents(instance, ImpossbleEscapeMC.getInstance());
        }
    }

    /**
     * レイドから帰った時の修理の画面を開く (結果の画面の、戦利品の次)。back・next は戻る・次へのボタンで開く画面
     */
    public static void openAfterRaid(Player player, Runnable back, Runnable next) {
        ensureRegistered();
        player.closeInventory();
        Session session = new Session(null);
        session.back = back;
        session.next = next;
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session);
    }

    /** レイドの後に直せる物があるか (結果の画面で、修理のページを出すか決める) */
    public static boolean hasRepairsAfterRaid(Player player) {
        TraderModule module = traderModule();
        PlayerData data = module != null ? module.getDataModule().getPlayerData(player.getUniqueId()) : null;
        return data != null && !collect(module, null, player, data).isEmpty();
    }

    /** トレーダーの修理の画面を開く */
    public static void open(Player player, TraderDefinition trader) {
        ensureRegistered();
        // チェストの画面 (バッグ) を閉じて保存させてから読む
        player.closeInventory();
        Session session = new Session(trader);
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session);
    }

    // ---- 画面 ----

    private static void show(Player player, Session session) {
        TraderModule module = traderModule();
        PlayerData data = module != null ? module.getDataModule().getPlayerData(player.getUniqueId()) : null;
        if (data == null) return;

        List<Row> rows = collect(module, session.trader, player, data);
        session.rows = rows;
        long money = (long) module.getEconomyModule().getBalance(player.getUniqueId());
        int count = 0;
        long total = 0;
        for (Row row : rows) {
            if (!session.isSelected(row)) continue;
            count++;
            total += row.quote().cost();
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "直す物を選んでください (着ている装備・持ち物・バックパックの中から。スタッシュの物は持ち出してから)", NOTE), BODY_W));
        body.add(DialogBody.plainMessage(join(headerLines(new HeaderView(money, count, total))), BODY_W));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            String place = row.traderName() != null ? row.place() + " ・ " + row.traderName() : row.place();
            RowView view = new RowView(HandInPlaces.plain(row.item()), place, row.quote().ratio(), row.quote().jammed(),
                    row.quote().cost(), session.isSelected(row), row.quote().allowed(), row.quote().reason(), "toggle/" + i);
            body.add(DialogBody.item(row.item()).description(DialogBody.plainMessage(join(rowLines(view)), ROW_BODY_W))
                    .showTooltip(true).showDecorations(true).width(ITEM_BOX).height(ITEM_BOX).build());
        }
        if (rows.isEmpty()) body.add(DialogBody.plainMessage(Component.text("修理の要る物はありません", NOTE), BODY_W));

        Component title = Component.text(session.trader != null ? "修理 ― " + session.trader.displayName : "修理 ― 帰還後の整備");
        if (session.notice != null && Bukkit.getCurrentTick() <= session.noticeUntil) {
            title = title.append(Component.text("  " + session.notice, AMBER));
        }
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        ActionButton go = ActionButton.create(Component.text(count > 0 ? "修理する (" + count + " 個 " + price(total) + ")" : "修理する"),
                Component.text(count > 0 ? "チェックした物を修理する" : "直す物が選ばれていません", NOTE), 150, action("go"));
        boolean allSelected = rows.stream().filter(r -> r.quote().allowed()).allMatch(session::isSelected);
        ActionButton all = ActionButton.create(Component.text(allSelected ? "選択を外す" : "すべて選ぶ"), null, 150, action("all"));
        DialogType type;
        if (session.trader != null) {
            ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("back"));
            type = DialogType.multiAction(List.of(go, all, back)).columns(3).exitAction(back).build();
        } else {
            ActionButton back = ActionButton.create(Component.text("戻る: 戦利品"), null, 150, action("back"));
            ActionButton next = ActionButton.create(Component.text("次へ: クエスト納品"), null, 150, action("next"));
            type = DialogType.multiAction(List.of(go, all, back, next)).columns(2).exitAction(next).build();
        }
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    // ---- 修理する ----

    /**
     * 選んだ物を直す。物を読み直し、まだそこにある同じ物だけを直し、修理代をまとめて払う。
     * 直した数 (所持金が足りない時は -1)
     */
    private static int repair(Player player, Session session) {
        TraderModule module = traderModule();
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        List<Row> chosen = collect(module, session.trader, player, data).stream().filter(session::isSelected).toList();
        if (chosen.isEmpty()) return 0;
        long total = chosen.stream().mapToLong(r -> r.quote().cost()).sum();
        if (total > 0 && !module.getEconomyModule().withdraw(player.getUniqueId(), total)) return -1;

        HandInPlaces.Store store = new HandInPlaces.Store(player, data);
        for (Row row : chosen) store.set(row.source(), Repairs.repair(store.get(row.source())));
        store.save();
        player.sendMessage(Component.text(chosen.size() + " 個を修理し、" + price(total) + " を支払いました。", OK));
        return chosen.size();
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

    private void handle(Player player, String action) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || !player.isOnline()) return;
        String[] parts = action.split("/");
        switch (parts[0]) {
            case "toggle" -> {
                int index;
                try {
                    index = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
                } catch (NumberFormatException e) {
                    return;
                }
                if (index < 0 || index >= session.rows.size() || !session.rows.get(index).quote().allowed()) return;
                if (session.selected == null) {
                    // 開いた時の選び方 (直せる物すべて) から 1 つ外す
                    session.selected = new LinkedHashSet<>();
                    for (Row row : session.rows) if (row.quote().allowed()) session.selected.add(row.key());
                }
                String key = session.rows.get(index).key();
                if (!session.selected.remove(key)) session.selected.add(key);
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
            }
            case "all" -> {
                boolean allSelected = session.rows.stream().filter(r -> r.quote().allowed()).allMatch(session::isSelected);
                session.selected = allSelected ? new LinkedHashSet<>() : null;
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.1f);
            }
            case "go" -> {
                int repaired = repair(player, session);
                if (repaired < 0) {
                    notice(session, "所持金が足りません");
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                } else if (repaired == 0) {
                    notice(session, "直す物が選ばれていません");
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                } else {
                    notice(session, "修理しました");
                    player.playSound(player, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
                    session.selected = null;
                }
            }
            case "back" -> {
                SESSIONS.remove(player.getUniqueId());
                if (session.trader == null) {
                    if (session.back != null) session.back.run();
                    return;
                }
                TraderModule module = traderModule();
                if (module != null) TraderDialog.open(module, session.trader, player);
                return;
            }
            case "next" -> {
                SESSIONS.remove(player.getUniqueId());
                if (session.next != null) session.next.run();
                return;
            }
            default -> {
                return;
            }
        }
        show(player, session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    private static void notice(Session session, String text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    // ---- 小物 ----

    static String price(long value) {
        return String.format("%,d₽", value);
    }

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    private static ClickEvent click(String action) {
        return action == null ? null : ClickEvent.custom(Key.key(NS, PREFIX + action));
    }

    private static DialogAction action(String action) {
        return DialogAction.customClick(Key.key(NS, PREFIX + action), null);
    }

    private static TraderModule traderModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(TraderModule.class);
    }
}

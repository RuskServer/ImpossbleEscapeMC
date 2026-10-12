package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.AttachmentItems;
import com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemWeights;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.TraderQuestGUI;
import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 買うトレーダーの画面 (ダイアログ)。出撃画面 (RaidSelectionGUI) と同じく、本文の文字をリソースパックのフォント (iem:trader) の
 * 絵にして端末を描き、品物はダイアログのアイテム欄 (本物の見た目。ホバーで詳細) で並べる。
 * <ul>
 *   <li>上の端末: 名前・所持金/容量/Lv の液晶・並び/未解放/修理の札・分類のタブ</li>
 *   <li>品物の行: 品物の枠 + 札 (名前・価格・購入の札 / 2 行目に口径や貫通・本日の残り)。買える物が先、未解放は暗く後ろ</li>
 *   <li>購入の確認: 早押しの個数ボタン (合計金額つき) と、個数のスライダー</li>
 *   <li>下の帯: クエスト一覧 (報告できる数) と閉じる。Esc は閉じる (確認の画面では戻る)</li>
 * </ul>
 * 本文の文字は行ごとに中央揃えになるので、すべての行をちょうど決まった幅にする ({@link Line})。
 * バニラの字の幅は {@link FontWidths}、絵の字は「幅 + 1」進む。絵の大きさ・字の番号・縦の位置は IEM_shader/tools/trader_font.py と同じにする。
 * 売る (broker) トレーダーと修理は、物を入れる欄が要るのでチェストの画面 ({@link TraderGUI}) のまま
 */
public final class TraderDialog implements Listener {

    // ---- フォント (trader_font.py と同じにする) ----
    private static final Key FONT = Key.key("iem", "trader");
    private static final char SPACE_POS = '';
    private static final char SPACE_NEG = '';
    private static final char HEADER_BASE = '';
    private static final int HEADER_TILES = 3;
    private static final int HEADER_TILE_W = 108;
    private static final char PLATE_BASE = '';
    private static final int PLATE_TILES = 3;
    private static final int PLATE_TILE_W = 100;
    private static final char RING = '';
    private static final int RING_W = 22;
    private static final char CHIP = '';
    private static final char CHIP_SOLD = '';
    private static final int CHIP_W = 36;
    private static final char LOCK = '';
    private static final int LOCK_W = 7;
    private static final char BOX_BASE = '';
    private static final int[] BOX_WIDTHS = {24, 32, 40, 48, 56, 64, 72, 80, 96, 112};
    private static final int BOX_TAB = 0, BOX_TAB_ON = 1, BOX_CHIP = 2;

    // ---- 寸法 (GUI の画素) ----
    // 本文の文字は、行の途中で一度でも折り返し幅 (要素の幅 - 余白 8) を超えるとそこで改行される。
    // 絵の右端・字の右端は、どの時点でも行の幅 (= 折り返し幅) を超えないように置く ({@link Line#max})
    /** 上の端末の絵の幅 (= 品物の行の、枠の左端から札の右端まで) */
    private static final int HEADER_ART_W = HEADER_TILES * HEADER_TILE_W;
    /** 上の端末の要素の幅と、行の幅。行は要素の中央に寄るので、絵の左端は要素の中央 - 166 (品物の枠の左端と同じ) */
    private static final int HEADER_BODY_W = 340;
    static final int HEADER_W = HEADER_BODY_W - 8;
    /** 上の端末の行の数 (絵の高さ 55 を覆う: 9 x 6 + 8 = 62)。1 行目: 名前と液晶、3 行目: 札、5 行目: タブ */
    private static final int HEADER_LINES = 6;
    /** 液晶の左端 (所持金・容量・Lv) */
    private static final int LCD_X = 145;
    /** 品物の説明の要素の幅と、行の幅 (要素の幅 - 余白 8)。札 (3 x 100) は行の頭の 1 つ左から行の終わりまで */
    private static final int ROW_BODY_W = 308;
    static final int ROW_LINE_W = ROW_BODY_W - 8;
    /** アイテム欄の大きさ (品物は左上に 16x16 で描かれる) */
    private static final int ITEM_BOX = 16;
    /** 札は行の頭の 1 つ左から、品物の枠は行の頭の 25 左から */
    private static final int PLATE_X = -1;
    private static final int RING_X = -25;
    /** 札の中の位置 (行の頭から) */
    private static final int TEXT_X = 5;
    private static final int CHIP_X = PLATE_X + PLATE_TILES * PLATE_TILE_W - 4 - CHIP_W;
    private static final int PRICE_RIGHT = CHIP_X - 6;
    private static final int LOCKED_PRICE_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 5;

    // ---- 色 ----
    private static final TextColor INK = TextColor.color(0xE0D6AA);
    private static final TextColor NAME = TextColor.color(0xEEE8D2);
    private static final TextColor NOTE = TextColor.color(0xAAA48C);
    private static final TextColor DIM = TextColor.color(0x827E6E);
    private static final TextColor AMBER = TextColor.color(0xDEAC4E);
    private static final TextColor AMBER_DIM = TextColor.color(0x96763C);
    private static final TextColor DARK = TextColor.color(0x18140A);
    private static final TextColor OK = TextColor.color(0x78CD60);
    private static final TextColor NG = TextColor.color(0xD64A34);

    private static final String NS = "iem";
    private static final String PREFIX = "trader/";
    private static final int NOTICE_TICKS = 80;
    /** 確認の画面のスライダーの上限 (スタック何個分まで) */
    private static final int SLIDER_STACKS = 10;

    /** 品物の分類 (タブの並び順) */
    enum Category {
        GUN("銃"), AMMO("弾薬"), ARMOR("防具"), GEAR("装備"), MED("医療"), ATTACHMENT("アタッチメント"), OTHER("その他");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** 並び (押すたびに次へ) */
    enum Sort {
        TYPE("種類"), CALIBER("口径"), PRICE("価格"), PENETRATION("貫通");

        final String label;

        Sort(String label) {
            this.label = label;
        }

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * 品物 1 つ (index は trader.items の番号。押した時はこれで引く)。
     * levelMet・questMet は解放の条件のそれぞれを満たしたか (未解放の行で色を分ける)
     */
    private record Entry(int index, TraderItem item, ItemStack icon, String name, Category category,
                         AmmoDefinition ammo, int size, boolean unlocked, boolean levelMet, boolean questMet, int remaining) {
    }

    private static final class Session {
        final String traderId;
        /** 選んでいるタブ (null は「すべて」) */
        Category tab;
        /** 並び (null は分類ごとの決まり: 弾薬は口径、ほかは種類) */
        Sort sort;
        /** 確認の画面を開いている品物 (-1 は一覧) */
        int confirming = -1;
        String notice;
        int noticeUntil;

        Session(String traderId) {
            this.traderId = traderId;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    /** 未解放の品物を隠しているプレイヤー (サーバーを再起動するまで) */
    private static final java.util.Set<UUID> HIDE_LOCKED = ConcurrentHashMap.newKeySet();
    /** 品物の見本 (データパックの銃は作るのに手間がかかるので覚えておく) */
    private static final Map<String, ItemStack> ICONS = new ConcurrentHashMap<>();

    private static TraderModule module;
    private static TraderDialog instance;

    private TraderDialog() {
    }

    public static void init(TraderModule traderModule) {
        module = traderModule;
        if (instance == null) {
            instance = new TraderDialog();
            Bukkit.getPluginManager().registerEvents(instance, traderModule.getPlugin());
        }
    }

    /** トレーダーの画面を開く: 買うトレーダーはこのダイアログ、売るトレーダーはチェストの画面 */
    public static void open(TraderModule traderModule, TraderDefinition trader, Player player) {
        if (trader.type != TraderType.BUY) {
            new TraderGUI(traderModule, trader, player).open();
            return;
        }
        init(traderModule);
        player.closeInventory();
        Session session = new Session(trader.id);
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session);
    }

    // ---- 画面 ----

    private static void show(Player player, Session session) {
        TraderDefinition trader = module.getTrader(session.traderId);
        if (trader == null) {
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        List<Entry> entries = entries(player, trader, data);
        if (session.confirming >= 0) {
            Entry entry = entries.stream().filter(e -> e.index() == session.confirming).findFirst().orElse(null);
            if (entry != null && entry.unlocked()) {
                showConfirm(player, session, trader, data, entry);
                return;
            }
            session.confirming = -1;
        }
        showList(player, session, trader, data, entries);
    }

    private static void showList(Player player, Session session, TraderDefinition trader, PlayerData data, List<Entry> entries) {
        boolean hideLocked = HIDE_LOCKED.contains(player.getUniqueId());
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Entry e : entries) counts.merge(e.category(), 1, Integer::sum);
        if (session.tab != null && !counts.containsKey(session.tab)) session.tab = null;
        Sort sort = effectiveSort(session);

        List<Entry> shown = new ArrayList<>();
        for (Entry e : entries) {
            if (session.tab != null && e.category() != session.tab) continue;
            if (hideLocked && !e.unlocked()) continue;
            shown.add(e);
        }
        shown.sort(comparator(sort));

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(header(headerView(player, trader, data, counts, entries.size(), session, sort, hideLocked)), HEADER_BODY_W));
        for (Entry e : shown) {
            body.add(DialogBody.item(e.icon()).description(DialogBody.plainMessage(row(rowView(e)), ROW_BODY_W))
                    .showTooltip(true).showDecorations(false).width(ITEM_BOX).height(ITEM_BOX).build());
        }
        if (shown.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(hideLocked ? "今買える品物はありません (未解放を表示で確認できます)" : "品物はありません", NOTE)));
        }

        DialogBase base = DialogBase.builder(title(trader, session))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build();
        int reportable = reportableQuests(trader, data);
        ActionButton quests = ActionButton.create(
                Component.text(reportable > 0 ? "クエスト  (報告可 " + reportable + ")" : "クエスト"),
                Component.text("このトレーダーのクエスト一覧を開く"), 150, action("quest"));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(quests, close))));
    }

    private static void showConfirm(Player player, Session session, TraderDefinition trader, PlayerData data, Entry entry) {
        int maxStack = Math.max(1, entry.icon().getMaxStackSize());
        double balance = module.getEconomyModule().getBalance(player.getUniqueId());
        int affordable = entry.item().price <= 0 ? Integer.MAX_VALUE : (int) Math.floor(balance / entry.item().price);
        int max = Math.min(Math.min(entry.remaining(), affordable), maxStack * SLIDER_STACKS);

        List<DialogBody> body = new ArrayList<>();
        int free = Math.max(0, CostSlotManager.capacity(player, player.getInventory()) - CostSlotManager.usedCost(player, player.getInventory()));
        ConfirmView view = new ConfirmView(entry.name(), price(entry.item().price), price(balance), free, maxStack,
                stockText(entry), entry.remaining() <= 0);
        body.add(DialogBody.item(entry.icon()).description(DialogBody.plainMessage(confirmRow(view), ROW_BODY_W))
                .showTooltip(true).showDecorations(false).width(ITEM_BOX).height(ITEM_BOX).build());
        String reason = max > 0 ? null : entry.remaining() <= 0 ? "本日の購入制限に達しています" : "所持金が足りません";
        if (reason != null) body.add(DialogBody.plainMessage(Component.text(reason, NG)));

        List<ActionButton> buttons = new ArrayList<>();
        DialogBase.Builder base = DialogBase.builder(Component.text("購入 ― " + entry.name())
                        .append(noticeText(session)))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body);
        if (max > 0) {
            for (int quantity : quickQuantities(maxStack, max)) {
                String label = (maxStack == 1 && quantity == 1 ? "購入" : "×" + quantity) + "  " + price(entry.item().price * quantity);
                buttons.add(ActionButton.create(Component.text(label), null, 98, action("buy/" + entry.index() + "/" + quantity)));
            }
            if (max > 1) {
                base.inputs(List.of(DialogInput.numberRange("count", Component.text("個数"), 1, max)
                        .step(1f).initial((float) Math.min(maxStack, max)).width(200).build()));
                buttons.add(ActionButton.create(Component.text("スライダーの個数で購入"), null, 200, action("slider/" + entry.index())));
            }
        }
        ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("back"));
        DialogBase built = base.build();
        // 買えない時 (所持金が足りない・本日の残りが 0) はボタンが無い。複数ボタンの型は空にできないので「戻る」だけの型にする
        DialogType type = buttons.isEmpty() ? DialogType.notice(back)
                : DialogType.multiAction(buttons).columns(3).exitAction(back).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(built).type(type)));
    }

    private static Component title(TraderDefinition trader, Session session) {
        TextComponent.Builder title = Component.text().content(traderName(trader));
        if (session.tab != null) title.append(Component.text("  ―  " + session.tab.label, NamedTextColor.GRAY));
        return title.append(noticeText(session)).build();
    }

    private static Component noticeText(Session session) {
        if (session.notice == null || Bukkit.getCurrentTick() > session.noticeUntil) return Component.empty();
        return Component.text("  " + session.notice, NamedTextColor.YELLOW);
    }

    // ---- 上の端末 ----

    /** 上の端末のタブ */
    record Tab(String label, boolean on, String action) {
    }

    /** 上の端末に出す値 (サーバー無しでも組み立てられるように分けておく。repairHover が null なら修理の札を出さない) */
    record HeaderView(String name, long money, int used, int capacity, int level, String sortLabel, boolean hideLocked,
                      String repairHover, List<Tab> tabs) {
    }

    private static HeaderView headerView(Player player, TraderDefinition trader, PlayerData data, Map<Category, Integer> counts,
                                         int total, Session session, Sort sort, boolean hideLocked) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("すべて " + total, session.tab == null, "tab/all"));
        if (counts.size() > 1) {   // 分類が 1 つだけなら「すべて」だけ
            for (Map.Entry<Category, Integer> c : counts.entrySet()) {
                tabs.add(new Tab(c.getKey().label + " " + c.getValue(), c.getKey() == session.tab,
                        "tab/" + c.getKey().name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        String repair = !trader.canRepairArmor && !trader.canRepairWeapon ? null
                : trader.canRepairArmor && trader.canRepairWeapon ? "アーマーと武器を修理する"
                : trader.canRepairArmor ? "アーマーを修理する" : "武器を修理する";
        return new HeaderView(traderName(trader).toUpperCase(java.util.Locale.ROOT),
                (long) module.getEconomyModule().getBalance(player.getUniqueId()),
                CostSlotManager.usedCost(player, player.getInventory()), CostSlotManager.capacity(player, player.getInventory()),
                data.getLevel(), sort.label, hideLocked, repair, tabs);
    }

    static Component header(HeaderView v) {
        return join(headerLines(v));
    }

    /** 上の端末の行 (テストで、どの行も途中で幅を超えないことを確かめる) */
    static List<Line> headerLines(HeaderView v) {
        // 1 行目: 筐体の絵、名前、液晶 (所持金・容量・Lv)
        Line line1 = new Line();
        for (int i = 0; i < HEADER_TILES; i++) line1.stamp((char) (HEADER_BASE + i), HEADER_TILE_W, i * HEADER_TILE_W);
        line1.moveTo(8).text(FontWidths.truncate(v.name(), LCD_X - 16), INK);
        line1.moveTo(LCD_X + 5).text("₽ ", AMBER_DIM).text(String.format("%,d", v.money()), AMBER);
        line1.shift(8).text("容量 ", AMBER_DIM).text(v.used() + "/" + v.capacity(), v.used() < v.capacity() ? OK : NG);
        String level = "Lv " + v.level();
        if (line1.x + 8 + FontWidths.width(level) <= HEADER_ART_W - 6) {
            line1.shift(8).text("Lv ", AMBER_DIM).text(String.valueOf(v.level()), AMBER);
        }
        line1.moveTo(HEADER_W);

        // 3 行目: 並び・未解放・修理の札
        Line line2 = new Line().moveTo(8);
        line2.box("並び: " + v.sortLabel() + " ▼", BOX_CHIP, AMBER, click("sort"),
                Component.text("並びを替える (種類 → 口径 → 価格 → 貫通)", NOTE)).shift(4);
        line2.box(v.hideLocked() ? "未解放: 隠す" : "未解放: 表示", BOX_CHIP, NOTE, click("locked"),
                Component.text(v.hideLocked() ? "今買えない品物も並べる" : "今買えない品物を隠す", NOTE)).shift(4);
        if (v.repairHover() != null) line2.box("修理", BOX_CHIP, NOTE, click("repair"), Component.text(v.repairHover(), NOTE));
        line2.moveTo(HEADER_W);

        // 5 行目: 分類のタブ
        Line line4 = new Line().moveTo(8);
        for (Tab tab : v.tabs()) {
            if (line4.x + boxWidth(tab.label()) > HEADER_ART_W - 6) break;   // 入りきらないタブは出さない (折り返させない)
            line4.box(tab.label(), tab.on() ? BOX_TAB_ON : BOX_TAB, tab.on() ? DARK : AMBER, click(tab.action()), null).shift(4);
        }
        line4.moveTo(HEADER_W);

        List<Line> lines = new ArrayList<>(List.of(line1, new Line().moveTo(HEADER_W), line2, new Line().moveTo(HEADER_W), line4));
        while (lines.size() < HEADER_LINES) lines.add(new Line().moveTo(HEADER_W));
        return lines;
    }

    private static Component join(List<Line> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(Line::build).toList());
    }

    // ---- 品物の行 ----

    record Part(String text, TextColor color) {
    }

    /**
     * 品物の行に出す値。buyAction は購入の札を押した時の操作 (売り切れ・未解放なら null)、
     * parts は 2 行目の補足 (未解放なら解放の条件)、stock は右の本日の残り (未解放なら null)
     */
    record RowView(String name, String price, boolean locked, boolean sold, String buyAction, List<Part> parts,
                   String stock, boolean stockRed) {
    }

    private static RowView rowView(Entry e) {
        if (!e.unlocked()) {
            List<Part> requirements = new ArrayList<>();
            requirements.add(new Part("Lv." + module.getRequiredLevel(e.item()), e.levelMet() ? OK : NG));
            if (e.item().requiredQuestId != null) {
                QuestModule quests = module.getPlugin().getServiceContainer().get(QuestModule.class);
                QuestDefinition quest = quests == null ? null : quests.getQuest(e.item().requiredQuestId);
                String questName = quest != null ? quest.getDisplayName() : e.item().requiredQuestId;
                requirements.add(new Part("クエスト「" + questName + "」", e.questMet() ? OK : NG));
            }
            return new RowView(e.name(), price(e.item().price), true, false, null, requirements, null, false);
        }
        boolean sold = e.remaining() <= 0;
        return new RowView(e.name(), price(e.item().price), false, sold, sold ? null : "item/" + e.index(),
                subParts(e), stockText(e), sold);
    }

    static Component row(RowView v) {
        return join(rowLines(v));
    }

    static List<Line> rowLines(RowView v) {
        Line line1 = new Line();
        for (int i = 0; i < PLATE_TILES; i++) {
            line1.stamp((char) (PLATE_BASE + (v.locked() ? PLATE_TILES : 0) + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        line1.stamp(RING, RING_W, RING_X);
        int nameX = TEXT_X;
        if (v.locked()) {
            line1.stamp(LOCK, LOCK_W, TEXT_X);
            nameX = TEXT_X + LOCK_W + 3;
        }
        int priceX = (v.locked() ? LOCKED_PRICE_RIGHT : PRICE_RIGHT) - FontWidths.width(v.price());
        line1.moveTo(nameX).text(FontWidths.truncate(v.name(), priceX - 6 - nameX), v.locked() ? DIM : NAME);
        line1.moveTo(priceX).text(v.price(), v.locked() ? DIM : AMBER);
        if (!v.locked()) {
            line1.moveTo(CHIP_X).glyph(v.sold() ? CHIP_SOLD : CHIP, CHIP_W, v.buyAction() != null ? click(v.buyAction()) : null,
                    Component.text(v.sold() ? "本日は売り切れ" : "クリックで個数を選んで購入", v.sold() ? NG : NOTE));
        }
        line1.moveTo(ROW_LINE_W);

        Line line2 = new Line().moveTo(TEXT_X);
        int limit = v.stock() != null ? PRICE_RIGHT - FontWidths.width(v.stock()) - 6 : ROW_LINE_W - 4;
        for (Part part : v.parts()) {
            if (line2.x >= limit) break;
            line2.text(FontWidths.truncate(part.text(), limit - line2.x), part.color()).shift(6);
        }
        if (v.stock() != null) {
            line2.moveTo(Math.max(line2.x, PRICE_RIGHT - FontWidths.width(v.stock()))).text(v.stock(), v.stockRed() ? NG : NOTE);
        }
        line2.moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    /** 確認の画面の品物の行に出す値 */
    record ConfirmView(String name, String unitPrice, String balance, int free, int maxStack, String stock, boolean stockRed) {
    }

    /** 確認の画面の品物の行: 1 行目に名前と単価、2 行目に所持金・容量の空き・本日の残り */
    static Component confirmRow(ConfirmView v) {
        return join(confirmLines(v));
    }

    static List<Line> confirmLines(ConfirmView v) {
        Line line1 = new Line();
        for (int i = 0; i < PLATE_TILES; i++) line1.stamp((char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        line1.stamp(RING, RING_W, RING_X);
        String unit = "単価 " + v.unitPrice();
        int unitX = LOCKED_PRICE_RIGHT - FontWidths.width(unit);
        line1.moveTo(TEXT_X).text(FontWidths.truncate(v.name(), unitX - 6 - TEXT_X), NAME);
        line1.moveTo(unitX).text(unit, AMBER).moveTo(ROW_LINE_W);

        Line line2 = new Line().moveTo(TEXT_X);
        line2.text("所持金 ", NOTE).text(v.balance(), AMBER).shift(8);
        line2.text("空き ", NOTE).text(v.free() + "マス", v.free() > 0 ? OK : NG);
        if (v.maxStack() > 1) line2.text(" (1マスに" + v.maxStack() + ")", NOTE);
        line2.shift(8).text(v.stock(), v.stockRed() ? NG : NOTE).moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    /** 2 行目の補足: 弾は口径と貫通、銃は装弾数、ほかは分類。どれもサイズを付ける */
    private static List<Part> subParts(Entry e) {
        List<Part> parts = new ArrayList<>();
        if (e.ammo() != null) {
            parts.add(new Part(e.ammo().caliber, AMBER));
            parts.add(new Part("貫通 " + e.ammo().ammoClass, NOTE));
            parts.add(new Part("1マスに" + e.icon().getMaxStackSize() + "発", NOTE));
            return parts;
        }
        if (e.category() == Category.GUN) {
            DatapackGunProfile gun = DatapackGunCatalog.get(e.item().itemId);
            if (gun != null) parts.add(new Part("装弾数 " + gun.magazineSize(), NOTE));
        } else {
            parts.add(new Part(e.category().label, NOTE));
        }
        parts.add(new Part("サイズ " + e.size() + "マス", NOTE));
        return parts;
    }

    private static String stockText(Entry e) {
        if (e.item().dailyLimit <= 0) return "制限なし";
        return e.remaining() <= 0 ? "売り切れ (本日 0/" + e.item().dailyLimit + ")" : "本日 残り " + e.remaining() + "/" + e.item().dailyLimit;
    }

    private static String traderName(TraderDefinition trader) {
        return PlainTextComponentSerializer.plainText().serialize(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(trader.displayName))
                .replace(" (Buy Only)", "");
    }

    // ---- 品物の一覧 ----

    private static List<Entry> entries(Player player, TraderDefinition trader, PlayerData data) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < trader.items.size(); i++) {
            TraderItem ti = trader.items.get(i);
            ItemStack icon = icon(player, ti);
            if (icon == null) continue;
            String name = PlainTextComponentSerializer.plainText().serialize(icon.effectiveName());
            entries.add(new Entry(i, ti, icon, name, categoryOf(ti.itemId), ItemRegistry.getAmmo(ti.itemId),
                    Math.max(1, ItemWeights.costOf(icon)), module.isUnlocked(data, ti),
                    data.getLevel() >= module.getRequiredLevel(ti),
                    ti.requiredQuestId == null || data.isQuestCompleted(ti.requiredQuestId),
                    module.remainingToday(data, trader, ti)));
        }
        return entries;
    }

    private static ItemStack icon(Player player, TraderItem ti) {
        ItemStack cached = ICONS.get(ti.itemId);
        if (cached == null) {
            cached = GameItems.create(player.getWorld(), ti.itemId, ti.displayName);
            if (cached == null) return null;
            ICONS.put(ti.itemId, cached);
        }
        return cached.clone();
    }

    static Category categoryOf(String id) {
        if (ItemRegistry.getAmmo(id) != null) return Category.AMMO;
        if (DatapackGunCatalog.get(id) != null) return Category.GUN;
        ItemDefinition def = ItemRegistry.get(id);
        if (def != null && def.type != null) {
            switch (def.type.toUpperCase(java.util.Locale.ROOT)) {
                case "ARMOR", "VISOR" -> {
                    return Category.ARMOR;
                }
                case "BACKPACK", "RIG" -> {
                    return Category.GEAR;
                }
                case "MED" -> {
                    return Category.MED;
                }
                default -> {
                }
            }
        }
        if (ItemRegistry.getAttachment(id) != null || AttachmentItems.resolve(id) != null) return Category.ATTACHMENT;
        return Category.OTHER;
    }

    private static Sort effectiveSort(Session session) {
        if (session.sort != null) return session.sort;
        return session.tab == Category.AMMO ? Sort.CALIBER : Sort.TYPE;
    }

    /** 並び。どの並びでも、買える物が先・未解放が後 */
    private static Comparator<Entry> comparator(Sort sort) {
        Comparator<Entry> unlockedFirst = Comparator.comparing(e -> !e.unlocked());
        Comparator<Entry> byType = Comparator.comparing((Entry e) -> e.category().ordinal()).thenComparingInt(Entry::index);
        Comparator<Entry> inner = switch (sort) {
            case TYPE -> byType;
            case CALIBER -> Comparator.comparingInt((Entry e) -> e.ammo() != null ? caliberRank(e.ammo().caliber) : Integer.MAX_VALUE)
                    .thenComparingInt(e -> e.ammo() != null ? e.ammo().ammoClass : 0)
                    .thenComparing(byType);
            case PRICE -> Comparator.comparingDouble((Entry e) -> e.item().price).thenComparing(byType);
            case PENETRATION -> Comparator.comparingInt((Entry e) -> e.ammo() != null ? -e.ammo().ammoClass : 1)
                    .thenComparing(byType);
        };
        return unlockedFirst.thenComparing(inner);
    }

    private static final Map<String, Integer> CALIBER_RANK = new HashMap<>();

    /** 口径の並び: 弾の登録の順 (5.45 → 5.56 → …) */
    private static int caliberRank(String caliber) {
        if (CALIBER_RANK.isEmpty()) {
            for (String id : ItemRegistry.getAmmoIds()) {
                AmmoDefinition ammo = ItemRegistry.getAmmo(id);
                if (ammo != null) CALIBER_RANK.putIfAbsent(ammo.caliber, CALIBER_RANK.size());
            }
        }
        return CALIBER_RANK.getOrDefault(caliber, Integer.MAX_VALUE - 1);
    }

    private static int reportableQuests(TraderDefinition trader, PlayerData data) {
        QuestModule quests = module.getPlugin().getServiceContainer().get(QuestModule.class);
        if (quests == null) return 0;
        int count = 0;
        for (QuestDefinition quest : quests.getQuestsByTrader(trader.id)) {
            ActiveQuest active = data.getActiveQuests().get(quest.getId());
            if (active != null && quests.isAllObjectivesMet(quest, active)) count++;
        }
        return count;
    }

    /** 早押しの個数: 半スタック・1 スタック・2 スタック (買える数まで) */
    static List<Integer> quickQuantities(int maxStack, int max) {
        LinkedHashMap<Integer, Boolean> set = new LinkedHashMap<>();
        for (int q : new int[]{maxStack / 2, maxStack, maxStack * 2}) {
            if (q >= 1 && q <= max) set.put(q, true);
        }
        if (set.isEmpty()) set.put(max, true);
        return new ArrayList<>(set.keySet());
    }

    private static String price(double value) {
        return String.format("%,d₽", Math.round(value));
    }

    // ---- 文字の行 ----

    /**
     * 1 行を、行の頭からの位置 (GUI の画素) を数えながら組み立てる。最後に {@link #moveTo} で決まった幅にそろえる
     * (中央揃えでも位置がずれないように)。絵の字は「幅 + 1」進む
     */
    static final class Line {
        private final TextComponent.Builder out = Component.text();
        int x;
        /** 一番右まで進んだ位置 (行の幅を超えると、クライアントがそこで改行してしまう) */
        int max;

        Line shift(int n) {
            if (n != 0) out.append(Component.text(spaces(n)).font(FONT));
            x += n;
            max = Math.max(max, x);
            return this;
        }

        Line moveTo(int target) {
            return shift(target - x);
        }

        /** 絵の字を今の位置に描いて進む。click があれば押せる */
        Line glyph(char c, int width, ClickEvent click, Component hover) {
            Component g = Component.text(String.valueOf(c)).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
            if (click != null) g = g.clickEvent(click);
            if (hover != null) g = g.hoverEvent(HoverEvent.showText(hover));
            out.append(g);
            x += width + 1;
            max = Math.max(max, x);
            return this;
        }

        /** 絵の字を at に描き、今の位置に戻る */
        Line stamp(char c, int width, int at) {
            int back = x;
            moveTo(at);
            glyph(c, width, null, null);
            return moveTo(back);
        }

        Line text(String s, TextColor color) {
            out.append(Component.text(s, color));
            x += FontWidths.width(s);
            max = Math.max(max, x);
            return this;
        }

        /** 箱 (タブ・札) と、その中央に字。箱は字の幅 + 8 が入る一番小さい物 */
        Line box(String label, int kind, TextColor color, ClickEvent click, Component hover) {
            int textW = FontWidths.width(label);
            int sizeIndex = boxIndex(textW);
            int w = BOX_WIDTHS[sizeIndex];
            int start = x;
            glyph((char) (BOX_BASE + sizeIndex * 3 + kind), w, click, hover);
            moveTo(start + (w - textW) / 2);
            Component t = Component.text(label, color);
            if (kind == BOX_TAB_ON) t = t.shadowColor(ShadowColor.none());
            if (click != null) t = t.clickEvent(click);
            if (hover != null) t = t.hoverEvent(HoverEvent.showText(hover));
            out.append(t);
            x += textW;
            return moveTo(start + w);
        }

        Component build() {
            return out.build();
        }
    }

    /** 字の幅 + 8 が入る一番小さい箱の番号 */
    private static int boxIndex(int textWidth) {
        int i = 0;
        while (i < BOX_WIDTHS.length - 1 && BOX_WIDTHS[i] < textWidth + 8) i++;
        return i;
    }

    /** 箱の送り (箱の幅 + 1) */
    static int boxWidth(String label) {
        return BOX_WIDTHS[boxIndex(FontWidths.width(label))] + 1;
    }

    /** n だけ進む (負なら戻る) 空白の文字列。±2^i の空白を組み合わせる */
    static String spaces(int n) {
        StringBuilder out = new StringBuilder();
        int abs = Math.abs(n);
        char base = n >= 0 ? SPACE_POS : SPACE_NEG;
        for (int bit = 0; abs > 0; bit++, abs >>= 1) {
            if ((abs & 1) != 0) out.append((char) (base + bit));
        }
        return out.toString();
    }

    private static ClickEvent click(String action) {
        return ClickEvent.custom(Key.key(NS, PREFIX + action));
    }

    /** ダイアログのボタンの操作 */
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
        Float count = null;
        if (action.startsWith("slider/") && event.getDialogResponseView() != null) {
            count = event.getDialogResponseView().getFloat("count");
        }
        Float slider = count;
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> handle(player, action, slider));
    }

    private static void handle(Player player, String action, Float slider) {
        if (!player.isOnline()) return;
        Session session = SESSIONS.get(player.getUniqueId());
        if (action.equals("close")) {
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        if (session == null) return;
        TraderDefinition trader = module.getTrader(session.traderId);
        if (trader == null) return;
        String[] parts = action.split("/");
        switch (parts[0]) {
            case "quest" -> {
                SESSIONS.remove(player.getUniqueId());
                player.closeDialog();
                QuestModule quests = module.getPlugin().getServiceContainer().get(QuestModule.class);
                if (quests != null) new TraderQuestGUI(player, trader, quests).open();
                return;
            }
            case "repair" -> {
                SESSIONS.remove(player.getUniqueId());
                player.closeDialog();
                new TraderGUI(module, trader, player).openRepair();
                return;
            }
            case "sort" -> session.sort = effectiveSort(session).next();
            case "locked" -> {
                if (!HIDE_LOCKED.remove(player.getUniqueId())) HIDE_LOCKED.add(player.getUniqueId());
            }
            case "tab" -> {
                session.tab = parts.length > 1 && !parts[1].equals("all") ? Category.valueOf(parts[1].toUpperCase(java.util.Locale.ROOT)) : null;
                session.sort = null;
            }
            case "item" -> session.confirming = Integer.parseInt(parts[1]);
            case "back" -> session.confirming = -1;
            case "buy", "slider" -> {
                int index = Integer.parseInt(parts[1]);
                int quantity = parts[0].equals("buy") ? Integer.parseInt(parts[2]) : slider != null ? Math.round(slider) : 0;
                if (index < 0 || index >= trader.items.size() || quantity <= 0) return;
                TraderModule.Purchase result = module.purchase(player, trader, trader.items.get(index), quantity);
                result.messages().forEach(player::sendMessage);
                session.notice = result.notice();
                session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
                if (result.bought() > 0) {
                    session.confirming = -1;
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
                }
            }
            default -> {
                return;
            }
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        show(player, session);
    }

    /** 別の画面 (チェスト画面) が開いた: ダイアログは置き換えられたので覚えておかない */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }
}

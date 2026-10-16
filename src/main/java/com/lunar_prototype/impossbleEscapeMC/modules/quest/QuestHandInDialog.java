package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResults;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_OFF;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHECK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.QUEST_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TRADER_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.line;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
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
 * トレーダーのクエストの納品の画面 (ダイアログ)。納品の目標ごとに使える物を並べ、どれを手放すかを選んで納品する。
 * <ul>
 *   <li>使える物: 持ち物・バックパックの中・スタッシュ ({@link HandInPlaces})。同じ場所の同じ物は 1 行にまとめる</li>
 *   <li>行のチェックを押すと、その目標に使う・使わないが切り替わる。開いた時は、価値 (ブローカーの買い値) の低い物から
 *       必要な数まで選んである (手に持っている物は最後)。数は目標に足りる分だけ使い、余りは残す</li>
 *   <li>納品する時に物を読み直し、まだそこにあって同じ物なら進捗を進めて減らす (持ち物・スタッシュのページ・バッグの中へ書き戻す)</li>
 * </ul>
 * 割り当ての決まりは、クエストの順 (トレーダーのクエスト一覧と同じ)・目標の順。支給品は使わない ({@link #handInIdOf})。
 * 見た目の部品はクエストの画面と同じ (フォント iem:trader・iem:quest)
 */
public final class QuestHandInDialog implements Listener {

    // ---- 寸法 (GUI の画素。共通の部品の字・寸法・色は gui/TerminalFont・TerminalPalette) ----
    private static final int BODY_W = 340;
    static final int LINE_W = BODY_W - 8;
    private static final int NAME_X = TEXT_X + CHECK_W + 5;
    private static final int ROW_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 6;

    private static final String NS = "iem";
    private static final String PREFIX = "quest_handin/";
    private static final int NOTICE_TICKS = 80;

    // ---- 使える物 ----

    /** 同じ場所の同じ物をまとめた 1 行。sources は使う順 (手に持っている欄は最後) */
    record Group(String key, String place, ItemStack sample, String itemId, String itemType, boolean fir, int amount,
                 long unitValue, boolean held, List<HandInPlaces.Source> sources) {
    }

    /** 納品の目標 1 つと、使える物の行 (表示の順: 価値の低い順、手に持っている物は最後) */
    record Objective(String key, QuestDefinition quest, int index, HandInObjective handIn, int before, int needed, List<Integer> groups) {
    }

    // ---- 割り当て (サーバーなしで確かめられる) ----

    /**
     * 選んだ行から目標ごとに使う数を決める (目標の順に、行の表示の順で。ほかの目標に使った分は減らす)。
     * use[目標][行] = 使う数
     */
    static int[][] allocate(int[] needed, List<List<Integer>> groups, int[] amounts, boolean[][] selected) {
        int[] left = amounts.clone();
        int[][] use = new int[needed.length][amounts.length];
        for (int o = 0; o < needed.length; o++) {
            int need = needed[o];
            for (int g : groups.get(o)) {
                if (need <= 0) break;
                if (!selected[o][g] || left[g] <= 0) continue;
                int take = Math.min(left[g], need);
                use[o][g] = take;
                left[g] -= take;
                need -= take;
            }
        }
        return use;
    }

    /**
     * 開いた時の選び方: 目標の順に、行の表示の順 (価値の低い順・手に持っている物は最後) で、必要な数に届くまで選ぶ
     * (ほかの目標のために選んだ分は数えない)
     */
    static boolean[][] defaultSelection(int[] needed, List<List<Integer>> groups, int[] amounts) {
        int[] left = amounts.clone();
        boolean[][] selected = new boolean[needed.length][amounts.length];
        for (int o = 0; o < needed.length; o++) {
            int need = needed[o];
            for (int g : groups.get(o)) {
                if (need <= 0) break;
                if (left[g] <= 0) continue;
                int take = Math.min(left[g], need);
                selected[o][g] = true;
                left[g] -= take;
                need -= take;
            }
        }
        return selected;
    }

    // ---- 画面に出す値 (サーバーなしで組み立てられる。QuestHandInDialogLayoutTest が幅を確かめる) ----

    record ObjectiveView(String quest, String description, int before, int after, int needed) {
    }

    record GroupView(String name, boolean fir, String place, int amount, boolean selected, int use, String action) {
    }

    static List<DialogLine> objectiveLines(ObjectiveView v) {
        DialogLine l = line();
        String counts = v.before() + "/" + v.needed() + " → " + v.after() + "/" + v.needed();
        int right = LINE_W - FontWidths.width(counts);
        String title = v.quest() != null ? "[" + v.quest() + "] " + v.description() : v.description();
        l.text(FontWidths.truncate(title, right - 8), NAME);
        l.moveTo(right).text(v.before() + "/" + v.needed(), DIM).text(" → ", AMBER)
                .text(v.after() + "/" + v.needed(), v.after() >= v.needed() ? OK : AMBER);
        l.moveTo(LINE_W);
        return List.of(l);
    }

    static List<DialogLine> groupLines(GroupView v) {
        ClickEvent click = click(v.action());
        Component hover = Component.text(v.selected() ? "この目標に使わない" : "この目標に使う", NOTE);
        DialogLine l1 = line();
        for (int i = 0; i < PLATE_TILES; i++) l1.stamp(TRADER_FONT, (char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        l1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        l1.moveTo(TEXT_X).glyph(QUEST_FONT, v.selected() ? CHECK_ON : CHECK_OFF, CHECK_W, click, hover);
        String tag = v.fir() ? "FIR" : "";
        int nameRight = ROW_RIGHT - (tag.isEmpty() ? 0 : FontWidths.width(tag) + 6);
        l1.moveTo(NAME_X).text(FontWidths.truncate(v.name(), nameRight - NAME_X), v.selected() ? NAME : INK, click, hover);
        if (!tag.isEmpty()) l1.textRight(tag, AMBER, ROW_RIGHT);
        l1.moveTo(ROW_LINE_W);

        DialogLine l2 = line().moveTo(NAME_X);
        String use = v.selected() ? (v.use() > 0 ? "使う ×" + v.use() : "使わない (足りている)") : "";
        int placeRight = ROW_RIGHT - (use.isEmpty() ? 0 : FontWidths.width(use) + 6);
        l2.text(FontWidths.truncate(v.place() + "  ×" + v.amount(), placeRight - NAME_X), NOTE);
        if (!use.isEmpty()) l2.textRight(use, v.use() > 0 ? OK : DIM, ROW_RIGHT);
        l2.moveTo(ROW_LINE_W);
        return List.of(l1, l2);
    }

    // ---- 開く ----

    private static final class Session {
        final TraderDefinition trader;
        /** 納品先のクエストの ID (まとめての時は複数、トレーダーのクエスト一覧の順) */
        final List<String> questIds;
        final boolean bulk;
        /** 選んでいる「目標の key|行の key」(null は開いた時の選び方にする) */
        Set<String> selected;
        /** 今出している目標と行 (押された番号から引く) */
        List<Objective> objectives = List.of();
        List<Group> groups = List.of();
        String notice;
        int noticeUntil;

        Session(TraderDefinition trader, List<String> questIds, boolean bulk) {
            this.trader = trader;
            this.questIds = questIds;
            this.bulk = bulk;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static QuestHandInDialog instance;

    private QuestHandInDialog() {
    }

    private static void ensureRegistered() {
        if (instance == null) {
            instance = new QuestHandInDialog();
            Bukkit.getPluginManager().registerEvents(instance, ImpossbleEscapeMC.getInstance());
        }
    }

    /** 1 つのクエストの納品の画面を開く */
    public static void open(Player player, TraderDefinition trader, QuestDefinition quest) {
        start(player, new Session(trader, List.of(quest.getId()), false));
    }

    /** トレーダーの進行中のクエストにまとめて納品する画面を開く。納品の残っているクエストが無ければ false */
    public static boolean openForTrader(Player player, TraderDefinition trader, QuestModule questModule) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        List<QuestDefinition> quests = openHandIns(data, trader, questModule);
        if (quests.isEmpty()) return false;
        start(player, new Session(trader, quests.stream().map(QuestDefinition::getId).toList(), true));
        return true;
    }

    private static void start(Player player, Session session) {
        ensureRegistered();
        // チェストの画面 (スタッシュ・バッグ) を閉じて保存させてから読む
        player.closeInventory();
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session);
    }

    /** トレーダーの進行中のクエストのうち、まだ終わっていない納品の目標があるもの (一覧の順) */
    public static List<QuestDefinition> openHandIns(PlayerData data, TraderDefinition trader, QuestModule questModule) {
        List<QuestDefinition> result = new ArrayList<>();
        if (data == null) return result;
        for (QuestDefinition quest : questModule.getQuestsByTrader(trader.id)) {
            ActiveQuest active = data.getActiveQuests().get(quest.getId());
            if (active != null && hasOpenHandIn(quest, active)) result.add(quest);
        }
        return result;
    }

    /** 納品の目標があり、まだ終わっていないものがあるか */
    public static boolean hasOpenHandIn(QuestDefinition quest, ActiveQuest active) {
        List<QuestObjective> objectives = quest.getObjectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (objectives.get(i) instanceof HandInObjective && !objectives.get(i).isCompleted(active, i)) return true;
        }
        return false;
    }

    // ---- 画面 ----

    private static void show(Player player, Session session) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        QuestModule quests = questModule();
        if (data == null || quests == null) return;

        List<Group> groups = collect(player, data);
        List<Objective> objectives = objectives(data, quests, session, groups);
        session.groups = groups;
        session.objectives = objectives;
        boolean[][] selected = selection(session, objectives, groups);
        int[][] use = allocate(needed(objectives), groupLists(objectives), amounts(groups), selected);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "納品に使う物を選んでください (持ち物・スタッシュ・バックパックの中から。着ている装備は使いません)", NOTE), BODY_W));
        int total = 0;
        for (int o = 0; o < objectives.size(); o++) {
            Objective objective = objectives.get(o);
            int used = 0;
            for (int u : use[o]) used += u;
            total += used;
            body.add(DialogBody.plainMessage(join(objectiveLines(new ObjectiveView(session.bulk ? objective.quest().getDisplayName() : null,
                    objective.handIn().getDescription(), objective.before(), objective.before() + used, objective.needed()))), BODY_W));
            if (objective.groups().isEmpty()) {
                body.add(DialogBody.plainMessage(Component.text(objective.handIn().isRequireFIR()
                        ? "使える物を持っていません (FIR の物だけ使えます)" : "使える物を持っていません", NG), BODY_W));
                continue;
            }
            for (int g : objective.groups()) {
                Group group = groups.get(g);
                GroupView view = new GroupView(plain(group.sample()), group.fir(), group.place(), group.amount(), selected[o][g], use[o][g],
                        "toggle/" + o + "/" + g);
                body.add(DialogBody.item(group.sample()).description(DialogBody.plainMessage(join(groupLines(view)), ROW_BODY_W))
                        .showTooltip(true).showDecorations(true).width(ITEM_BOX).height(ITEM_BOX).build());
            }
        }
        if (objectives.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("納品の残っている目標はありません", NOTE), BODY_W));
        }

        Component title = Component.text(session.bulk ? "まとめて納品 ― " + session.trader.displayName
                : "納品 ― " + (objectives.isEmpty() ? session.trader.displayName : objectives.get(0).quest().getDisplayName()));
        if (session.notice != null && Bukkit.getCurrentTick() <= session.noticeUntil) {
            title = title.append(Component.text("  " + session.notice, AMBER));
        }
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        ActionButton go = ActionButton.create(Component.text(total > 0 ? "納品する (" + total + " 個)" : "納品する"),
                Component.text(total > 0 ? "チェックした物を納品する" : "使う物が選ばれていません", NOTE), 150, action("go"));
        ActionButton auto = ActionButton.create(Component.text("自動で選び直す"),
                Component.text("価値の低い物から必要な数まで選び直す", NOTE), 150, action("auto"));
        ActionButton back = ActionButton.create(Component.text("戻る"), null, 150, action("back"));
        DialogType type = DialogType.multiAction(List.of(go, auto, back)).columns(3).exitAction(back).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    /** 目標ごとの選んでいる行 (覚えている選び方。無ければ開いた時の選び方) */
    private static boolean[][] selection(Session session, List<Objective> objectives, List<Group> groups) {
        if (session.selected == null) {
            boolean[][] selected = defaultSelection(needed(objectives), groupLists(objectives), amounts(groups));
            session.selected = new LinkedHashSet<>();
            for (int o = 0; o < objectives.size(); o++) {
                for (int g : objectives.get(o).groups()) {
                    if (selected[o][g]) session.selected.add(objectives.get(o).key() + "|" + groups.get(g).key());
                }
            }
            return selected;
        }
        boolean[][] selected = new boolean[objectives.size()][groups.size()];
        for (int o = 0; o < objectives.size(); o++) {
            for (int g : objectives.get(o).groups()) {
                selected[o][g] = session.selected.contains(objectives.get(o).key() + "|" + groups.get(g).key());
            }
        }
        return selected;
    }

    private static int[] needed(List<Objective> objectives) {
        return objectives.stream().mapToInt(o -> o.needed() - o.before()).toArray();
    }

    private static List<List<Integer>> groupLists(List<Objective> objectives) {
        return objectives.stream().map(Objective::groups).toList();
    }

    private static int[] amounts(List<Group> groups) {
        return groups.stream().mapToInt(Group::amount).toArray();
    }

    /** 納品先のクエストのまだ終わっていない納品の目標と、それに使える行 (価値の低い順・手に持っている物は最後) */
    private static List<Objective> objectives(PlayerData data, QuestModule quests, Session session, List<Group> groups) {
        List<Objective> result = new ArrayList<>();
        for (String id : session.questIds) {
            QuestDefinition quest = quests.getQuest(id);
            ActiveQuest active = quest != null ? data.getActiveQuests().get(id) : null;
            if (active == null) continue;
            List<QuestObjective> list = quest.getObjectives();
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof HandInObjective handIn) || handIn.isCompleted(active, i)) continue;
                List<Integer> usable = new ArrayList<>();
                for (int g = 0; g < groups.size(); g++) {
                    Group group = groups.get(g);
                    if (handIn.matches(group.itemId(), group.itemType(), group.fir())) usable.add(g);
                }
                usable.sort(Comparator.comparing((Integer g) -> groups.get(g).held())
                        .thenComparingLong(g -> groups.get(g).unitValue()));
                result.add(new Objective(id + "/" + i, quest, i, handIn, active.getProgress(i), handIn.getTargetAmount(), usable));
            }
        }
        return result;
    }

    // ---- 使える物を集める ----

    /** 納品に使える物 ({@link HandInPlaces#scan}) を、同じ場所の同じ物 (数以外が同じ) ごとの行にまとめる */
    private static List<Group> collect(Player player, PlayerData data) {
        Map<String, List<HandInPlaces.Entry>> byPlace = new LinkedHashMap<>();
        for (HandInPlaces.Entry entry : HandInPlaces.scan(player, data)) {
            if (handInIdOf(entry.item()) != null) byPlace.computeIfAbsent(entry.place(), k -> new ArrayList<>()).add(entry);
        }
        int held = player.getInventory().getHeldItemSlot();
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, List<HandInPlaces.Entry>> place : byPlace.entrySet()) {
            List<List<HandInPlaces.Entry>> similar = new ArrayList<>();
            for (HandInPlaces.Entry entry : place.getValue()) {
                List<HandInPlaces.Entry> match = similar.stream().filter(e -> e.get(0).item().isSimilar(entry.item())).findFirst().orElse(null);
                if (match == null) similar.add(match = new ArrayList<>());
                match.add(entry);
            }
            for (List<HandInPlaces.Entry> stacks : similar) {
                // 手に持っている欄は最後に使う
                stacks.sort(Comparator.comparing(e -> isHeld(e.source(), held)));
                ItemStack sample = stacks.get(0).item().asOne();
                String id = handInIdOf(sample);
                QuestItems.Definition def = QuestItems.resolve(id);
                if (def == null) continue;
                boolean fir = isFir(sample);
                int amount = stacks.stream().mapToInt(e -> e.item().getAmount()).sum();
                boolean anyHeld = stacks.stream().anyMatch(e -> isHeld(e.source(), held));
                String key = place.getKey() + "|" + id + "|" + fir + "|" + sample.hashCode();
                groups.add(new Group(key, place.getKey(), sample, id, def.type(), fir, amount, RaidResults.valueOf(sample), anyHeld,
                        stacks.stream().map(HandInPlaces.Entry::source).toList()));
            }
        }
        return groups;
    }

    private static boolean isHeld(HandInPlaces.Source source, int heldSlot) {
        return source.kind() == HandInPlaces.Kind.INVENTORY && source.inner() < 0 && source.slot() == heldSlot;
    }

    // ---- 納品する ----

    /** 選んだ物で納品する。目標ごとに納品できた数 */
    private static Map<Objective, Integer> handIn(Player player, Session session) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        List<Group> groups = collect(player, data);
        List<Objective> objectives = objectives(data, questModule(), session, groups);
        boolean[][] selected = selection(session, objectives, groups);
        int[][] use = allocate(needed(objectives), groupLists(objectives), amounts(groups), selected);

        HandInPlaces.Store store = new HandInPlaces.Store(player, data);
        Map<Objective, Integer> done = new LinkedHashMap<>();
        for (int o = 0; o < objectives.size(); o++) {
            Objective objective = objectives.get(o);
            ActiveQuest active = data.getActiveQuests().get(objective.quest().getId());
            if (active == null) continue;
            for (int g : objective.groups()) {
                int want = use[o][g];
                Group group = groups.get(g);
                for (int s = 0; s < group.sources().size() && want > 0; s++) {
                    HandInPlaces.Source source = group.sources().get(s);
                    ItemStack item = store.get(source);
                    if (item == null || !item.isSimilar(group.sample())) continue;   // 動いた・別の物になった
                    int take = Math.min(want, item.getAmount());
                    Map<String, Object> params = Map.of("itemId", group.itemId(), "itemType", group.itemType(), "isFIR", group.fir(), "amount", take);
                    if (!objective.handIn().updateProgress(player, data, active, objective.index(), QuestTrigger.HAND_IN, params)) break;
                    item.setAmount(item.getAmount() - take);
                    store.set(source, item.getAmount() > 0 ? item : null);
                    want -= take;
                    done.merge(objective, take, Integer::sum);
                }
            }
        }
        store.save();
        if (!done.isEmpty()) data.setDirty(true);
        return done;
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
                if (parts.length < 3) return;
                int o, g;
                try {
                    o = Integer.parseInt(parts[1]);
                    g = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    return;
                }
                if (o < 0 || o >= session.objectives.size() || g < 0 || g >= session.groups.size()) return;
                String key = session.objectives.get(o).key() + "|" + session.groups.get(g).key();
                if (session.selected == null) session.selected = new LinkedHashSet<>();
                if (!session.selected.remove(key)) session.selected.add(key);
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
            }
            case "auto" -> {
                session.selected = null;
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.1f);
            }
            case "go" -> {
                Map<Objective, Integer> done = handIn(player, session);
                if (done.isEmpty()) {
                    notice(session, "納品できる物が選ばれていません");
                    player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                    break;
                }
                PlayerData data = dataModule().getPlayerData(player.getUniqueId());
                QuestModule quests = questModule();
                done.forEach((objective, amount) -> player.sendMessage(Component.text("納品しました: "
                        + (session.bulk ? "[" + objective.quest().getDisplayName() + "] " : "") + objective.handIn().getDescription()
                        + " +" + amount, OK)));
                done.keySet().stream().map(Objective::quest).distinct().forEach(quest -> {
                    ActiveQuest active = data.getActiveQuests().get(quest.getId());
                    if (active != null && quests.isAllObjectivesMet(quest, active)) {
                        player.sendMessage(Component.text((session.bulk ? "「" + quest.getDisplayName() + "」の" : "")
                                + "すべての目標を達成しました。クエスト一覧で報告できます。", AMBER));
                    }
                });
                player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
                notice(session, "納品しました");
                // 残りの目標は、開いた時と同じ選び方からやり直す
                session.selected = null;
            }
            case "back" -> {
                SESSIONS.remove(player.getUniqueId());
                QuestDialog.resume(player, session.trader);
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

    // ---- 物の見分け ----

    static boolean isFir(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
    }

    /** 納品で使うアイテムのID: データパックのアタッチメント → プラグインのアイテム・弾 → データパックの銃 */
    static String handInIdOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        // 支給品は納品できない (クエストの強調表示もしない)
        if (com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout.isSupplied(item)) return null;
        String id = DatapackAttachments.attachmentIdOf(item);
        if (id != null) return id;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        id = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        return id != null ? id : datapackGunId(pdc);
    }

    private static String datapackGunId(PersistentDataContainer pdc) {
        for (String namespace : new String[]{"minecraft", "toisarm"}) {
            PersistentDataContainer sub = pdc.get(new NamespacedKey(namespace, "toisarm"), PersistentDataType.TAG_CONTAINER);
            if (sub == null) continue;
            for (String subNamespace : new String[]{"minecraft", "toisarm"}) {
                String id = sub.get(new NamespacedKey(subNamespace, "id"), PersistentDataType.STRING);
                if (id != null) return id;
            }
        }
        return null;
    }

    // ---- 小物 ----

    private static String plain(ItemStack item) {
        return HandInPlaces.plain(item);
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

    private static PlayerDataModule dataModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
    }

    private static QuestModule questModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(QuestModule.class);
    }
}

package com.lunar_prototype.impossbleEscapeMC.modules.craft;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
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
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_DONE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_STEPS;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BAR_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.BOX_TAB_ON;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_ACTIVE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_DONE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_REPORT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_START;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_TEXT_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.CHIP_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.HEADER_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.LOCK_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_REPORT_BASE;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILES;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_TILE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.QUEST_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TRADER_FONT;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxArtWidth;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.boxGlyph;
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
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.PLATE_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.RING_X;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_BODY_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_LINE_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.TEXT_X;

/**
 * 隠れ家の作る設備 (ワークベンチ・弾薬製造・医療設備) で共通の、クラフトの画面 (ダイアログ)。2 つの画面がある:
 * <ul>
 *   <li>製作: 設備のレシピを並べる。行は 作る物 (アイテム欄に本物の見た目)・数・かかる時間・必要な素材 (持っている数/必要な数)、
 *       右の札で製作をキューに入れる。素材が足りない・キューがいっぱい・設備のレベルが足りない (錠) 時は押せない</li>
 *   <li>キュー: 製作中・待ち・完成した物を並べる。行は 進み具合の棒と残り時間、右の札で受け取る (完成) か取り消す (素材は返す)</li>
 * </ul>
 * 見た目の部品はクエストの画面と同じ (フォント iem:trader の端末・札・箱、iem:quest の札・棒)。
 * 行の組み立て (headerLines・recipeLines・queueLines) はサーバーなしで組み立てられる (CraftDialogLayoutTest が幅を確かめ、見本を書き出す)。
 * 時間の決まりは {@link CraftQueues}、レシピは {@link CraftRecipes}。キューの画面を開いている間は 1 秒ごとに送り直す (残り時間・完成)
 */
public final class CraftDialog implements Listener {

    // ---- 寸法 (GUI の画素。共通の部品の字・寸法・色は gui/TerminalFont・TerminalPalette) ----
    private static final int HEADER_ART_W = HEADER_TILES * HEADER_TILE_W;
    static final int HEADER_BODY_W = 340;
    static final int HEADER_W = HEADER_BODY_W - 8;
    /** 上の端末の行の数 (1 行目: 設備の名前と液晶、3 行目: 画面のタブ、5 行目: 種類のタブ) */
    static final int HEADER_LINES = 6;
    private static final int LCD_X = 150;
    private static final int CHIP_X = PLATE_X + PLATE_TILES * PLATE_TILE_W - 4 - CHIP_W;
    private static final int VALUE_RIGHT = CHIP_X - 6;
    private static final int LOCKED_VALUE_RIGHT = PLATE_X + PLATE_TILES * PLATE_TILE_W - 5;

    private static final String NS = "iem";
    private static final String PREFIX = "craft/";

    private static final int REFRESH_TICKS = 20;
    private static final int NOTICE_TICKS = 80;

    private CraftDialog() {
    }

    // ---- 開く ----

    enum Screen { CRAFT, QUEUE }

    private static final class Session {
        final HideoutFacility facility;
        Screen screen = Screen.CRAFT;
        /** 製作の画面の種類のタブ (null は「すべて」) */
        String category;
        /** 今出しているレシピの種類 (押されたタブの番号から引く) */
        List<String> categories = List.of();
        String notice;
        int noticeUntil;
        /** 最後に送った中身 (変わっていなければ送り直さない) */
        String lastSignature;

        Session(HideoutFacility facility) {
            this.facility = facility;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static CraftDialog instance;
    private static BukkitTask refresher;

    private static void ensureRegistered() {
        if (instance == null) {
            instance = new CraftDialog();
            Bukkit.getPluginManager().registerEvents(instance, ImpossbleEscapeMC.getInstance());
        }
        if (refresher == null || refresher.isCancelled()) {
            refresher = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), CraftDialog::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
        }
    }

    /** 設備の製作の画面を開く (製作の画面のある設備だけ) */
    public static void open(Player player, HideoutFacility facility) {
        if (!CraftRecipes.crafts(facility)) return;
        ensureRegistered();
        player.closeInventory();
        Session session = new Session(facility);
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session, true);
    }

    /** キューの画面を開いている人に、残り時間・完成が変わっていれば送り直す */
    private static void refreshAll() {
        SESSIONS.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) return true;
            show(player, entry.getValue(), false);
            return false;
        });
        if (SESSIONS.isEmpty() && refresher != null) {
            refresher.cancel();
            refresher = null;
        }
    }

    private static void show(Player player, Session session, boolean force) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        HideoutModule hideout = hideoutModule();
        if (data == null || hideout == null) return;
        long now = System.currentTimeMillis();
        int level = data.getHideoutLevel(session.facility.id);
        List<CraftJob> queue = data.getCraftQueue(session.facility.id);
        int limit = CraftQueues.limit(level);
        int done = (int) queue.stream().filter(j -> CraftQueues.state(j, now) == CraftQueues.State.DONE).count();
        String notice = session.notice != null && Bukkit.getCurrentTick() <= session.noticeUntil ? session.notice : null;

        // 送り直すのは中身が変わった時だけ (キューの画面の残り時間は 1 秒ごとに変わる)
        StringBuilder signature = new StringBuilder().append(session.screen).append('|').append(session.category).append('|').append(notice);
        if (session.screen == Screen.QUEUE) {
            for (CraftJob job : queue) signature.append('|').append(job.getRecipeId()).append(':').append(Math.max(0, (job.getEndMillis() - now) / 1000));
        }
        if (!force && signature.toString().equals(session.lastSignature)) return;
        session.lastSignature = signature.toString();

        List<CraftRecipes.Recipe> recipes = CraftRecipes.of(session.facility);
        session.categories = recipes.stream().map(CraftRecipes.Recipe::category).filter(java.util.Objects::nonNull).distinct().toList();
        List<Tab> screens = List.of(new Tab("製作", session.screen == Screen.CRAFT, "screen/craft"),
                new Tab("キュー " + queue.size() + "/" + limit, session.screen == Screen.QUEUE, "screen/queue"));
        List<Tab> categories = new ArrayList<>();
        if (session.screen == Screen.CRAFT && !session.categories.isEmpty()) {
            categories.add(new Tab("すべて", session.category == null, "cat/all"));
            for (int i = 0; i < session.categories.size(); i++) {
                categories.add(new Tab(session.categories.get(i), session.categories.get(i).equals(session.category), "cat/" + i));
            }
        }

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(join(headerLines(new HeaderView(session.facility.label, level, queue.size(), limit, done, screens, categories))),
                HEADER_BODY_W));
        if (session.screen == Screen.CRAFT) {
            List<CraftRecipes.Recipe> shown = recipes.stream().filter(r -> session.category == null || session.category.equals(r.category())).toList();
            List<HideoutFacility.ItemCost> all = shown.stream().flatMap(r -> r.materials().stream()).toList();
            Map<String, Integer> have = hideout.countMaterials(player, data, all);
            for (CraftRecipes.Recipe recipe : shown) {
                List<Need> needs = recipe.materials().stream()
                        .map(m -> new Need(itemName(m.itemId()), have.getOrDefault(m.itemId(), 0), m.amount())).toList();
                RecipeState state = level < recipe.level() ? RecipeState.LOCKED
                        : queue.size() >= limit ? RecipeState.QUEUE_FULL
                        : needs.stream().allMatch(Need::ok) ? RecipeState.READY : RecipeState.SHORT;
                RecipeView view = new RecipeView(itemName(recipe.productId()), recipe.count(), time(recipe.seconds() * 1000L), needs, state,
                        "Lv." + recipe.level() + " で解放", "make/" + recipe.id());
                body.add(itemRow(product(player, recipe), recipeLines(view)));
            }
        } else {
            for (int i = 0; i < queue.size(); i++) {
                CraftJob job = queue.get(i);
                CraftRecipes.Recipe recipe = CraftRecipes.byId(job.getRecipeId());
                if (recipe == null) continue;
                CraftQueues.State state = CraftQueues.state(job, now);
                QueueState shownState = switch (state) {
                    case CRAFTING -> QueueState.CRAFTING;
                    case WAITING -> QueueState.WAITING;
                    case DONE -> QueueState.DONE;
                };
                long left = state == CraftQueues.State.WAITING ? job.getDurationMillis() : job.getEndMillis() - now;
                String action = (state == CraftQueues.State.DONE ? "take/" : "cancel/") + i + "/" + job.getRecipeId();
                QueueView view = new QueueView(itemName(recipe.productId()), recipe.count(), shownState, CraftQueues.progress(job, now), time(left),
                        CraftQueues.order(queue, i, now), action);
                body.add(itemRow(product(player, recipe), queueLines(view)));
            }
            if (queue.isEmpty()) body.add(DialogBody.plainMessage(Component.text("キューは空です", NOTE), HEADER_BODY_W));
        }

        Component title = Component.text(session.facility.label + " ― " + (session.screen == Screen.CRAFT ? "製作" : "キュー"));
        if (notice != null) title = title.append(Component.text("  " + notice, AMBER));
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, DialogAction.customClick(Key.key(NS, PREFIX + "close"), null));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.notice(close))));
    }

    private static DialogBody itemRow(ItemStack icon, List<DialogLine> lines) {
        return DialogBody.item(icon).description(DialogBody.plainMessage(join(lines), ROW_BODY_W))
                .showTooltip(true).showDecorations(true).width(16).height(16).build();
    }

    // ---- 操作 ----

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Key id = event.getIdentifier();
        if (!NS.equals(id.namespace()) || !id.value().startsWith(PREFIX)) {
            // 別の画面のボタン: この画面はもう閉じている (送り直すと別の画面を上書きしてしまう)
            SESSIONS.remove(player.getUniqueId());
            return;
        }
        String action = id.value().substring(PREFIX.length());
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> handle(player, action));
    }

    private static void handle(Player player, String action) {
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null || !player.isOnline()) return;
        String[] parts = action.split("/");
        switch (parts[0]) {
            case "close" -> {
                SESSIONS.remove(player.getUniqueId());
                player.closeDialog();
                return;
            }
            case "screen" -> session.screen = "queue".equals(parts[1]) ? Screen.QUEUE : Screen.CRAFT;
            case "cat" -> {
                int index = parts.length > 1 && !"all".equals(parts[1]) ? parseIndex(parts[1]) : -1;
                session.category = index >= 0 && index < session.categories.size() ? session.categories.get(index) : null;
            }
            case "make" -> make(player, session, parts.length > 1 ? CraftRecipes.byId(parts[1]) : null);
            case "take", "cancel" -> {
                if (parts.length < 3) return;
                finish(player, session, parts[0].equals("take"), parseIndex(parts[1]), parts[2]);
            }
            default -> {
                return;
            }
        }
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.4f, 1.3f);
        show(player, session, true);
    }

    /** 製作を始める (キューに入れる): レベル・キューの空き・素材を確かめ直してから素材を取る */
    private static void make(Player player, Session session, CraftRecipes.Recipe recipe) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        if (recipe == null || recipe.facility() != session.facility || data == null) return;
        List<CraftJob> queue = data.getCraftQueue(session.facility.id);
        int level = data.getHideoutLevel(session.facility.id);
        if (level < recipe.level()) {
            notice(session, "設備のレベルが足りません");
            return;
        }
        if (queue.size() >= CraftQueues.limit(level)) {
            notice(session, "キューがいっぱいです");
            return;
        }
        try {
            if (!hideoutModule().takeMaterials(player, data, recipe.materials())) {
                notice(session, "素材が足りません");
                return;
            }
        } catch (IOException e) {
            ImpossbleEscapeMC.getInstance().getLogger().log(Level.WARNING, "製作の素材を取れませんでした", e);
            notice(session, "素材を取れませんでした");
            return;
        }
        long now = System.currentTimeMillis();
        CraftJob job = CraftQueues.enqueue(queue, recipe.id(), recipe.seconds() * 1000L, now);
        data.setDirty(true);
        dataModule().saveAsync(player.getUniqueId());
        notice(session, CraftQueues.state(job, now) == CraftQueues.State.CRAFTING ? "製作を始めました" : "キューに入れました");
    }

    /** 受け取る (完成した物) か、取り消す (素材を返す)。押した時と同じ製作か (番号とレシピ) を確かめる */
    private static void finish(Player player, Session session, boolean take, int index, String recipeId) {
        PlayerData data = dataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        List<CraftJob> queue = data.getCraftQueue(session.facility.id);
        if (index < 0 || index >= queue.size() || !queue.get(index).getRecipeId().equals(recipeId)) return;
        CraftRecipes.Recipe recipe = CraftRecipes.byId(recipeId);
        long now = System.currentTimeMillis();
        CraftQueues.State state = CraftQueues.state(queue.get(index), now);
        if (recipe == null) return;
        if (take) {
            if (state != CraftQueues.State.DONE) return;
            queue.remove(index);
            hideoutModule().deliver(player, data, products(player, recipe.productId(), recipe.count()));
            notice(session, "受け取りました: " + itemName(recipe.productId()) + (recipe.count() > 1 ? " ×" + recipe.count() : ""));
        } else {
            if (CraftQueues.cancel(queue, index, now) == null) return;
            List<ItemStack> refund = new ArrayList<>();
            for (HideoutFacility.ItemCost m : recipe.materials()) refund.addAll(products(player, m.itemId(), m.amount()));
            hideoutModule().deliver(player, data, refund);
            notice(session, "取り消しました (素材を返しました)");
        }
        data.setDirty(true);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        // チェストの画面に替わった (ダイアログは置き換えられた) ので送り直さない
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // レイド・ロビーに移った (隠れ家の外で製作の画面を出し直さない)
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    private static void notice(Session session, String text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    // ---- 品物 ----

    /** 見本 (行のアイテム欄)。作れなければ紙 */
    private static ItemStack product(Player player, CraftRecipes.Recipe recipe) {
        ItemStack item = ItemFactory.create(player.getWorld(), recipe.productId());
        return item != null ? item : new ItemStack(Material.PAPER);
    }

    /** id の物を count 個 (スタックの上限で分ける) */
    private static List<ItemStack> products(Player player, String id, int count) {
        List<ItemStack> out = new ArrayList<>();
        int left = count;
        while (left > 0) {
            ItemStack item = ItemFactory.create(player.getWorld(), id);
            if (item == null) break;
            int amount = Math.min(left, Math.max(1, item.getMaxStackSize()));
            item.setAmount(amount);
            out.add(item);
            left -= amount;
        }
        return out;
    }

    private static String itemName(String id) {
        ItemDefinition def = ItemRegistry.get(id);
        if (def != null && def.displayName != null) return def.displayName.replaceAll("§.", "");
        AmmoDefinition ammo = ItemRegistry.getAmmo(id);
        if (ammo != null && ammo.displayName != null) return ammo.displayName.replaceAll("§.", "");
        return id;
    }

    /** 時間 (分:秒) */
    static String time(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    private static int parseIndex(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Component join(List<DialogLine> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
    }

    private static PlayerDataModule dataModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
    }

    private static HideoutModule hideoutModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(HideoutModule.class);
    }

    // ---- 上の端末 ----

    /** タブ (画面の切り替え・レシピの種類)。on は選んでいる物 */
    record Tab(String label, boolean on, String action) {
    }

    /**
     * 上の端末に出す値。facility は設備の名前、level は設備のレベル、queued・queueMax はキューに入っている数と上限、
     * done は完成して受け取れる数、screens は画面のタブ (製作・キュー)、categories はレシピの種類のタブ (無ければ空)
     */
    record HeaderView(String facility, int level, int queued, int queueMax, int done, List<Tab> screens, List<Tab> categories) {
    }

    static List<DialogLine> headerLines(HeaderView v) {
        DialogLine line1 = line();
        for (int i = 0; i < HEADER_TILES; i++) line1.stamp(TRADER_FONT, (char) (HEADER_BASE + i), HEADER_TILE_W, i * HEADER_TILE_W);
        String level = " Lv." + v.level();
        line1.moveTo(8).text(FontWidths.truncate(v.facility(), LCD_X - 21 - FontWidths.width(level)), INK).text(level, AMBER_DIM);
        line1.moveTo(LCD_X).text("キュー ", AMBER_DIM).text(v.queued() + "/" + v.queueMax(), v.queued() >= v.queueMax() ? NG : AMBER);
        line1.shift(8).text("完成 ", AMBER_DIM).text(String.valueOf(v.done()), v.done() > 0 ? OK : DIM);
        line1.moveTo(HEADER_W);

        List<DialogLine> lines = new ArrayList<>();
        lines.add(line1);
        lines.add(line().moveTo(HEADER_W));
        lines.add(tabs(v.screens()));
        lines.add(line().moveTo(HEADER_W));
        lines.add(tabs(v.categories()));
        while (lines.size() < HEADER_LINES) lines.add(line().moveTo(HEADER_W));
        return lines;
    }

    private static DialogLine tabs(List<Tab> tabs) {
        DialogLine l = line().moveTo(8);
        for (Tab tab : tabs) {
            if (l.x + boxWidth(tab.label()) > HEADER_ART_W - 6) break;   // 入りきらないタブは出さない (折り返させない)
            l.box(TRADER_FONT, boxGlyph(tab.label(), tab.on() ? BOX_TAB_ON : BOX_TAB), boxArtWidth(tab.label()),
                    tab.label(), tab.on() ? DARK : AMBER, tab.on(), click(tab.action()), null).shift(4);
        }
        return l.moveTo(HEADER_W);
    }

    // ---- 製作の画面: レシピの行 ----

    /** レシピの状態 (右の札) */
    enum RecipeState { READY, SHORT, QUEUE_FULL, LOCKED }

    /** 必要な素材 1 つと、持っている数 */
    record Need(String label, int have, int need) {
        boolean ok() {
            return have >= need;
        }
    }

    /**
     * レシピの行に出す値。count は作る数 (1 なら出さない)、time はかかる時間 (「3:00」)、lock は錠の時の理由 (「Lv.2 で解放」)
     */
    record RecipeView(String name, int count, String time, List<Need> needs, RecipeState state, String lock, String action) {
    }

    static List<DialogLine> recipeLines(RecipeView v) {
        boolean locked = v.state() == RecipeState.LOCKED;
        DialogLine line1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            line1.stamp(TRADER_FONT, (char) (PLATE_BASE + (locked ? PLATE_TILES : 0) + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        line1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        int nameX = TEXT_X;
        if (locked) {
            line1.stamp(TRADER_FONT, LOCK, LOCK_W, TEXT_X);
            nameX = TEXT_X + LOCK_W + 3;
        }
        // 右: 錠なら理由、そうでなければかかる時間 (時計のアイコン付き)
        int right = locked ? LOCKED_VALUE_RIGHT : VALUE_RIGHT;
        String value = locked ? v.lock() : v.time();
        int valueW = FontWidths.width(value) + (locked ? 0 : UiIcon.GLYPH_ADVANCE + 2);
        String count = v.count() > 1 ? "  ×" + v.count() : "";
        String name = FontWidths.truncate(v.name(), right - valueW - 8 - nameX - FontWidths.width(count));
        line1.moveTo(nameX).text(name, locked ? DIM : NAME).text(count, locked ? DIM : AMBER);
        line1.moveTo(right - valueW);
        if (locked) {
            line1.text(value, NG);
        } else {
            line1.icon(UiIcon.TIMER).shift(2).text(value, NOTE);
            ChipStyle chip = switch (v.state()) {
                case READY -> new ChipStyle(CHIP_START, "製作", OK, "キューに入れる (素材はすぐ使う)");
                case SHORT -> new ChipStyle(CHIP_DONE, "不足", DIM, "素材が足りません");
                case QUEUE_FULL -> new ChipStyle(CHIP_DONE, "満杯", DIM, "キューがいっぱいです");
                default -> null;
            };
            line1.moveTo(CHIP_X).chip(QUEST_FONT, (char) (CHIP_BASE + chip.glyph()), CHIP_W, CHIP_TEXT_FONT, chip.label(), chip.color(), false,
                    v.state() == RecipeState.READY ? click(v.action()) : null, Component.text(chip.hover(), NOTE));
        }
        line1.moveTo(ROW_LINE_W);

        // 2 行目: 必要な素材 (足りない物は赤)
        DialogLine line2 = line().moveTo(nameX);
        int limit = locked ? LOCKED_VALUE_RIGHT : VALUE_RIGHT;
        for (Need need : v.needs()) {
            String text = need.label() + " " + need.have() + "/" + need.need();
            if (line2.x >= limit - 12) break;
            line2.text(FontWidths.truncate(text, limit - line2.x), locked ? DIM : need.ok() ? NOTE : NG).shift(8);
        }
        line2.moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    private record ChipStyle(int glyph, String label, TextColor color, String hover) {
    }

    // ---- キューの画面: 製作中・待ち・完成の行 ----

    enum QueueState { CRAFTING, WAITING, DONE }

    /**
     * キューの行に出す値。ratio は進み具合 (0..1)、left は残り時間 (「2:13」。待ちは全体の時間)、order は待ちの順番 (1 から)
     */
    record QueueView(String name, int count, QueueState state, double ratio, String left, int order, String action) {
    }

    static List<DialogLine> queueLines(QueueView v) {
        boolean done = v.state() == QueueState.DONE;
        DialogLine line1 = line();
        for (int i = 0; i < PLATE_TILES; i++) {
            if (done) line1.stamp(QUEST_FONT, (char) (PLATE_REPORT_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
            else line1.stamp(TRADER_FONT, (char) (PLATE_BASE + i), PLATE_TILE_W, PLATE_X + i * PLATE_TILE_W);
        }
        line1.stamp(TRADER_FONT, RING, RING_W, RING_X);
        String status = switch (v.state()) {
            case CRAFTING -> "製作中";
            case WAITING -> "待ち " + v.order();
            case DONE -> "完成";
        };
        TextColor statusColor = switch (v.state()) {
            case CRAFTING -> AMBER;
            case WAITING -> DIM;
            case DONE -> OK;
        };
        int statusX = VALUE_RIGHT - FontWidths.width(status);
        String count = v.count() > 1 ? "  ×" + v.count() : "";
        line1.moveTo(TEXT_X).text(FontWidths.truncate(v.name(), statusX - 8 - TEXT_X - FontWidths.width(count)), NAME).text(count, AMBER);
        line1.moveTo(statusX).text(status, statusColor);
        if (done) {
            line1.moveTo(CHIP_X).chip(QUEST_FONT, (char) (CHIP_BASE + CHIP_REPORT), CHIP_W, CHIP_TEXT_FONT, "受取", DARK, true,
                    click(v.action()), Component.text("持ち物に受け取る (入らない分はスタッシュへ)", NOTE));
        } else {
            line1.moveTo(CHIP_X).chip(QUEST_FONT, (char) (CHIP_BASE + CHIP_ACTIVE), CHIP_W, CHIP_TEXT_FONT, "取消", AMBER, false,
                    click(v.action()), Component.text("取り消して素材を返す", NOTE));
        }
        line1.moveTo(ROW_LINE_W);

        // 2 行目: 進み具合の棒と残り時間
        DialogLine line2 = line().moveTo(TEXT_X);
        line2.glyph(QUEST_FONT, barGlyph(done ? 1.0 : v.ratio(), done), BAR_W, null, null).shift(6);
        if (done) {
            line2.text("受け取れます", OK);
        } else if (v.state() == QueueState.CRAFTING) {
            line2.text("残り " + v.left(), AMBER).shift(6).text(Math.round(v.ratio() * 100) + "%", NOTE);
        } else {
            line2.text("前の製作の後 " + v.left(), DIM);
        }
        line2.moveTo(ROW_LINE_W);
        return List.of(line1, line2);
    }

    // ---- 小物 ----

    private static char barGlyph(double ratio, boolean done) {
        int step = (int) Math.round(Math.max(0, Math.min(1, ratio)) * BAR_STEPS);
        return (char) ((done ? BAR_DONE_BASE : BAR_BASE) + step);
    }

    private static ClickEvent click(String action) {
        return action == null ? null : ClickEvent.custom(Key.key(NS, PREFIX + action));
    }
}

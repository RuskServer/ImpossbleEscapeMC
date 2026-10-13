package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 隠れ家の設備の画面 (ダイアログ)。左に端末のパネル (フォント iem:hideout の絵。IEM_shader/tools/hideout_font.py)、
 * 右は空けて、カメラ ({@link HideoutCamera}) が映す設備が見えるようにする (プレビュー: IEM_shader/tools/hideout_dialog.py)。
 * <ul>
 *   <li>◀ ▶ で設備を前・次へ切り替える (端は反対の端へ回る)。カメラもその設備へ動く</li>
 *   <li>下の帯: 強化する / 閉じる。カメラを使っている間は Esc で閉じない (閉じるで元の位置とゲームモードに戻す)</li>
 *   <li>カメラは自分の隠れ家にいる時だけ使う。ほかの所で開いた時は、普通のダイアログ (後ろはぼける)</li>
 * </ul>
 * 本文は 1 つの要素 (幅 {@link #BODY_W}) で、行はすべて {@link #LINE_W} にそろえる (クライアントの中央揃えで位置がずれないように)。
 * 行の頭はパネルの左の 4 右、行の上端はパネルの上から 9 x 行目 - 3 (要素の上 + 4 + 9 x 行目)
 */
public final class HideoutDialog implements Listener {

    private static final String NS = "iem";
    private static final String PREFIX = "hideout/";
    static final Key FONT = Key.key("iem", "hideout");
    /** 空白の字はトレーダーの画面と同じ物 (iem:trader の ±2^i) */
    private static final Key SPACE_FONT = Key.key("iem", "trader");
    private static final char SPACE_POS = '';
    private static final char SPACE_NEG = '';

    static final int BODY_W = 452;
    static final int LINE_W = BODY_W - 8;
    /** パネル (高さ 162) が入るだけの行 */
    static final int LINES = 19;
    /** 行の頭から見たパネルの左 */
    private static final int PANEL = -4;

    // フォント iem:hideout の字 (hideout_font.py と同じ)
    private static final char CASING = '';     // + 行 x 2 + 列
    private static final int CASING_TILE_W = 95;
    private static final char LCD = '';        // + 列
    private static final int LCD_TILE_W = 86;
    private static final char NAME = '';       // + 列
    private static final int NAME_TILE_W = 71;
    private static final char ARROW_LEFT = '';
    private static final char ARROW_RIGHT = '';
    private static final int ARROW_W = 18;
    private static final char DOT_CURRENT = '';
    private static final char DOT_BUILT = '';
    private static final char DOT_EMPTY = '';
    private static final char PIP_ON = '';
    private static final char PIP_NEXT = '';
    private static final char PIP_OFF = '';
    private static final int PIP_W = 6;

    private static final TextColor AMBER = TextColor.color(0xDEAC4E);
    private static final TextColor AMBER_DIM = TextColor.color(0x96763C);
    private static final TextColor INK = TextColor.color(0xE0D6AA);
    private static final TextColor GREEN = TextColor.color(0x78CD60);
    private static final TextColor RED = TextColor.color(0xD64A34);
    private static final int NOTICE_TICKS = 80;
    /** カメラから外れていないかを見る間隔 (tick) */
    private static final int WATCH_TICKS = 10;

    private static final HideoutFacility[] FACILITIES = HideoutFacility.values();

    private static final class Session {
        int index;
        Component notice;
        int noticeUntil;
    }

    private static HideoutDialog instance;

    private final HideoutModule module;
    private final HideoutCamera camera;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private HideoutDialog(HideoutModule module) {
        this.module = module;
        this.camera = new HideoutCamera(module.getPlugin());
    }

    static void init(HideoutModule module) {
        if (instance != null) return;
        instance = new HideoutDialog(module);
        Bukkit.getPluginManager().registerEvents(instance, module.getPlugin());
        Bukkit.getScheduler().runTaskTimer(module.getPlugin(), instance::watch, WATCH_TICKS, WATCH_TICKS);
    }

    static void shutdown() {
        if (instance != null) instance.camera.stopAll();
    }

    /** 画面を開く。自分の隠れ家にいればカメラで設備を映す */
    public static void open(Player player) {
        if (instance == null) return;
        instance.openFor(player);
    }

    private void openFor(Player player) {
        player.closeInventory();
        Session session = sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        if (!camera.isActive(player) && inOwnHideout(player, data)) {
            camera.start(player, module.cameraPose(data, FACILITIES[session.index]));
        }
        show(player, session);
    }

    private boolean inOwnHideout(Player player, PlayerData data) {
        return data != null && data.getHideoutIndex() >= 0 && module.getLayout().base() != null
                && player.getWorld().getName().equals(HideoutWorldManager.PLAYER_WORLD_NAME);
    }

    // ---- 表示 ----

    /** パネルに出す値 */
    record PanelView(String label, int level, int maxLevel, int index, boolean[] built, String description,
                     boolean hasRequirements, long cost, long money, String blocking) {
    }

    private PanelView view(Player player, PlayerData data, int index) {
        HideoutFacility facility = FACILITIES[index];
        boolean[] built = new boolean[FACILITIES.length];
        for (int i = 0; i < FACILITIES.length; i++) built[i] = data.getHideoutLevel(FACILITIES[i].id) > 0;
        int level = data.getHideoutLevel(facility.id);
        EconomyModule economy = module.getPlugin().getServiceContainer().get(EconomyModule.class);
        long money = economy != null ? (long) economy.getBalance(player.getUniqueId()) : 0;
        long cost = facility.moneyCost(level + 1);
        return new PanelView(facility.label, level, facility.maxLevel(module.getLayout()), index, built, facility.description(),
                facility.hasRequirements(), cost, money, blocking(data, facility, money));
    }

    /** 上げられない理由 (上げられるなら null) */
    private String blocking(PlayerData data, HideoutFacility facility, long money) {
        int level = data.getHideoutLevel(facility.id);
        int max = facility.maxLevel(module.getLayout());
        if (level >= max) return max == 0 ? "まだ建てられません" : "最大レベルです";
        if (!module.getLayout().hasLayer(facility, level + 1)) return "Lv." + (level + 1) + " の建築はまだ作られていません";
        if (money < facility.moneyCost(level + 1)) return "所持金が足りません";
        return null;
    }

    private void show(Player player, Session session) {
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        PanelView view = view(player, data, session.index);
        Component title = Component.text("隠れ家 ― " + view.label());
        if (session.notice != null && Bukkit.getCurrentTick() < session.noticeUntil) {
            title = title.append(Component.text("   ")).append(session.notice);
        }
        DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(!camera.isActive(player))
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(join(panelLines(view)), BODY_W)))
                .build();
        ActionButton upgrade = ActionButton.create(Component.text(upgradeLabel(view)), null, 150, action("upgrade"));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(upgrade, close))));
    }

    static String upgradeLabel(PanelView view) {
        if (view.level() >= view.maxLevel() && view.maxLevel() > 0) return "最大レベル";
        boolean base = HideoutFacility.values()[view.index()] == HideoutFacility.BASE;
        return view.level() == 0 && !base ? "設置する" : "強化する (Lv." + (view.level() + 1) + ")";
    }

    /** パネルの行 (サーバーなしで組み立てられる。HideoutDialogLayoutTest が幅を確かめる) */
    static List<Line> panelLines(PanelView v) {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < LINES; i++) lines.add(new Line());

        // 行 0: 筐体 (2 x 2) と大きい液晶 (2 枚)
        Line l0 = lines.get(0);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) l0.stamp((char) (CASING + r * 2 + c), CASING_TILE_W, PANEL + c * CASING_TILE_W);
        }
        for (int c = 0; c < 2; c++) l0.stamp((char) (LCD + c), LCD_TILE_W, PANEL + 9 + c * LCD_TILE_W);

        // 行 1: 見出し
        lines.get(1).moveTo(PANEL + 9).text("HIDEOUT", AMBER).moveTo(PANEL + 64).text("隠れ家の設備", AMBER_DIM);

        // 行 2: ◀ 名前の液晶 ▶
        Line l2 = lines.get(2);
        l2.moveTo(PANEL + 5).glyph(ARROW_LEFT, ARROW_W, click("prev"));
        for (int c = 0; c < 2; c++) l2.stamp((char) (NAME + c), NAME_TILE_W, PANEL + 24 + c * NAME_TILE_W);
        l2.moveTo(PANEL + 167).glyph(ARROW_RIGHT, ARROW_W, click("next"));

        // 行 3: 設備の名前とレベル (名前の液晶の真ん中に)
        String state = v.level() == 0 && HideoutFacility.values()[v.index()] != HideoutFacility.BASE ? "未設置" : "Lv." + v.level();
        int nameW = FontWidths.width(v.label()) + 6 + FontWidths.width(state);
        Line l3 = lines.get(3);
        l3.moveTo(PANEL + 95 - nameW / 2).text(v.label(), AMBER).shift(6).text(state, INK);

        // 行 4: 何番目の設備か (点。今見ている物は大きく、建ててある物は明るく)
        Line l4 = lines.get(4);
        int count = v.built().length;
        for (int i = 0; i < count; i++) {
            int center = 95 + (i * 2 - (count - 1)) * 9 / 2;
            if (i == v.index()) {
                l4.stamp(DOT_CURRENT, 5, PANEL + center - 2);
            } else {
                l4.stamp(v.built()[i] ? DOT_BUILT : DOT_EMPTY, 3, PANEL + center - 1);
            }
        }

        // 行 6: 次のレベルと目盛り
        Line l6 = lines.get(6);
        boolean maxed = v.level() >= v.maxLevel();
        l6.moveTo(PANEL + 14).text("次のレベル", AMBER_DIM).moveTo(PANEL + 64)
                .text(maxed ? (v.maxLevel() == 0 ? "まだありません" : "最大レベル") : "Lv." + v.level() + " → Lv." + (v.level() + 1), INK);
        // 目盛りは右寄せ。文字に重なるほど多い時 (建物の改良など) は出さない
        boolean pipsFit = PANEL + 176 - v.maxLevel() * (PIP_W + 2) >= l6.x + 4;
        for (int k = 0; pipsFit && k < v.maxLevel(); k++) {
            char pip = k < v.level() ? PIP_ON : (k == v.level() ? PIP_NEXT : PIP_OFF);
            l6.stamp(pip, PIP_W, PANEL + 176 - (v.maxLevel() - k) * (PIP_W + 2));
        }

        // 行 8: 効果
        lines.get(8).moveTo(PANEL + 14).text("効果", AMBER_DIM).moveTo(PANEL + 40)
                .text(FontWidths.truncate(v.description() != null ? v.description() : "―", 136), INK);

        // 行 11: 必要な物
        Line l11 = lines.get(11);
        l11.moveTo(PANEL + 14).text("必要", AMBER_DIM).moveTo(PANEL + 40);
        if (maxed) {
            l11.text("―", INK);
        } else if (!v.hasRequirements() || v.cost() <= 0) {
            l11.text("なし (条件は未定)", INK);
        } else {
            boolean ok = v.money() >= v.cost();
            String have = String.format("%,d", v.money());
            l11.text(String.format("₽ %,d", v.cost()), INK).moveTo(PANEL + 160 - FontWidths.width(have)).text(have, ok ? GREEN : RED)
                    .moveTo(PANEL + 166).text(ok ? "✓" : "✕", ok ? GREEN : RED);
        }

        // 行 15: 上げられるか
        Line l15 = lines.get(15);
        l15.moveTo(PANEL + 14);
        if (v.blocking() != null) {
            l15.text(FontWidths.truncate(v.blocking(), 162), RED);
        } else {
            l15.text("強化できます", GREEN);
        }

        for (Line line : lines) line.moveTo(LINE_W);
        return lines;
    }

    private static Component join(List<Line> lines) {
        return Component.join(JoinConfiguration.newlines(), lines.stream().map(Line::build).toList());
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
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> handle(player, action));
    }

    private void handle(Player player, String action) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || !player.isOnline()) return;
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        if (data == null) return;
        switch (action) {
            case "prev", "next" -> {
                session.index = Math.floorMod(session.index + (action.equals("prev") ? -1 : 1), FACILITIES.length);
                if (camera.isActive(player)) camera.move(player, module.cameraPose(data, FACILITIES[session.index]));
            }
            case "upgrade" -> upgrade(player, data, session);
            case "close" -> {
                close(player);
                return;
            }
            default -> {
                return;
            }
        }
        player.playSound(player, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.3f);
        show(player, session);
    }

    private void upgrade(Player player, PlayerData data, Session session) {
        HideoutFacility facility = FACILITIES[session.index];
        EconomyModule economy = module.getPlugin().getServiceContainer().get(EconomyModule.class);
        long money = economy != null ? (long) economy.getBalance(player.getUniqueId()) : 0;
        String blocking = blocking(data, facility, money);
        if (blocking != null) {
            notice(session, Component.text(blocking, NamedTextColor.RED));
            return;
        }
        int next = data.getHideoutLevel(facility.id) + 1;
        long cost = facility.moneyCost(next);
        if (cost > 0 && (economy == null || !economy.withdraw(player.getUniqueId(), cost))) {
            notice(session, Component.text("所持金が足りません", NamedTextColor.RED));
            return;
        }
        try {
            module.upgrade(player, data, facility);
        } catch (IOException e) {
            module.getPlugin().getLogger().log(Level.WARNING, "隠れ家の設備を建てられませんでした", e);
            notice(session, Component.text("建築を反映できませんでした (次に入った時に建て直します)", NamedTextColor.RED));
            return;
        }
        notice(session, Component.text(facility.label + " を " + (next == 1 && facility != HideoutFacility.BASE ? "設置しました" : "Lv." + next + " にしました"),
                NamedTextColor.GREEN));
        if (camera.isActive(player)) camera.move(player, module.cameraPose(data, facility));
    }

    private static void notice(Session session, Component text) {
        session.notice = text;
        session.noticeUntil = Bukkit.getCurrentTick() + NOTICE_TICKS;
    }

    private void close(Player player) {
        sessions.remove(player.getUniqueId());
        camera.stop(player);
        player.closeDialog();
    }

    /** カメラから外れた (しゃがんで抜けた・別の所へ移された) 人は、画面を閉じて元に戻す */
    private void watch() {
        for (UUID id : sessions.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                sessions.remove(id);
                continue;
            }
            if (camera.lost(player)) close(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        camera.stop(event.getPlayer());
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
            if (n != 0) out.append(Component.text(spaces(n)).font(SPACE_FONT));
            x += n;
            max = Math.max(max, x);
            return this;
        }

        Line moveTo(int target) {
            return shift(target - x);
        }

        /** 絵の字を今の位置に描いて進む。click があれば押せる */
        Line glyph(char c, int width, ClickEvent click) {
            Component g = Component.text(String.valueOf(c)).font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
            if (click != null) g = g.clickEvent(click);
            out.append(g);
            x += width + 1;
            max = Math.max(max, x);
            return this;
        }

        /** 絵の字を at に描き、今の位置に戻る */
        Line stamp(char c, int width, int at) {
            int back = x;
            moveTo(at);
            glyph(c, width, null);
            return moveTo(back);
        }

        Line text(String s, TextColor color) {
            out.append(Component.text(s, color));
            x += FontWidths.width(s);
            max = Math.max(max, x);
            return this;
        }

        Component build() {
            return out.build();
        }
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
}

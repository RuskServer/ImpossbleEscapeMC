package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout;
import com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadoutGUI;
import com.lunar_prototype.impossbleEscapeMC.party.Party;
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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出撃画面。サーバーから送るダイアログ (1.21.6+) の本文に、リソースパックのフォント (iem:raid_dialog) で
 * 端末の絵 (地域の地形図) を描き、その上に地点の印と自動マッチのボタンを別の文字として重ねる。
 * 文字ごとにクリック (待機列への参加・取り消し、自動マッチ) とホバー (状況の吹き出し) が効く。
 * <p>
 * 絵の寸法・文字の番号・地点の位置は IEM_shader/tools/raid_dialog.py と同じにする。
 * ダイアログの作り (26.3 のクライアント):
 * <ul>
 *   <li>本文の文字は中央揃え・余白 4。高さは「9 x 行数 + 8」で決まるので、絵の高さは空行 (幅0の文字) で取る</li>
 *   <li>1080p で GUI スケールが自動 (4) だと本文の高さは 204 まで。端末は 320x192 に収めてある</li>
 *   <li>押した後も開いたまま (after_action NONE)。サーバーが状況の変わるたびに送り直す。閉じるボタンと Esc は
 *       サーバーに知らせる (それで閉じたことが分かる)</li>
 * </ul>
 * パーティではリーダーだけが待機列を操作できる。下のボタンで支給装備の画面を開ける (受け取るか戻ると、この画面に戻る)
 */
public class RaidSelectionGUI implements Listener {

    private static final Key FONT = Key.key("iem", "raid_dialog");
    private static final String KEY_NAMESPACE = "iem";
    private static final String CLOSE = "raid/close";
    private static final String AUTO_MATCH = "raid/auto";
    private static final String SUPPLY = "raid/supply";
    private static final String MAP_PREFIX = "raid/map_";

    // ---- 絵の寸法・文字の番号 (raid_dialog.py と同じ) ----
    private static final int PANEL_W = 320;
    private static final int COLS = 5;
    private static final int ROWS = 4;
    private static final int TILE_W = 64;
    private static final char TILE_BASE = '';
    private static final char MARKER_BASE = '';
    private static final char AUTO_CHAR = '';
    private static final char SPACE_POS = '';
    private static final char SPACE_NEG = '';
    private static final char SPACE_ZERO = '';
    private static final int MARKER_SIZE = 20;
    /** 地点の印の左端 (地点ごと。0: 薬品工場, 1: 沿岸工業地区, 2・3: 地図にまだ地点の無いマップ用の予備) */
    private static final int[] SITE_LEFT = {153, 207, 234, 261};
    private static final int AUTO_LEFT = 10;
    private static final int AUTO_W = 48;
    /** 本文の行数 (端末の高さ 192 を覆う: 9 x 21 + 8 = 197 <= 204) と幅 (端末 + 余白 + 折り返さないためのゆとり) */
    private static final int RESERVED_LINES = 21;
    private static final int BODY_WIDTH = PANEL_W + 4 * 2 + 2;

    /** マップの地点 (地図の絵で地点を描いた位置)。変える時は region_map.py と raid_dialog.py の SITES も合わせて描き直す */
    private static final Map<String, Integer> MAP_SITES = Map.of(
            "ノヴォザリエ薬品工場", 0,
            "ノヴォザリエ沿岸工業地区", 1);
    private static final int[] SPARE_SITES = {2, 3};

    /** 印の状態 (文字の並び順と同じ) */
    private enum State { OPEN, LATE_JOIN, LIVE, QUEUED }

    private static final TextColor AMBER = TextColor.color(0xDEAC4E);
    private static final TextColor[] STATE_COLORS = {AMBER, TextColor.color(0x78CD60), TextColor.color(0xD64A34)};
    private static final int REFRESH_TICKS = 20;
    /** 開いたまま放置された画面を送り直し続けない (閉じたことを知らせずに画面が消えた時の保険) */
    private static final int SESSION_TIMEOUT_TICKS = 20 * 60 * 20;
    /** 操作できなかった時の案内をタイトルに出す長さ */
    private static final int NOTICE_TICKS = 60;

    /** ダイアログを開いているプレイヤー。送ったマップの並び (押された地点の番号から引く) と、最後に送った内容 */
    private static final class Session {
        final int openedTick = Bukkit.getCurrentTick();
        List<String> mapIds = List.of();
        String lastSignature;
        String notice;
        int noticeUntilTick;
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static RaidModule module;
    private static BukkitTask refresher;

    public RaidSelectionGUI(RaidModule manager) {
        module = manager;
    }

    public void open(Player player) {
        // チェスト画面 (PDA など) から開いた時は、先に閉じる (ダイアログが画面を置き換えても、サーバーは開いたままとみなすため)
        player.closeInventory();
        Session session = new Session();
        SESSIONS.put(player.getUniqueId(), session);
        show(player, session, true);
        if (refresher == null || refresher.isCancelled()) {
            refresher = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), RaidSelectionGUI::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
        }
    }

    /** 開いている全員の画面を、状況 (待機人数・出撃までの時間など) が変わっていれば送り直す */
    private static void refreshAll() {
        int now = Bukkit.getCurrentTick();
        SESSIONS.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || now - entry.getValue().openedTick > SESSION_TIMEOUT_TICKS) return true;
            if (module.isInRaid(player)) {
                player.closeDialog();
                return true;
            }
            show(player, entry.getValue(), false);
            return false;
        });
        if (SESSIONS.isEmpty() && refresher != null) {
            refresher.cancel();
            refresher = null;
        }
    }

    private static void show(Player player, Session session, boolean force) {
        List<String> mapIds = module.getMapIds();
        String queued = module.getQueuedMap(player);
        int now = Bukkit.getCurrentTick();
        String notice = session.notice != null && now < session.noticeUntilTick ? session.notice : null;

        StringBuilder signature = new StringBuilder().append(queued).append('|').append(notice);
        List<MapStatus> statuses = new ArrayList<>();
        for (String id : mapIds) {
            MapStatus status = MapStatus.of(id, queued);
            statuses.add(status);
            signature.append('|').append(status.signature());
        }
        if (!force && signature.toString().equals(session.lastSignature)) return;
        session.lastSignature = signature.toString();
        session.mapIds = List.copyOf(mapIds);

        DialogBase base = DialogBase.builder(title(queued, statuses, notice))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(board(statuses), BODY_WIDTH)))
                .build();
        ActionButton supply = ActionButton.create(Component.text("支給装備"), supplyTooltip(player), 150,
                DialogAction.customClick(Key.key(KEY_NAMESPACE, SUPPLY), null));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150,
                DialogAction.customClick(Key.key(KEY_NAMESPACE, CLOSE), null));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.confirmation(supply, close))));
    }

    /** マップ1つの今の状況 */
    private record MapStatus(String id, State state, boolean queued, int players, int waiting, int timeLeft,
                             RaidConditions current, RaidConditions next) {

        static MapStatus of(String id, String queuedMap) {
            RaidInstance active = module.getActiveRaid(id);
            long age = (System.currentTimeMillis() - module.getMapLastStartTime(id)) / 1000;
            boolean lateJoinable = age < RaidModule.LATE_JOIN_WINDOW;
            State state = lateJoinable ? State.LATE_JOIN : active != null ? State.LIVE : State.OPEN;
            return new MapStatus(id, state, id.equals(queuedMap), active != null ? active.getPlayerCount() : 0,
                    module.getQueueCount(id), module.getMapTimeLeft(id), module.getConditions(id), module.getNextConditions(id));
        }

        String signature() {
            return id + ',' + state + ',' + queued + ',' + players + ',' + waiting + ',' + timeLeft
                    + ',' + (current != null ? current.describe() : "") + ',' + (next != null ? next.describe() : "");
        }
    }

    private static Component title(String queued, List<MapStatus> statuses, String notice) {
        TextComponent.Builder title = Component.text().content("出撃");
        if (queued != null) {
            int timeLeft = statuses.stream().filter(s -> s.id().equals(queued)).mapToInt(MapStatus::timeLeft).findFirst().orElse(0);
            title.append(Component.text("  ―  待機中: " + queued + "  " + formatTime(timeLeft), NamedTextColor.YELLOW));
        }
        if (notice != null) title.append(Component.text("  " + notice, NamedTextColor.RED));
        return title.build();
    }

    /**
     * 本文: 1行目に端末の絵 (タイル)、地点の印、自動マッチのボタンを並べ、残りの行で絵の高さを取る。
     * 絵は描くと「幅 + 1」進むので、タイルの後に -1 戻り、行の終わりで端末の幅だけ戻る。
     * 印やボタンは、置く位置まで進んで描き、行の頭まで戻る。最後に端末の幅だけ進めて、行の幅を端末の幅にする (中央揃えのため)
     */
    private static Component board(List<MapStatus> statuses) {
        TextComponent.Builder line = Component.text().font(FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
        StringBuilder tiles = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                tiles.append((char) (TILE_BASE + row * COLS + col)).append(shift(-1));
            }
            tiles.append(shift(-PANEL_W));
        }
        line.append(Component.text(tiles.toString()));

        int spare = 0;
        for (int i = 0; i < statuses.size(); i++) {
            MapStatus status = statuses.get(i);
            Integer site = MAP_SITES.get(status.id());
            if (site == null) {
                if (spare >= SPARE_SITES.length) continue;
                site = SPARE_SITES[spare++];
            }
            State shown = status.queued() ? State.QUEUED : status.state();
            char glyph = (char) (MARKER_BASE + site * 4 + shown.ordinal());
            line.append(Component.text(shift(SITE_LEFT[site])));
            line.append(Component.text(String.valueOf(glyph))
                    .hoverEvent(HoverEvent.showText(mapTooltip(status)))
                    .clickEvent(ClickEvent.custom(Key.key(KEY_NAMESPACE, MAP_PREFIX + i))));
            line.append(Component.text(shift(-(SITE_LEFT[site] + MARKER_SIZE + 1))));
        }

        line.append(Component.text(shift(AUTO_LEFT)));
        line.append(Component.text(String.valueOf(AUTO_CHAR))
                .hoverEvent(HoverEvent.showText(autoMatchTooltip()))
                .clickEvent(ClickEvent.custom(Key.key(KEY_NAMESPACE, AUTO_MATCH))));
        line.append(Component.text(shift(-(AUTO_LEFT + AUTO_W + 1))));
        line.append(Component.text(shift(PANEL_W)));

        // 絵の高さぶんの空行 (幅0の文字で、行として数えさせる)
        line.append(Component.text(("\n" + SPACE_ZERO).repeat(RESERVED_LINES - 1)));
        return line.build();
    }

    /** n だけ進む (負なら戻る) 空白の文字列。±2^i の空白を組み合わせる */
    private static String shift(int n) {
        StringBuilder out = new StringBuilder();
        int abs = Math.abs(n);
        char base = n >= 0 ? SPACE_POS : SPACE_NEG;
        for (int bit = 0; abs > 0; bit++, abs >>= 1) {
            if ((abs & 1) != 0) out.append((char) (base + bit));
        }
        return out.toString();
    }

    private static Component mapTooltip(MapStatus status) {
        TextColor color = STATE_COLORS[status.state().ordinal()];
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(status.id(), status.queued() ? NamedTextColor.GOLD : NamedTextColor.WHITE, TextDecoration.BOLD));
        lines.add(Component.text(switch (status.state()) {
            case LATE_JOIN -> "進行中 (途中参加可能)";
            case LIVE -> "進行中 (レイド中)";
            default -> "待機中 (空きあり)";
        }, color));
        if (status.state() != State.OPEN) lines.add(line("参加人数", status.players() + "名"));
        lines.add(line("待機人数", status.waiting() + "名"));
        lines.add(line("出撃まで", formatTime(status.timeLeft())));
        if (status.state() == State.LATE_JOIN && status.current() != null) lines.add(line("現在の現地", status.current().describe()));
        if (status.next() != null) lines.add(line("次の出撃", status.next().describe()));
        lines.add(Component.empty());
        if (status.queued()) {
            lines.add(Component.text("▶ 出撃待機中 (クリックで取り消し)", NamedTextColor.YELLOW));
        } else if (status.state() == State.LATE_JOIN) {
            lines.add(Component.text("▶ クリックで途中参加", NamedTextColor.YELLOW));
        } else {
            lines.add(Component.text("▶ クリックで出撃待機列に参加", NamedTextColor.YELLOW));
        }
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
    }

    private static Component autoMatchTooltip() {
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),
                Component.text("オートマッチング", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("最適なレイドを自動で選んで出撃します", NamedTextColor.GRAY),
                Component.text("- 途中参加できるレイドを優先", NamedTextColor.DARK_GRAY),
                Component.text("- なければ開始が最も近いマップ", NamedTextColor.DARK_GRAY),
                Component.empty(),
                Component.text("▶ クリックでマッチング開始", NamedTextColor.YELLOW));
    }

    /** 支給装備のボタンの吹き出し (次に受け取れるまでの時間) */
    private static Component supplyTooltip(Player player) {
        var dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        long remaining = data != null ? SupplyLoadout.remainingCooldown(data) : 0;
        return Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(),
                Component.text("装備が無くても出撃できる、無料の最低限の装備", NamedTextColor.GRAY),
                Component.text("売値は 0₽ (処分はできる)・納品はできません", NamedTextColor.GRAY),
                remaining > 0 ? Component.text("次に受け取れるまで " + SupplyLoadout.formatDuration(remaining), NamedTextColor.YELLOW)
                        : Component.text("今すぐ受け取れます", NamedTextColor.GREEN));
    }

    private static Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    // ---- 操作 ----

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!KEY_NAMESPACE.equals(id.namespace()) || !id.value().startsWith("raid/")) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        String action = id.value();
        // 届いたスレッドに関わらず、待機列の操作はメインスレッドで行う
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> handle(player, action));
    }

    private void handle(Player player, String action) {
        if (!player.isOnline()) return;
        if (CLOSE.equals(action)) {
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            return;
        }
        Session session = SESSIONS.get(player.getUniqueId());
        if (session == null) return;
        if (SUPPLY.equals(action)) {
            // チェスト画面を開くとダイアログは置き換わる (onInventoryOpen で送り直しを止める)
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new SupplyLoadoutGUI(player).open();
            return;
        }
        if (AUTO_MATCH.equals(action)) {
            SESSIONS.remove(player.getUniqueId());
            player.closeDialog();
            module.autoMatch(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            return;
        }
        if (!action.startsWith(MAP_PREFIX)) return;
        int index;
        try {
            index = Integer.parseInt(action.substring(MAP_PREFIX.length()));
        } catch (NumberFormatException e) {
            return;
        }
        if (index < 0 || index >= session.mapIds.size()) return;
        String mapId = session.mapIds.get(index);

        Party party = ImpossbleEscapeMC.getInstance().getPartyManager().getParty(player.getUniqueId());
        if (party != null && !party.isLeader(player.getUniqueId())) {
            session.notice = "パーティリーダーのみが待機列を操作できます";
            session.noticeUntilTick = Bukkit.getCurrentTick() + NOTICE_TICKS;
            show(player, session, true);
            return;
        }
        List<Player> members = new ArrayList<>();
        if (party != null) {
            for (UUID member : party.getMembers()) {
                Player p = Bukkit.getPlayer(member);
                if (p != null) members.add(p);
            }
        } else {
            members.add(player);
        }
        if (mapId.equals(module.getQueuedMap(player))) {
            members.forEach(module::leaveQueue);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.5f);
        } else {
            // パーティーはまとめて (途中参加なら同じ所に出る。1 人でも出撃できなければ誰も入らない)
            boolean joined = module.joinQueue(members, mapId);
            if (joined) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            } else {
                // 理由 (容量オーバーなど) はチャットに出ているが、ダイアログの間は見えないのでタイトルで知らせる
                session.notice = "出撃できません (理由はチャット)";
                session.noticeUntilTick = Bukkit.getCurrentTick() + NOTICE_TICKS;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            }
        }
        show(player, session, true);
    }

    /** 別の画面 (チェスト画面) が開いた: ダイアログは置き換えられたので送り直さない */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        SESSIONS.remove(event.getPlayer().getUniqueId());
    }
}

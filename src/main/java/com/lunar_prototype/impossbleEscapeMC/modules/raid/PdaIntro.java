package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import io.papermc.paper.math.Angle;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 出撃時の PDA の演出: 暗転 → 電源 → 起動ログ → 戦術マップ (脱出地点) → 閉じる → 顔を上げる → 両手で持った PDA をしまう。
 * <ul>
 *   <li>筐体・液晶・電源と閉じる演出はポストエフェクト ({@link ScreenEffectService#playPdaIntro}、iem:post/pda)</li>
 *   <li>液晶の中身はボスバーの題名。1本目に絵と英数字 (フォント iem:pda、幅が分かる)、2本目からマップ名と脱出地点の名前
 *       (幅が分からない日本語は1本ずつ中央揃え)。色に置き場所を入れ、文字のシェーダー (text.vsh) が液晶の上へ置く</li>
 *   <li>演出の間は手に戦術マップ (地図) を持たせておく。手は画面の効果の下に描かれるので見えず、液晶が消えた時に
 *       両手で PDA を構えた姿になる。最後に元の欄に戻すと、持ち替えの動きで PDA が下がる</li>
 *   <li>閉じる時に PDA を見下ろす向き ({@link #LOOK_DOWN_PITCH}) にし、見終わったら水平まで顔を上げる。上げる間は
 *       レイド開始の演出の弱い段が抜けていく。向きは相対の回転のパケットを細かく送って動かす (マウスの操作は足される)</li>
 *   <li>演出の間は動けず、撃てず、ダメージも受けない</li>
 * </ul>
 * 座標は「設計の単位」(画面の高さ = 270、横は中央が 240)。筐体・液晶の位置は iem:post/pda と tools/raid_pda.py と同じ
 */
public final class PdaIntro implements Listener {

    private static final Key FONT = Key.key("iem", "pda");
    // ---- フォント iem:pda の文字 (raid_pda.py と同じ) ----
    private static final char CELL_BASE = '';
    /** セルの記号 (raid_pda.py の CELLS・{@link PdaTerrain} の記号と同じ順) */
    private static final String CELLS = "#B=|+.:,~Tx^ ";
    private static final char MARKER_RING = '', MARKER_CROSS = '', MARKER_PLAYER = '', MARKER_RING_SMALL = '';
    private static final char MARKER_PLAYER_UNDER = '';
    private static final char WORD_AVAILABLE = '', WORD_CLOSED = '', WORD_HERE = '', WORD_UNDER = '';
    private static final int WORD_AVAILABLE_W = 24, WORD_CLOSED_W = 18, WORD_HERE_W = 24, WORD_UNDER_W = 18;
    private static final char SPACE_POS = '', SPACE_NEG = '';
    private static final int CHAR_W = 6, CELL_W = 6, CELL_H = 8, MARKER_W = 8;

    // ---- 色番号 (text.vsh の PALETTE と同じ) ----
    private static final int AMBER = 0, AMBER_DIM = 1, AMBER_DARK = 2, GREEN = 3, RED = 4, WHITE = 5;

    // ---- 液晶の中の配置 (設計の単位。y は 4 の倍数) ----
    private static final int LINE_WIDTH = 480;
    private static final int HEADER_Y = 20;
    private static final int MAP_X = 48, MAP_Y = 36, MAP_COLS = 33, MAP_ROWS = 19;
    private static final int CARD_X = 252, CARD_Y = 36, CARD_STEP = 60, CARD_COLS = 30, MAX_CARDS = 3;
    private static final int LOG_X = 50, LOG_Y = 36, LOG_STEP = 12;

    private static final int MAP_SLOT = RaidMapManager.MAP_SLOT;
    /** 閉じる時の向き (下向き)。両手で持った地図 (PDA) が画面の真ん中に来る */
    private static final float LOOK_DOWN_PITCH = 71f;
    /** 顔を上げる間、向きを送る間隔 (ms)。tick (50ms) ごとだとカクつくので、フレームに近い細かさで送る */
    private static final long RISE_PERIOD_MS = 10;
    private static final ScheduledExecutorService RISE = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "IEM-PdaIntro-rise");
        thread.setDaemon(true);
        return thread;
    });
    /** 演出が別の演出に置き換わった・止まった時の打ち切り (tick) */
    private static final int TIMEOUT_TICKS = 20 * 30;

    private static final String[] BOOT_LOG = {
            "KPK-3M FIELD TERMINAL   BIOS 2.31",
            "MEM CHECK .............. 4096K OK",
            "GPS .................... LOCK (7 SAT)",
            "MESH LINK .............. OK",
            "EXFIL DATA ............. RECV",
            "DECRYPT ................ OK",
            "> LAUNCH TACMAP"};

    private enum Phase { WAITING, BOOT, TACMAP, CLOSING, RISE }

    /** 1人分の演出 */
    private static final class Session {
        final UUID playerId;
        final RaidMap map;
        final List<RaidMap.ExtractionPoint> active;
        final int timeLeft;
        final Location spawn;
        final int previousSlot;
        final Runnable onDone;
        final int startTick = Bukkit.getCurrentTick();
        Phase phase = Phase.WAITING;
        int phaseTick;
        BossBar art;
        final List<BossBar> labels = new ArrayList<>();
        /** 顔を上げる向きを送る仕事 (送り終えたら自分で止まる。途中で抜けた時は止める) */
        volatile ScheduledFuture<?> rise;

        Session(Player player, RaidMap map, List<RaidMap.ExtractionPoint> active, int timeLeft, Runnable onDone) {
            this.playerId = player.getUniqueId();
            this.map = map;
            this.active = List.copyOf(active);
            this.timeLeft = timeLeft;
            this.spawn = player.getLocation().clone();
            this.previousSlot = player.getInventory().getHeldItemSlot();
            this.onDone = onDone;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static BukkitTask ticker;

    /**
     * 演出を始める。終わったら (しまい終えたら) onDone を呼ぶ
     *
     * @param active 今回のレイドで使える脱出地点
     */
    static void start(Player player, RaidMap map, List<RaidMap.ExtractionPoint> active, int timeLeft, Runnable onDone) {
        finish(player.getUniqueId(), false);
        PdaTerrain.prepare(map, MAP_COLS, MAP_ROWS);
        Session session = new Session(player, map, active, timeLeft, onDone);
        SESSIONS.put(player.getUniqueId(), session);
        // 戦術マップ (地図) を持たせておく (画面の効果の下で手が上がり、液晶が消えると両手で構えた姿になる)
        ItemStack held = player.getInventory().getItem(MAP_SLOT);
        if (held != null && held.getType() == Material.FILLED_MAP) player.getInventory().setHeldItemSlot(MAP_SLOT);

        UUID id = player.getUniqueId();
        ScreenEffectService.playPdaIntro(player,
                () -> sound(id, "pda.switch"),
                () -> sound(id, "pda.power_on"),
                () -> enter(id, Phase.BOOT),
                () -> enter(id, Phase.TACMAP),
                () -> enter(id, Phase.CLOSING),
                () -> enter(id, Phase.RISE),
                () -> finish(id, true));
        if (ticker == null || ticker.isCancelled()) {
            ticker = Bukkit.getScheduler().runTaskTimer(ImpossbleEscapeMC.getInstance(), PdaIntro::tickAll, 1L, 1L);
        }
    }

    static boolean isPlaying(Player player) {
        return SESSIONS.containsKey(player.getUniqueId());
    }

    private static void enter(UUID id, Phase phase) {
        Session session = SESSIONS.get(id);
        Player player = Bukkit.getPlayer(id);
        if (session == null || player == null) return;
        session.phase = phase;
        session.phaseTick = 0;
        switch (phase) {
            case BOOT -> {
                session.art = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                player.showBossBar(session.art);
                sound(id, "pda.boot");
            }
            case TACMAP -> {
                sound(id, "pda.tacmap");
                showLabels(player, session);
            }
            case CLOSING -> {
                hideBars(player, session);
                sound(id, "pda.power_off");
                // 液晶が消えた時に、手元の PDA を見下ろしている
                player.setRotation(Angle.relative(0f), Angle.absolute(LOOK_DOWN_PITCH));
            }
            case RISE -> startRise(player, session);
            default -> {
            }
        }
        render(session);
    }

    /**
     * PDA の効果音 (リソースパックの iem:pda.*。IEM_shader/tools/pda_sfx.py)。本人にだけ聞こえる
     */
    private static void sound(UUID id, String name) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) player.playSound(player, "iem:" + name, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void tickAll() {
        int now = Bukkit.getCurrentTick();
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null) {
                SESSIONS.remove(session.playerId);
                continue;
            }
            // 演出が置き換わった (別の画面効果が始まった)・長すぎる時は、ここで終える
            boolean playing = ScreenEffectService.isPlaying(player, ScreenEffectService.PDA_INTRO);
            if ((!playing && now - session.startTick > 2) || now - session.startTick > TIMEOUT_TICKS) {
                finish(session.playerId, true);
                continue;
            }
            session.phaseTick++;
            if (session.phase == Phase.BOOT || (session.phase == Phase.TACMAP && session.phaseTick % 5 == 0)) render(session);
        }
        if (SESSIONS.isEmpty() && ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    /** 今の向きから水平まで、ゆっくり始まってゆっくり止まるように顔を上げる (顔を上げる段の長さで) */
    private static void startRise(Player player, Session session) {
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
        float total = -player.getLocation().getPitch();
        long start = System.nanoTime();
        long duration = TimeUnit.MILLISECONDS.toNanos(ScreenEffectService.PDA_RISE_TICKS * 50L);
        float[] sent = {0f};
        session.rise = RISE.scheduleAtFixedRate(() -> {
            double t = Math.min(1.0, (System.nanoTime() - start) / (double) duration);
            float target = (float) (total * t * t * (3 - 2 * t));
            float delta = target - sent[0];
            if (Math.abs(delta) >= 1e-3f) {
                connection.send(new ClientboundPlayerRotationPacket(0f, true, delta, true));
                sent[0] = target;
            }
            if (t >= 1.0 && session.rise != null) session.rise.cancel(false);
        }, 0, RISE_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /** 演出を終える。restore なら手に持つ欄を戻し (持ち替えの動きで PDA が下がる)、終わった知らせを呼ぶ */
    private static void finish(UUID id, boolean restore) {
        Session session = SESSIONS.remove(id);
        if (session == null) return;
        // しまい終えた時は、顔を上げ終えるまで送らせる (段の時間と実時間は少しずれる)
        if (!restore && session.rise != null) session.rise.cancel(false);
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            hideBars(player, session);
            if (restore && player.getInventory().getHeldItemSlot() == MAP_SLOT && session.previousSlot != MAP_SLOT) {
                player.getInventory().setHeldItemSlot(session.previousSlot);
            }
            if (restore) sound(id, "pda.stow");
        }
        if (restore) session.onDone.run();
    }

    private static void hideBars(Player player, Session session) {
        if (session.art != null) player.hideBossBar(session.art);
        session.art = null;
        session.labels.forEach(player::hideBossBar);
        session.labels.clear();
    }

    // ---- 液晶の中身 ----

    private static void render(Session session) {
        if (session.art == null) return;
        Line line = new Line();
        switch (session.phase) {
            case BOOT -> bootLog(line, session.phaseTick);
            case TACMAP -> tacmap(line, session);
            default -> {
            }
        }
        line.moveTo(LINE_WIDTH);
        Component title = line.build();
        if (!title.equals(session.art.name())) session.art.name(title);
    }

    /** 起動ログ: 1 tick ごとに文字を打ち出す */
    private static void bootLog(Line line, int tick) {
        int budget = tick * 12;
        for (int i = 0; i < BOOT_LOG.length && budget > 0; i++) {
            String text = BOOT_LOG[i].substring(0, Math.min(BOOT_LOG[i].length(), budget));
            budget -= BOOT_LOG[i].length();
            boolean last = i == BOOT_LOG.length - 1 || budget <= 0;
            line.text(LOG_X, LOG_Y + i * LOG_STEP, text + (last && tick % 6 < 3 ? "_" : ""), last ? WHITE : AMBER);
        }
    }

    private static void tacmap(Line line, Session session) {
        boolean blink = (session.phaseTick / 5) % 2 == 0;
        // 上の帯 (マップ名は名前のボスバー)
        line.text(MAP_X + 2, HEADER_Y, "EXFIL // TACMAP", AMBER);
        String timer = String.format("RAID %02d:%02d", session.timeLeft / 60, session.timeLeft % 60);
        line.text(432 - timer.length() * CHAR_W, HEADER_Y, timer, AMBER);
        line.text(MAP_X, HEADER_Y + 8, "-".repeat(64), AMBER_DARK);

        // 戦術マップ
        PdaTerrain.Grid grid = PdaTerrain.tactical(session.map, MAP_COLS, MAP_ROWS);
        if (grid != null) {
            for (int r = 0; r < grid.rows(); r++) {
                for (int c = 0; c < grid.cols(); c++) {
                    int symbol = CELLS.indexOf(grid.cells()[r][c]);
                    if (symbol < 0 || grid.cells()[r][c] == ' ') continue;
                    line.glyph(MAP_X + c * CELL_W, MAP_Y + r * CELL_H, (char) (CELL_BASE + symbol), CELL_W, AMBER_DIM);
                }
            }
        } else {
            line.text(MAP_X + 60, MAP_Y + 72, "NO TERRAIN DATA", AMBER_DARK);
        }
        String world = session.map.getWorldName();
        List<RaidMap.ExtractionPoint> points = session.map.getExtractionPoints();
        for (int i = 0; i < points.size(); i++) {
            RaidMap.ExtractionPoint point = points.get(i);
            Location at = point.getLocation(world);
            if (at == null || grid == null) continue;
            boolean ok = session.active.contains(point);
            int[] xy = toMap(grid, at);
            char marker = ok ? (blink ? MARKER_RING : MARKER_RING_SMALL) : MARKER_CROSS;
            line.glyph(xy[0] - MARKER_W / 2, xy[1] - MARKER_W / 2, marker, MARKER_W, ok ? GREEN : RED);
            line.text(xy[0] + 6, xy[1] - MARKER_W / 2, "EX" + (i + 1), ok ? GREEN : RED);
        }
        if (grid != null) {
            // 地下なら中抜きの矢印にし、「現在地」の下に深さを出す (地図は地上なので、真上の建物の中ではないと分かるように)
            int[] me = toMap(grid, session.spawn);
            int depth = PdaTerrain.depthBelowGround(session.spawn);
            String meters = depth + "m";
            int width = depth > 0 ? Math.max(WORD_HERE_W, WORD_UNDER_W + 3 + meters.length() * CHAR_W) : WORD_HERE_W;
            // 地図の右端からはみ出す時は印の左に出す
            int labelX = me[0] + 6 + width <= MAP_X + MAP_COLS * CELL_W ? me[0] + 6 : me[0] - 6 - width;
            line.glyph(me[0] - MARKER_W / 2, me[1] - MARKER_W / 2, depth > 0 ? MARKER_PLAYER_UNDER : MARKER_PLAYER, MARKER_W, WHITE);
            line.glyph(labelX, me[1] - MARKER_W / 2, WORD_HERE, WORD_HERE_W, WHITE);
            if (depth > 0) {
                line.glyph(labelX, me[1] + 4, WORD_UNDER, WORD_UNDER_W, AMBER);
                line.text(labelX + WORD_UNDER_W + 3, me[1] + 4, meters, AMBER);
            }
        }

        // 脱出地点のカード (使えるものを先に)
        List<RaidMap.ExtractionPoint> cards = cardOrder(session);
        for (int k = 0; k < cards.size(); k++) {
            RaidMap.ExtractionPoint point = cards.get(k);
            boolean ok = session.active.contains(point);
            int y = CARD_Y + k * CARD_STEP;
            int index = points.indexOf(point) + 1;
            line.text(CARD_X, y, "+" + "-".repeat(CARD_COLS - 2) + "+", ok ? AMBER_DIM : AMBER_DARK);
            PdaTerrain.Grid vignette = PdaTerrain.vignette(session.map, point);
            if (vignette != null) {
                for (int r = 0; r < vignette.rows(); r++) {
                    for (int c = 0; c < vignette.cols(); c++) {
                        int symbol = CELLS.indexOf(vignette.cells()[r][c]);
                        if (symbol < 0 || vignette.cells()[r][c] == ' ') continue;
                        line.glyph(CARD_X + 6 + c * CELL_W, y + 4 + r * CELL_H, (char) (CELL_BASE + symbol), CELL_W, ok ? AMBER : AMBER_DARK);
                    }
                }
                line.glyph(CARD_X + 6 + 14 * CELL_W - MARKER_W / 2, y + 4 + 2 * CELL_H - MARKER_W / 2,
                        ok ? MARKER_RING : MARKER_CROSS, MARKER_W, ok ? GREEN : RED);
            }
            line.text(CARD_X + 6, y + 40, "EX" + index, ok ? GREEN : RED);
            line.glyph(CARD_X + 6, y + 48, ok ? WORD_AVAILABLE : WORD_CLOSED, ok ? WORD_AVAILABLE_W : WORD_CLOSED_W, ok ? GREEN : RED);
            Location at = point.getLocation(world);
            if (at != null && at.getWorld() == session.spawn.getWorld()) {
                String distance = direction(session.spawn, at) + " " + Math.round(session.spawn.distance(at)) + "m";
                line.text(CARD_X + CARD_COLS * CHAR_W - 6 - distance.length() * CHAR_W, y + 48, distance, AMBER_DIM);
            }
            line.text(CARD_X, y + 56, "+" + "-".repeat(CARD_COLS - 2) + "+", ok ? AMBER_DIM : AMBER_DARK);
        }
    }

    /** 名前のボスバー: マップ名と、カードの脱出地点の名前 (幅が分からないので1本ずつ中央揃え) */
    private static void showLabels(Player player, Session session) {
        addLabel(player, session, session.map.getMapId(), 240, HEADER_Y);
        List<RaidMap.ExtractionPoint> cards = cardOrder(session);
        for (int k = 0; k < cards.size(); k++) {
            addLabel(player, session, cards.get(k).getName(), CARD_X + CARD_COLS * CHAR_W / 2 + 10, CARD_Y + k * CARD_STEP + 40);
        }
    }

    private static void addLabel(Player player, Session session, String text, int centerX, int top) {
        int bar = session.labels.size() + 1;   // 1本目は絵のボスバー
        TextColor code = TextColor.color(245 + bar, Math.min(240, centerX / 2), Math.min(135, top / 2));
        BossBar label = BossBar.bossBar(Component.text(text).color(code).shadowColor(ShadowColor.none()),
                0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        player.showBossBar(label);
        session.labels.add(label);
    }

    private static List<RaidMap.ExtractionPoint> cardOrder(Session session) {
        List<RaidMap.ExtractionPoint> all = new ArrayList<>(session.map.getExtractionPoints());
        all.sort(Comparator.comparing((RaidMap.ExtractionPoint p) -> !session.active.contains(p)));
        return all.subList(0, Math.min(MAX_CARDS, all.size()));
    }

    private static int[] toMap(PdaTerrain.Grid grid, Location at) {
        int x = (int) Math.round(MAP_X + (at.getX() - grid.minX()) / grid.blocksPerCellX() * CELL_W);
        int y = (int) Math.round(MAP_Y + (at.getZ() - grid.minZ()) / grid.blocksPerCellZ() * CELL_H);
        x = Math.max(MAP_X, Math.min(MAP_X + MAP_COLS * CELL_W - MARKER_W, x));
        y = Math.max(MAP_Y + 4, Math.min(MAP_Y + MAP_ROWS * CELL_H - 4, y));
        return new int[]{x, y};
    }

    /** from から to の方角 (8方位) */
    private static String direction(Location from, Location to) {
        double angle = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.floorMod(Math.round(angle / 45.0), 8)];
    }

    /**
     * 絵のボスバーの題名を組み立てる。文字を設計の単位の位置に並べ (横は空白の文字、縦は色に入れる)、
     * 色に縦の位置と色番号を入れる (245, 160 + 上端/4, 240 + 色番号)
     */
    private static final class Line {
        private final List<Component> parts = new ArrayList<>();
        private int cursor;

        /** 絵の文字1つ (幅 width、描くと width + 1 進む) */
        void glyph(int x, int y, char glyph, int width, int color) {
            moveTo(x);
            parts.add(Component.text(String.valueOf(glyph)).color(code(y, color)));
            cursor += width + 1;
        }

        /** 英数字 (等幅、1文字 6 進む) */
        void text(int x, int y, String text, int color) {
            if (text.isEmpty()) return;
            moveTo(x);
            parts.add(Component.text(text).color(code(y, color)));
            cursor += text.length() * CHAR_W;
        }

        void moveTo(int x) {
            int delta = x - cursor;
            if (delta == 0) return;
            StringBuilder spaces = new StringBuilder();
            char base = delta > 0 ? SPACE_POS : SPACE_NEG;
            int n = Math.abs(delta);
            for (int i = 9; i >= 0; i--) {
                while (n >= (1 << i)) {
                    spaces.append((char) (base + i));
                    n -= 1 << i;
                }
            }
            parts.add(Component.text(spaces.toString()));
            cursor = x;
        }

        private static TextColor code(int y, int color) {
            return TextColor.color(245, 160 + Math.max(0, Math.min(63, y / 4)), 240 + color);
        }

        Component build() {
            TextComponent.Builder builder = Component.text().font(FONT).shadowColor(ShadowColor.none());
            parts.forEach(builder::append);
            return builder.build();
        }
    }

    // ---- 演出の間の操作の停止 ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isPlaying(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isPlaying(event.getPlayer()) || event.getTo() == null) return;
        // 向きは変えてよい (PDA の画面は向きに関係なく出る)。場所だけ戻す
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ()) {
            Location back = event.getFrom().clone();
            back.setYaw(event.getTo().getYaw());
            back.setPitch(event.getTo().getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isPlaying(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer().getUniqueId(), false);
    }
}

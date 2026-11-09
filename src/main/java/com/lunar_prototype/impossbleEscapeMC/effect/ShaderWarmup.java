package com.lunar_prototype.impossbleEscapeMC.effect;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.text.JoinConfiguration;
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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.line;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.AMBER_DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.DIM;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.INK;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NAME;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalPalette.NOTE;

/**
 * 参加した時の、ポストエフェクトの先読み (コンパイル)。
 * <p>
 * ポストエフェクトのシェーダーは、初めて使われるフレームでクライアントの描画のスレッドを止めて組み立てられる
 * (重いものは 0.5 秒前後止まる。調べた結果は {@link ShaderWarmDiagnostic})。ゲーム中に初めて出ると画面が固まるので、
 * 参加した直後に、画面を真っ黒にして (画面の後ろに隠して) 重いエフェクトを 1 つずつ重ねて先に組み立てておく。
 * その間、読み込み画面のようなダイアログ (進み具合の棒と「スキップ」) を出す。
 * <p>
 * 始める条件は 2 つ。ワールドの読み込み完了 (PLAYER_LOADED) と、リソースパックの読み込み完了
 * (クライアントはリロードがすべて終わってから SUCCESSFULLY_LOADED を返す)。パックを断った・失敗した人にはやらない。
 * 1 つのエフェクトの組み立てが終わったことは、ping の返事で分かる。重ねた次の tick から ping を 2 回送ると、
 * 組み立てで止まっている間は返事が返らない。両方の返事が来たら、止まりが終わっている。
 * 返事が来ない時のために、1 つあたりのタイムアウトがある。
 * <p>
 * 設定: {@code shader-warmup.enabled} (既定 true)。先読みするエフェクトは、リソースの shaderwarm_warmup.txt
 */
public final class ShaderWarmup implements Listener {

    private static final String NAMESPACE = "iem";
    private static final String CLICK_NS = "iem";
    private static final String CLICK_SKIP = "warmup/skip";
    /** ping の id の範囲 (ShaderWarmDiagnostic とも他のパケットとも混ざらない範囲) */
    private static final int PING_ID_BASE = 0x5E7B0000;
    private static final int PING_ID_RANGE = 0x10000;
    /** ダイアログを出してから、画面を黒くするまで */
    private static final int START_DELAY_TICKS = 10;
    /** 1 つのエフェクトの待ちのタイムアウト (重いもので 1.3 秒。遅い PC を見込んで 8 秒) */
    private static final int STEP_TIMEOUT_TICKS = 160;
    /** エフェクトを重ねてから ping を送る tick (重ねた次の tick から。同じ tick の ping は止まる前に返ってしまう) */
    private static final int PING_FIRST_TICK = 1;
    private static final int PING_SECOND_TICK = 2;
    /** 次のエフェクトまでの間 */
    private static final int GAP_TICKS = 2;
    /** 最後に 100% を見せてから閉じるまで */
    private static final int FINISH_HOLD_TICKS = 12;
    /** パックの読み込み完了を待つ最大の tick (これを過ぎたらやらない) */
    private static final int PACK_WAIT_TIMEOUT_TICKS = 20 * 60 * 5;
    // ---- 画面の寸法 (GUI の画素)。リソースパックのフォント iem:loading (tools/loading_font.py・font_common.Loading) と同じにする ----
    static final Key LOADING_FONT = Key.key("iem", "loading");
    private static final char CARD_BASE = '\uE300';
    private static final int CARD_TILES = 3, CARD_TILE_W = 108;
    private static final char SEG_OFF = '\uE310', SEG_ON = '\uE311', SEG_HEAD_0 = '\uE312', SEG_HEAD_1 = '\uE313';
    private static final int SEG_W = 7, SEGMENTS = 34;
    private static final char SPIN_BASE = '\uE320';
    private static final int SPIN_W = 7, SPIN_FRAMES = 8;
    /** カードの幅 (108 x 3)。行の幅は、タイルの最後の字の送り (+ 1) が入るよう 2 広い。本文の要素の幅は行の幅 + 余白 8 */
    private static final int CARD_W = CARD_TILES * CARD_TILE_W;
    static final int LINE_W = CARD_W + 2;
    static final int BODY_W = LINE_W + 8;
    /** カードの左右の余白 (字・棒の左端と右端) */
    private static final int PAD = 10;
    /** 本文の行数 (カードは 1 行目の 4 上から 72 画素。最後に空行を足して、要素の高さがカードを超えるようにする) */
    private static final int LINES = 8;
    /** カードの上の空行 (カードを縦の真ん中に置く) */
    private static final int TOP_PAD_LINES = 7;

    private static ShaderWarmup instance;

    private final ImpossbleEscapeMC plugin;
    private final List<String> names;
    private final List<Key> effects;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Map<Integer, PingInfo> pings = new ConcurrentHashMap<>();
    private int nextPingId = 0;
    private PongListener listener;

    private record PingInfo(Run run, int step) {
    }

    /** 参加してから、始める条件がそろうまでの状態 */
    private static final class Pending {
        boolean worldLoaded;
        boolean packLoaded;
        BukkitTask timeout;
    }

    private ShaderWarmup(ImpossbleEscapeMC plugin, List<String> names) {
        this.plugin = plugin;
        this.names = names;
        this.effects = names.stream().map(n -> Key.key(NAMESPACE, n)).toList();
    }

    public static void init(ImpossbleEscapeMC plugin) {
        instance = new ShaderWarmup(plugin, loadNames(plugin));
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    public static void shutdown() {
        if (instance == null) return;
        for (Run run : new ArrayList<>(instance.runs.values())) run.finish(false);
        for (Pending p : instance.pending.values()) if (p.timeout != null) p.timeout.cancel();
        instance.pending.clear();
        instance.removeListener();
        instance = null;
    }

    /** 先読みの最中か (最中は、ロアのムービーなどの演出を始めない) */
    public static boolean isActive(UUID playerId) {
        return instance != null && (instance.runs.containsKey(playerId));
    }

    /** ワールドの読み込みが終わった時 (参加時以外のワールド移動・リスポーンでも呼ばれる。参加の待ちがある時だけ扱う) */
    public static void onWorldLoaded(Player player) {
        if (instance == null) return;
        Pending p = instance.pending.get(player.getUniqueId());
        if (p == null) return;
        p.worldLoaded = true;
        instance.tryStart(player);
    }

    // ---- 一覧の読み込み (サーバーなしで確かめられる) ----

    /** 行の一覧から、エフェクト名 (リソースパックの post_effect のファイル名) を取り出す。空行と # から始まる行は除く。小文字・数字・_ だけ */
    static List<String> parseEffectNames(List<String> lines) {
        List<String> names = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!line.matches("[a-z0-9_]+")) throw new IllegalArgumentException("エフェクト名が正しくありません: " + line);
            if (names.contains(line)) throw new IllegalArgumentException("エフェクト名が重複しています: " + line);
            names.add(line);
        }
        return names;
    }

    /** 棒の点灯する目盛りの数 (0..SEGMENTS)。終わった段 / 全体 */
    static int litSegments(int done, int total) {
        if (total <= 0) return SEGMENTS;
        return Math.max(0, Math.min(SEGMENTS, (int) Math.round((double) done / total * SEGMENTS)));
    }

    private static List<String> loadNames(ImpossbleEscapeMC plugin) {
        List<String> lines = new ArrayList<>();
        try (InputStream in = plugin.getResource("shaderwarm_warmup.txt")) {
            if (in == null) return List.of();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
            return parseEffectNames(lines);
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().warning("shaderwarm_warmup.txt を読めません: " + e.getMessage());
            return List.of();
        }
    }

    // ---- 画面 (サーバーなしで組み立てられる。ShaderWarmupLayoutTest が幅を確かめ、プレビューの絵にする) ----

    /**
     * 画面に出す値。done = 終わった段の数 (0..total)、total = 全体の段の数、id = 今重ねているエフェクトの名前 (英語)、
     * frame = 段の番号 (回る点と、棒の先頭の脈打ちを 1 コマ進める)
     */
    record View(int done, int total, String id, int frame) {
    }

    /** カードの上に字を重ねた本文の行 (どの行も LINE_W にそろえる) */
    static List<DialogLine> bodyLines(View v) {
        int percent = v.total() <= 0 ? 100 : Math.min(100, v.done() * 100 / v.total());
        boolean finished = v.done() >= v.total();

        DialogLine l1 = line();
        for (int i = 0; i < CARD_TILES; i++) l1.stamp(LOADING_FONT, (char) (CARD_BASE + i), CARD_TILE_W, i * CARD_TILE_W);
        String counter = Math.min(v.done() + (finished ? 0 : 1), v.total()) + " / " + v.total();
        l1.moveTo(PAD).text("シェーダー準備", AMBER_DIM);
        l1.textRight(counter, finished ? NAME : INK, CARD_W - PAD);
        l1.moveTo(LINE_W);

        DialogLine l3 = line().moveTo(PAD + 2);
        if (finished) {
            l3.text("準備完了", AMBER);
        } else {
            l3.glyph(LOADING_FONT, (char) (SPIN_BASE + Math.floorMod(v.frame(), SPIN_FRAMES)), SPIN_W, null, null).shift(5);
            // 英語の名前は回る点の隣 (左寄せ)。カードの右の余白までで切る
            l3.text(FontWidths.truncate(v.id(), CARD_W - 2 * PAD - SPIN_W - 14), NAME);
        }
        l3.moveTo(LINE_W);

        DialogLine l5 = line().moveTo(PAD);
        int lit = finished ? SEGMENTS : litSegments(v.done(), v.total());
        for (int i = 0; i < SEGMENTS; i++) {
            char c = i < lit - 1 ? SEG_ON : i == lit - 1 ? (finished || v.frame() % 2 == 0 ? SEG_HEAD_0 : SEG_HEAD_1) : SEG_OFF;
            l5.glyph(LOADING_FONT, c, SEG_W, null, null);
        }
        l5.textRight(percent + "%", finished ? NAME : INK, CARD_W - PAD);
        l5.moveTo(LINE_W);

        DialogLine l6 = line().moveTo(PAD).text(finished ? "ゲームを始めます" : "初回だけです。画面が止まって見えることがあります", NOTE);
        l6.moveTo(LINE_W);

        // 画面の縦の真ん中にカードが来るよう、上に空行を置く (GUI の大きさ 4 の 1080p で、ダイアログの題と下のボタンの間の真ん中)
        List<DialogLine> lines = new ArrayList<>();
        for (int i = 0; i < TOP_PAD_LINES; i++) lines.add(line().moveTo(LINE_W));
        lines.addAll(List.of(l1, line().moveTo(LINE_W), l3, line().moveTo(LINE_W), l5, line().moveTo(LINE_W), l6));
        while (lines.size() < TOP_PAD_LINES + LINES) lines.add(line().moveTo(LINE_W));
        return lines;
    }

    static Component body(View v) {
        return Component.join(JoinConfiguration.newlines(), bodyLines(v).stream().map(DialogLine::build).toList());
    }

    // ---- 参加 → 条件がそろうまで ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        dropPending(id);
        Run old = runs.get(id);
        if (old != null) old.finish(false);
        String url = plugin.getConfig().getString("resource-pack.url");
        if (!plugin.getConfig().getBoolean("shader-warmup.enabled", true) || effects.isEmpty() || url == null || url.isEmpty()) return;
        Pending p = new Pending();
        p.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.get(id) == p) pending.remove(id);
        }, PACK_WAIT_TIMEOUT_TICKS);
        pending.put(id, p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        dropPending(id);
        Run run = runs.get(id);
        if (run != null) run.finish(false);
    }

    private void dropPending(UUID id) {
        Pending p = pending.remove(id);
        if (p != null && p.timeout != null) p.timeout.cancel();
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.get(player.getUniqueId());
        if (p == null) return;
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                p.packLoaded = true;
                tryStart(player);
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> dropPending(player.getUniqueId());
            default -> {
            }
        }
    }

    private void tryStart(Player player) {
        UUID id = player.getUniqueId();
        Pending p = pending.get(id);
        if (p == null || !p.worldLoaded || !p.packLoaded || runs.containsKey(id)) return;
        dropPending(id);
        ensureListener();
        Run run = new Run(player);
        runs.put(id, run);
        run.task = Bukkit.getScheduler().runTaskTimer(plugin, run::tick, 1L, 1L);
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!CLICK_NS.equals(id.namespace()) || !CLICK_SKIP.equals(id.value())) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Run run = runs.get(player.getUniqueId());
            if (run != null) run.finish(false);
        });
    }

    // ---- ping ----

    private synchronized int newPingId() {
        nextPingId = (nextPingId + 1) % PING_ID_RANGE;
        return PING_ID_BASE + nextPingId;
    }

    private void sendPing(Player player, Run run) {
        int id = newPingId();
        pings.put(id, new PingInfo(run, run.step));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(id));
    }

    private synchronized void ensureListener() {
        if (listener != null) return;
        listener = new PongListener();
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    private synchronized void removeListener() {
        if (listener == null) return;
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        listener = null;
    }

    private final class PongListener extends PacketListenerAbstract {
        private PongListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() != PacketType.Play.Client.PONG) return;
            PingInfo info = pings.remove(new WrapperPlayClientPong(event).getId());
            // 古い段の返事 (タイムアウトの後に来たもの) は数えない
            if (info != null && info.run().step == info.step()) info.run().pongs.incrementAndGet();
        }
    }

    // ---- 1 人ぶんの流れ ----

    /**
     * 段 0 = 画面を黒くする、段 1.. = エフェクトを 1 つずつ重ねる。段を始めた tick から数えて、
     * PING_FIRST_TICK・PING_SECOND_TICK に ping を送り、両方の返事が来たら (またはタイムアウトで) 次の段へ
     */
    private final class Run {
        private final UUID playerId;
        private final int steps = effects.size() + 1;
        private BukkitTask task;
        private int tick = 0;
        /** 今の段 (-1 = まだ始めていない)。ping の返事を受ける側 (別のスレッド) も読む */
        private volatile int step = -1;
        private int stepTick = 0;
        private final AtomicInteger pongs = new AtomicInteger();
        private int gap = 0;
        private int hold = -1;
        private boolean done = false;

        private Run(Player player) {
            this.playerId = player.getUniqueId();
        }

        void tick() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                finish(false);
                return;
            }
            int t = tick++;
            if (t == 0) {
                show(player, 0);
                return;
            }
            if (t < START_DELAY_TICKS) return;
            if (hold >= 0) {
                if (hold-- == 0) finish(true);
                return;
            }
            if (step < 0) {
                startStep(player, 0);
                return;
            }
            stepTick++;
            if (stepTick == PING_FIRST_TICK || stepTick == PING_SECOND_TICK) sendPing(player, this);
            boolean ready = pongs.get() >= 2;
            if (!ready && stepTick < STEP_TIMEOUT_TICKS) return;
            if (!ready) plugin.getLogger().warning("シェーダーの先読みで返事が来ませんでした: " + player.getName() + " 段 " + step);
            if (gap++ < GAP_TICKS) return;
            gap = 0;
            int next = step + 1;
            if (next >= steps) {
                show(player, steps);
                hold = FINISH_HOLD_TICKS;
                return;
            }
            show(player, next);
            startStep(player, next);
        }

        private void startStep(Player player, int next) {
            step = next;
            stepTick = 0;
            pongs.set(0);
            if (next == 0) ScreenEffectService.setCover(player, true);
            else ScreenEffectService.setProbe(player, effects.get(next - 1));
        }

        void finish(boolean completed) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            runs.remove(playerId);
            pings.values().removeIf(info -> info.run() == this);
            if (runs.isEmpty()) removeListener();
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            ScreenEffectService.setProbe(player, null);
            ScreenEffectService.setCover(player, false);
            player.closeDialog();
        }

        /** 進み具合のダイアログ。doneSteps = 終わった段の数 (今重ねる段は doneSteps 番目。段 0 は画面を黒くする分) */
        private void show(Player player, int doneSteps) {
            String id = doneSteps == 0 ? "cover" : doneSteps <= names.size() ? names.get(doneSteps - 1) : "";
            View view = new View(doneSteps, steps, id, doneSteps);
            boolean finished = doneSteps >= steps;
            List<DialogBody> body = List.of(DialogBody.plainMessage(body(view), BODY_W));
            DialogBase base = DialogBase.builder(Component.text(finished ? "準備完了" : "シェーダーを準備しています", AMBER))
                    .canCloseWithEscape(false).pause(false).afterAction(DialogBase.DialogAfterAction.NONE).body(body).build();
            ActionButton skip = ActionButton.create(Component.text("スキップ"), Component.text("準備をやめて始める (ゲーム中に画面が止まることがあります)", NOTE),
                    150, DialogAction.customClick(Key.key(CLICK_NS, CLICK_SKIP), null));
            DialogType type = DialogType.notice(skip);
            player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
        }
    }
}

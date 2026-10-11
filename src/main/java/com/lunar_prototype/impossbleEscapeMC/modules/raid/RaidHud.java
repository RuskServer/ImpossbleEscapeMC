package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * レイド中の HUD: 画面の中央上にコンパス (向いている方位と、今回のレイドの脱出地点の方角・距離)、右上に残り時間。
 * <p>
 * プレイヤーごとの白いボスバーの題名に、リソースパックのフォント iem:hud の文字を並べて描く
 * (文字・幅・空白は IEM_shader/tools/hud_compass.py と同じ。白いボスバーのバーはパックで透明にしてある)。
 * <ul>
 *   <li>帯は幅 {@link #STRIP} GUI px、1 px = 1°で、向いている方位の左右 90°を映す</li>
 *   <li>脱出地点は非常口の看板の印と距離。帯の外 (後ろ側) は端に矢印で出す。PDA の設定で消せる ({@link PlayerData#isHideExtractMarkers})</li>
 *   <li>残り時間は決まった色 ({@link #TIMER}) で、帯の中央の少し左で終わるように書く。
 *       文字のシェーダー (text.vsh) がその色の文字を画面の幅の半分だけ右へずらすので、右上の隅に来る</li>
 * </ul>
 * 向きはサーバーが知っている向きなので、振り向くと回線の遅れの分だけ遅れて付いてくる
 */
final class RaidHud {

    private static final Key FONT = Key.key("iem", "hud");
    static final int STRIP = 181;
    private static final int CENTER = STRIP / 2;
    /** 方位の文字・印をこれより外 (中央から何度) には描かない (帯の端で切れないように) */
    private static final int VISIBLE = 86;
    /** 残り時間の右端と画面の右端の間 (GUI px) */
    private static final int TIMER_MARGIN = 6;
    private static final int UPDATE_TICKS = 2;
    /** 残り時間を赤くする残り秒数 */
    private static final int WARN_SECONDS = 120;

    // 文字と幅 (hud_compass.py の GLYPHS と同じ)。クライアントは 幅 + 1 だけ進める
    private static final char BG = '', TICK = '', CENTER_MARK = '', EXTRACT = '',
            EDGE_LEFT = '', EDGE_RIGHT = '', DIGIT_0 = '', METER = '';
    /** 45°ごとの方位の文字と幅 (N, NE, E, SE, S, SW, W, NW) */
    private static final char[] DIRECTION = {'', '', '', '', '', '', '', ''};
    private static final int[] DIRECTION_WIDTH = {5, 8, 5, 7, 5, 9, 5, 10};
    private static final int TICK_WIDTH = 1, CENTER_WIDTH = 3, EXTRACT_WIDTH = 11, EDGE_WIDTH = 4, DIGIT_WIDTH = 3,
            METER_WIDTH = 5;
    /** 空白の文字: +2^i が U+E500+i、-2^i が U+E510+i (i = 0..8) */
    private static final char SPACE_POSITIVE = '', SPACE_NEGATIVE = '';

    /** 残り時間の色 (文字, 影)。text.vsh がこの色の文字を右へずらす */
    private static final TextColor TIMER = TextColor.color(240, 240, 241);
    private static final ShadowColor TIMER_SHADOW = ShadowColor.shadowColor(60, 60, 61, 255);
    private static final TextColor TIMER_WARN = TextColor.color(255, 80, 65);
    private static final ShadowColor TIMER_WARN_SHADOW = ShadowColor.shadowColor(63, 20, 17, 255);

    private static final Style HUD = Style.empty().font(FONT).color(TextColor.color(0xFFFFFF));
    private static final Style HUD_NO_SHADOW = HUD.shadowColor(ShadowColor.none());

    private final ImpossbleEscapeMC plugin;
    private final RaidMap map;
    private final List<RaidMap.ExtractionPoint> extractions;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private int timeLeft;

    RaidHud(ImpossbleEscapeMC plugin, RaidMap map, List<RaidMap.ExtractionPoint> extractions) {
        this.plugin = plugin;
        this.map = map;
        this.extractions = extractions;
    }

    void show(Player player) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                id -> BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
        update(player, bar);
        player.showBossBar(bar);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, UPDATE_TICKS, UPDATE_TICKS);
    }

    void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    /** 全員から外して止める */
    void stop() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.hideBossBar(entry.getValue());
        }
        bars.clear();
        if (task != null) task.cancel();
        task = null;
    }

    void setTimeLeft(int seconds) {
        this.timeLeft = Math.max(0, seconds);
    }

    private void tick() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) update(player, entry.getValue());
        }
    }

    private void update(Player player, BossBar bar) {
        Component title = title(player);
        if (!title.equals(bar.name())) bar.name(title);
    }

    private Component title(Player player) {
        Location here = player.getLocation();
        List<double[]> targets = new ArrayList<>();
        if (!hideMarkers(player)) {
            for (RaidMap.ExtractionPoint point : extractions) {
                Location target = point.getLocation(map.getWorldName());
                if (target != null && target.getWorld() == here.getWorld()) {
                    targets.add(new double[]{target.getX() - here.getX(), target.getZ() - here.getZ()});
                }
            }
        }
        return compose(here.getYaw(), targets, timeLeft);
    }

    /**
     * 題名を組み立てる。
     *
     * @param yaw      マイクラの yaw (0 = 南、90 = 西)
     * @param targets  脱出地点への水平の差 {dx, dz}
     * @param timeLeft 残り秒数
     */
    static Component compose(double yaw, List<double[]> targets, int timeLeft) {
        Line line = new Line();
        line.glyph(0, BG, STRIP, HUD_NO_SHADOW);

        // 方位: 0 = 北 (-z)、時計回り
        double heading = wrap360(yaw + 180.0);
        for (int angle = 0; angle < 360; angle += 15) {
            double rel = wrap180(angle - heading);
            if (Math.abs(rel) > VISIBLE) continue;
            int x = CENTER + (int) Math.round(rel);
            if (angle % 45 == 0) {
                int i = angle / 45;
                line.glyph(x - DIRECTION_WIDTH[i] / 2, DIRECTION[i], DIRECTION_WIDTH[i], HUD);
            } else {
                line.glyph(x, TICK, TICK_WIDTH, HUD);
            }
        }
        line.glyph(CENTER - CENTER_WIDTH / 2, CENTER_MARK, CENTER_WIDTH, HUD_NO_SHADOW);

        for (double[] target : targets) {
            double bearing = Math.toDegrees(Math.atan2(target[0], -target[1]));
            double rel = wrap180(bearing - heading);
            int x;
            if (Math.abs(rel) <= VISIBLE) {
                x = CENTER + (int) Math.round(rel);
                line.glyph(x - EXTRACT_WIDTH / 2, EXTRACT, EXTRACT_WIDTH, HUD);
            } else if (rel < 0) {
                x = 2;
                line.glyph(0, EDGE_LEFT, EDGE_WIDTH, HUD);
            } else {
                x = STRIP - 3;
                line.glyph(STRIP - EDGE_WIDTH, EDGE_RIGHT, EDGE_WIDTH, HUD);
            }
            distance(line, x, (int) Math.round(Math.hypot(target[0], target[1])));
        }

        timer(line, Math.max(0, timeLeft));
        line.moveTo(STRIP);
        return line.build();
    }

    /** 印の下に距離 (m) を中央揃えで書く (帯からはみ出さない) */
    private static void distance(Line line, int x, int meters) {
        String digits = Integer.toString(Math.min(meters, 9999));
        int width = digits.length() * (DIGIT_WIDTH + 1) + METER_WIDTH;
        int left = Math.max(0, Math.min(STRIP - width, x - width / 2));
        for (char c : digits.toCharArray()) {
            line.glyph(left, (char) (DIGIT_0 + (c - '0')), DIGIT_WIDTH, HUD);
            left += DIGIT_WIDTH + 1;
        }
        line.glyph(left, METER, METER_WIDTH, HUD);
    }

    /** 残り時間 (バニラのフォントの数字は幅 5 + 1、コロンは 1 + 1)。帯の中央の TIMER_MARGIN 左で終わる */
    private static void timer(Line line, int timeLeft) {
        String text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        int width = 0;
        for (char c : text.toCharArray()) width += c == ':' ? 2 : 6;
        boolean warn = timeLeft <= WARN_SECONDS;
        Style style = Style.empty().color(warn ? TIMER_WARN : TIMER).shadowColor(warn ? TIMER_WARN_SHADOW : TIMER_SHADOW);
        // 題名は画面の中央に描かれる。帯の中央が画面の中央なので、ここから半画面右へずらすと右端から TIMER_MARGIN の所で終わる
        line.text(CENTER + 1 - TIMER_MARGIN - width, text, width, style);
    }

    private boolean hideMarkers(Player player) {
        PlayerDataModule module = plugin.getServiceContainer().get(PlayerDataModule.class);
        PlayerData data = module != null ? module.getPlayerData(player.getUniqueId()) : null;
        return data != null && data.isHideExtractMarkers();
    }

    private static double wrap360(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    private static double wrap180(double degrees) {
        double d = wrap360(degrees);
        return d > 180.0 ? d - 360.0 : d;
    }

    /** 文字を GUI px の位置に並べる (位置は空白の文字で合わせる) */
    private static final class Line {
        private final List<Component> parts = new ArrayList<>();
        private int cursor;

        void glyph(int x, char glyph, int width, Style style) {
            text(x, String.valueOf(glyph), width + 1, style);
        }

        /** advance: 書いた後に進む幅 */
        void text(int x, String text, int advance, Style style) {
            moveTo(x);
            parts.add(Component.text(text, style));
            cursor += advance;
        }

        void moveTo(int x) {
            int delta = x - cursor;
            if (delta == 0) return;
            StringBuilder spaces = new StringBuilder();
            char base = delta > 0 ? SPACE_POSITIVE : SPACE_NEGATIVE;
            int n = Math.abs(delta);
            for (int i = 8; i >= 0; i--) {
                while (n >= (1 << i)) {
                    spaces.append((char) (base + i));
                    n -= 1 << i;
                }
            }
            parts.add(Component.text(spaces.toString(), HUD));
            cursor = x;
        }

        Component build() {
            TextComponent.Builder builder = Component.text();
            parts.forEach(builder::append);
            return builder.build();
        }
    }
}

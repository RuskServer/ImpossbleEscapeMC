package com.lunar_prototype.impossbleEscapeMC.command;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.command.Command;
import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import com.lunar_prototype.impossbleEscapeMC.effect.SunShadowService;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数値の画素 (空のシェーダーが画面に書くカメラ・太陽の向きなど) の確認用エフェクトを、自分の画面に付け外しする。
 * 正しく読めていれば、地平線・方角・太陽の輪が絵とずれずに重なる。左下の四角が緑なら数値がそろっている。
 * <p>
 * {@code /shaderdebug data}: データの画素 (サーバーからシェーダーへデータを送る試作。リソースパックの iem_control.glsl の IEM_DATA_*) の
 * テスト用のテキストディスプレイを出す・消す。確認用エフェクトの左下の帯が緑になれば、値が正しい場所に届いている。
 * {@code /shaderdebug sun}: サーバーで計算した太陽の影 ({@link SunShadowService}。ふだんはレイド中だけ自動) を、レイドに関係なく付け外しする。
 * {@code /shaderdebug sun auto} で自動に戻す。データの画素を使うので、テストとはどちらか一方
 */
@Command(name = "shaderdebug", description = "数値の画素 (空のシェーダーが書くカメラ・太陽の向き) の確認表示を付け外しします。", permission = "op")
public class ShaderDebugCommand implements BasicCommand {

    // ---- データの画素のテスト (IEM_shader の tools/data_font.py・core/text.vsh・post/control_debug.fsh と同じ決まり) ----
    /** フォント iem:data: 文字 U+4E00 + 値 (14bit、送り 2)。値の後に毎回すき間の文字 (送り 6) を置き、値 1 つの間隔を 8 にする */
    private static final Key DATA_FONT = Key.key("iem", "data");
    private static final int DATA_BASE = 0x4E00;
    private static final char DATA_GAP = '\u3000';
    private static final int DATA_PITCH = 8;
    /** データの文字の色 (text.vsh がこの色の文字だけを移す) */
    private static final TextColor DATA_COLOR = TextColor.color(212, 122, 1);
    private static final int DATA_TEST_COUNT = 1024;
    /** 値の間隔を 1/256 ブロックにする大きさ (間隔 8 x 文字の大きさ 0.025 x この値 = 1/256) */
    private static final float DATA_SCALE = 1f / (256f * DATA_PITCH * 0.025f);
    private static final Map<UUID, TextDisplay> dataTests = new ConcurrentHashMap<>();

    static int dataTestValue(int i) {
        return i == 0 ? 0x2A5A : (i * 7919 + 13) & 0x3FFF;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーだけが使えます");
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("data")) {
            if (SunShadowService.isEnabled(player)) SunShadowService.toggle(player);
            toggleDataTest(player);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("lore")) {
            if (!com.lunar_prototype.impossbleEscapeMC.effect.LoreMovie.play(player)) {
                player.sendMessage(Component.text("ロアのムービーを見ている途中です", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("ロアのムービーを流します (" + ScreenEffectService.LORE_INTRO_TICKS / 20 + " 秒。見ている間はスペクテイター)", NamedTextColor.AQUA));
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("sun")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("compression")) {
                if (args.length == 3 && (args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
                    SunShadowService.setCompressionEnabled(player, args[2].equalsIgnoreCase("on"));
                } else if (args.length != 2 && !(args.length == 3 && args[2].equalsIgnoreCase("status"))) {
                    player.sendMessage(Component.text("/shaderdebug sun compression on|off|status", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(Component.text(SunShadowService.compressionStatus(player), NamedTextColor.AQUA));
                player.sendMessage(Component.text("新しいパックが必要です。光が停止中なら /shaderdebug sun。更新後に status で容量を確認できます", NamedTextColor.GRAY));
                return;
            }
            if (args.length > 1 && args[1].equalsIgnoreCase("auto")) {
                SunShadowService.clearOverride(player);
                player.sendMessage(Component.text("サーバーの太陽の影をレイド中だけ自動で送るのに戻しました", NamedTextColor.AQUA));
                return;
            }
            TextDisplay test = dataTests.remove(player.getUniqueId());
            if (test != null) test.remove();
            boolean on = SunShadowService.toggle(player);
            player.sendMessage(Component.text(on
                    ? "サーバーの光を送ります (周り " + SunShadowService.W + "x" + SunShadowService.H + "x" + SunShadowService.D
                    + " ブロックまで、圧縮OFF時は48x16x48。/shaderdebug sun auto でレイド中だけに戻す)"
                    : "サーバーの光を止めました (/shaderdebug sun auto でレイド中だけ自動に戻す)", NamedTextColor.AQUA));
            return;
        }
        boolean enabled = ScreenEffectService.toggleControlDebug(player);
        player.sendMessage(Component.text(enabled
                ? "数値の画素の確認を表示 (左下: 緑 = 空と太陽の数値あり / 黄 = 空だけ / 赤 = 無し)"
                : "数値の画素の確認を消しました", NamedTextColor.AQUA));
        return;
    }

    /**
     * テスト用のテキストディスプレイ: 0 文字目が x = 64 の倍数 (プレイヤーに一番近い所) に来るように置き、文字を +x の向きに 1/256 ブロックずつ並べる。
     * テキストディスプレイは行を「1 - 幅 / 2」から描く (DisplayRenderer.TextDisplayRenderer。文字の単位、0.025 倍) ので、
     * 0 文字目の左の端が置いた所にちょうど来るよう、(幅 / 2 - 1) だけ +x にずらす。自分にだけ見せる
     */
    private static void toggleDataTest(Player player) {
        TextDisplay old = dataTests.remove(player.getUniqueId());
        if (old != null) {
            old.remove();
            player.sendMessage(Component.text("データの画素のテストを消しました", NamedTextColor.AQUA));
            return;
        }
        StringBuilder text = new StringBuilder(DATA_TEST_COUNT * 2);
        for (int i = 0; i < DATA_TEST_COUNT; i++) text.append((char) (DATA_BASE + dataTestValue(i))).append(DATA_GAP);
        Location at = player.getLocation();
        Location anchor = new Location(player.getWorld(), Math.round(at.getX() / 64.0) * 64.0, at.getY() + 1.5, at.getZ() + 2.0, 0f, 0f);
        float halfWidth = (DATA_TEST_COUNT * DATA_PITCH / 2f - 1f) * 0.025f * DATA_SCALE;
        TextDisplay display = player.getWorld().spawn(anchor, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.text(Component.text(text.toString(), DATA_COLOR).font(DATA_FONT));
            d.setSeeThrough(true);
            d.setShadowed(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setLineWidth(1_000_000);
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(4f);
            d.setTransformation(new Transformation(new Vector3f(halfWidth, 0f, 0f), new Quaternionf(),
                    new Vector3f(DATA_SCALE, DATA_SCALE, DATA_SCALE), new Quaternionf()));
        });
        player.showEntity(ImpossbleEscapeMC.getInstance(), display);
        dataTests.put(player.getUniqueId(), display);
        player.sendMessage(Component.text("データの画素のテストを出しました (x = " + (int) anchor.getX()
                + ")。/shaderdebug の左下の帯: 緑 = 正しい、黄 = 逆順、青 = ずれ、赤 = 違う値、灰 = 届いていない", NamedTextColor.AQUA));
    }
}

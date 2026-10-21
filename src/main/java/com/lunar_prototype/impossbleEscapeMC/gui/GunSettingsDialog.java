package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Toi's Armory (データパックの銃) の設定を、プラグインの画面で変える (PDA の銃の設定から開く)。
 * <p>
 * 設定はデータパックと同じ、プレイヤーごとのスコア (toisarm.settings.*) に書く。値の回り方・既定値もデータパックと同じ
 * (dialog/change_settings/ の各関数と initialize)。
 * 移動補完 ({@link #LOCKED}) は画面に出さず、サーバーが既定値に固定する。データパックのメニュー (クイックアクション) や
 * /trigger で変えられても、{@link #LOCK_TICKS} ごとに戻す
 */
public final class GunSettingsDialog implements Listener {

    private static final String NS = "iem";
    private static final String PREFIX = "gun_settings/";
    private static final String OBJECTIVE_PREFIX = "toisarm.settings.";
    private static final int LOCK_TICKS = 20;
    private static final int BUTTON_W = 300;

    /** 画面に出さず、この値に固定する設定 (データパックの既定値) */
    static final Map<String, Integer> LOCKED = Map.of("move_comp.enable", 1, "move_comp.rate", 60);

    /** 値の見せ方 */
    private enum Kind { KEYBIND, TOGGLE, PERCENT, DEGREE }

    /**
     * 画面に出す設定 (データパックの画面と同じ順)。id はスコアの名前 (toisarm.settings.&lt;id&gt;)、
     * fallback は初めての人 (スコアが無い) の値、next は押した時の次の値
     */
    enum Setting {
        KEYBIND_SWAP("keybind.swap", null, Kind.KEYBIND, 1, v -> v >= 2 ? 1 : v + 1),
        KEYBIND_DROP("keybind.drop", null, Kind.KEYBIND, 2, v -> v >= 2 ? 1 : v + 1),
        RELOAD_DISABLE_ADS("reload_disable_ads", "リロード開始時に照準を解除する", Kind.TOGGLE, 1, v -> v == 0 ? 1 : 0),
        ROTATE_ENABLE("rotate_comp.enable", "視点移動補完を有効化する", Kind.TOGGLE, 1, v -> v == 0 ? 1 : 0),
        ROTATE_RATE("rotate_comp.rate", "視点移動補完の適用倍率", Kind.PERCENT, 110,
                v -> v >= 200 ? 5 : v <= 95 ? v + 5 : v + 15),
        ROTATE_NOISE_SUP("rotate_comp.noise_sup", "視点移動補完のノイズ抑制率", Kind.PERCENT, 40, v -> v >= 90 ? 0 : v + 10),
        ROTATE_MAX("rotate_comp.max", "視点移動補完の最大角度", Kind.DEGREE, 3, v -> v <= 9 ? v + 1 : v >= 60 ? 1 : v + 5),
        ROTATE_SEMI("rotate_comp.enable_semi", "視点移動補完をセミオートでも適用する", Kind.TOGGLE, 1, v -> v == 1 ? 0 : 1);

        final String id;
        private final String label;
        private final Kind kind;
        final int fallback;
        private final IntUnaryOperator next;

        Setting(String id, String label, Kind kind, int fallback, IntUnaryOperator next) {
            this.id = id;
            this.label = label;
            this.kind = kind;
            this.fallback = fallback;
            this.next = next;
        }

        /** ボタンの ID (スコアの名前。小文字なので Key に使える) の設定 (無ければ null) */
        static Setting byId(String id) {
            for (Setting setting : values()) if (setting.id.equals(id)) return setting;
            return null;
        }

        int next(int value) {
            return next.applyAsInt(value);
        }

        /** ボタンの文字 (データパックの画面と同じ) */
        Component label(int value) {
            Component name = switch (this) {
                case KEYBIND_SWAP -> Component.translatable("key.swapOffhand");
                case KEYBIND_DROP -> Component.translatable("key.drop");
                default -> Component.text(label);
            };
            Component shown = switch (kind) {
                case KEYBIND -> Component.text(value == 1 ? "リロード" : "射撃モード切替");
                case TOGGLE -> value == 0 ? Component.text("無効", NamedTextColor.RED) : Component.text("有効", NamedTextColor.GREEN);
                case PERCENT -> Component.text(value + "%");
                case DEGREE -> Component.text(value + "°");
            };
            return Component.text().append(name).append(Component.text(" : ")).append(shown).build();
        }
    }

    private static GunSettingsDialog instance;

    private GunSettingsDialog() {
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new GunSettingsDialog();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, GunSettingsDialog::enforceLocked, LOCK_TICKS, LOCK_TICKS);
    }

    /** 銃の設定の画面を開く (データパックが読み込まれていなければ、そう伝える) */
    public static void open(Player player) {
        if (objective(Setting.KEYBIND_SWAP.id) == null) {
            player.sendMessage(Component.text("銃の設定を開けません (Toi's Armory データパックが読み込まれていません)", NamedTextColor.RED));
            return;
        }
        show(player);
    }

    private static void show(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Setting setting : Setting.values()) {
            buttons.add(ActionButton.create(setting.label(get(player, setting.id, setting.fallback)), null, BUTTON_W,
                    action("set/" + setting.id)));
        }
        buttons.add(ActionButton.create(Component.text("推奨設定にリセットする", NamedTextColor.RED), null, 150, action("reset")));
        ActionButton close = ActionButton.create(Component.text("閉じる"), null, 150, action("close"));
        DialogBase base = DialogBase.builder(Component.text("銃の設定")).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE).build();
        DialogType type = DialogType.multiAction(buttons).columns(1).exitAction(close).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    private static void showResetConfirm(Player player) {
        DialogBase base = DialogBase.builder(Component.text("銃の設定")).canCloseWithEscape(true).pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(List.of(DialogBody.plainMessage(Component.text("本当に設定をリセットしますか？")))).build();
        DialogType type = DialogType.confirmation(
                ActionButton.create(Component.text("はい"), null, 150, action("reset_yes")),
                ActionButton.create(Component.text("キャンセル"), null, 150, action("back")));
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(type)));
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!NS.equals(id.namespace()) || !id.value().startsWith(PREFIX)) return;
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) return;
        Player player = connection.getPlayer();
        String action = id.value().substring(PREFIX.length());
        Bukkit.getScheduler().runTask(ImpossbleEscapeMC.getInstance(), () -> handle(player, action));
    }

    private static void handle(Player player, String action) {
        if (!player.isOnline() || objective(Setting.KEYBIND_SWAP.id) == null) return;
        if (action.startsWith("set/")) {
            Setting setting = Setting.byId(action.substring("set/".length()));
            if (setting == null) return;
            set(player, setting.id, setting.next(get(player, setting.id, setting.fallback)));
            show(player);
        } else if (action.equals("reset")) {
            showResetConfirm(player);
        } else if (action.equals("reset_yes")) {
            // データパックのリセット (reset_confirm) と同じく、すべて既定値に戻す
            for (Setting setting : Setting.values()) set(player, setting.id, setting.fallback);
            LOCKED.forEach((key, value) -> set(player, key, value));
            show(player);
        } else if (action.equals("back")) {
            show(player);
        } else if (action.equals("close")) {
            player.closeDialog();
        }
    }

    /** 固定する設定を、変わっていたら戻す (まだ設定の無い人は、データパックの初期化に任せる) */
    private static void enforceLocked() {
        for (Map.Entry<String, Integer> locked : LOCKED.entrySet()) {
            Objective objective = objective(locked.getKey());
            if (objective == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Score score = objective.getScore(player.getName());
                if (score.isScoreSet() && score.getScore() != locked.getValue()) score.setScore(locked.getValue());
            }
        }
    }

    private static Objective objective(String id) {
        return Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJECTIVE_PREFIX + id);
    }

    private static int get(Player player, String id, int fallback) {
        Objective objective = objective(id);
        if (objective == null) return fallback;
        Score score = objective.getScore(player.getName());
        return score.isScoreSet() ? score.getScore() : fallback;
    }

    private static void set(Player player, String id, int value) {
        Objective objective = objective(id);
        if (objective != null) objective.getScore(player.getName()).setScore(value);
    }

    /** ボタンの Key (Key の値は小文字・数字・_-./ だけ) */
    static Key actionKey(String action) {
        return Key.key(NS, PREFIX + action);
    }

    private static DialogAction action(String action) {
        return DialogAction.customClick(actionKey(action), null);
    }
}

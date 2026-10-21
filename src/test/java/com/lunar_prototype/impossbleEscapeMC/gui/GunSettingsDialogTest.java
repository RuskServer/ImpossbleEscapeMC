package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.gui.GunSettingsDialog.Setting;

/**
 * 銃の設定の画面: 押した時の次の値・既定値が、データパック (Toi's Armory の dialog/change_settings/ と initialize) と同じ。
 * 移動補完は画面に出さず、既定値に固定する
 */
public final class GunSettingsDialogTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void cycle(Setting setting, int... values) {
        for (int i = 0; i + 1 < values.length; i++) {
            int next = setting.next(values[i]);
            check(next == values[i + 1], setting + ": " + values[i] + " -> " + next + " (expected " + values[i + 1] + ")");
        }
    }

    public static void main(String[] args) {
        cycle(Setting.KEYBIND_SWAP, 1, 2, 1);
        cycle(Setting.KEYBIND_DROP, 2, 1, 2);
        cycle(Setting.RELOAD_DISABLE_ADS, 1, 0, 1);
        cycle(Setting.ROTATE_ENABLE, 1, 0, 1);
        cycle(Setting.ROTATE_SEMI, 1, 0, 1);
        // 95 までは 5 ずつ、その上は 15 ずつ (データパックが 10 と 5 を続けて足す)、200 以上は 5 に戻る
        cycle(Setting.ROTATE_RATE, 110, 125, 140, 155, 170, 185, 200, 5, 10, 15);
        cycle(Setting.ROTATE_RATE, 90, 95, 100, 115);
        cycle(Setting.ROTATE_NOISE_SUP, 40, 50, 60, 70, 80, 90, 0, 10);
        // 9 までは 1 ずつ、その上は 5 ずつ、60 以上は 1 に戻る
        cycle(Setting.ROTATE_MAX, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20);
        cycle(Setting.ROTATE_MAX, 55, 60, 1);

        int[] defaults = {1, 2, 1, 1, 110, 40, 3, 1};
        for (Setting setting : Setting.values()) {
            check(setting.fallback == defaults[setting.ordinal()], setting + " default " + setting.fallback);
            check(!setting.id.startsWith("move_comp"), setting + " must not be shown");
        }
        check(GunSettingsDialog.LOCKED.get("move_comp.enable") == 1 && GunSettingsDialog.LOCKED.get("move_comp.rate") == 60,
                "move_comp locked at the datapack defaults");
        // ボタンの Key が作れる (大文字などが入ると Key.key が例外を投げ、画面が開かない) と、ID から設定に戻せる
        for (Setting setting : Setting.values()) {
            GunSettingsDialog.actionKey("set/" + setting.id);
            check(Setting.byId(setting.id) == setting, setting + " byId");
        }
        for (String action : new String[]{"reset", "reset_yes", "back"}) GunSettingsDialog.actionKey(action);
        check(Setting.byId("move_comp.rate") == null, "locked setting has no button");
        System.out.println("GunSettingsDialogTest OK");
    }
}

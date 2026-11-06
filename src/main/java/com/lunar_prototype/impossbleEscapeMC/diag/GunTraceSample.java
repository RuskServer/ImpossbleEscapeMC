package com.lunar_prototype.impossbleEscapeMC.diag;

/**
 * 銃を持つプレイヤーの、ある tick の状態 (gun-trace の 1 行)。
 * プラグインのタスクはデータパックの tick より先に動くので、この値は「前の tick の終わりの状態」になる。
 *
 * @param tick            サーバーの tick
 * @param gunId           銃の ID (データパックの toisarm.id)
 * @param animationId     toisarm.animation_id (0 idle / 1 ads / 2 bolt_action / 3 change_fire_mode / 4 empty_reload / 5 reload / 6 reload_loop / 7 sprint / 8 swap)
 * @param animationChanged toisarm.animation_changed (データパックは同じ tick の中で 0 に戻す。次の tick の始めに 1 なら取りこぼし)
 * @param frame           custom_model_data の数値 #0 (アニメーションのフレーム)。無ければ NaN
 * @param strings         custom_model_data の文字列の数 (銃は 12 個で渡す)
 * @param model           item_model (例 toisarm:gun/ak74/idle)。無ければ null
 * @param timers          各タイマー (順序は {@link #TIMER_NAMES})
 * @param workWorld       データパックの作業用エンティティがいるワールド名。いなければ null
 * @param slot            選んでいるスロット
 * @param writes          その tick の前に、プラグインがデータパックのスコアへ書いた内容 (無ければ null)
 */
record GunTraceSample(long tick, String gunId, int animationId, int animationChanged, float frame, int strings,
                      String model, int[] timers, String workWorld, int slot, String writes) {

    /** toisarm.timer.* のうち見る物 (配列 timers の順序) */
    static final String[] TIMER_NAMES = {"swap", "ads", "sprint", "reload", "empty_reload", "reload_loop", "bolt_action", "change_fire_mode"};

    /** 銃を渡す時の custom_model_data の文字列の数 (データパックの give と同じ) */
    static final int EXPECTED_STRINGS = 12;

    boolean hasFrame() {
        return !Float.isNaN(frame);
    }

    /** ログの 1 行 */
    String line(long now) {
        StringBuilder timersText = new StringBuilder();
        for (int i = 0; i < TIMER_NAMES.length; i++) {
            if (i > 0) timersText.append(' ');
            timersText.append(TIMER_NAMES[i], 0, Math.min(3, TIMER_NAMES[i].length())).append('=').append(timers[i]);
        }
        return String.format("t%+d id=%d chg=%d fr=%s str=%d model=%s timers[%s] util=%s slot=%d%s",
                tick - now, animationId, animationChanged, hasFrame() ? trim(frame) : "-", strings,
                model == null ? "-" : shortModel(model), timersText, workWorld == null ? "MISSING" : workWorld, slot,
                writes == null ? "" : " WRITE{" + writes + "}");
    }

    static String shortModel(String model) {
        int slash = model.lastIndexOf('/');
        return slash >= 0 ? model.substring(slash + 1) : model;
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format("%.2f", value);
    }
}

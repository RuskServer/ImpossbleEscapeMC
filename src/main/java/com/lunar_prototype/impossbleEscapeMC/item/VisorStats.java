package com.lunar_prototype.impossbleEscapeMC.item;

/**
 * ヘルメットに付けるバイザー ({@link HelmetVisors})。付けたヘルメットをかぶっている間、
 * ScreenEffectService がバイザー越しの見え方を画面にかけ、銃弾処理が正面頭部の跳弾を判定する
 */
public class VisorStats {
    public double ricochetChance; // 正面の頭部被弾を跳弾させる確率 (0..1)
    public String postEffect; // かぶっている間のバイザーの効果。画作りを post_effect/grade_<path>.json に替える (IEM_shader/tools/post_effects.py)
    public int helmetCustomModelData; // この装着品を付けたヘルメットのモデル。0ならヘルメットの既定値
}

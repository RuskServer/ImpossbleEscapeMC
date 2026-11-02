package com.lunar_prototype.impossbleEscapeMC.item.stats;

/**
 * ヘルメットに付けるバイザー ({@link HelmetVisors})。付けたヘルメットをかぶっている間、
 * ScreenEffectService がバイザー越しの見え方を画面にかけ、銃弾処理が顔面の防護クラスと専用耐久を判定する
 */
public class VisorStats {
    public int armorClass; // 顔面の防護クラス。暗視装置は0。
    public int maxDurability; // バイザー単体の耐久。ヘルメット本体とは別管理。
    public String postEffect; // かぶっている間のバイザーの効果。画作りを post_effect/grade_<path>.json に替える (IEM_shader/tools/post_effects.py)
    public int helmetCustomModelData; // この装着品を付けたヘルメットのモデル。0ならヘルメットの既定値
}

package com.lunar_prototype.impossbleEscapeMC.ai;

/**
 * SCAV の声 (ロシア語の掛け声)。リソースパックの iem:scav.voice.* に、場面ごとに言い回しの違う6つの音声があり、
 * 鳴らすたびにクライアントがその中からランダムに選ぶ。戦闘中の叫びは40ブロック、独り言は20ブロックまで届く。
 */
public enum ScavVoice {
    /** 敵を見つけた */
    SPOTTED("spotted"),
    /** 撃たれ続けて頭を上げられない */
    UNDER_FIRE("under_fire"),
    /** 戦闘中に再装填を始めた */
    RELOAD("reload"),
    /** 撃たれた */
    HIT("hit"),
    /** 近くの味方を救援に呼ぶ */
    CALL_HELP("call_help"),
    /** 戦闘中の挑発 */
    TAUNT("taunt"),
    /** 敵を倒した */
    KILL("kill"),
    /** 警戒していない時の独り言 */
    IDLE("idle"),
    /** 物音に気付いた */
    HEARD("heard");

    private final String sound;

    ScavVoice(String name) {
        this.sound = "iem:scav.voice." + name;
    }

    public String sound() {
        return sound;
    }
}

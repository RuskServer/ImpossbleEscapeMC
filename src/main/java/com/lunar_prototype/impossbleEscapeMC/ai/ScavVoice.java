package com.lunar_prototype.impossbleEscapeMC.ai;

/**
 * SCAV の声 (ロシア語の掛け声)。リソースパックの iem:scav.voice.* に、場面ごとに言い回しの違う6つの音声があり、
 * 鳴らすたびにクライアントがその中から発声時に1つ選び、全リスナーで同じテイクを使う。戦闘中の叫びは40ブロック、独り言は20ブロックまで届く。
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
    IDLE("idle",20),
    /** 物音に気付いた */
    HEARD("heard",20);

    public static final java.util.List<String> TAKES = java.util.List.of("a1","a2","b1","b2","c1","c2");
    private final String sound;
    private final int range;

    ScavVoice(String name) { this(name,40); }
    ScavVoice(String name,int range) {
        this.sound = "iem:scav.voice." + name;
        this.range = range;
    }
    public int range(){return range;}
    public String sound(int take,ScavVoiceOcclusion.Level level){return sound+"."+TAKES.get(take)+level.suffix();}

    public String sound() {
        return sound;
    }
}

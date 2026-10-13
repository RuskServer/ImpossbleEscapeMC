package com.lunar_prototype.impossbleEscapeMC.modules.skill;

/** スキルツリーの系統 (画面のタブ)。id はノードの id の頭 (「shooting.reload」) */
public enum SkillCategory {
    SHOOTING("shooting", "射撃"),
    BODY("body", "体力"),
    MEDICAL("medical", "医療"),
    STEALTH("stealth", "隠密"),
    TRADE("trade", "交易");

    public final String id;
    public final String label;

    SkillCategory(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static SkillCategory byId(String id) {
        for (SkillCategory c : values()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }
}

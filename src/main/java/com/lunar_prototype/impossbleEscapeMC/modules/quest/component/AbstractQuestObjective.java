package com.lunar_prototype.impossbleEscapeMC.modules.quest.component;

/**
 * 目標の共通部分。説明文は、クエスト定義で表示名を付けた場合はそれを、なければ目標の種類ごとの既定の文を使う
 */
public abstract class AbstractQuestObjective implements QuestObjective {
    private String label;

    /** GUIに出す説明文を、既定の文の代わりに指定する */
    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public final String getDescription() {
        return label != null ? label : defaultDescription();
    }

    /** 表示名を指定しなかった時の説明文 */
    protected abstract String defaultDescription();
}

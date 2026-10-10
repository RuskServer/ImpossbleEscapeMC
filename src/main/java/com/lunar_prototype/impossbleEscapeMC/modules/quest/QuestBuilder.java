package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.AbstractQuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.QuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ExtractObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.HandInObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.LevelCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.ExpReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.MoneyReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.QuestReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.UnlockTradeReward;

import java.util.ArrayList;
import java.util.List;

/**
 * クエスト定義を組み立てる。受領条件はすべて満たす必要があり、目標は並べた順にGUIへ出る。
 * {@link #label(String)} は直前に追加した目標の説明文を指定する
 */
public final class QuestBuilder {
    private final QuestModule module;
    private final String id;
    private String traderId;
    private String displayName;
    private String description = "";
    private final List<QuestCondition> conditions = new ArrayList<>();
    private final List<QuestObjective> objectives = new ArrayList<>();
    private final List<QuestReward> rewards = new ArrayList<>();

    QuestBuilder(QuestModule module, String id) {
        this.module = module;
        this.id = id;
    }

    public QuestBuilder trader(String traderId) {
        this.traderId = traderId;
        return this;
    }

    public QuestBuilder name(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public QuestBuilder description(String description) {
        this.description = description;
        return this;
    }

    // --- 受領条件 ---

    public QuestBuilder requiresLevel(int level) {
        conditions.add(new LevelCondition(level));
        return this;
    }

    public QuestBuilder requiresQuest(String questId) {
        conditions.add(new CompletedQuestCondition(questId, module));
        return this;
    }

    // --- 目標 ---

    public QuestBuilder kill(String entityType, int amount) {
        objectives.add(new KillEntityObjective(entityType, amount, null, null));
        return this;
    }

    /** 距離を指定した討伐。min / max は null で制限なし (ブロック) */
    public QuestBuilder killAtDistance(String entityType, int amount, Double minDistance, Double maxDistance) {
        objectives.add(new KillEntityObjective(entityType, amount, minDistance, maxDistance));
        return this;
    }

    /** FIR (レイドで拾った) 品の納品 */
    public QuestBuilder handInFir(String itemId, int amount) {
        objectives.add(new HandInObjective(itemId, null, amount, true));
        return this;
    }

    public QuestBuilder handIn(String itemId, int amount) {
        objectives.add(new HandInObjective(itemId, null, amount, false));
        return this;
    }

    /** カテゴリー (med, gun, attachment など) で指定した納品 */
    public QuestBuilder handInCategory(String itemType, int amount, boolean requireFir) {
        objectives.add(new HandInObjective(null, itemType, amount, requireFir));
        return this;
    }

    /** マップからの脱出 (mapId は脱出時に渡るマップ名と同じ文字列) */
    public QuestBuilder extract(String mapId) {
        objectives.add(new ExtractObjective(mapId, 1));
        return this;
    }

    public QuestBuilder reach(String world, double x, double y, double z, double radius, String locationName) {
        objectives.add(new ReachLocationObjective(world, x, y, z, radius, locationName));
        return this;
    }

    /** 直前に追加した目標の説明文 (GUI表示) */
    public QuestBuilder label(String label) {
        if (objectives.isEmpty() || !(objectives.get(objectives.size() - 1) instanceof AbstractQuestObjective objective)) {
            throw new IllegalStateException("クエスト " + id + ": 説明文を付ける目標がありません");
        }
        objective.setLabel(label);
        return this;
    }

    // --- 報酬 ---

    public QuestBuilder money(double amount) {
        rewards.add(new MoneyReward(amount));
        return this;
    }

    public QuestBuilder exp(int amount) {
        rewards.add(new ExpReward(amount));
        return this;
    }

    /**
     * 取引の解放の表示。実際の解放はトレーダー設定の quest: がこのクエストのIDを指すことで行われる
     */
    public QuestBuilder unlockTrade(String traderId, String itemId) {
        rewards.add(new UnlockTradeReward(traderId, itemId));
        return this;
    }

    public QuestDefinition build() {
        if (traderId == null || displayName == null) {
            throw new IllegalStateException("クエスト " + id + ": トレーダーと表示名は必須です");
        }
        if (objectives.isEmpty()) {
            throw new IllegalStateException("クエスト " + id + ": 目標がありません");
        }
        return new QuestDefinition(id, traderId, displayName, description,
                List.copyOf(conditions), List.copyOf(objectives), List.copyOf(rewards));
    }
}

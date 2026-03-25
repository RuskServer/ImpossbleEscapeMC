# クエストシステム仕様書 (Quest System Specification)

独自イベントバスとコンポーネント方式を採用した、柔軟なクエストシステムの仕様をまとめます。

## 1. 独自イベントバス (QuestEventBus)
クエストモジュール内で完結する軽量なイベント配信システムです。
以下の **QuestTrigger** を検知し、進行中のクエストを更新します。

### QuestTrigger 一覧
| トリガー | 説明 |
| :--- | :--- |
| `KILL_ENTITY` | エンティティを殺害したとき |
| `COLLECT_ITEM` | アイテムを入手したとき (未実装) |
| `RAID_EXTRACT` | レイドから脱出したとき |
| `INTERACT_NPC` | NPCと会話したとき |
| `LOCATION_REACHED` | 特定地点に到達したとき |
| `LEVEL_UP` | プレイヤーレベルが上がったとき |
| `HAND_IN` | アイテムを納品したとき |

---

## 2. DSL コンポーネント (YAML)

クエスト定義ファイルは `/quests/*.yml` に配置します。

### A. 条件コンポーネント (Conditions)
クエストの受領条件を定義します。

| タイプ | YAML キー | パラメータ | 説明 |
| :--- | :--- | :--- | :--- |
| **論理積** | `and` | `conditions: []` | リスト内の全ての条件を満たす必要がある |

### B. 目標コンポーネント (Objectives)
クエストの達成目標を定義します。

| タイプ | YAML キー | パラメータ | 説明 |
| :--- | :--- | :--- | :--- |
| **エンティティ討伐** | `kill_entity` | `entity: "ID"`, `amount: 数` | 指定された種類のエンティティを一定数倒す |
| **脱出** | `extract` | `map: "ID"`, `amount: 数` | 指定されたマップ(or "any")から脱出する |
| **納品** | `hand_in` | `item_id: "ID"`, `item_type: "TYPE"`, `amount: 数` | アイテムIDまたはカテゴリーで指定されたアイテムを納品する |

### C. 報酬コンポーネント (Rewards)
クエスト完了時に付与される報酬を定義します。

| タイプ | YAML キー | パラメータ | 説明 |
| :--- | :--- | :--- | :--- |
| **取引解放** | `unlock_trade` | `trader_id: "ID"`, `item_id: "ID"` | 特定トレーダーのアイテムを解放する |
| **経験値** | `exp` | `amount: 数` | プレイヤーに経験値を付与する |
| **資金** | `money` | `amount: 数` | プレイヤーに通貨を付与する |

---

## 3. YAML 記述例

```yaml
trader_id: "prapor"          # 依頼主のトレーダーID
display_name: "初陣"          # クエスト表示名
description: "スカブを5体排除し、アイテムを納品して脱出せよ" # クエスト説明

# 受領条件
conditions:
  - type: "and"
    conditions:
      - type: "and" 
        conditions: []

# 達成目標
objectives:
  - type: "kill_entity"
    entity: "SCAV"
    amount: 5
  - type: "extract"
    map: "factory"
    amount: 1
  - type: "hand_in"
    item_id: "salewa"        # 特定アイテムID指定
    amount: 3
  - type: "hand_in"
    item_type: "med"         # カテゴリー指定 (med, gun, attachment等)
    amount: 5

# 完了報酬
rewards:
  - type: "money"
    amount: 5000             # 5000ルーブル
  - type: "exp"
    amount: 100              # 100 EXP
  - type: "unlock_trade"
    trader_id: "prapor"
    item_id: "ak74"          # AK-74の販売を解放
```

---

## 4. 拡張方法
新しいコンポーネントを追加する場合、以下のクラスを実装・更新してください。

1.  `com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl` パッケージに新しいクラスを作成。
2.  `QuestParser.java` の `parseCondition`, `parseObjective`, `parseReward` メソッドに新しいタイプを登録。
3.  必要に応じて `QuestTrigger` を追加し、`QuestListener` で Bukkit イベントをブリッジする。

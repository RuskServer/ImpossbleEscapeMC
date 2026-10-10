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

## 2. クエスト定義 (Java)

クエストは `QuestCatalog` に `QuestBuilder` で定義します (以前の `/quests/*.yml` は読み込まれません。残っていれば起動時に警告が出ます)。
クエストIDはプレイヤーの進捗 (完了・進行中) とトレーダー設定の `quest:` が参照するため、一度公開したら変えないでください。

### A. 受領条件 (すべて満たす必要がある)

| メソッド | 説明 |
| :--- | :--- |
| `requiresLevel(レベル)` | プレイヤーレベルが指定値以上 |
| `requiresQuest("クエストID")` | 指定したクエストが完了している |

### B. 目標 (並べた順にGUIへ出る)

| メソッド | 説明 |
| :--- | :--- |
| `kill("SCAV", 数)` | 指定した種類のエンティティを一定数倒す |
| `killAtDistance("SCAV", 数, 最小, 最大)` | プレイヤーからの直線距離の範囲を指定した討伐 (ブロック、`null` で制限なし。同じ値なら完全一致) |
| `extract(マップ名)` | 指定したマップから脱出する (マップ名は脱出時に渡る名前と同じ文字列) |
| `reach(ワールド, x, y, z, 半径, "名称")` | 特定の座標地点に到達する |
| `handInFir("アイテムID", 数)` | FIR品 (レイドで拾った物) のみ受け付ける納品 |
| `handIn("アイテムID", 数)` | 通常品も受け付ける納品 |
| `handInCategory("med", 数, FIRのみか)` | カテゴリー (med, gun, attachment 等) で指定した納品 |
| `label("説明文")` | 直前の目標のGUI表示を、既定の文の代わりにこの文にする |

### C. 報酬

| メソッド | 説明 |
| :--- | :--- |
| `money(額)` | 通貨を付与する |
| `exp(量)` | 経験値を付与する |
| `unlockTrade("トレーダーID", "アイテムID")` | 取引解放の表示。実際の解放は、トレーダー設定の該当アイテムの `quest:` がこのクエストのIDを指すことで行われる |

### D. 起動時の確認
`QuestModule` は読み込み時に次を確かめ、ログに出します。誤りがあっても読み込みは続きます。

- クエストIDの重複 (後の定義は無視)
- `requiresQuest` の前提クエストが存在するか (無ければそのクエストは受けられない)
- 納品・取引解放のアイテムIDが登録されているか

---

## 3. 記述例

```java
quests.add(new QuestBuilder(module, "first_contact")
        .trader(KOVACS)
        .name("初陣")
        .description("スカブを5体排除し、アイテムを納品して脱出せよ")
        .requiresLevel(5)
        .requiresQuest("supply_route")
        .killAtDistance("SCAV", 5, 30.0, 80.0)
        .label("30〜80m の距離からSCAVを5体排除する")
        .extract(FACTORY)
        .handInFir("salewa", 3)
        .handInCategory("med", 5, false)
        .reach("factory", 100, 64, 200, 5, "給水塔")
        .money(5000)
        .exp(100)
        .unlockTrade(KOVACS, "ak74")
        .build());
```

---

## 4. 拡張方法
新しいコンポーネントを追加する場合、以下のクラスを実装・更新してください。

1.  `com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl` パッケージに新しいクラスを作成 (目標は `AbstractQuestObjective` を継承し、既定の説明文を `defaultDescription` に書く)。
2.  `QuestBuilder` に、そのコンポーネントを追加するメソッドを足す。
3.  必要に応じて `QuestTrigger` を追加し、`QuestListener` で Bukkit イベントをブリッジする。

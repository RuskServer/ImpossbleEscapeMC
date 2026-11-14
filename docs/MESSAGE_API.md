# メッセージAPIとサーバー側翻訳

## 対象

チャット、単発のアクションバー、タイトル、全体・パーティー通知、コマンドのコンソール返信は `api.message.Messages` を通す。日本語の既存通知をキー付きComponentへ移し、分類、直接の呼び出し元、送信前後のBukkitイベントを共通化している。

GUI本文・アイテム名/lore・常設HUD・シェーダー用データ通信・サーバーログは対象外。他プラグインやバニラの通知を横取りするAPIではない。リソースパックやクライアントModの追加は不要。

## 言語ファイル

- `src/main/resources/lang/ja_jp.json`: 日本語の標準辞書。
- `src/main/resources/lang/en_us.json`: 英語辞書。日本語辞書と同じ全キーを英訳済み。追加時も同じキーを両辞書へ登録する。
- `src/main/resources/messages/definitions.json`: 送信キーごとのカテゴリ、レベル、チャンネル。
- `src/main/resources/messages/arguments.json`: 本文・部分文のキーごとの引数数。

初回起動時にプラグインのデータフォルダーへ `lang/ja_jp.json` と `lang/en_us.json` をコピーする。運用時はそのファイルを編集する。同梱辞書へファイルの値を上書きするため、アップデートで追加されたキーはファイルに未記載でも利用できる。辞書からキーを削除して通知を無効化することはできない。

`de_de.json` などの追加言語も置ける。ファイル名は小文字の言語コードまたは `言語_地域`。既知の翻訳キーのみ受け付ける。`config.yml` の `messages.default-language`（初期値 `ja_jp`）で未対応言語とコンソールの既定言語を指定する。

プレイヤーの `Player.locale()` に対し、完全一致、言語のみ、英語なら `en_us`／日本語なら `ja_jp`、設定の既定言語、日本語の順でキーごとに探索する。翻訳はサーバー側で通常のComponentへ展開してから送信する。バニラ等の未知の翻訳キーは残し、クライアント本来の翻訳を妨げない。Adventureの `GlobalTranslator` にも同じ辞書を登録し、停止時に解除する。

## 本文と引数

文面はMiniMessage形式。例:

```json
{
  "iem.admin.messages.reload_failed": "<red>再読み込みに失敗しました。現在の辞書を維持します: <arg:0></red>"
}
```

`<arg:0>`、`<arg:1>` のように0始まりで挿入する。引数はComponentとして挿入し、プレイヤー名などに含まれるタグをMiniMessageとして解釈しない。引数側のクリック、ホバー、フォント、明示色も保持する。順番の入れ替え、同じ引数の再利用が可能。定義された引数はすべて含める必要がある。

移行した複合通知には、イベントに使う送信キー（`notice_001`など）と、文面の部分キー（`.text_01`、`.support_001`など）がある。外側が `<arg:0>` だけの場合は内側の部分キーを編集する。採番済みのキーを文面修正で変更・再採番しない。新規通知は意味の分かる安定したキーを付ける。

クエスト名・アイテム名・設定ファイル由来の文章・例外メッセージ・一部の管理用診断結果は動的な引数として既存の内容を渡す。そのデータ自体の翻訳は今回の通知辞書の対象外。既存の日本語が自動的に英訳されるわけではない。

既存文面との互換のためMiniMessageの開きタグ・リセットを許容する。JSON構造、キー、引数の数・タグは検証するが、未知の装飾タグをすべてエラーにする厳格モードではない。

## 送信

新規通知は、先にdefinitions、arguments、日本語辞書へキーを追加する。

```java
Messages.chat(player, "iem.quest.completed",
        MessageText.of("iem.quest.completed", questName));

Messages.actionBar(player, "iem.inventory.full",
        MessageText.of("iem.inventory.full"));

Messages.title(player, "iem.raid.started", title);
Messages.broadcast("iem.raid.started_chat", content);
Messages.group(partyMembers, "iem.party.chat", content);
```

上記のクエスト等のキーは追加手順の説明用。実在するキーはdefinitionsを参照する。`MessageText.of` は文字列・数値・ComponentLikeを受け取る。装飾済みの動的ComponentもそのままAPIに渡せる。`MessageText.legacy/payload(String)` は既存のセクション色コード文字列の移行用で、新規コードではComponentを組み立てる。

`MessageService` は `ServiceContainer` からも取得できる。`Messages` は共通の入口として同じサービスを使い、呼び出した時点で `MessageOrigin` を取得する。下位の `MessageService.send/group/broadcast` は明示的なoriginを受け取る。

主スレッドからの呼び出しは直ちに処理する。非同期からの呼び出しは主スレッドへ予約し、元の呼び出し元を保持する。返り値は `CompletionStage<List<MessageResult>>`。主スレッドで非同期処理の完了を待つコードを作らない。

## 分類と呼び出し元

カテゴリ: `SYSTEM`, `RAID`, `COMBAT`, `LOOT`, `INVENTORY`, `QUEST`, `TRADER`, `MARKET`, `HIDEOUT`, `PARTY`, `PROGRESSION`, `MINIGAME`, `ADMIN`。

レベル: `INFO`, `SUCCESS`, `WARNING`, `ERROR`, `DEBUG`。チャンネル: `CHAT`, `ACTION_BAR`, `TITLE`, `CONSOLE`。チャットをコンソール／RCONへ送る場合はイベントのチャンネルを `CONSOLE` にする。

分類はdefinitionsで管理する。既存の条件分岐で本文や色が変わる通知も、同じ送信キーの分類を使う。厳密にレベルを分ける新規通知はキーも分ける。

`MessageOrigin` はAPIの直接の呼び出し元のクラス・メソッド・ファイル・行番号。StackWalkerで予約前に取得する。共通ヘルパーを経由した場合はそのヘルパーを示す。発生理由、親イベント、業務処理の追跡チェーンは記録しない。

## Bukkitイベント

両イベントとも受信者単位で主スレッド上に発火する。

### IemMessageSendEvent

送信前、翻訳前に発火する。送信UUID、受信者、定義、origin、元のMessageContentを参照できる。`setMessage(Component)`／`setContent(MessageContent)` で今回の本文を差し替え、`setCancelled(true)` で今回の通知だけを止める。受信者・分類・送信キー・元本文は変更できない。キャンセルしてもゲーム側の消費・報酬付与等は巻き戻さない。

```java
@EventHandler(ignoreCancelled = true)
public void onMessage(IemMessageSendEvent event) {
    if (event.getCategory() == MessageCategory.ADMIN) {
        // 監視・分類や表示内容の変更をここに置く。
    }
}
```

### IemMessageDispatchEvent

送信処理の結果。元本文、イベントによる変更後本文、翻訳後本文、選択したLocale、状態、失敗時のエラー文字列を参照する。キャンセル不可。翻訳後本文は未送信の場合nullになり得る。

`DISPATCHED` はBukkit送信APIを呼べた意味で、クライアントの受信・実際の表示までは保証しない。`CANCELLED`, `OFFLINE`, `FAILED`, `STOPPED` も区別する。送信前からオフラインの宛先にはbeforeイベントを発火せず、結果イベントのみ通知する。イベント後に切断した場合も再確認する。

全体通知は標準の `BroadcastMessageEvent` も維持する。標準イベントでの本文・受信者の変更やキャンセルを反映した後、受信者ごとのIEMイベントへ進む。パーティー等の限定通知は `group` を使い、標準の全体通知イベントは発火しない。全体／グループの宛先はスナップショット化し、1回の呼び出しでは同じ送信UUIDを使う。

リスナーからの同期再送は深さ8で打ち切る。再帰上限時はさらにイベントを発火せず `RECURSION_LIMIT` を返す。停止時に未実行の予約は `STOPPED` で完了し、停止後のイベントは発火しない。結果イベント自身の再送・例外で残りの受信者を止めない。

## 再読み込み

`/iemmessages reload`（権限 `iem.messages.admin`、初期値op）。configの既定言語とlangファイルを非同期で読み、検証後に主スレッドで辞書全体を差し替える。同時再読み込みは受け付けない。失敗した場合は現在の辞書を維持する。他のゲーム設定の再読み込みは行わない。

## 検証と直接送信の禁止

`MessageCatalogTest`、`MessageServiceTest`、`MessageRoutingArchitectureTest` は単独mainでも動く。`MessageSystemTest` からMaven/Surefireでも実行する。

アーキテクチャテストはコンパイル済みクラスのメソッド参照を走査し、`BukkitMessagePlatform` 以外からのAudience/CommandSenderへの直接送信やBukkit/Serverのbroadcastを禁止する。ラムダ・メソッド参照も対象。通知を追加する際に直接 `player.sendMessage` へ戻さない。

実機確認は、日本語・英語のクライアント、コンソール返信、クエスト報酬表示、パーティーチャット、アクションバー、タイトル、辞書編集後のreloadを確認する。イベントを利用するプラグインではキャンセル・本文差し替え時にもゲーム処理が一度だけ完了することを確認する。

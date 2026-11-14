# Contributing to ImpossbleEscapeMC

ImpossbleEscapeMCへの貢献を検討していただきありがとうございます。
このドキュメントは「何を守って、どう実装して、どうPRを出すか」を短くまとめたガイドです。

## Quick Start

### 1) ブランチを切る

```bash
git checkout -b feature/your-topic
# or
git checkout -b fix/your-topic
```

### 2) ビルドして確認する

```bash
mvn clean package
```

### 3) 変更をコミットする

```bash
git add .
git commit -m "feat: add scav armor selection by item class"
```

### 4) PR を作る

PR本文には最低限、以下を記載してください。

- 目的（なぜ必要か）
- 変更点（何を変えたか）
- 影響範囲（どこに影響するか）
- 確認手順（どう動作確認したか）

## Architecture Rules

このプロジェクトは **Modular Monolith** です。  
新規機能は「モジュール」として追加し、疎結合を維持してください。

### コンストラクタDIを使う

新規コンポーネントは `@Service` / `@Component` / `@Repository` / `@Module` を付け、依存は `@Inject` コンストラクタで受け取る。起動・終了処理は `Startable` / `Stoppable` に置く。レイド等への実行時の参照には `Supplier<T>` を注入できる。Supplier.getは全体起動後に使う。

既存の `IModule` と `ServiceContainer.get/register` は同じコンテナ上で維持する。モジュールを登録する場合は `moduleBootstrap.registerModule(Type.class)` で構築できる。状態サービスや公開インターフェースを機能ごとに増やす必要はない。

自動検出・遅延参照・起動順・コマンド登録・移行時の注意点は [DEPENDENCY_INJECTION.md](docs/DEPENDENCY_INJECTION.md) を参照。直接のシングルトン参照を新たに増やさない。

## Threading & I/O

メインスレッドをブロックしないことを最優先にしてください。

```java
CompletableFuture.runAsync(() -> {
    // file/db/network heavy task
});
```

- ファイル/DBアクセスは非同期で実行
- 共有データは `ConcurrentHashMap` / `Atomic*` などで保護

## Code Style

### Text API

`ChatColor` や `§` は新規コードで使わず、Adventure API を使ってください。

```java
Messages.chat(player, "iem.raid.started", MessageText.of("iem.raid.started"));
```

通知は `api.message.Messages` を使い、キーをdefinitions・arguments・lang辞書へ登録する。上記は新規キーの例。直接送信はアーキテクチャテストで禁止している。分類・イベント・運用は [MESSAGE_API.md](docs/MESSAGE_API.md) を参照。

### Naming

```text
Class: UpperCamelCase
method/field: lowerCamelCase
constant: UPPER_SNAKE_CASE
```

### Comments

- 複雑な処理（特にAI/状態遷移）は意図を書く
- 日本語コメントは可
- 自明なコメントは避ける

## Command / Config Changes

コマンド追加・変更時は以下をセットで更新してください。

```text
1. src/main/resources/plugin.yml
2. Command 実装クラス
3. README.md の Commands セクション
```

設定キー追加時は以下も更新してください。

```text
1. default config (config.yml 等)
2. 読み込みコード
3. README.md / 必要なら運用ドキュメント
```

## PR Checklist

PR前にこのチェックを通してください。

```text
[ ] ビルドが通る (mvn clean package)
[ ] 追加/変更したコマンドや設定のドキュメントを更新した
[ ] メインスレッドで重い処理をしていない
[ ] onDisable で後始末が必要な処理を回収した
[ ] 変更理由と検証手順をPR本文に書いた
```

## Dependencies

- Paper API
- PacketEvents
- LibsDisguises / Citizens

Thanks for contributing.

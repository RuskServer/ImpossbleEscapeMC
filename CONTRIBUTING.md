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

### ServiceContainer を優先する

```java
@Override
public void onEnable(ServiceContainer container) {
    RaidModule raidModule = container.get(RaidModule.class);
}
```

- モジュール間連携は `ServiceContainer` 経由で行う
- 直接シングルトン参照を増やさない
- `ImpossbleEscapeMC.getInstance()` は必要時のみ使用

### 新規機能は IModule で実装する

```java
public final class ExampleModule implements IModule {
    @Override
    public void onEnable(ServiceContainer container) {
        // init
    }

    @Override
    public void onDisable() {
        // cleanup
    }
}
```

- 初期化ロジックは `IModule#onEnable` に置く
- リソース解放は `onDisable` で必ず行う
- `ImpossbleEscapeMC#onEnable` で `moduleBootstrap.registerModule(...)` する

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
player.sendMessage(Component.text("Raid started.", NamedTextColor.GREEN));
```

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

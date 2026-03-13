# Contributing to ImpossbleEscapeMC

このプロジェクトは、タルコフ風のハードコア・サバイバル体験を提供するMinecraftサーバー「ImpossbleEscapeMC」のコアプラグインです。

## アーキテクチャの指針

本プロジェクトは **Modular Monolith** アーキテクチャを採用しています。機能は独立した「モジュール」として実装され、中央のサービスコンテナを通じて疎結合に管理されます。

### 1. 依存性の注入 (DI) と ServiceContainer
直接的なシングルトンパターンの使用は避け、`ServiceContainer` を使用して依存関係を解決してください。

- **モジュール間の連携:** `onEnable(ServiceContainer container)` 内で `container.get(ModuleClass.class)` を使用して他のモジュールを取得します。
- **プラグインインスタンス:** 必要な場合は `ImpossbleEscapeMC.getInstance()` を使用できますが、可能な限りコンストラクタ注入または `ServiceContainer` を優先してください。

### 2. モジュールの実装 (`IModule`)
新しい機能を追加する場合は、`com.lunar_prototype.impossbleEscapeMC.core.IModule` を実装する新しいクラスを作成してください。

- **初期化:** 全ての初期化ロジックは `IModule#onEnable` に記述します。`JavaPlugin#onEnable` に直接ロジックを書き込まないでください。
- **クリーンアップ:** リソースの解放、タスクの停止、データの保存などは `onDisable` で確実に行なってください。
- **登録:** 作成したモジュールは `ImpossbleEscapeMC#onEnable` 内で `moduleBootstrap.registerModule()` を使用して登録する必要があります。

### 3. 非同期処理とI/O
- **ファイル/DBアクセス:** データのロード・保存は必ず非同期 (`CompletableFuture`, `BukkitScheduler#runTaskAsynchronously`) で行い、メインスレッドをブロックしないようにしてください。
- **スレッド安全性:** 複数のスレッドからアクセスされるデータ（キャッシュなど）には `ConcurrentHashMap` や `Atomic` クラスを使用してください。

## コーディング規格

### 1. メッセージとテキスト
- **Adventure API:** `org.bukkit.ChatColor` やセクション記号 (`§`) は非推奨です。必ず Paper の **Adventure Component API** (`Component`, `NamedTextColor` など) を使用してください。

### 2. 命名規則
- クラス名: `UpperCamelCase` (例: `GunListener`, `PlayerDataModule`)
- 変数・メソッド名: `lowerCamelCase` (例: `getPlayerData()`, `isDirty`)
- 定数: `UPPER_SNAKE_CASE` (例: `MAX_STAMINA`)

### 3. コメント
- 複雑なロジックやアルゴリズム（特にAI関連）には、その意図を説明するJavaDocまたはインラインコメントを残してください。
- 日本語でのコメントを許容します。

## 開発フロー

1. **ブランチ作成:** 機能追加やバグ修正は `feature/xxx` や `fix/xxx` ブランチで行なってください。
2. **テスト:** 変更を加えた後は、必ずビルドが通ること (`mvn clean package`) を確認し、ローカル環境で動作検証を行なってください。
3. **プルリクエスト (PR):** 変更内容を簡潔にまとめたPRを作成してください。

## 依存関係
- **Paper API:** サーバーのベースAPI。
- **PacketEvents:** パケットレベルの処理用。
- **LibsDisguises / Citizens:** NPCおよびエンティティの変装・制御用。

---
ご協力ありがとうございます！

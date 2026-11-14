# DIとモジュールの起動管理

Deepwither_V2のコンストラクタDI・注釈検出・依存順ライフサイクルという方式をIEMへ移植した。参照元はDeepwither_V2のAGPL-3.0コード（参照コミット `90c4e6ef7370e8116776261f0e13210216fef7cf`）。IEM側は既存のServiceContainer、IModule、Paperコマンド登録へ合わせた実装で、追加ライブラリは使わない。

## 共有されるコンテナ

`ServiceContainer` は `DIContainer` を継承する。既存の `register/get` と新しい `resolve` は同じインスタンス群を使い、レイドやプレイヤーデータを二重生成しない。

- `register(Type.class, instance)` / `registerInstance`: 外部で作ったインスタンスを登録。同じオブジェクトの再登録は可、別オブジェクトへの上書きは拒否。
- `get(Type.class)` / `findInstance`: 作成済みのインスタンスを探す。未登録ならnull。複数候補は曖昧として拒否。
- `resolve(Type.class)`: 明示登録または要求した具体型そのものを再利用し、未作成ならコンストラクタ依存を解決する。親と子を別々に宣言した場合は、それぞれを構築する。新規作成は構成・起動前の準備段階だけ許可。
- `registerTypes`: 自動検出以外の構築対象を明示する。

プラグイン本体、JavaPlugin、Logger、既存マネージャーは同じコンテナへ登録する。既存のプラグインgetterも引き続き同じオブジェクトを返す。通常の起動経路ではレイドもDIで生成する。`RaidInstance`等のセッションごとに作るオブジェクトは、所有元が引き続き手動で生成する。

## 新規サービス

`@Service`、`@Component`、`@Repository`、`@Module` のいずれかをクラスに付けると、プラグインのパッケージ以下から自動検出される。`@Ignore` は自動検出の対象外にする。スキャン自体ではクラスのstatic初期化を実行しない。

引数付きコンストラクタには `@Inject` を付ける。無引数コンストラクタも既存コードとの互換で使用できる。複数の `@Inject`、型引数のないSupplier/List、不明確な実装の選択は起動時にエラーにする。フィールド注入は行わない。

```java
@Service
public final class ExampleService implements Startable, Stoppable {
    private final PlayerDataModule data;

    @Inject
    public ExampleService(PlayerDataModule data) {
        this.data = data;
    }

    public void start() { /* リソースの初期化 */ }
    public void stop() { /* リソースの解放 */ }
}
```

コンストラクタでは依存を保持する。依存先の起動済みリソースを使う処理はstartへ置く。依存先のコンストラクタ生成完了と、ライフサイクルの起動完了は異なる。

インターフェース注入は明示的なインスタンス登録、または検出済み実装が一つの場合に解決する。`List<T>` は検出済み／登録済みの実装をクラス名順で取得し、同一オブジェクトの別名登録は重複させない。新しい公開APIインターフェースを作るための仕組みではなく、具体クラスをそのまま注入してよい。

## レイド等への遅延参照

コンストラクタの相互依存を必要とする箇所は、通常のJava `Supplier<T>` で必要時の参照を表す。

```java
@Service
public final class ExampleTaskService {
    private final Supplier<RaidModule> raids;

    @Inject
    public ExampleTaskService(Supplier<RaidModule> raids) {
        this.raids = raids;
    }

    public boolean canHandIn(Player player) {
        return !raids.get().isInRaid(player);
    }
}
```

`RaidModule → ExampleTaskService → Supplier<RaidModule>` は許容する。通常の `A → B → A` は循環経路を含む例外にする。遅延参照も依存グラフには記録するが、生成・起動順の制約には使わない。

Supplierの対象も全体起動前に生成・検証する。Supplier.getは全体がRUNNINGになった後だけ成功し、起動途中・停止中・停止後は例外にする。コンストラクタやstart内でSupplier.getを呼ばない。通常のゲーム処理時に同じ起動済みインスタンスを返し、ゲーム中に新しいサービスを生成しない。

これで参加状態の管理先を別Serviceへ切り出す必要はない。ただし、ゲーム処理そのものの再帰（AのメソッドがBを呼び、BがAを呼び続ける）は解決しない。遅延注入は生成時の循環を扱う仕組み。

## ライフサイクルと互換

`Startable.start` は通常依存の先から起動し、成功した順序を記録して `Stoppable.stop` を逆順に実行する。同じインスタンスは別名登録されていても一度だけ起動・停止する。

既存の `IModule.onEnable/onDisable` も同じ起動管理へ接続する。`ModuleBootstrap.registerModule(Class)` は型と起動順の登録だけを行う。enableModulesで全コンポーネントを検出し、宣言が揃ってから構築・起動する。従来の登録順は明示的な起動順として維持する。既存モジュールの登録順と新しい必須依存が衝突したら、循環エラーとして起動を拒否する。新規の独立サービスにはこの既存順序の鎖を付けない。

既存getは構築済みモジュールを起動前にも取得できる。getが非nullでも起動完了を保証しないので、既存onEnableでの順序を崩さない。移行の例としてEconomyModuleはPlayerDataModuleのコンストラクタ注入、ScoreboardModuleはレイドのSupplier注入へ変更している。他の旧モジュール内部のgetやプラグインgetterは互換のため残している。

以前のBootstrapはモジュール起動失敗後も続行していた。現在は失敗を伝播して起動を止め、失敗したコンポーネントの停止を試み、その後に起動済みのコンポーネントを逆順で停止する。一つの停止処理の失敗で、残りの停止を中断しない。初期化途中の失敗にも対応できるよう、stop/onDisableは未初期化フィールドを考慮する。

start/onEnableとstop/onDisableはコンテナのモニターを保持せずに実行する。そのため、それらの処理が別スレッドの作業を待ち、別スレッドが構築済みサービスをget/resolveしても相互待ちしない。コンストラクタは引き続きリソース初期化や非同期作業待ちをせず、依存の保持だけを行う。

停止の再要求は二重実行しない。PREPARING/STARTING中の停止要求は起動スレッドへ引き渡し、実行中のコールバックが戻った後に部分起動も含め逆順に解放する。起動はCancellationExceptionで終了し、コンテナはSTOPPEDになる。stopAllは起動・停止の進行中に呼ばれた場合、完了待ちをせずに返る。停止要求後にRUNNINGへ復帰することはない。

通常依存で後から新サービスを必要とする場合は、注釈やregisterTypesで起動前の対象に含める。起動後の動的登録やコンテナ再起動はサポートしない。

## リスナーとコマンド

注釈付きの新規コンポーネントがListenerを実装していれば自動登録し、停止時に登録解除する。Startableも実装する場合はstartの後にイベント登録する。

BasicCommandの場合は、既存の `core.command.Command` 注釈も付ける。CommandRegistryへ自動登録され、既存と同じ権限・補完処理を使う。既存の `@Command` だけが付いたクラスは従来の手動登録を維持する。新規の自動登録には `@Component` 等も必要。

IModuleは従来どおり自身でリスナー・コマンドを登録するので、自動登録を重ねない。IModuleとStartable/Stoppableを同時実装した場合はIModuleのライフサイクルを優先する。

## 診断と検証

`config.yml` の `di.debug: true` で通常依存・Supplier依存・既存の起動順制約を起動ログへ表示する。必須循環や解決不能な依存はdebug設定に関係なく例外で示す。

`DIContainerTest` は通常循環・遅延循環・起動前取得・シングルトン・一覧注入・起動失敗の巻き戻し・停止順・旧getとの互換・注釈スキャンを検証する。単独mainとMavenの両方で実行する。`DILifecycleRegressionTest` は別スレッド参照待ち、停止の再入、起動途中の停止、親子の具体型の区別を検証する。`DIBukkitTest` はMockBukkitでリスナー／コマンド登録と、検出後のモジュール構築・一覧注入を確認する。

実サーバーでは起動・終了ログ、参加／再接続、出撃、脱出・死亡、クエスト納品、スコアボードを確認する。NMS・Citizens・WorldEdit等を含む完全なゲーム起動は単体テスト環境だけでは再現していない。

# テストの実行

Java 25、JUnit 5、Maven Surefireを使う。`*Test`と`*Tests`のクラスはすべてJUnitから直接実行する。mainだけの検証、mainを呼ぶだけの橋渡し、独自の実行ループは使わない。

```bash
mvn test
mvn package
mvn -Dtest=ScavCombatMotionTest test
```

GitHub ActionsのBuildも`mvn package`を実行するので、通常テストの失敗は配布を止める。NMSの実装はMockBukkitのレジストリ・Adventure実装と衝突するため、従来どおりSurefireの実行クラスパスから除外する。実サーバー・クライアントによる統合確認は別途行う。

## ケースとフィクスチャ

- 独立したシナリオは別の`@Test`とし、ケース名・DisplayName・assertionの理由から失敗箇所を特定できるようにする。連続した状態遷移を確かめる統合シナリオは同じテストに保つ。
- 方向・速度・格子サイズなど同じ性質を複数入力で検査する場合は`@ParameterizedTest`を使う。
- 可変フィクスチャはインスタンスに保持する。ファイルには`@TempDir`を使い、失敗した場合もJUnitに削除させる。
- Bukkit/プラグイン参照を反射で差し替える場合は`@ExtendWith(BukkitState.class)`等で元に戻す。共有レジストリやメッセージサービスも`@AfterEach`/finallyで復元する。
- `BukkitBootstrap`が最初に正規のMockBukkitでBukkitの静的レジストリを初期化し、すぐにサーバーを解放する。テスト用の汎用レジストリが最初の初期化元になり、後続テストの定数を壊すことを防ぐ。
- 共通設定は`src/test/resources/junit-platform.properties`。MavenとIDEの両方で拡張機能の自動検出を有効にし、共有Bukkit状態を扱うため並列実行は無効にする。

実行順への依存を確かめる場合:

```bash
mvn test '-Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer$Random' \
  '-Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random' \
  -Djunit.jupiter.execution.order.random.seed=731645
```

`JUnitDiscoveryTest`は、テストクラスにJUnitケースが存在することと、main入口・main呼び出しの橋渡しが残っていないことをソースの構文木から確認する。

## 実フォント幅を使う検証

ダイアログ配置などの11クラスは`font-data`タグを付け、通常の`mvn test`とActionsから除外している。Mojangのデータをリポジトリやjarに含めないため、実データのある環境では`font-tests`プロファイルで通常テストと一緒に実行する。

```bash
IEM_FONT_WIDTHS=local/font_widths.txt mvn -Pfont-tests test
# またはシステムプロパティで指定
mvn -Pfont-tests -Diem.fontWidths=local/font_widths.txt test
```

プロファイルを有効にして表を指定しなかった場合や、ファイルが無い場合は失敗する。FontData拡張が実際の表を読み込むので、代替の全角幅で成功扱いにしない。IDEで全クラスを実行する場合も同じ表を指定するか、`font-data`を除外する。

表は既存の`FontWidthTable`で手元のクライアントとアセットから作る。書き出した表は`local/`に置き、コミットしない。

```bash
mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
# paper-nmsはこの補助ツールのクラスパスからも外す
CP="target/test-classes:target/classes:$(tr ':' '\n' < target/cp.txt | grep -v paper-nms | paste -sd:)"
java -cp "$CP" com.lunar_prototype.impossbleEscapeMC.util.FontWidthTable \
  <client.jar> <assets-directory> <asset-index-id> local/font_widths.txt
```

## プレビューと測定の補助ツール

通常の検証はJSON等を出力しない。プレビューを作る時だけ、出力先を`local/`配下に指定する。

```bash
mvn -Pfont-tests -Diem.fontWidths=local/font_widths.txt \
  -Dtest=TraderDialogLayoutTest -Diem.previewDirectory=local/previews test
```

`local/previews/TraderDialogLayoutTest.json`のようにクラス別に出力する。複数の画面を一度に実行しても同じファイルへ上書きしない。旧`IEM_DIALOG_DUMP`/`IEM_PANEL_DUMP`の指定は使わない。

`GuiBackgroundDump`はチェスト背景の書き出し、`LightGridCompressionTool`は取得済み光データの圧縮測定用としてmainを残している。これらは`*Test`ではなく、JUnitの検証を起動する入口ではない。前節のクラスパスを使い、必要な時だけ手動実行する。

# Maven依存解決とActionsの待ち時間

通常依存とプラグイン依存の両方で、同じ`central` IDと`https://repo.maven.apache.org/maven2`を最優先にする。Paperなどのプロキシで汎用ライブラリを先に取得すると、別のビルド段階でその取得元IDが利用できず、キャッシュ済みでも再確認が発生するため。専用リポジトリは引き続き残す。CodeMCのrelease/snapshotや、今回使用するrelease専用リポジトリのポリシーも明示する。

Resources・Surefire・Jarは調査時のActionsが実際に使っていた版へ固定する。Versionsの呼び出しは`org.codehaus.mojo:versions-maven-plugin:2.23.0:set`へ固定し、プラグイン接頭辞や最新版メタデータの探索を避ける。Compiler・Shade・Paper NMSの既存指定版は変更しない。`-ntp`は転送進捗の表示を省くもので、取得元検証やチェックサム検証は無効化しない。

## キャッシュ

`setup-java@v4`の`cache-dependency-path`へ`pom.xml`と`.github/workflows/maven.yml`を指定する。依存・取得元・CI手順が変われば新しいキーとなり、その実行で整ったキャッシュを保存できるようにする。以前の完全ヒットでは、実行中に追加された取得元情報が次回へ保存されず、古い取得元の再確認が繰り返されていた。

最初の実行で以前のキャッシュを復元した場合は、一度だけ移行のための再確認が残る場合がある。正常終了して新しいキャッシュが保存された後の実行で確認する。SNAPSHOTの更新・依存変更時の通常のリモート確認は残す。内部の`_remote.repositories`を削除・改変したり、通常ビルドをオフラインにして検証を回避したりはしない。

## 検証記録（2026-11-03）

調査対象の[Actions実行](https://github.com/RuskServer/ImpossbleEscapeMC/actions/runs/41561075711/job/125671553710)ではBuild工程が111秒、キャッシュ取得元の再確認が824回。メインのコンパイル工程は約10秒だった。

変更後はJava 25・Maven 3.9.11で、CIと同じVersions→Paper NMS初期化→Packageの順に検証した。依存キャッシュが温まった後、`target`と`.paper-nms`のない別の作業ディレクトリからやり直した結果は以下。

| 工程 | 手元の実時間 | 取得元再確認 |
| --- | ---: | ---: |
| Versions | 1.82秒 | 0回 |
| Paper NMS初期化 | 6.82秒 | 0回 |
| Package | 8.79秒 | 0回 |

GitHubのRunnerと手元ではCPU・ネットワーク・Mavenの版などが異なるため、同じ所要時間を保証する比較ではない。Actions側の改善量は次回の実行で確認する。初回検証では旧取得元からの移行確認がVersionsで10回、Packageで6回あり、再実行では0回になった。

POM/XMLとWorkflow/YAML、キャッシュ対象パス、CalVerの成果物名、Jar内の既存記述子とクラスを確認した。関連する移動・スタミナ・音響・防具・ダメージのmainテスト11件も成功。

参考：[Mavenのリポジトリ検索順](https://maven.apache.org/guides/mini/guide-multiple-repositories.html)、[Mavenの取得元を考慮するローカルキャッシュ](https://maven.apache.org/resolver/local-repository.html)、[setup-javaのキャッシュ](https://github.com/actions/setup-java#caching-packages-dependencies)。

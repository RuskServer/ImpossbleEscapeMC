# AGENTS.md

このリポジトリで作業する AI エージェント (Claude Code・Codex・Copilot など) 向けの決まり。
人向けの手順は [CONTRIBUTING.md](CONTRIBUTING.md)、編集の境界は [docs/AI_COLLAB_HANDOFF.md](docs/AI_COLLAB_HANDOFF.md) も読むこと。
ここと CONTRIBUTING.md が食い違う所は、エージェントはここに従う。

このリポジトリは公開されている。コミットした物は消しても履歴と配布物 (Releases の jar) に残る、と考えて扱う。

## 手元の物は local/ に置く

リポジトリの直下の `local/` は git の対象外 (`.gitignore`)。リポジトリに入れない物はすべてここに置く。

- サーバーのログ (`logs/`)・クラッシュレポート。プレイヤーの名前や IP が入っている
- バックアップ・アーカイブ (`archive-*.tar.gz` など)
- 書き出したマップの JSON・スクリーンショット・画像
- 試験の出力 (`IEM_DIALOG_DUMP` の書き出し、プレビュー画像)、文字の幅の表 (下の「テスト」)
- その他、作業のための一時ファイル

リポジトリの直下やソースのフォルダに、こうした物を置かない。

## コミットしてはいけない物

- **秘密**: トークン・パスワード・鍵・Webhook の URL・認証情報のファイル・Cloudflare Access のヘッダー。サーバーの IP アドレス・内部のホスト名
- **個人の環境**: ローカルの絶対パス (`C:\Users\...`・`/home/...`・`file:///...` のリンク)、IDE の設定 (`.idea/`・`.vscode/`・`*.iml`)。ドキュメントのリンクはリポジトリ内の相対パスで書く
- **ビルドの生成物**: `target/`・`dependency-reduced-pom.xml`・jar
- **Mojang のクライアント由来の物**: テクスチャ・音・言語ファイル・フォントなどのアセット、クライアントの jar から抜き出した・計算した表やデータ、クライアントのコードを写した物。
  クライアントのデータが要る時は、サーバーで起動時に Mojang の配布元から取って作る (例: 文字の幅の表は `util/FontWidthCache` が作る)
- **他人の物**: AGPLv3 と両立しないライセンスのコード、出所の分からないコード
- **リソースパックの中身**: パックは別のリポジトリ (New_IEM_Resorcepack) で管理する
- `local/` の中身 (上)

## コミットの作り方

- コミットとプッシュは、人に頼まれた時だけ行う。force push・履歴の書き換え・ブランチやタグの削除は、はっきり頼まれた時だけ
- ステージはファイルをパスで指定する。`git add -A`・`git add .`・`git commit -a` は使わない (関係ない物・上の禁止物が入るため)
- 1 つのコミットに 1 つの目的。頼まれていない機能・リファクタ・整形を混ぜない
- メッセージは [Conventional Commits](https://www.conventionalcommits.org/): `feat(scope): ...`・`fix: ...`・`refactor:`・`docs:`・`test:`・`chore:`。
  件名は英語の命令形で短く、本文に「何を・なぜ」。AI が書いたコミットは、本文の最後に `Co-Authored-By:` でそのエージェントを示す
- コミットの前に、次を確かめる
  1. `git diff --cached --stat` で、入れるつもりのファイルだけか
  2. `git diff --cached --check` (空白の誤り)
  3. 秘密の検査: `git diff --cached | grep -inE "password|secret|token|api[_-]?key|BEGIN .*PRIVATE"` で何も出ないか (出たら中身を確かめる)
  4. ステージした内容だけでビルドとテストが通るか (作業中の未ステージの変更に頼っていないか):
     `git checkout-index -a --prefix=local/index/` で書き出した所で、下の「ビルドとテスト」を行う

## ビルドとテスト

- ビルド: `mvn package` (Java 25)
- テストは JUnit ではなく、`src/test/java` の `*Test` クラスの `main`。成功すると何か表示して終わり、失敗すると例外で止まる
- ダイアログの配置のテストは、文字の幅の表を環境変数 `IEM_FONT_WIDTHS` で受け取る。表は手元の 26.3 のクライアントから作る:

```bash
mvn -q package test-compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
# paper-nms (サーバーの中身) を入れると Adventure の実装が変わって落ちるので外す
CP="target/test-classes:target/classes:$(tr ':' '\n' < target/cp.txt | grep -v paper-nms | paste -sd:)"
# 表を作る (クライアントの jar、assets フォルダ、アセットの index の ID。作るのは最初の 1 回だけ)
java -cp "$CP" com.lunar_prototype.impossbleEscapeMC.util.FontWidthTable \
    <client.jar> <.minecraft/assets> <index の ID> local/font_widths.txt
for c in $(cd target/test-classes && find . -name '*Test.class' | sed 's|^\./||;s|\.class$||;s|/|.|g'); do
    IEM_FONT_WIDTHS=local/font_widths.txt java -cp "$CP" "$c" || echo "FAIL $c"
done
```

- 変更の前から落ちているテストは、変更の前の状態で同じように落ちることを確かめてから「既存の失敗」として報告する。自分の変更と関係ないテストを勝手に直さない

## コードの書き方

- まわりのコードに合わせる: コメントは日本語で、まわりと同じくらいの量と書き方。名前の付け方も同じ
- 文字の表示は Adventure API (`Component`)。`ChatColor`・`§` は新しく使わない
- モジュールの作り方・スレッドの扱いは CONTRIBUTING.md の通り

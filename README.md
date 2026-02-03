# ImpossbleEscapeMC

Paper 1.21 向けのサーバープラグインです。SCAV 召喚や専用アイテム取得など、サーバー内の体験を拡張します。  

## 必要要件
- Java 21
- Paper 1.21.x

## インストール
1. `mvn package` でビルドします。
2. 生成された jar を `plugins/` に配置してサーバーを起動します。

## コマンド
- `/getitem` : 特別なアイテムを取得（権限: `impossbleescapemc.getitem`）
- `/scavspawn` : 自分の位置に AI 搭載 SCAV を召喚（権限: `op`）

## 設定
`config.yml` の `resource-pack` でサーバー専用リソースパックを配布します。

## ライセンス
LICENSE を参照してください。

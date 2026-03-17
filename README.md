# 🌆 ImpossbleEscapeMC

![Version](https://img.shields.io/badge/version-2026.03.08-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Platform](https://img.shields.io/badge/Platform-Paper%201.21-green.svg)
![License](https://img.shields.io/badge/License-AGPL-yellow.svg)

**ImpossbleEscapeMC** は、Minecraft 1.21 (Paper) 向けの高機能タクティカル・サバイバル拡張プラグインです。
高度な AI を搭載した SCAV、アニメーション同期型の銃器システム、そしてリアルな戦闘体験を提供します。

---

## 🚀 主な機能

- **🔫 リアルな銃器システム**:
  - アニメーション側の秒数に同期したリロード・ボルト操作。
  - タルコフ風の反動（リコイル）制御と弾道計算（ラグ補填対応）。
  - 各種アタッチメント（サイト、サプレッサー等）の着脱が可能な GUI システム。
- **🤖 高度な AI SCAV**:
  - 視覚・聴覚に基づく索敵、遮蔽物の利用、分隊での連携行動。
  - ボイスラインシステム搭載（状況に応じた `scav1`〜`4` の音声再生）。
  - ダメージや制圧射撃に対する心理状態の変化。
- **🎮 ミニゲーム管理**:
  - マップ管理、チーム分け、リスポーン制御を含む統合ミニゲームエンジン。
- **📦 リソースパック同期**:
  - サーバー専用リソースパックの自動配布とハッシュ値による更新検知。

---

## 📥 ダウンロード

本プロジェクトは **CalVer (Calendar Versioning)** を採用しています。

- **開発版 (Releases)**: 
  [GitHub Releases](https://github.com/RuskServer/ImpossbleEscapeMC/releases) から最新のタグ（例: `2026.03.08`）をダウンロードしてください。

---

## 📜 コマンド

| コマンド | 説明 | 権限 |
| :--- | :--- | :--- |
| `/getitem <ID>` | 特殊アイテム（銃・弾薬・医療品）を取得 | `impossbleescapemc.getitem` |
| `/scavspawn` | 現在位置に AI 搭載 SCAV を召喚 | `op` |
| `/attachment` | 手持ちの銃のアタッチメント GUI を開く | なし |
| `/itemreload` | アイテム定義（YAML）と Config を再読み込み | `op` |
| `/mg <start\|stop\|join>` | ミニゲームの管理・参加 | `op` (管理のみ) |

---

## 🛠 必要要件

- **Java**: 21 以上
- **Server**: Paper 1.21.x
- **Dependency**: 
  - [PacketEvents](https://github.com/retrooper/packetevents) (ビルド時に含まれます)

---

## 🏗 開発とビルド

```bash
git clone https://github.com/RuskDev/ImpossbleEscapeMC.git
cd ImpossbleEscapeMC
mvn clean package
```
ビルドされた JAR は `target/` ディレクトリに生成されます。

---

## 📄 ライセンス

このプロジェクトは [AGPLv3](LICENSE) の下で公開されています。

# ImpossbleEscapeMC

![Java](https://img.shields.io/badge/Java-21-ff7a18)
![Platform](https://img.shields.io/badge/Paper-1.21.x-2ea043)
[![License](https://img.shields.io/badge/License-AGPLv3-f2cc60)](LICENSE)

> Tactical survival expansion for Minecraft Paper 1.21.x  
> 高度なSCAV AI、アニメーション同期銃器、リアル寄り戦闘ループを統合したサーバープラグイン。

## Overview

ImpossbleEscapeMC は、Minecraft の PvE/PvP 体験を「ルート」「交戦」「脱出」中心に再設計するためのプラグインです。  
銃器・防具・医療・AI・ミニゲーム管理を同一基盤で扱えるようにし、運用面では YAML ベースで調整できる構成を採用しています。

## Feature Set

| Module | What it gives you |
| :-- | :-- |
| Weapon System | リロード/ボルト操作のアニメーション同期、リコイル、弾薬クラス、アタッチメントGUI |
| SCAV AI | 視覚/聴覚索敵、遮蔽利用、状況反応、ボイスライン |
| Item Pipeline | `items`/`ammo`/`attachments` YAML ロード、PDC ベース状態管理、ItemFactory 生成 |
| Minigame Core | チーム管理、リスポーン、ラウンド制御、ロードアウト処理 |
| Resource Pack Sync | サーバー配布、更新検知（ハッシュ） |

## Quick Start

### 1. Requirements

- Java 21+
- Paper 1.21.x

### 2. Build

```bash
git clone https://github.com/RuskDev/ImpossbleEscapeMC.git
cd ImpossbleEscapeMC
mvn clean package
```

生成物は `target/` に出力されます。

### 3. Install

1. `target/*.jar` をサーバーの `plugins/` へ配置
2. サーバー起動後、生成される設定を確認
3. 必要に応じて `items` / `ammo` / `attachments` の YAML を編集

## Commands

| Command | Description | Permission |
| :-- | :-- | :-- |
| `/getitem <itemId> [amount]` | 特殊アイテムを取得 | `impossbleescapemc.getitem` |
| `/scavspawn [x y z]` | SCAV を召喚（`/scavspawn heatmap` でヒートマップ表示切替） | `op` |
| `/attachment` | 手持ち銃のアタッチメント GUI を開く | none |
| `/itemreload` | アイテム定義と設定を再読み込み | `op` |
| `/mg <create\|setspawn\|split\|start\|stop\|loadout> ...` | ミニゲーム管理コマンド | `op` |
| `/raid <open\|join\|leave\|map\|start\|spawn\|extract\|scavspawn> ...` | レイド管理・出撃コマンド | `op` |
| `/party <create\|invite\|accept\|leave\|kick\|disband\|info\|chat> ...` | パーティー操作コマンド | none |
| `/loot <container\|egg\|refill\|reload> ...` | ルートコンテナ管理・再補充 | `op` |
| `/trader <open\|reload> [traderId]` | トレーダーUIを開く/設定再読み込み | `op` (`reload` は `impossbleescapemc.trader.reload` も使用) |

## Release & Versioning

本プロジェクトは **CalVer**（Calendar Versioning）を採用しています。  
リリースは [GitHub Releases](https://github.com/RuskServer/ImpossbleEscapeMC/releases) から取得してください。

## Development Notes

- 依存ライブラリは Maven で解決されます（例: PacketEvents）
- 設定・定義は運用時の変更を想定した YAML 中心設計です
- アイテム仕様の追加は `ItemRegistry` と `ItemFactory` の拡張が基本導線です

## License

This project is licensed under [AGPLv3](LICENSE).

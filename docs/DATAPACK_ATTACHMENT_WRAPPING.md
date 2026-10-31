# データパックアタッチメントのラップ

同じ ID が存在する場合、名前・装着スロット・生成アイテム・銃への効果はデータパックの定義を使う。
プラグインの `attachments/*.yml` は説明・重量・サイズ・レアリティの補足に使う。
データパック側に存在しない ID は、従来のプラグイン定義を使う。

既存のアタッチメント設定に以下の項目を追加できる。

```yaml
eotech_xps2:
  weight: 320
  cost: 1
  rarity: 3
  description:
    - '&7ホログラフィックサイト。'
```

`weight` はグラム、`cost` はインベントリの占有サイズ（最小 1）。
`description` は文字列またはリストで、既存の `lore` と併記した場合は lore の後に表示する。
プラグイン側の定義がない場合、重量は既存の DatapackItemSpecs 表、サイズは 1、レアリティは 1、説明は空。
重量表にもない場合は 200g。Eotech XPS2 は表にあるため 320g。

生成時と lore 更新時にプラグインの表示・重量・サイズを付与する。
データパックの custom_data と item_model、FIR フラグは保持する。
既存の未ラップ品も、納品判定と重量・サイズ計算ではデータパック ID を認識する。
クエスト・販売品の存在確認は起動の次 tick とサーバーのリソース再読み込み後に行う。

## クエストと販売

- `optic_calibration` の Eotech XPS2 納品はデータパック品を認識する。FIR 条件は維持する。
- Kovacs の AK-74 は `field_deployment` 完了と必要レベルで販売解放。
- Bastion の MBSS は `bastion_logistics_02` 完了と必要レベルで販売解放。

## 実サーバー確認

1. データパックを読み込み、Eotech XPS2 の生成・購入で名前、説明、レアリティ色、重量、サイズ、モデルを確認する。
2. 銃に装着して、スロットと効果がデータパック定義のままであることを確認する。
3. 「照準の微調整」で通常品は納品できず、FIR 品は必要数だけ減り進捗が増えることを確認する。
4. AK-74 と MBSS が未完了時に購入できず、該当クエスト完了後に購入できることを確認する。
5. 起動・リソース再読み込みで有効な eotech_xps2 に未定義警告が出ないことを確認する。

## ルート対象のアタッチメント

ルートにはデータパックの銃に装着先がある通常品を明示登録する。抽選値は各プール内の独立抽選率であり、コンテナからの最終出現率ではない（個数上限と最低個数の補充抽選も適用される）。

| ID | 対応銃 | プール | 抽選値 |
| --- | --- | --- | --- |
| `eotech_xps2` | M4A1 / SCAR-H / AS VAL | rare_pool | 15% |
| `pso_1` | SVD | rare_pool | 8% |
| `2_stage_suppressor` | UZI | rare_pool | 8% |
| `9x39mm_30round_magazine` | AS VAL | rare_pool | 8% |
| `uzi_short_magazine` | UZI | rare_pool | 5% |
| `uzi_mini_barrel` | UZI | rare_pool | 5% |
| `uzi_folded_stock` | UZI | rare_pool | 5% |
| `scar_h_drum_magazine` | SCAR-H | legendary_pool | 5% |
| `svd_long_barrel` | SVD | legendary_pool | 6% |

- 装着先のない旧定義 `ekp_8` / `pt_1` はルートから除外する。所持済みアイテムは削除しない。
- デバッグ用 `infinity_magazine` / `no_recoil_grip` はルートに登録しない。
- 名前・モデル・装着効果は Toi’s Armory の定義から生成する。重量は既存の DatapackItemSpecs を使う。データパック側は変更しない。
- 更新jarで再起動後、新しく補充されたコンテナから反映される。既に生成済みの中身は自動削除しない。

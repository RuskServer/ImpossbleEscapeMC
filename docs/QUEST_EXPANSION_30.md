# クエスト30件への拡張

既存11件のID・目標・進捗の構造を維持し、マップ指定のない独立系列を19件追加。
Kovacs 14件、Pharmakon 6件、Bastion 10件。Brokerには追加しない。
UNTAR納品の表記は定義に合わせて「ヘルメット2個」に訂正。

新規納品はすべてFIR。討伐・脱出は受注後の累計で、納品と同じレイドである必要はない。
弾薬は同じ口径の複数弾種を混ぜて納品できる。防具条件にはヘルメットも含む。
条件付き納品のGUI判定・進捗判定は共通処理を使用し、必要数だけ消費する。
納品はクエスト一覧の右クリックで開く納品画面に、プレイヤーが納品する物を入れて行う（インベントリから自動では取らない）。
余り・条件に合わない物は欄に残り、画面を閉じると返却される。

## 新規クエスト

| 依頼主 | ID | 名前 | 目標 | 現金 / 経験値 |
|---|---|---|---|---|
| Kovacs | kovacs_workbench | 整備台の再稼働 | スクラップ5、レンチ1、ドライバー1 | 8,000 / 1,200 |
| Kovacs | kovacs_eastern_supply | 東側の補給線 | 5.45mm 60発、7.62x54R 20発、9x39 20発 | 15,000 / 2,200 |
| Kovacs | kovacs_western_supply | 西側の補給線 | 5.56mm 60発、7.62x51 20発 | 12,000 / 2,000 |
| Kovacs | kovacs_close_supply | 近接装備の備蓄 | 9x19 30発、.45ACP 30発、12x70 12発 | 10,000 / 1,800 |
| Kovacs | kovacs_optic_reserve | 照準器の予備 | EKP-8 1、SCAVを15〜40mで5体 | 16,000 / 2,800 |
| Kovacs | kovacs_penetration | 貫通力の確保 | 貫通クラス4以上の弾30発、SCAV8体 | 24,000 / 3,800 |
| Kovacs | kovacs_small_deal | 小さな取引 | シガレット3、ボルト2 | 6,000 / 900 |
| Kovacs | kovacs_power_supply | 電源の確保 | 小型バッテリー2、スクラップ3 | 10,000 / 1,600 |
| Kovacs | kovacs_data_recovery | 記録媒体の回収 | USB 1、脱出2回 | 42,000 / 3,500 |
| Pharmakon | pharmakon_field_clinic | 仮設診療所 | トイレットペーパー4、固形燃料2 | 7,000 / 1,100 |
| Pharmakon | pharmakon_transport_security | 搬送路の安全確保 | SCAV4体、脱出2回 | 12,000 / 2,200 |
| Pharmakon | pharmakon_emergency_stock | 救急備蓄の更新 | 医療品6、浄水フィルター1 | 23,000 / 3,000 |
| Bastion | bastion_light_transport | 軽装輸送の準備 | Tactical Sling Bag 1、Micro Rig 1 | 15,000 / 1,700 |
| Bastion | bastion_escort_equipment | 護衛隊の装備 | MF-UNTAR 1、SCAV5体 | 15,000 / 2,500 |
| Bastion | bastion_carrying_capacity | 積載能力の拡張 | MBSS 1、D3CRX 1、脱出2回 | 28,000 / 3,500 |
| Bastion | bastion_protection_selection | 防護装備の選別 | 防具クラス3以上1、SCAV10体、脱出3回 | 30,000 / 4,500 |
| Bastion | bastion_workshop_gift | 職人への手土産 | 工具箱1、レンチ1、ドライバー1 | 11,000 / 2,000 |
| Bastion | bastion_computing_resources | 演算資源の調達 | GPU 1 | 105,000 / 4,200 |
| Bastion | bastion_buying_time | 時間を買う | 金時計1、脱出3回 | 145,000 / 5,000 |

## 販売解放

各商品はクエスト完了と販売レベルの両方が必要。購入画面と拒否メッセージにも両方を表示。
新規弾薬の販売価格・日次上限はTraderCatalogに定義。高級弾を無制限供給しない。

- 東側の補給線：7.62x54R PS、9x39 SP-5（Lv3）
- 西側の補給線：5.56 HP、7.62x51 FMJ-28（Lv3）
- 近接装備の備蓄：9x19 Pst gzh、.45ACP Match FMJ-26、12x70スチールバックショット（Lv4）
- 照準器の予備：EKP-8（Lv5）
- 貫通力の確保：全8口径のクラス4弾（Lv8、日次60発／散弾24発）
- 積載能力の拡張：Kovacsの既存D3CRX販売（Lv8）
- 防護装備の選別：Bastionの既存Trooper販売（Lv20）

初期の5.45 PS、医療品、軽量バッグ・リグは購入可能なまま。
クラス5・6弾は今回販売に追加せず、既存ルート表からの回収対象として利用できる。

## 確認

`QuestExpansionTest`で件数、既存ID、前提の存在・循環、新規系列のマップ非依存、
全8口径、納品条件に合うルート品の存在、販売解放の相互参照、FIR・性能条件・混合納品・進捗上限・複数マップ脱出を検証。

実サーバーでは次を確認する。

1. 3トレーダーのクエスト一覧に新規系列が出る。
2. 納品画面に入れた物のうち、口径の違う弾と非FIR弾が消費されず、条件に合う複数スタックから必要数だけ減る。余りは画面を閉じると戻る。
3. クラス3以上の防具条件でUNTARが消費されず、FAST-MTなどが納品できる。
4. 受注後、異なるマップからの脱出を累計できる。
5. 販売クエスト未完了ではレベルが高くても購入できず、完了後に日次上限内で購入できる。
6. 既存クエストの進行中・完了済み状態が残る。

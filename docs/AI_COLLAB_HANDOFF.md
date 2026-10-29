# AI Collaboration Handoff (ImpossbleEscapeMC)

## Purpose
This file defines editing boundaries for AI agents working in this repo.
Use it to avoid regressions when multiple agents edit the same systems.

## Core Rule (Important)
- If you need to change logic outside your assigned area, **do not rewrite existing behavior directly**.
- First add a comment with `// TODO(ai-collab): <reason + intended change>` near the target logic.
- Prefer additive changes (new method/new branch/new config key) over destructive refactors.
- If a direct change is unavoidable, keep it minimal and document it in this file under `Change Log`.

## Ownership / Sensitive Areas

### A. Search GUI / Loot interaction
Files:
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/loot/SearchGUI.java`

Current behavior that must be preserved:
- Search GUI blocks top-inventory unsafe actions and drag exploits.
- Container updates are forced to live inventory and persisted immediately.
- Session-searched slots are tracked to avoid repeated `???` search loops.

If touching this area:
- Do not remove top-inventory protections (`MOVE_TO_OTHER_INVENTORY`, drag block, etc.).
- Do not revert container live-update/writeback flow.
- If changing transfer logic, keep partial-move accounting correct.

### B. SCAV brain level and combat adaptation
Files:
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavBrain.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavController.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavSpawner.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/listener/PlayerListener.java`

Current behavior that must be preserved:
- Spawn random brain levels are `LOW/MID` only (no random `HIGH`).
- MID can adapt target preference using low-effective-hit memory.
- Bullet hit events feed SCAV learning via controller hooks.

If touching this area:
- Keep constructor compatibility (`default -> MID`) intact.
- Do not silently remove low-effective-hit memory structures.
- Maintain event path: `BulletHitEvent -> PlayerListener -> ScavController`.

### C. Alertness / Complacency state machine
Files:
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavController.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavVision.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavBrain.java`

Current behavior that must be preserved:
- Alertness decays over time; LOW decays faster than MID.
- Sound and damage raise alertness.
- RELAXED state tends to return to home (spawn) location.
- Vision performance scales with alertness.

If touching this area:
- Keep thresholds and decay values configurable-by-code constants unless explicitly redesigning.
- Preserve `homeLocation` return behavior and investigate flow.

### D. AI Raid Logger pipeline
Files:
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/AiRaidLogger.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/modules/raid/RaidInstance.java`
- `src/main/java/com/lunar_prototype/impossbleEscapeMC/ImpossbleEscapeMC.java`
- `src/main/resources/config.yml`
- related event emitters:
  - `src/main/java/com/lunar_prototype/impossbleEscapeMC/listener/GunListener.java`
  - `src/main/java/com/lunar_prototype/impossbleEscapeMC/listener/PlayerListener.java`
  - `src/main/java/com/lunar_prototype/impossbleEscapeMC/ai/ScavSpawner.java`

Current behavior that must be preserved:
- Raid-scoped logging session starts/stops with raid lifecycle.
- Outputs:
  - `meta.json`
  - `timeline.jsonl`
  - `scav_events.jsonl`
  - `summary.json`
- Snapshot includes SCAV state + player snapshots.
- Optional raycast capture uses actual LoS rays and compressed depth/surface summary.

If touching this area:
- Keep schema backward-compatible when possible (add fields instead of rename/remove).
- Do not move logging I/O to main-thread blocking flow.
- Keep `ai_log.enabled` guard checks.

## Config Keys (Current)
In `config.yml`:
- `ai_log.enabled`
- `ai_log.sample_interval_ticks`
- `ai_log.capture_raycast`
- `ai_log.max_raid_logs`
- `ai_log.async_write`

## Editing Protocol for Other AI Agents
1. Read this file before editing AI, loot, or raid systems.
2. If outside assigned area, prefer extension points:
   - add event
   - add config key
   - add wrapper method
3. For risky legacy changes, leave `// TODO(ai-collab): ...` and avoid broad rewrites.
4. Update `Change Log` below after any non-trivial change.

## Change Log
- 2026-03-24: Added collaboration boundary doc and ownership map for SearchGUI, SCAV behavior, alertness, and raid logger pipeline.
- 2026-03-24: Added TraderSelectionGUI and updated PDAGUI to allow remote trader access when not in a raid.
- 2026-03-31: Added loot session start/end custom events and a loot-backpack overlay listener that temporarily maps player inventory rows (9-35) to backpack pages during SearchGUI sessions, with map/rig suppression and end-of-session restoration.
- 2026-08-22: Restored SCAV gunshot awareness by polling Toi's Armory's `toisarm.timer.firing` score, and limited SCAV sprint gating to active tactical relocation while clearing expired cover targets.
- 2026-10-20: Added `CursorItemListener`: when a container is opened, the item held on the cursor is returned to the player's inventory. Paper's `Player#openInventory` swaps the screen without closing the player's own inventory screen, so vanilla's "cursor item goes back to the inventory on close" step never ran and the item stayed on the cursor.
- 2026-10-27: SearchGUI now plays a private reveal sound based on the discovered item's rarity, and ignores repeated clicks on a slot while its search is running.
- 2026-10-28: Added plugin-side bullet audio observation for Toi's Armory Marker movement, ground impacts, and entity hits. The datapack remains unchanged; CC0 sounds live in New_IEM_Resorcepack.
- 2026-10-28: Raid result EXP animation now plays CC0-derived result sounds when each EXP row appears and when the displayed level rises; the old vanilla completion chime is replaced.

- 2026-10-28: SearchGUI uses shared textured unknown/search icons, preserves concurrent search animations during inventory refresh, and draws rarity-colored reveal waves in the title without replacing revealed items. Backpack title changes preserve the wave overlay.

- 2026-10-28: Added persistent player-spawn IDs, list/remove/analyze commands, and bounded tick-distributed terrain/LOS diagnostics targeting 30–40 seconds. Old spawn coordinate arrays remain compatible; ID migration is saved on load. Route timing is explicitly an approximation and incomplete pairs never receive a safe rating.

- 2026-10-28: Spawn diagnostics preload bounded existing chunks asynchronously and hold shared ChunkLease tickets through diagnosis, with cleanup on cancellation/disable. SCAV chunk retention uses the same reference-counted leases. Encounter estimates now use a full-stamina run/walk reference profile with runtime stamina constants, weight stages, food recovery and shared weight speed penalties.

- 2026-10-28: Added dedicated SCAV withdrawal planning: evaluate reachability and sampled route exposure before selecting cover, keep the chosen path through close combat and temporary target loss, reject stalled destinations, and reengage only after reaching cover and satisfying existing readiness/health/suppression checks. Shared chunk retention behavior remains in place.

- 2026-10-28: Added gunshot investigate/alert/ignore bands (32/64m defaults) and a 40m home leash for sound-only searches, including inferred positions and route/direct/peek destinations. Visual contact, damage and accepted help retain normal combat behavior. Out-of-bounds sound searches stop and cool down; distant shots never create or extend investigation targets.

- 2026-10-28: Spawn diagnostics distinguish zero-exit lower-level sites as underground/vertical-passage candidates, reporting the upper spawn and height difference. Early-contact, incomplete-route and unsafe-footing warnings retain priority.

- 2026-10-28: Stabilized SCAV decisions without input smoothing: fixed per-weapon/individual engagement range, shared 0.60/0.35 suppression hysteresis, and a 15-tick hold for final ordinary movement proposals. Removed repetition-only action substitution and mid-segment random strafe reversals. Existing interrupts and dedicated route/cover movement retain priority; urgent interrupts can cancel peeks. See SCAV_DECISION_STABILITY.md.

- 2026-10-29: Bullet whizz audio now requires confirmed, distinct shooter/listener datapack IDs. Removed the distance-only fallback for unknown owners so a player cannot hear their own unidentified bullet; impact audio is unchanged.

- 2026-10-29: Spawn list defaults to compact rows for all sites with per-site fastest estimated contact time/opponent, color-coded ratings and hover details; explicit pages show 12 rows. All spawn pairs are considered without the former pair cap, initial LOS is checked first, route order rotates sites, and missing or incomplete pairs remain explicitly unknown. Existing tick/time/chunk budgets and underground warnings remain in place.

- 2026-10-29: Spawn route diagnostics now interleave one operation across at most eight active pair searches instead of blocking on one pair. Completion counts and grouped unknown reasons are shown in normal chat; timeout distinguishes active searches from unstarted pairs, and absent contact timings retain an unknown rating rather than a retreat-direction label. Tick/time/chunk and per-route limits are unchanged.

- 2026-10-29: Added bounded SCAV sight recovery for ready, healthy combatants stalled without sight; reselect firing positions or search toward remembered contact positions while preserving withdrawal/reload/suppression waits and memory expiry. Failed spots are temporarily rejected, ENGAGE cannot retain HIDE, peek candidates require direct walkability, and path/arrival handling avoids stale or repeatedly restarted movement. See SCAV_SIGHT_RECOVERY.md.

- 2026-10-29: Added client Japanese radio and a compass-underlay threat-entry animation, with WorldEdit/FAWE cuboid zone add/list/remove commands persisted per map. SearchGUI adds only a reveal event with a cloned item; quest discovery checks existing hand-in/FIR rules without changing transfers, protections or storage. Per-raid notification state deduplicates discoveries, debounces zones, prioritizes end-sequence radio and clears presentation on departure. Russian UN audio remains separate. See RAID_RADIO.md; resource-pack generation and provenance live in its own repository.

- 2026-10-29: Adopted the conversational Japanese radio voice selected as preview A for all four client lines, retaining the exact synthetic reference voice and the two auditioned discovery/zone performances. Added the same comparison loudness normalization to generation, adjusted EMP static timing to 83 ticks after voice start, and removed mid-clause punctuation from the withdrawal warning input.

- 2026-10-29: Preserved the approved A withdrawal-warning performance as a fixed source asset in the resource-pack tools, replacing full TTS regeneration with checksum-guarded quiet-interval edits. Speech samples and gain remain unchanged; only two quiet spans and 5 ms joins are edited. Other radio lines and EMP timing are unchanged.

- 2026-10-29: Adopted radio preview two using the original low male synthetic reference (VoiceDesign seed 211), speaker-identity-only cloning and non-streaming generation. Kept the exact auditioned warning/discovery takes, generated matching EMP/zone lines, and saved all four processed performances with checksum guards in the resource-pack tools. Subtitles match the adopted wording; EMP static now starts 83 ticks after voice start. See RAID_RADIO.md.

- 2026-10-29: Added two-drawer loot furniture from the supplied BlockDisplay geometry, with independent shared inventories, viewer-driven opening/closing and map/PDC persistence. SearchGUI adds a SearchableLootSource extension without changing transfer/protection branches for existing sources. Raid refill uses deferred generations for unloaded drawers; commands support placement, listing, removal and targeted refill. See DRAWER_LOOT.md.

- 2026-10-29: Added /loot draweregg with persisted map/crate tags, tab completion and OP-only main-hand placement through the same drawer placement method. Placement eggs are reusable; off-hand, air and entity interactions suppress vanilla spawning.

- 2026-10-29: Expanded scav_ai_specification.md against current implementation, documenting timing units, perception, Utility/movement priority, withdrawal/reengagement, sight recovery, weapon readiness, coordination, configuration and diagnostic limits. Corrected SCAV_HELP_ENCOUNTERS.md to match the implemented cumulative two-responder and three-member encounter limits. Documentation only; AI behavior unchanged.

- 2026-10-29: Added SCAV-only articulated display corpses using a bounded collision/joint simulation, with existing Mannequin inventories retained behind an Interaction. CorpseCreatedEvent and CorpseAppearanceEvent connect presentation without changing loot transfer/protection. Added supported-equipment synchronization, bounded active/total display counts, fallback to normal corpses and lifecycle cleanup. Player corpses remain on the existing path. See SCAV_RAGDOLL.md; resource models are generated in the resource-pack repository.

- 2026-10-29: Fixed SCAV reserve ammo filling empty equipment slots and causing ragdoll eligibility to reject normal corpses. CorpseLootLayout reserves slots 0-5, merges/splits extra loot only from slot 6 and returns overflow for drops. Added a regression test through the real ragdoll eligibility check, startup logging and optional skip-reason diagnostics.

- 2026-10-29: Registered existing SCAV skin and iron/diamond equipment textures in the items atlas under distinct corpse sprite aliases, fixing missing textures in body/armor ItemDisplays. Added the missing local Y half-turn to held-item attachments so muzzle/stock orientation matches the arm; gun datapack and models remain unchanged.

- 2026-10-29: Replaced ragdoll mesh-corner-only contacts with oriented body-box versus voxel-box separation, including a skin margin and four bounded final contact sweeps per substep. This catches thin-wall face intersections and rechecks parts displaced by later limb corrections. Added thin-wall/rotated-axis tests and 336 wall/corner trajectories checking each rendered pose, floor clearance and joint continuity. Loot and resource-pack assets are unchanged.

- 2026-10-29: Added animated loot lockers through the existing SearchableLootSource boundary. User-supplied geometry is grouped into shell/door item models (two displays and one shared Interaction per locker). Each refill generation selects empty with 75% probability or standard_crate/medical_case/ammo_case with equal remaining probability; placement ID and generation keep that choice stable across reloads. Added map persistence, reusable placement eggs, commands, remover/refill hooks and lifecycle cleanup without changing SearchGUI transfers/protection. See LOCKER_LOOT.md.

- 2026-10-29: Corrected server-side diffuse sun bounces to use the actual hit-face incidence and attenuate partially transmitting cells along the return path. Added a bounded near-field screen-space diffuse/contact pass in the resource pack, sharing the existing depth-normal reconstruction and bilateral upsampling. No light-data protocol changes. See LIGHTING_NATURALNESS.md for approximation limits and synthetic GPU verification.

- 2026-10-29: Rebalanced the fixed grade toward neutral material colors, reduced lens/grain/vignette effects and disabled peripheral chromatic shift. Added an SDR-aware linear-light highlight shoulder with fixed exposure and black-preserving contrast, generated consistently across all grade variants. No additional plugin runtime changes; the new resource-pack GPU check validates the actual tone GLSL. See LIGHTING_NATURALNESS.md.

- 2026-10-29: Restored pre-rework bloom/flare settings and kept the lens contribution outside scene highlight compression. Corrected sunlight gain to operate on linearized energy instead of encoded display color, and reduced the daylight haze veil. Reduced screen-space indirect lighting to 480x270/four samples with cheap candidate rejection before normal reconstruction; added actual-grade GPU checks for sun/shade energy and lens composition. Plugin runtime behavior is unchanged.

- 2026-10-29: Addressed flat lit/shadowed surfaces with continuous sunlight incidence, bounded hemispherical sky response and incidence-weighted face relief. Retained direct sun in sky-kind cells with confirmed sun visibility and preserved point-light-only shading. Exposure remains 1.0; no additional screen-space samples or passes. Added GPU angular/ambient checks.

- 2026-10-29: Rebalanced direct sun versus exposed outdoor sky fill while retaining the indoor/night sky coefficient and diffuse fill in weak sun. Linearized map-color reflectance before bounce estimation instead of treating encoded RGB as energy, with bounded reflectance and packed-grid contrast tests. Matched the resource-pack direct-share/far-light constants without adding rays or changing the light protocol. See LIGHTING_NATURALNESS.md.

### 2026-10-30: SCAV死体右クリックの補助経路
- ScavRagdollModuleに部位回転箱へのサーバー視線判定を追加。Interactionイベントが届かない場合も空/地面右クリックから到達可能にする。原因は未確定で、実機確認待ち。
- 射程属性、ブロック遮蔽、イベント経由の保護判定、同一GUI重複防止を適用。SearchGUIの移動・保存には変更なし。RagdollPickingTestと診断ログを追加。詳細はSCAV_RAGDOLL.md。

### 2026-10-30: 高脅威区域通知によるHUD横ずれ
- 通知bitmapの1文字440×160pxは26.3の256×256px文字アトラスに収まらない。左右220×160pxへ分割し、RaidHudは表示110pxずつを隙間なしで描く。各文字のadvance111を相殺し、通知中も題名全体のadvance181を保つ。
- パックtools/radio_alert.py・radio_alert.json・全12フレームの左右画像とプラグインRaidHudを同時更新する。左U+E000〜E00B、右U+E020〜E02B。旧プラグインと新パックを混ぜない。
- RaidRadioHudTestは全フレームの配置・総幅を確認。パックcheck_radio_alert.pyは実PNGのサイズ、alphaから求めたadvanceとフォント設定を検証。実機確認待ち。

### 2026-10-29: 日なた・日陰の表面色の分離試作
- パックの `iem_lighting_response.glsl` / `light.fsh` で昼の色の分離を強める。旧shaftsの無深度ぼかしは戻さず、既存の表面照明経路を使う。
- 光量、フレア、プラグイン実装、送信形式は変更なし。GPUで色・輝度・夜の復帰を検証。実機の見栄えは未確認。詳細は `LIGHTING_NATURALNESS.md`。

### 2026-10-30: 接地遮蔽の補助調整
- パックのindirectで反射光の経路判定と遮蔽を分離。4標本を接地側へ寄せ、減光上限を28%へ。プラグイン実装・光送信形式は変更なし。
- 実際のフラグメントのGPU試験を `tools/check_indirect.py` に追加。画面外の物陰を補えない点、標本位置変更が反射にも影響する点は `LIGHTING_NATURALNESS.md` を参照。

### 2026-10-30: ロッカーをドロワーと同じ表示方式へ変更
- LockerModel/LockerLootModuleを14個のBlockDisplay方式へ変更。元コマンドの変換行列を保持し、床中央・前面-Z・共通蝶番を使用する。旧ItemDisplayモデルを参照しない。
- 所有キー、操作Interaction、PDCの中身・世代、25%抽選は維持。読込時に旧表示だけ再生成する。描画数2→14の負荷は実機確認が必要。LockerModelTest/LockerLootRollTest通過。詳細LOCKER_LOOT.md。
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 
- 2026-10-30: Added opt-in per-player `/shaderdebug sun compression on|off|status` for lossless indexed 4x4x4 near-light palettes. Encoding runs in the existing asynchronous calculation, retains the 48x16x48 range and existing channel/magic, and falls back to legacy when not smaller. New resource pack reads both formats and gates text-transport palette/cache transitions on complete same-frame arrival. Default OFF; cleared on logout. See LIGHT_GRID_COMPRESSION.md for protocol, synthetic GPU measurements, tests and real-client validation steps. Real Minecraft validation remains pending.

- 2026-10-30: Expanded opt-in compressed near lighting to 64x16x64, retaining 48x16x48 when OFF and a centered, origin-adjusted 48 fallback when the complete compressed packet cannot fit. Removed runtime middle-height-map capture/calculation/transmission; kept existing coarse outer map. Pack grid is now 1024x96 with kind rows at 64. Added capacity-edge/crop/GLSL layout checks. Real-client range/load validation pending. See LIGHT_GRID_COMPRESSION.md.

- 2026-10-30: Adopted compressed 64x16x64 lighting as the default after user real-client validation. Per-player diagnostic OFF still selects legacy 48; logout/restart clears overrides back to default ON. Kept centered capacity fallback, middle-map removal, protocol and existing pack/Mod compatibility. Updated current-operation docs.

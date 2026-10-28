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

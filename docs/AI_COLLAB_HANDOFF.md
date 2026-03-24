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

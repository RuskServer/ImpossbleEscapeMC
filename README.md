<h1 align="center">ImpossbleEscapeMC</h1>

<p align="center"><strong>Extraction survival in Minecraft</strong></p>
<p align="center">Deploy alone or with your squad. Search for supplies, fight your way out, and bring your loot back to the hideout.</p>
<p align="center"><code>Paper 26.3</code> · <code>Java 25</code> · <a href="LICENSE">AGPL-3.0</a></p>
<p align="center"><a href="#server-setup">Server setup</a> · <a href="#build-from-source">Build from source</a> · <a href="#documentation">Documentation</a></p>

## How a raid plays

**Deploy.** Choose a map from your PDA and enter solo or with a party. Bring a loadout suited to the raid.

**Search and survive.** Reveal loot one item at a time while watching for SCAVs and rare boss encounters. Customize your firearms with attachments, and manage armor and injuries in the field.

**Extract and progress.** Reach an extraction point before time runs out. Turn in items for trader quests, unlock new gear, and use recovered supplies to upgrade your hideout.

## Server setup

1. Run Paper 26.3 on Java 25. Place the ImpossbleEscapeMC and PacketEvents JARs in `plugins/`.
2. Install the matching IEM resource pack and weapon datapack, then prepare your raid maps.
3. Start the server once to generate `plugins/ImpossbleEscapeMC/config.yml`. Adjust server settings there. Item, loot pool, and trader catalogs are defined in Java.

## Build from source

```sh
mvn clean package
```

Build with Java 25. Maven runs the default test suite and writes the plugin JAR to `target/`. See the [contribution guide](CONTRIBUTING.md) for development instructions.

## Common commands

| Command | Purpose |
| --- | --- |
| `/party` | Manage your party. |
| `/attachment` | Edit attachments on the held gun. |
| `/raid` | Configure and manage raids. |
| `/loot` | Place, remove, and refill loot containers and spawn points. |
| `/scavspawn` | Spawn a SCAV for testing. |
| `/iemmessages reload` | Validate and reload message translations. |

## Documentation

| Topic | Guides |
| --- | --- |
| Raids | [Spawn diagnostics](docs/RAID_SPAWN_DIAGNOSTICS.md) · [Boss encounters](docs/RAID_BOSS.md) · [Radio alerts](docs/RAID_RADIO.md) |
| Loot | [Drawers](docs/DRAWER_LOOT.md) · [Lockers](docs/LOCKER_LOOT.md) |
| Configuration | [Localization](docs/MESSAGE_API.md) · [SCAV corpses](docs/SCAV_RAGDOLL.md) |
| Development | [Architecture](docs/DEPENDENCY_INJECTION.md) · [Testing](docs/TESTING.md) |

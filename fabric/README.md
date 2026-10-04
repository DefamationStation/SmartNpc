# Fabric 26.4 Snapshot 2 port

Behaviour design: [player journey and automation reuse](PLAYER_JOURNEY.md),
[development roadmap](DEVELOPMENT_ROADMAP.md).
Current implementation milestone: [bounded food acquisition, cooking prerequisites and shelter diagnostic](RESOURCE_TASKS.md).
Current checks: [food acquisition verification](verification/FOOD-RESULTS.md).
Earlier prerequisite checks: [prerequisite verification](verification/PREREQUISITE-RESULTS.md).
Earlier completed checks: [daily routine verification](verification/ROUTINE-RESULTS.md).

Experimental, independently built Fabric port of Smart NPC 3.0.0. Tested in
fresh worlds on Minecraft **26.4 Snapshot 2**, including an actual saved-world
restart. See [validation results](verification/RESULTS.md) for the tested scope.

This module targets launcher version `26.4-snapshot-2` (internal version
`26.4-alpha.2`), Java 25 and Fabric Loader 0.19.5. The root project continues to
target NeoForge 26.1.2 without changes to its source or build configuration.

Requires Fabric API **0.161.2+26.4**. Install the jar from the fork's releases in
the `mods` directory on both client and server. The tested deployment is an
integrated singleplayer server; dedicated-server deployment is not yet verified.
This jar is not a NeoForge build and does not target other 26.4 snapshots.

Build from this directory with `./gradlew build` (Windows: `gradlew.bat build`).
The module shares the upstream Java/resources and provides explicit replacement
files only for platform or snapshot API differences. `prepareSources` merges
them in the build directory without modifying the upstream files.

The jar is written to `fabric/build/libs/`. Configuration uses the original keys
in `config/smart_npc-server.toml` and `config/smart_npc-names.toml`; restart the
game after changing them. The inspector item is
`smart_npc:player_npc_inspector`, and the NPC entity is `smart_npc:player_npc`.

## Port scope

- Fabric registration, networking, commands, lifecycle hooks, natural spawning,
  configuration and resource reload listeners.
- Persistent entity and furnace attachments, including NPC task data and
  inspector state; vanilla chunk tickets with per-NPC ownership accounting.
- Updated tool tags, colored blocks/items, saved data, projectile handling,
  bonemeal, durability, swing animations and render APIs for this snapshot.
- NPC bow control for a friendly mob, inspector HUD and first/third-person
  spectator controls.

Better Combat / Player Animation Library integration is deliberately disabled
in this build. Vanilla combat remains available. Epic Fight integration is not
enabled. Cross-loader world migration and exhaustive long-running farming,
building, fishing and combat behaviour have not been validated.

## Upstream review

The original NeoForge project is untouched. This is an additive module, with
explicit source overlays for files needing loader/version changes. It can be
merged as an optional Fabric target without changing the existing default build.
Whether to accept this layout is the upstream author's decision; the fork can
also remain independently maintained.

Run `python fabric/verification/overlay-diff.py` from the repository to review
only differences between the upstream files and their Fabric replacements.
New platform classes are under `fabric/src/main/java/com/pla/smart_npc/fabric`.
Overlays should be reconciled when rebasing upstream changes; this layout
prioritizes keeping the existing release untouched over a wider common-code
refactor during the initial snapshot port.

## Verification

`./gradlew build` runs config/event and resource-memory tests and validates the access widener.
The Windows integration harness in `verification/run-smoke.ps1` creates its own
world, then `-Replay` reopens that test world. It requires a local offline
launcher argument template, an existing instance for its libraries/mods/options,
a Java 25 installation, and a separate output directory. The optional
`-FullModStack` copies the instance's mods and shader pack into that test instance.
It never copies or opens the source instance's saves. Inspect `complete.txt`,
`replay-complete.txt`, `failure.txt`, and the `SMARTNPC_PASS` log entries.

Upstream: https://github.com/PlaIsMe/SmartNpc (26.1.2 branch).
Port: https://github.com/DefamationStation/SmartNpc/tree/fabric-26.4-snapshot-2

The original author is pla_is_me. GPLv3 and the upstream third-party notices apply.

The experimental personal-memory branch adds a resumable coal-gathering task,
bounded food acquisition, cooking prerequisites and a read-only shelter readiness
diagnostic. The current `3.0.0-fabric.26.4-snapshot-2.6-food` milestone lets an NPC
with a carried fishing rod use native fishing when fewer than four safe edible
items remain, including without a fishing interest. Cookable catches hand off
immediately to cooking. Acquisition attempts are bounded to 120 seconds of game
time with a 60-second retry delay; their local state is not persisted. Missing
rods are explicit blockers, with no automatic crafting or free supplies. The
food reserve is an inventory policy rather than a physiological hunger model;
existing eating for healing retains its role.

Cooking reuses native gathering, recipes, navigation, placement, pickup and
furnace output collection. Its retained stone survey now reaches `(4, 0, 0)` at
probe 175 instead of 665 using the same 16-probe scan budget, with unique full
coverage of horizontal radius 24 and y ±6 before wrapping. These are cursor
probe counts; gameplay timing improvement has not yet been measured. Ordinary
fishing bobbers are excluded from living-entity collision handling. Existing
animal/crop acquisition and external-station ownership limitations remain outside
this change's property guarantees.

The starter cabin includes
crafting and lighting alongside the bed, storage and furnace. Shelter status is
local structural/equipment evidence, not proof of autonomous house construction.
See [RESOURCE_TASKS.md](RESOURCE_TASKS.md) for commands and limitations,
and [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) for the approved direction.
The survival prototype also makes neutral encounters peaceful, records defensive
causes, attributes actual chest withdrawals, gathers cooking fuel automatically,
and restores legacy blueprint block states for 26.4. The main game remains disabled
while the complete survival-day behaviour is developed and tested.
The food milestone's targeted fixture is underway; these implementation notes do
not claim its runtime acceptance or a complete autonomous survival day. The root
NeoForge project remains unchanged.

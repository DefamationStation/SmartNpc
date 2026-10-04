# Fabric 26.4 Snapshot 2 port

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

The experimental personal-memory branch adds a first resumable coal-gathering
task. See [RESOURCE_TASKS.md](RESOURCE_TASKS.md) for commands and limitations,
and [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) for the approved direction.

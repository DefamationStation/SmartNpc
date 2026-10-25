# Fabric 26.4 Snapshot 5 port

Persistent survival journeys and copied-world tests: [M1 delivery and limits](verification/M1-RESULTS.md).
Saved-cabin approach recovery and verification: [building follow-up](verification/BUILDING-RESULTS.md).
Faster build/test/deployment: [release workflow](verification/RELEASE-WORKFLOW.md).

Ordinary daily resident routine and recovery: [daily results](verification/DAILY-RESULTS.md).

Cooking stall and real restart regression: [cooking results](verification/COOKING-RECOVERY-RESULTS.md).

Cabin completion fix and regression: [cabin results](verification/CABIN-RESULTS.md).

Snapshot 5 changes and current validation: [port results](verification/SNAPSHOT5-RESULTS.md).

Behaviour design: [player journey and automation reuse](PLAYER_JOURNEY.md),
[development roadmap](DEVELOPMENT_ROADMAP.md).
Current implementation milestone: [bounded food acquisition, cooking prerequisites and shelter diagnostic](RESOURCE_TASKS.md).
Current checks: [cooking resource-search fallback](verification/SEARCH-RESULTS.md).
Earlier workflow checks: [workflow handoffs and shared bow recovery](verification/WORKFLOW-RESULTS.md).
Independent review: [workflow audit and acceptance gaps](WORKFLOW_AUDIT.md).
Final follow-up review: [remaining workflow gaps and next steps](WORKFLOW_AUDIT_FOLLOWUP.md).
Earlier equipment checks: [rod and equipment verification](verification/EQUIPMENT-RESULTS.md).
Earlier food checks: [food acquisition verification](verification/FOOD-RESULTS.md).
Earlier prerequisite checks: [prerequisite verification](verification/PREREQUISITE-RESULTS.md).
Earlier completed checks: [daily routine verification](verification/ROUTINE-RESULTS.md).

Experimental, independently built Fabric port of Smart NPC 3.0.0. Tested in
fresh worlds on Minecraft **26.4 Snapshot 5**, including an actual saved-world
restart. See [validation results](verification/RESULTS.md) for the tested scope.

This module targets launcher version `26.4-snapshot-5` (internal version
`26.4-alpha.5`), Java 25 and Fabric Loader 0.19.5. The root project continues to
target NeoForge 26.1.2 without changes to its source or build configuration.

Requires Fabric API **0.165.0+26.4**. Install the jar from the fork's releases in
the `mods` directory on both client and server. The tested deployment is an
integrated singleplayer server; disposable dedicated-server scenarios also cover
the common-mod stack and saved journeys. Public multiplayer and settlements are
not covered by those single-resident tests.
This jar is not a NeoForge build and does not target other 26.4 snapshots.

Build from this directory with `./gradlew build` (Windows: `gradlew.bat build`).
The module shares the upstream Java/resources and provides explicit replacement
files only for platform or snapshot API differences. `prepareSources` merges
them in the build directory without modifying the upstream files.

For a maintenance build use `-PportBuildDirectory=<fresh-private-directory>`
to preserve previous artifacts. The default jar output is `fabric/build/libs/`. Configuration uses the original keys
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

## Snapshot 5 verification

`verification/run-snapshot5.ps1` uses the maintained full-stack test runner. Pass
`-Workspace`, an exact built `-ModJar`, a fresh `-TestName` and `-Suite`
(`Functional`, `Population`, `Cabin`, `Inventory`, `RenderBuddy`, `Settings`,
`CookingRecovery`, `CookHome`, `Food`, `FoodCheckpoint`, `FoodBurnCheckpoint`,
`Daily`, `DailyNight`, `DailyContinuous` or `SleepRecovery`). `-Replay`
reopens a completed functional/cabin fixture or a saved food checkpoint with the
same jar. Inspect completion and
failure markers; the launcher returning alone is not a pass. Functional tests run
at 60 ticks/second and restore 20 before saving; the population fixture runs three
simulated days at up to 200 ticks/second. These are correctness tests, not FPS
benchmarks. Prepared terrain/supplies are explicit test inputs.

`Cabin` keeps one builder's ordinary selector intact, supplies a declared initial
inventory and prepared ground, and requires every starter-cabin block to match
for 200 consecutive ticks. The observer stays outside the plot. A saved-world
replay checks the persisted home plan and all completed blocks again. This is
construction acceptance, not a claim of gathering all starting supplies from
scratch. Population and inventory fixtures move their observer away after UI
inspection so it cannot occupy future construction cells.

## Earlier verification

`./gradlew build` runs config/event and resource-memory tests and validates the access widener.
The Windows integration harness in `verification/run-smoke.ps1` creates its own
world, then `-Replay` reopens that test world. It requires a local offline
launcher argument template, an existing instance for its libraries/mods/options,
a Java 25 installation, and a separate output directory. The optional
`-FullModStack` copies the instance's mods and shader pack into that test instance.
It never copies or opens the source instance's saves. Inspect `complete.txt`,
`replay-complete.txt`, `failure.txt`, and the `SMARTNPC_PASS` log entries.

Upstream: https://github.com/PlaIsMe/SmartNpc (26.1.2 branch).
Port: https://github.com/DefamationStation/SmartNpc/tree/fabric-26.4-snapshot-5

The original author is pla_is_me. GPLv3 and the upstream third-party notices apply.

The experimental personal-memory branch adds a resumable coal-gathering task,
bounded food acquisition, cooking prerequisites and a read-only shelter readiness
diagnostic. The food milestone lets an NPC
with a carried fishing rod use native fishing when fewer than four safe edible
items remain, including without a fishing interest. Cookable catches hand off
immediately to cooking. Acquisition attempts are bounded to 120 seconds of game
time with a 60-second retry delay; their local state is not persisted. Missing
rod materials are explicit blockers. The equipment milestone
`3.0.0-fabric.26.4-snapshot-2.7-equipment` crafts a missing rod from two real string
and carried sticks/wood, reusing a reachable table or crafting/placing one from
enough carried wood. Native recipe transactions preserve materials on failure;
no string or equipment is granted. The
food reserve is an inventory policy rather than a physiological hunger model;
existing eating for healing retains its role.

Temporary mining and fishing equipment swaps now share one saved transaction.
Reload finishes the interrupted transfer while preserving original held stacks,
current tool wear and insertion remainders. This recovers items rather than live
navigation. The workflow milestone `3.0.0-fabric.26.4-snapshot-2.8-workflows` also
unifies bow swaps and migrates older nested bow/tool saves. Home storage now
recognises non-fisher food rod/string needs, retains reusable rods and protects
coal committed to an active task. Its food fixture starts without a table, requiring
the same actor to craft/place one before making its rod. See the current workflow
report for native checks and remaining ordinary-selector integration limits.

Cooking reuses native gathering, recipes, navigation, placement, pickup and
furnace output collection. Its retained stone survey now reaches `(4, 0, 0)` at
probe 175 instead of 665 using the same 16-probe scan budget, with unique full
coverage of horizontal radius 24 and y ±6 before wrapping. These are cursor
probe counts; fixture timings are recorded separately and do not establish a
general gameplay speedup. Ordinary
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
and restores legacy blueprint block states for 26.4. The installed build preserves the player’s existing population and scheduling
settings; wider survival milestones remain explicitly scoped below.
Fresh and saved-world targeted food fixtures pass; see
[food results](verification/FOOD-RESULTS.md) for those measurements and limits.
Fresh and saved-world equipment runs also pass the same-actor rod → catch → cooked
fish chain and temporary equipment conservation checks; see
[equipment results](verification/EQUIPMENT-RESULTS.md). The controlled resident daily routine and its evidence boundaries are described
in [daily results](verification/DAILY-RESULTS.md); a complete wilderness start
remains outstanding. The root
NeoForge project remains unchanged.


`CookingRecovery` exercises the exact crafting-table edge/pond-bank route that
stalled furnace placement. `Food` observes one actor craft/place a table, craft
a rod, catch actual native fish, pick them up and cook/collect them, with recipe,
rod wear, loot and fuel conservation assertions. `FoodCheckpoint` saves that
same actor after pickup with its cooking task pending; `FoodBurnCheckpoint`
saves while its furnace is burning. Reopen each using `-Replay` and require
`replay-complete.txt` with no `failure.txt`. Only the test observation ledger is
restored by the fixture: Minecraft saves/loads the original actor, inventory,
world blocks, furnace contents and task data. No fresh supplies are injected.
These targeted food fixtures retain the registered food goals, ordinary startup
and shared work admission, plus native swimming safety; unrelated jobs are
excluded. The full `Functional` fixture also runs prerequisite gathering checks.


`Daily` and `DailyNight` keep the resident's complete registered goal and target
selectors. They save during cooking or actual sleep and reopen the same actor
with `-Replay`. The prepared home/trees/water/tools/surplus and chest coal/string
are declared inputs. Natural clock advancement must produce supplies, gathered
wood, cooked food, night deposits, sleep, dawn work and a safe food reserve.
At dawn a declared environmental event removes one roof plank; only the NPC may
repair it from its real materials. The observer never moves or supplies it.
`DailyContinuous` exercises the same acceptance without a process interruption.
`SleepRecovery` leaves every normal goal enabled, verifies bounded release of
an unreachable bed approach, then opens the real enclosure entrance and requires
native sleep. `CookHome` retains native door opening as well as cooking/safety;
its furnace route must go through the actual cabin entrance. These checks use
isolated full-stack worlds and a peaceful environment, not the player's saves.

`HomeRepair` requires a funded one-plank repair without unrelated timber
harvesting. `HomePit` requires the ordinary resident to escape an enclosed pit
and reach its home bed. `Cabin` and its replay require the full starter cabin.

`HomeStorage` verifies selection of an already in-range chest stand before
navigation setup, followed by a real surplus deposit and sleep with every normal
goal enabled. `StorageRecovery` reopens an obstructed chest stand and requires a
real delayed retry. `HomeEntry` exercises the furnished cabin entrance.
`HomeRepairChurn` changes an irrelevant paper item's name while the NPC repairs
from its one initial plank, without harvesting extra timber. In-progress scans
retain their cursor through inventory changes; actual placement validates and
consumes current materials. A safe builder keeps its stand during the work delay.

Optional projects and new stockpile trips yield to committed cooking or a bounded
actionable food opportunity. Existing support/descent cleanup remains available.
Furnished home floors use normal room routing rather than passive pillaring;
genuine pits and explicit recovery requests retain bounded escape behaviour.
Night storage allows one successful pass and at most three delayed approaches,
with saved retry metadata. Every final required runtime case uses the exact
reported artifact; earlier failures remain separate historical evidence.

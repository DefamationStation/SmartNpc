# Cabin completion regression — 24 October 2026

Target: Minecraft **26.4 Snapshot 5**, internal **26.4-alpha.5**, Java 25.
Build: `3.0.0-fabric.26.4-snapshot-5.2-cabin`.
Artifact SHA-256: `533fb5533dabdbabd3d3e7945f28f2a4886177456b1d6dafcbd6cb11f95e418e`.

## Cause and correction

The builder's clearance search tested possible standing locations with a body
box expanded by 0.05 blocks in every direction. Its feet therefore extended
below the intended standing surface, intersecting the solid supporting floor.
Ordinary safe floor locations were rejected, leaving the builder trying to
move away from unfinished wall placements without a usable clearance location.
The remaining amenities waited behind that unfinished structural phase.

The corrected box keeps the same horizontal and head margin but its bottom
stays at foot height. Existing world-border, solid support, fluids, ceiling,
wall, entity and actual block-placement collision checks remain active. Movement
still uses native controls; the fix does not teleport the NPC or bypass material
consumption, collision checks, work admission or placement delays.

A separate fixture issue left the observer inside the house after opening the
NPC inventory. That could obstruct construction or recovery. Cabin acceptance
now keeps the observer outside the plot. Inventory/population fixtures move the
observer away after inspection. An observer outside the plot still reproduced
an unfinished cabin with the old production code, so observer interference
alone did not explain the production defect.

## Whole-cabin acceptance

The `Cabin` suite keeps one BUILDING NPC's entire ordinary goal selector intact.
It uses the full installed player stack, Photon and selected resource packs.
The declared starting inventory and prepared flat ground match the construction
scenario. The home plan is the 73-block starter cabin. No helper places any
blueprint block, moves the NPC, calls its build goal directly or adds supplies
after setup. A real geometry check accepts unobstructed supported ground and
rejects a solid wall before construction starts.

Three independent fresh worlds completed all required blocks:

| Trial | Tick confirming 73/73 blocks and 200 consecutive stable ticks |
|---|---:|
| Fresh 1 | 2,129 |
| Fresh 2 | 2,424 |
| Fresh 3 | 2,438 |

These are simulation ticks, including the 200-tick stability window. Trials use
up to 200 ticks/second for test speed; this is not an FPS benchmark. The builder
reports its completed home and proceeds to ordinary post-build behaviour.

Each world was then actually saved, the game closed, and the same world reopened
with the same tested artifact. All three replays recovered the original NPC and
home plan and verified 73/73 matching blocks for another 200 ticks.

A negative control uses the **old installed jar** with the focused geometry
fixture. It fails with `Standing clearance incorrectly rejects an unobstructed
solid floor`. The corrected build accepts that floor and rejects a wall. The
old observer-away whole-house trial also failed its strict completion deadline.
Earlier population trials and their incomplete outcomes remain in the historical
Snapshot 5 report; they have not been rewritten as successes.

## Preserved behaviour and regression scope

The candidate changes only the BuildHouseGoal class family and version metadata;
all other archived class/resource contents match the previous installed jar.
Nested NightConfig dependencies, bundled layouts, licensing and custom survival
controllers are retained. All 39 unit tests and access-widener validation pass.

Additional full-stack survival/replay and actual Buddy/rendering checks are
recorded with the installation validation receipt. Construction
acceptance establishes completing this cabin from carried materials. It does
not establish gathering every starting material from wilderness, every possible
layout/terrain, concurrent excavators respecting every construction site, or
completion of the broader independent-survival roadmap.

## Reproduce and maintain

Build the Fabric module into fresh private staging as described in its README.
Run `verification/run-snapshot5.ps1` with the exact jar and `-Suite Cabin`, then
rerun that fixture with `-Replay`. Both `cabin-complete.txt` and
`replay-complete.txt` must exist without `failure.txt`. A progress-only or idle
state is not a completed cabin. Keep the supporting-floor/wall checks and the
observer separation in future upgrades.

The maintained public fork is https://github.com/DefamationStation/SmartNpc,
branch `fabric-26.4-snapshot-5`, descended from the customized
`feature/personal-resource-memory` lineage. The original author remains
[pla_is_me](https://github.com/PlaIsMe/SmartNpc); original shared NeoForge sources
and GPLv3/NOTICE are preserved.


The wider saved-world regression exposed an older fixture assumption: a live
log target can remain at three while native leaf drops supply the two sticks,
allowing a table and pick from two logs. Test-only `SmartNpcWoodEvidence` now
observes actual stick item spawns near the declared trees and applies the real
7-plank or 9-plank recipe budget, while retaining actual harvest, movement,
closed job gates and no-preexisting-drop assertions. It neither changes loot
nor supplies items. The original failed replay remains private evidence.


## Separate broader replay limitation

The full fresh survival suite passed, including the table/rod/fish/cooked-food
chain. Its saved-world repeat later timed out in that food chain: the actor stood
at y286 on top of its y285 table with a carried furnace still unplaced. Its
cooking/fishing controllers, physics and resources are byte-identical to the
previous installed artifact; the selected-goal food fixture excludes
BuildHouseGoal. This is not presented as a passing replay or a new cabin-fix
result. The previously documented cooking/recovery roadmap remains unfinished.
All three **cabin-specific** saved-world replays passed. This release is scoped
to construction clearance, not to completing the broader survival planner.

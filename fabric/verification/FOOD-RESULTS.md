# Food acquisition and stone discovery checks

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.6-food`.
Companions: [player journey](../PLAYER_JOURNEY.md),
[resource and food tasks](../RESOURCE_TASKS.md).

## Implemented scope

A low carried food reserve can now admit the native fishing executor without
requiring a fishing profession. One adaptor shares that executor with normal
daily fishing; it does not add a competing casting or movement controller.
The NPC needs a real carried rod and suitable reachable loaded water. Four
safe staple items form the reserve threshold, counting actual hands/inventory.
Raw cookable food takes priority through the existing cooking intention.
Manual resource requests, danger, low health, sleep, following and escape work
take precedence. Attempts are capped at 2,400 game ticks with a 1,200-tick retry
delay. These local timers are not persisted; inventory determines need on reload.

Ordinary NPC fishing bobbers refuse living targets during collision, hit handling
and retrieval. They retain item hooks. The separate combat fishing goal is
unchanged. The first real bite exposed a snapshot-port defect: fishing loot
creation supplied `ATTACKING_ENTITY`, which Minecraft 26.4 rejects. Inspection
of the target game's `LootContextParamSets.FISHING` and vanilla hook bytecode
confirmed required `ORIGIN`/`TOOL` and optional `THIS_ENTITY`. The port now uses
that context, preserving the real rod, hook location/entity and fishing luck.

The retained stone cursor now searches weighted 3D shells. A foot-height offset
four blocks away is reached at probe 175 instead of 665, with the same 16-probe
native slice. Every coordinate within horizontal radius 24 and y ±6 remains
covered exactly once per cycle; unloaded probes still consume budget and the
scanner requests no chunks.

## Build and checks

Java 25 / Gradle 9.6 Fabric build passes with **34 unit tests**: the previous
configuration/event/memory/fuel/grievance/routine/cooking/shelter tests,
five progressive stone cursor tests and four food admission policy tests.
Cursor cases check all-coordinate uniqueness, increasing shell order, bounds,
wrap coverage and nearby discovery at several radii, including the maximum.

The fresh full-stack runtime run passes (`complete.txt`). Its new actor has a
non-fishing personality, one supplied real rod and no food, fuel, tools, logs
or stations. The fixture provides an open-sky pond with two source-water layers.
It retains actual registered startup/worker wrappers for the food adaptor and
pickup, without forcing a bite, invoking fishing delegates manually or gifting
loot. It observes a real bobber, checks living-target rejection and item-hook
allowance, and requires actual carried cod/salmon, rod wear and `COOK_FIRST`.

The first edible pickup and cooking handoff completed at **2,244 server ticks**
(112.2 seconds at 20 ticks/second), with rod damage 4. Completion is carried food,
not a bite flag or lifetime catch counter. The actor still had no cooking materials
and correctly handed off to the wood prerequisite; this does not prove this actor
cooked its catch or built up the entire four-item reserve.

The concurrent wood fixture completed at **1,192 ticks** (59.6 seconds). The
cooking fixture completed at **1,656 ticks** (82.8 seconds), with real tool wear,
eight stone blocks removed, eight cobblestone observed before crafting, placed
stations and two cooked beef. The preceding milestone's fresh cooking fixture
took 2,813 ticks (140.65 seconds). These are individual controlled fixture runs,
not a statistically controlled speedup or population benchmark; starting positions,
random decisions and concurrent worker occupancy can differ.

The saved-world replay also passes (`replay-complete.txt`). Its fishing handoff
took 449 ticks (22.45 seconds), wood prerequisites 1,105 ticks (55.25 seconds),
and cooking 1,723 ticks (86.15 seconds). The large catch-time difference reinforces
why individual fishing runs are not throughput benchmarks. This replay checks
saved NPC memory and reruns the native work fixtures; it does not prove that a
live fishing episode or its local deadline resumes across restart.

Two replay-fixture assumptions were corrected before the passing run: cooking
checkpoints must count a real pickaxe in hands as well as inventory, and repeated
coal fixtures must remove their previous disposable actors/items to avoid resource
competition. The pending cooking checkpoint preserves parent intent and actual
carried tools through entity serialization.

Recipe/output conservation, shelter, peaceful targeting/defence, theft attribution,
native sleep, coal pickup/return, inspector networking/spectator, bow/tool wear
and shared chunk-ticket regression checks also pass. All testing occurs in
disposable worlds with the full instance mod stack; natural spawning is disabled
only in test configuration and native worker limits remain in place.

## Remaining limits

There is no physiological hunger model: existing eating heals injuries, while
this change manages an inventory reserve. Rod acquisition/crafting, sustainable
food-source planning and a complete natural-world survival day remain unfinished.
The new adaptor does not broaden animal hunting or external crop harvesting;
those existing systems lack sufficient livestock/property checks. General dropped
item reservations and external station permissions remain gaps. The new count is
a vanilla staple allowlist, not a promise about arbitrary modded food components.
The existing tool-swap bookkeeping also retains a previous held item in local
memory without serialization; broader reload conservation for a nonempty previous
hand remains a follow-up. The checkpoint fixture starts with empty hands and
verifies the equipped pickaxe itself.

Main-game Smart NPC remains disabled. Production changes stay in Fabric overlays
and new files; upstream root source/NeoForge build remain unchanged against
upstream base `554e2a7`.

# Cooking prerequisites and shelter checks

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.5-prerequisites`.
Companion: [player journey](../PLAYER_JOURNEY.md).

## Change and scope

Raw food now creates a persistent cooking intention that resolves missing wood,
a starter pickaxe, eight furnace stones, a real furnace and fuel before handing
execution to the existing cooking goal. The intention records its current step,
dimension, origin, deadline and evidence of actual cooked output collected.
Native log/stone collectors retain navigation, tool use, breaking and pickup;
native server recipes consume actual ingredients. Failed crafting preparation
does not alter the real inventory. Optional gear stockpiling yields while cooking
prerequisites are missing, and crafting yields to native log/pillar recovery.

The runtime investigation found two stone delays. The old 16-probe search restarted
on each call, preventing a stationary NPC from finding the exposed wall four
blocks away. The Fabric overlay retains a bounded cursor across calls. After a
successful break the native goal also cleared its current target, then stopped
before consuming its connected-block queue. A cooking-only continuation allows
that pending handoff with native admission, phase, tool and safety checks.

Successful furnace interactions normally impose a random 400–799-tick cooldown.
A committed cooking intention uses 40–59 ticks after a successful interaction
so short-burning fuel can be replenished. Failed attempts keep their backoff.
Both output transfer paths now drop only the inventory remainder, fixing the
partial-insertion duplication exposed by a full-inventory test.

The starter cabin includes a crafting table and supported light. A read-only
`shelter` command distinguishes an unbuilt recorded plot, an incomplete structure,
a temporary refuge, a basic usable shelter and an unknown unloaded/oversized
site. This assesses actual blocks; it is not proof of autonomous construction.

All production changes are in Fabric overlays/new files. The upstream root
source and NeoForge build remain unchanged against upstream base `554e2a7`.
The main instance remains disabled; tests use isolated disposable worlds and
the full instance mod stack, with natural spawning disabled only in test config.

## Build and unit checks

Java 25 / Gradle 9.6 Fabric build passes. All **29 unit tests** pass:
configuration 2, event bridge 2, resource memory 4, fuel decisions 2,
grievances 4, daily routine 3, cooking prerequisites 5, progressive stone
search 4, shelter classification/bounds 3.

## Runtime method

The cooking actor starts with two raw beef and four oak logs, without tools,
stone, stations or coal. A bedrock floor supplies no harvestable crafting stone;
an exposed stone wall remains four blocks away. Real registered startup/worker
wrappers drive cooking, gathering and pickup. Competing daily jobs are removed
from these test actors, so this tests the prerequisite chain rather than a full
day with every other goal competing.

A separate food-only actor begins with two raw beef and a natural-looking oak
tree, without logs, tools, stone or stations. The test observes wood demand,
native tree breaking and pickup, a placed table and a crafted wooden pickaxe.
It stops at starter-tool completion; it is not a single empty-inventory actor
completing the entire cooking chain.

Both real output-transfer methods are additionally tested with four named
cooked beef and an otherwise full inventory. A matching stack of 63 receives
one and drops three; a stack of 64 drops all four. Quantities, components,
blocking stacks and completion evidence are checked. Failed table/ingredient/
capacity crafts preserve all original inventory slots and components.

## Fresh-world result

The full-stack fresh-world run passed (`complete.txt`). The food-only wood actor
completed at **1,030 server ticks**, or **51.5 seconds** at 20 ticks/second, with
three tree logs removed and picked up, a placed table and a real wooden pickaxe.
The cooking actor completed at **2,813 ticks**, or **140.65 seconds**, with actual
pickaxe durability loss, nine stone blocks removed, eight cobblestone observed in
inventory before crafting, a placed table/furnace and exactly two cooked beef.
The parent cooking intention ended and its automatic fuel child was inactive.
The ninth block illustrates that native mining can have pending drops before
inventory demand is satisfied; this is not an exact-eight-block extraction limit.

All four output conservation cases and five failed-craft conservation cases
passed. A pending cooking step, its origin/dimension/start time and real pickaxe
survived entity serialization. Shelter fixtures passed for an unbuilt plot,
the completed physical starter cabin, a roof gap, missing lighting and unloaded
chunks. Earlier peaceful encounters, assault/vandalism/theft attribution, native
sleep, coal collection, inspector networking/spectator, bow/tool use and shared
chunk-ticket checks also passed.

These are controlled fixture durations, not a seeded population benchmark or
a claim that every natural-world NPC completes within the same time.

## Saved-world restart

The saved-world replay also passed (`replay-complete.txt`), including NPC
attachment/experience, the pending coal request and observations, and defensive
evidence respecting its expiry. The repeated wood fixture completed at 975 ticks;
the cooking fixture completed at 1,445 ticks, with eight real stone blocks removed,
eight cobblestone observed and two cooked beef collected. The repeat is in an
existing world and is not a like-for-like fresh-world timing comparison.

Fixture setup removes loose items and previous actors only inside its disposable
test areas. An initial replay exposed leftover drops from the first run; the
cleanup prevents those drops from substituting for new collection assertions.
The final replay passes after that correction. The cooking checkpoint itself is
tested through actual entity serialization; a live, pending cooking intention
interrupted by a whole-world restart is not separately tested here.

## Practical limits

These fixtures do not establish general survival from world spawn, food hunting,
autonomous cabin construction, iron/diamond progression, farming, trading or
relationships. The shelter assessment is deliberately bounded and does not
prove mob safety, route safety or actual construction. Personal durable resource
coordinates currently cover coal; wood and stone use native local discovery.

Discovery and navigation still have visible latency. Worker bounds and loaded
chunk guards are enforced, but this is not a 16-NPC server tick performance
benchmark. General station ownership/permissions, old dimensionless home records,
remote station migration and every enchanted-tool/datapack combination remain
outside the tested scope. Cooking pauses on unavailable materials, unsafe state,
inaccessible stations or its time limit rather than manufacturing progress.

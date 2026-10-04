# Personal resource memory prototype

Branch: `feature/personal-resource-memory`. Experimental Fabric-only addition; the upstream NeoForge source tree and build remain untouched.

Each ticking NPC with AI enabled observes a rotating sample of nearby blocks: 24 probes per second within four blocks horizontally and two vertically. Only exposed coal ore and deepslate coal ore passing a collision ray from the NPC's eyes are recorded. This is omnidirectional perception, not a camera field-of-view simulation. A stationary scan covers the volume in about 17 seconds. Reads are restricted to loaded chunks, including the ray's intervening chunk columns.

At most 64 personal observations are retained, keyed by dimension and position, with a last-seen timestamp. Entries expire after three Minecraft days. Refreshing a deposit replaces its old observation; oldest entries are evicted first. Versioned data is saved in the NPC's existing persistent attachment. No chunk tickets or terrain generation are added by this feature; the mod's existing force-tick configuration still applies.

## Try a coal task

Operator commands, targeting one loaded NPC (a UUID can replace the selector):

```mcfunction
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] coal 10
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] status
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] shelter
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] cancel
```

`coal 10` records the current coal inventory count plus ten as the target. The NPC selects a remembered deposit, equips a pickaxe, walks toward it, mines using the existing block-breaking rules, collects actual drops and returns to where the request was made. Completion requires the target count still to be present on return. Coal consumed or lost during work must be replenished; this is a net inventory target, not a lifetime mining counter. Silk Touch ore does not satisfy an item-coal target. The prototype keeps the coal in the NPC inventory; chest delivery is later work.

The request survives an interruption or save/reload. Actual movement, tools and partial block-break progress are reconstructed on resume. Emergency/combat goals can preempt it; it uses the existing startup gate and work budget. Missing tools, absent deposits and inaccessible routes produce waiting states. Failed destinations receive a temporary retry delay so another remembered deposit can be attempted. Cancellation stops the task at the next goal update. Active requests cannot be silently replaced by the command.

## Current boundaries

- Observation is automatic. A safe edible reserve below four carried items can start bounded native fishing using an already carried rod, including for a non-fisher. Cookable food starts a bounded cooking intention that checks wood, crafting access, pickaxe, eight furnace stones, furnace and fuel as needed. Existing supplies/stations skip fulfilled needs. Remembered coal can become an automatic fuel child task; wood fuel at an existing furnace avoids requiring a pickaxe when no coal is known. Manual requests remain available and take precedence. The complete food/tool/shelter planner, storage delivery, trading and learning remain subsequent milestones.
- Automatic coal work pauses after one minute without completion and waits another minute before reconsidering. Manual requests remain resumable until cancelled. Cancellation also suppresses immediate automatic replanning. Timers and task purpose persist across saves.
- The task uses walking navigation and only considers remembered ore within 32 blocks. It does not tunnel, bridge, explore unknown terrain or port Baritone yet.
- Waiting tasks permit other routine goals to run. Those goals can consume resources; the task's inventory target accounts for that, but this is not yet a complete intention/prerequisite scheduler.
- Resource memory currently covers coal only. Separate NPC identities do not automatically share observations.
- Runtime checks use a controlled mining fixture. Long-route navigation, combat interruptions and large populations require broader validation before promoting this build to the default development instance.

## Peaceful survival prototype

Player/NPC/villager/golem targets require recorded defensive evidence. Prank hits, golem trolling, interrupting dancers with an attack and automatic chest raiding are disabled. Cautious NPCs treat visitors as neutral. Ownership reactions require a nearby owner that can see the offender; they grant no knowledge of remote events. Actual assault and tracked property damage can trigger defence; caution and existing ally rules still apply. Evidence is bounded to 16 offenders per NPC and expires after one minute without a new offence.

Opening an owned chest issues a rate-limited warning. A server-side hook compares its contents immediately before and after the player's real container input. Item/component totals distinguish removal from rearrangement and deposits. NPC inventory transfers report theft after items actually move. Shared/team access is respected by existing ally rules; a dedicated per-container permission system remains planned. Taking an item onto the cursor counts as removal in this first implementation. Returning supplies does not yet automatically reconcile a defensive encounter.

Idle NPCs queued for routine work take short attentive pauses instead of decorative random walks. Fresh configs cap natural spawns at four; existing configured values are preserved. The bundled legacy blueprint palettes now convert to 26.4 keys, restoring their blocks and orientations. An original 4×5 starter cabin includes a bed, chest, furnace, door, crafting table and wall torch. Full autonomous cabin construction and the multi-day survival loop are still to be validated.

## Cooking prerequisites milestone

Introduced in `3.0.0-fabric.26.4-snapshot-2.5-prerequisites`; carried forward in
`3.0.0-fabric.26.4-snapshot-2.6-food`. The intention
persists its current step, origin/dimension, start time and cooked-output evidence
in the NPC attachment. Wood and stone shortages feed the existing native
gathering goals. A narrow crafting goal finds a loaded visible nearby table or
places one from carried/recipe-crafted materials at a valid local site. Starter
pickaxe and furnace recipes use the target game's native recipe backend and real
inventory. Placement, navigation, tool selection, breaking, pickup and cooking
continue through Smart NPC's existing execution systems.

Cooking decisions are staggered and reconsidered at a 40-tick interval; local
station searches, table placement candidates and navigation attempts are bounded.
Successful furnace interactions during a committed cooking intention retry after
40–59 ticks, so short-burning fuel can be topped up without the ordinary
400–799-tick idle cooldown. Failed interactions retain their existing backoff.
Stone discovery retains a bounded cursor instead of repeatedly inspecting the
same first 16 positions. In the food milestone, shells ordered by
`max(abs(x), abs(z), 2 * abs(y))` prioritize nearby ground while covering every
coordinate within horizontal radius 24 and y ±6 once before wrapping. The
offset `(4, 0, 0)` appears at probe 175 instead of 665; the full foot-height
perimeter at radius four is covered within 179 probes instead of 669. These are
cursor probe counts, not measured gameplay timings. Each native scan still has
its existing 16-probe budget. A cooking-only continuation preserves the native
connected-stone queue between block breaks. The scanner requests no new chunks.
Danger, low health, healing, sleep and team-following suspend prerequisite work.
The cooking intention pauses after 6,000 game ticks, with a 1,200-tick retry
delay. Removing inputs without collecting cooked output reports a pause rather
than successful cooking. Completion records an actual native output transfer
that increased carried cooked-food stock. Full inventories, missing recipes,
unreachable stations and unavailable local resources remain honest blockers.
The cooking chain processes carried ingredients; food acquisition is the separate
bounded fishing path below. Neither generates resources while unloaded or
implements charcoal production as a durable task.

Prerequisite snapshots are inventory/station evidence rather than completed
navigation proofs: a carried furnace still needs placement, and a tracked/home
furnace can still have an unavailable interaction stand or route. Table discovery
is local and visible; native cooking retains its existing nearby-furnace discovery
and footprint policy. New temporary cooking furnace records include a dimension;
parent-task station facts require the cooking kind, matching dimension and a
loaded furnace within eight blocks. Native execution preserves old records with
no dimension but refuses an explicitly different dimension. Home records still
lack a dimension registry, and a general per-station permission/reservation
system and complete home migration remain outside this milestone.
Do not infer that every nearby station is owned or usable merely because the
planner has skipped its recipe.

## Food acquisition milestone

Current artifact version: `3.0.0-fabric.26.4-snapshot-2.6-food`. A food shortage is
a carried reserve of fewer than four safe edible items. This is an inventory
threshold, not a physiological hunger/saturation model. Existing eating for
healing remains the existing healing behaviour; this milestone adds no separate
hunger depletion or survival-health simulation.

The shortage can activate native fishing for an NPC that already carries a
fishing rod, even without a fishing interest. The native execution still needs
reachable suitable water and obtains actual fishing loot. A missing rod is an
explicit blocker: this milestone neither crafts one nor provides a free rod or
food. Raw cookable catches hand off immediately to the cooking prerequisites,
which must obtain real cooked output. Unsafe edible items do not satisfy the
safe reserve merely because they have a food component.

Acquisition attempts are bounded to 120 seconds of game time, with a 60-second
retry delay after an unsuccessful attempt. Attempt/retry state is local to the
running goal and does not persist across a save/reload. This differs from the
persisted cooking intention and coal task; restarting is not evidence of a
resumed food-acquisition checkpoint. The four-item reserve describes carried
stock, not lifetime catches or delivered chest supplies.

Ordinary NPC fishing casts refuse to hook or pull living entities; item hooks
remain available. Existing animal/crop acquisition behaviour and
external-station ownership limitations remain baseline behaviour; this milestone
does not extend or establish their property guarantees. General station
permissions/reservations, food-source planning and rod acquisition remain work
for later milestones.

The targeted fresh-world fixture passes: a non-fisher with a carried rod obtains
real edible fishing loot and hands it to cooking. See the
[food verification report](verification/FOOD-RESULTS.md) for durations and limits.
This does not establish a complete survival day or persistent acquisition
checkpoint. Main-game installation
remains disabled until the broader release gates are met.

## Shelter diagnostic

The `shelter` command reads the NPC's existing home record and the current loaded
blocks. It creates no home, places nothing and requests no chunks. A two-block
chunk margin is checked before world reads; assessment is capped at 16×16×8.
Unloaded or oversized homes return `UNKNOWN`, and unavailable checks are not
evidence that a feature is absent.

| Status | Meaning |
| --- | --- |
| `NO_HOME` | No home record exists. |
| `UNKNOWN` | Required chunks are unloaded or dimensions exceed the scan bound. |
| `INCOMPLETE` | Access, interior, cover/perimeter evidence or the observed hazard checks fail. |
| `TEMPORARY_REFUGE` | Enclosed accessible covered space passes those checks, but some bed/station/light evidence is missing. |
| `BASIC_SHELTER` | Those checks pass with a paired unoccupied usable bed, furnace, crafting table, storage and a light source. |

Access uses a flat ground-floor flood fill with solid support and two-block
clearance, treating complete wooden doors as operable. Roof evidence requires
a solid overhead block above every rectangular interior column; perimeter
evidence requires two blocks of solid wall or a complete wooden door. Beds need
matching halves, support, overhead clearance and a dimension bed rule that
permits sleep without destroying the bed. Time of day is deliberately excluded.
Stations must be adjacent to reachable ground-floor space; furnace/storage
checks require actual container block entities. Storage is conservatively limited
to single chests with clear overhead space and barrels.

Observed fluids, fire and selected damaging blocks count as hazards. A light
source is evidence of illumination equipment, not a spawn-proof lighting survey.
The check does not establish routes from the NPC to its home, mob safety, external
ownership permissions, stairs, multiple floors or every arbitrary house shape.
Conservative results can reject otherwise useful nonrectangular/glass structures.
`BASIC_SHELTER` is local structural/equipment evidence; it does not prove that the
NPC autonomously completed construction or sustained a full survival day.

Runtime acceptance remains separate from this description of code. Use isolated
fixtures for complete cabin, removed roof/wall, missing stations/light, blocked
entrance, bed obstruction and unloaded/oversized home cases. Main-game Smart NPC
installation remains disabled while broader behaviour is validated.

## Why Buddy commands work

The local Buddy implementation launches a second hidden Minecraft client and routes commands to that client's Baritone. Reusing the high-level task pattern is useful. Running a full client per Smart NPC is not the intended population architecture. A future Baritone adapter should be judged against this native backend using the same actor-isolation, survival-rule and performance checks.

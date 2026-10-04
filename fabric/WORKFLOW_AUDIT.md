# Independent NPC workflow audit

4 October 2026. Reviewed baseline: `feature/personal-resource-memory`, commit
`577ea4703b9d5f2dc6e40db00be05323054db737`. This is a source/design audit, not a
new gameplay or performance result. Fabric overlays are authoritative; inherited
`src/main/java` classes were read as execution context. No upstream source,
build, instance installation or save was changed by this review.

The parent task is concurrently changing equipment and the food-chain fixture.
The findings below describe the stated baseline; line numbers for files changed
after that commit may move. In particular, the bow finding is a baseline finding
and must be rechecked against the final implementation before release.

## What currently works, and what the evidence means

The food path already reuses native recipes, navigation, casting, loot, pickup
and furnaces. Its rod prerequisite consumes carried string/wood; cooking feeds
native log/stone collectors; coal work retains a durable inventory target and
return origin. The overlay registers these through the existing startup/worker
wrapper rather than giving each task a new movement controller.

The recorded equipment fixture demonstrates a controlled non-fisher
rod → fish → cooked-food chain. `verification/EQUIPMENT-RESULTS.md` records an
existing table, carried logs/string/fuel/furnace, fresh and reopened-world runs,
and preserved tool transfers. It explicitly does not establish live navigation
resumption, autonomous string acquisition or a survival day. The fixture retains
only selected food goals (`verification/SmartNpcFoodChainChecks.java:48,124`), so
normal home storage, sleep, daily work and competing priorities are absent from
that actor's selector. Its success therefore cannot close the arbitration gaps
below. Concurrent changes to that fixture need their own updated evidence.

`RESOURCE_TASKS.md`, `PLAYER_JOURNEY.md` and `DEVELOPMENT_ROADMAP.md` correctly
describe storage delivery, full shelter construction, source planning, personal
progression and multi-day life as future work. These should remain future claims.

## Actionable findings

### WF-01 — P1: committed cooking disables its native search fallbacks

**Classification:** integration gap introduced by the cooking commitment; the
underlying exploration/digging machinery already exists.

`entity/PlayerNpcEntity.java:841` makes job-interest gates false during cooking.
`SurvivalGatherGoal.java:43` bypasses that gate for the native local log/stone
collectors, but the log and stone exploration goals remain job-gated at
`entity/PlayerNpcEntity.java:2241,2260`. `DigDownForStoneGoal` is also job-gated
at line 2230. `SurvivalGatherGoal` has no exploration or digging successor.

**Trigger/outcome:** a survivor carries raw food but has no usable nearby tree or
exposed/reachable stone. Cooking commits and the local selector finds nothing.
Even a personality whose ordinary work could search farther now loses that
search path. It waits until the 6,000-tick cooking limit instead of seeking the
missing prerequisite. A non-builder/non-fisher has an even narrower route.

**Reuse opportunity:** admit existing bounded exploration/digging for the exact
committed prerequisite, sharing the authoritative collector's selected/pending
state. Avoid a broad gate bypass or independent proximity probe that stops
exploration before a collector can take over. Preserve loaded-chunk, protection,
weather, work admission and pillar/access recovery restrictions.

**Acceptance:** with raw food and a tree outside local collection reach, and
separately no exposed stone but a permitted native dig site, the registered
selector either makes resource progress or reports a specific bounded failure.
It must not remain idle solely because cooking closed its profession gate. Test
both an interested NPC and a jobless/non-fisher personality.

### WF-02 — P1: survival rods do not participate in existing storage policy

**Classification:** mismatch exposed by the new non-fisher food behavior; the
profession-only storage predicates are baseline behavior.

`entity/goal/ManageHomeBaseGoal.java:921` preserves carried rods/string only when
the NPC has `FISHING` interest (lines 930–932). `CheckHomeSuppliesGoal.java:588`
and 593 likewise request a rod/string only for that interest. Survival fishing
and rod crafting deliberately work for non-fishers.

**Trigger/outcome:** a non-fisher obtains a real food rod, returns to its owned
base with more than half its inventory slots occupied, and takes a nightly
deposit pass. The rod can enter storage. On the next food shortage, the NPC has
no rod, ignores that same owned stored rod/string, and either crafts another or
reports missing carried string. This wastes work and can strand a valid food
source behind its own storage policy. An already stored rod also fails to serve
a first survival need.

**Reuse opportunity:** make supply withdrawal and retention understand a
survival food dependency. Reuse the existing owned-chest path and transfer
machinery. Define which one rod/two string are a reserve; do not make every
surplus rod permanently undeployable to storage. Keep reserve policy independent
of temporary safety/combat state so an interruption cannot erase the reserve.

**Acceptance:** a non-fisher with low food withdraws a real owned stored rod or
the required string, then uses it. A nighttime deposit retains or deliberately
stores/retrieves its usable food rod without fabricating another. Player chests
must not become supply sources without permission.

### WF-03 — P2: a manual coal request and nightly storage can undo each other

**Classification:** integration mismatch between durable resource work and
baseline opportunistic deposits; inventory accounting itself is correct.

`GatherCoalGoal.java:94–99` requires the net target to remain in inventory at the
return origin and correctly restarts gathering when it is lower. The generic
deposit retention list (`ManageHomeBaseGoal.java:921–945`) has no active coal
request reservation. Nightly deposit is priority 4 while coal is priority 5
(`PlayerNpcEntity.java:2199,2202`).

**Trigger/outcome:** a resource task returns near an owned chest at night with
many occupied slots. The higher-priority native deposit transfers coal before
the request finishes. Replenishment then mines more coal although the requested
stock exists safely in the chest. This is not a duplication or false-success
bug; it is conflicting definitions of completion and stock ownership.

**Reuse opportunity:** reserve the task's required carried coal through return
completion, or introduce an explicit delivery mode whose completion evidence is
the actual chest transfer. The prototype currently promises carried inventory;
the narrow reserve is the smaller change. Do not silently reinterpret the
command as storage delivery.

**Acceptance:** a near-home nighttime request with a deposit-eligible inventory
finishes its declared carried target; surplus may still be stored. Consumption
or loss must still cause honest replenishment.

### WF-04 — P1 milestone gap: the rod chain has no resource-acquisition successor

**Classification:** acknowledged planned scope, not a new regression.

`FishingRodCraftGoal.java:99–112` reports unavailable carried string/wood and
returns false. `SurvivalGatherGoal.java:43` services only cooking. Thus “two
string available, no wood” does not request native logs for a non-fisher with no
raw food. `CheckHomeSuppliesGoal` can help some professions, but WF-02 limits that
reuse. No string source planner is implemented. The food-water exploration path
also remains `FISHING`-gated (`PlayerNpcEntity.java:2291–2300`), while the native
exploration predicate at `PlayerNpcFishingGoal.java:102,348–380` checks ordinary
daily fishing rather than the survival-demand override.

**Trigger/outcome:** an empty/limited-inventory non-fisher can honestly explain
that it lacks materials or reachable local water, but cannot progress toward
them. Giving it a rod does not solve water outside the native local search.

**Reuse opportunity:** first close owned-storage and wood/water fallback
handoffs using existing collectors and exploration; treat string acquisition
as a separately reviewed source policy. Do not invent free string or indiscriminate
spider targeting to claim an empty-inventory survival loop.

**Acceptance:** two string and accessible timber produce a real rod without
pre-supplied logs; a carried rod can seek a permitted loaded-water site when the
local search is empty. Missing string remains explicit until a real source path
is implemented and tested.

### WF-05 — P2: food demand and owned-supply checks use different reserve rules

**Classification:** baseline storage policy is insufficient for the new safe
food policy.

`SurvivalFishingGoal.java:47–69` counts a deliberate safe staple list and targets
four. `CheckHomeSuppliesGoal.java:562` counts any inventory item with a food
component against six. Its ordinary chest check is daily; only tool needs have
the ten-second recheck (`CheckHomeSuppliesGoal.java:131–139,544`).

**Trigger/outcome:** a chest has safe staples, but the NPC carries enough unsafe
food-component items to satisfy the native supply check. The survival planner
can still start fishing. Or it exhausts food after today's chest check and
fishes/crafts while its own chest has supplies. These are avoidable acquisitions,
not a guarantee of total starvation: native healing or other work may change
the state.

**Reuse opportunity:** share a safe-food count/classification with supply
withdrawal and add bounded rechecks for a changed urgent food need. Prefer
consuming available owned supplies before commissioning a new rod/food trip.

**Acceptance:** unsafe stock cannot suppress withdrawal of real safe staples;
depletion after a daily check can trigger a bounded revisit. Verify conservation
and avoid per-tick chest/path scans.

### WF-06 — P2: broader rest admission exposes an unbounded bed approach

**Classification:** inherited sleep implementation, newly applied to every
personality. `src/main/java/.../goal/SleepAtHomeGoal.java` is inherited unchanged;
use a Fabric overlay if fixing it.

Sleep is now a priority-2 work goal without a building gate
(`PlayerNpcEntity.java:2185`). The inherited goal chooses the first matching bed
halves at lines 166–181, checks no loaded-chunk/dimension/occupancy/clearance rule
at lines 198–214, and repeatedly calls `moveTo` at lines 88–93,122–126 without
checking route success. `sleepTicks` decreases only after the bed-distance
branch (line 103). Therefore its nominal maximum sleep time does not bound the
approach. This differs from the more conservative diagnostic in
`survival/ShelterReadiness.java`.

**Trigger/outcome:** a saved home has a structurally paired bed behind blocked
access. Sleep can own movement and retry until daylight or another interruption,
without reaching sleep or a fallback. It remains subject to the existing worker
lease; the audit does not claim permanent scheduler starvation. Home coordinates
also lack the general dimension migration discussed in `RESOURCE_TASKS.md`.

**Reuse opportunity:** use bounded bed stand/route validation and a no-progress
timeout with another candidate/refuge or explicit rest blocker. Keep this as a
separate home/rest change; an ungated sleep registration alone is not shelter
completion.

**Acceptance:** normal registered selectors sleep at a reachable owned bed, reject
blocked/occupied/unusable beds, and release failed approach within a declared
bound. Include non-builders, restart, dimension mismatch and loaded-chunk cases.
`ROUTINE-RESULTS.md` explicitly tests the delegate directly and does not establish
normal work admission or a long route.

### WF-07 — P2: prerequisite deadlines include suspended time

**Classification:** intentional bounded attempts with an unresolved checkpoint
policy, not resource corruption.

`SurvivalTasks.java:134,154` subtracts persisted game-time starts for the coal
child and cooking parent. Cooking hazards return early at lines 141–142 but do
not stop its clock. Fishing similarly keeps an absolute deadline during unsafe
state (`SurvivalFishingGoal.java:99,105,122–127`), and its timers are goal-local.

**Trigger/outcome:** combat, sleep/time skip, an unavailable worker lease or
unload consumes the attempt budget despite no admitted task work. On resumption,
the intention can pause and impose another delay. Save/reload reconstructs
cooking/coal but restarts fishing timing. This is documented as bounded game
time and must not be described as a fully resumed survival checkpoint.

**Decision/acceptance:** explicitly choose elapsed game-time deadlines versus
admitted/no-progress time. If retaining present policy, expose the pause/retry
cause and test a night skip and lease wait. If changing it, retain a separate
hard bound so persistent blockers cannot run forever.

### WF-08 — P2 baseline: bow and tool interruption recovery have separate owners

Baseline `PlayerNpcEntity.java:1575–1600,1830–1834,1994–2016` stores bow state
separately and restores the tool first, then the bow on load. Tool swaps use the
entity-owned transaction (`entity/ai/ToolAi.java:23–64`). Nested restoration can
change hand/source order even where stacks remain physically conserved; this
limitation is already stated in `EQUIPMENT-RESULTS.md`.

The parent task is implementing a shared path now. Treat this finding as a
baseline rationale, not a claim that the final edited code still has the defect.
Acceptance must cover real inventory/offhand/reserved sources, wear/components,
stale goal stops, interrupted save/load and exact item conservation across both
bow → tool and tool → bow transitions. Completing a transfer does not resume
live combat/navigation.

## Performance and release follow-up

Execution admission is preserved, but cooking deliberation itself is called
from each NPC's staggered post-tick observer (`SurvivalTasks.java:120–128,162`),
outside `StartupWorkGatedGoal`. The station snapshot can scan 405 local blocks
every 40 ticks (`CookingCraftGoal.java:57–74`), including NPCs without a worker
lease. This is bounded and loaded-only; no measured slowdown is established by
this audit. Measure its cost at queued populations before asserting that worker
limits cap all new prerequisite work. Shared geometry caches may help while
keeping individual knowledge and intentions separate.

The next useful integration fixture should retain the ordinary selector and
include a food shortage, missing wood, local-resource exhaustion, owned supply
withdrawal, nighttime deposit/rest, an interruption and restart on the same
actor. Record the actual task owner, blocker and real transfers at each handoff.
A second NPC should contest the same route/station under native worker limits.
Follow with suitable/resource-poor seeds and a multi-day soak. Keep main-game
installation disabled until the broader documented gates pass.

## Smallest reviewable next changes

1. Align non-fisher food rod/string retention and owned supply withdrawal
   (WF-02), with one real nighttime deposit/next-shortage fixture.
2. Reserve active coal through declared return completion (WF-03).
3. Admit existing search fallbacks for exact cooking prerequisites (WF-01),
   retaining authoritative target handoff and bounded native admission.

String sourcing, shelter construction, long-term progression and full expedition
delivery are larger milestones. Their absence should remain an explicit scope
boundary instead of being hidden by supplied-material fixtures.

## Follow-through in the workflow milestone

The implementation following this baseline audit addresses WF-02 with food-driven
rod/string supply admission, live avoidance of redundant string withdrawals and
rod/material retention during deposits. WF-03 now retains coal while its task is
active, releasing it for normal storage afterward. WF-08 now shares bow/tool
transactions and migrates older nested saves. The stronger food fixture removes
its preprovided table before the live actor starts.

See [workflow verification](verification/WORKFLOW-RESULTS.md) for current evidence
and limitations. Native predicate/accounting tests do not replace the nighttime
ordinary-selector scenario proposed above. WF-01 and the remaining findings stay
open; this follow-through does not change the reviewed baseline or retroactively
claim its gaps were already fixed.

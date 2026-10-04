# Rod prerequisites and equipment conservation

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.7-equipment`.

## Implemented scope

A low food reserve with no usable rod can now request a rod from real carried
string and wood/sticks. Missing two string is an explicit blocker. The NPC uses
loaded visible crafting tables, or crafts/places a real table when enough carried
wood is available for both table and rod. It uses native recipes and inventory
transactions: failed preparation or output insertion leaves real materials intact.
Station admission runs at least 40 ticks apart, with a maximum of four path starts
and sixteen placement candidates per attempt. Execution is capped at 200 ticks;
failed attempts retry after 100 ticks. Cooking, hazards, requested work and active
log-gathering recovery take precedence. No string, equipment or food is granted.

Temporary tool swaps share one entity-owned transaction. Saving stores the actual
previous held stack and tool source; loading completes the interrupted swap before
weapon cache repair. The recovered tool keeps its live wear and components. A
stale goal cannot restore another goal's equipment. Inventory overflow inserts what
fits and drops only the remainder, without replacing an occupied offhand source.
Native fishing now uses that transaction for rod equipment too, including a real
offhand rod, replacing its separate unsaved previous-hand field.
These are completed item transfers, not restoration of live goal/navigation state.

Rod table placement and cooking use the existing translated crafting state with
specific task details. Station path checks try alternative standing faces within
the same four-path budget rather than repeatedly selecting an unreachable face.

## Verification

The Java 25 Fabric build passes **39 unit tests**, including five new rod material
policy tests. They cover string shortages, combined table/stick costs, existing
table savings, preemption and invalid counts. Fresh and saved-world full-stack
runtime runs pass (`complete.txt` and `replay-complete.txt`).

The new same-actor fixture supplies two logs, two string, one coal, one carried
furnace and a real nearby table. It starts with no rod or food, retains registered
startup/work wrappers, and observes native crafting, casting, fishing item drops,
pickup, furnace input/burn/output and retained cooked stock. Bite timers and loot
are unmodified. Failed rod recipes with zero/one string must preserve actual
carried stacks and their components. This verifies a controlled material-to-food
chain; it does not establish an empty-inventory survival day.

Fresh same-actor completion took **809 server ticks** (40.45 seconds at 20 ticks
per second), including 80 ticks of retained cooked stock after completion. It
observed one real salmon drop, one cooked salmon, rod wear, furnace input/burning/
output and consumption of the supplied coal. Exact rod preparation used two string
and three sticks made from one log, leaving one log, two planks and one stick
before cooking. Remaining fuel ingredients may be admitted to the furnace by the
native routine; final fish and coal counts conserve actual inputs.

The saved-world same-actor chain completed in **1,051 ticks** (52.55 seconds),
observing one real cod drop and one retained cooked cod. It also passed exact
recipe, fish, coal and equipment checks. The replay reruns these work fixtures
after reopening the world and confirms saved NPC resource/social state; it does
not prove live fishing/navigation checkpoint resumption. Separate entity
round-trip checks cover interrupted tool and rod transfers themselves.

The existing fresh regression fixtures completed wood prerequisites at 1,205 ticks,
cooking at 1,721 ticks, and first edible fishing pickup at 2,536 ticks. These are
individual controlled runs, not population or statistical throughput benchmarks.
Replay wood, cooking and first edible fishing pickup took 1,087, 2,367 and 1,568
ticks respectively. Existing peaceful targeting/defence, witnessed property
offences, shelter, native sleep, coal gathering, bow wear, inspector networking/
spectator and shared chunk-ticket assertions also pass. Natural population is
disabled only in disposable test configuration; native worker limits stay intact.

Synchronous native entity checks pass for named bread and pickaxe recovery, live
tool wear/components, repeated serialization, stale goal stops, cross-goal swaps,
empty temporary hands, original axe/cache mirrors, fishing rod recovery from
inventory/offhand, repeated fishing stop and full-inventory partial insertion
(one coal inserted, exactly nine dropped). An initial launch used a class captured
before a late original-tool guard; the rebuilt frozen source passes that check.

## Remaining limits

Acquiring string and missing wood for the rod is still unfinished. Fishing attempt
timers remain local, and food is an inventory reserve rather than physiological
hunger. Existing livestock, crop and external-station permissions are not broadened
or solved. Combat weapon caches still mix mirrored and reserved item semantics;
this work does not claim a global migration of legacy inventory/cache data.
Temporary bow swaps still have a separate saved transaction: nested bow/tool
recovery can restore a different main-hand/source order, even when the physical
stacks are conserved. Unifying that path remains follow-up work.
The runtime food chain uses an existing table; automatic rod-table placement and
alternative standing-face recovery have bounded code and material checks, but no
dedicated end-to-end runtime claim here.
Multi-day survival, autonomous shelter construction and population performance
remain separate acceptance gates. Main-game installation stays disabled.

# Workflow handoffs and shared bow recovery

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.8-workflows`.
Independent baseline findings: [workflow audit](../WORKFLOW_AUDIT.md).

## Changes

The home supply/deposit workflow now recognises food-driven rods independently
of fishing profession. A survivor with fewer than four safe food items and no rod
can request a real stored rod or its two-string shortage through the existing
home supply selector. Finding a rod during a supply snapshot avoids subsequently
withdrawing redundant crafting string for a non-fisher. Existing fishermen keep
their profession reserve behavior. Chest discovery, ownership and navigation
permissions are unchanged; arbitrary external storage is not newly admitted.

Home deposits preserve reusable rods even when the food reserve is already ready.
While a rod is missing, string and carried wood/sticks with the string prerequisite
remain available for crafting. Coal remains carried while a resource request or
cooking intention is active, so storage cannot undo its inventory target. When
the task ends, normal coal deposits resume. This is conservative whole-stack
retention under a narrow need, not a general quantified reservation service.

Bow swaps now use the same entity-owned temporary equipment transaction as mining
and fishing. The last tool owner restores the original hand; stale stops cannot
reapply it. Saved wear/components and source destinations survive recovery, and
the native bow cooldown applies after a completed transfer. Older bow-only and
nested bow/tool fields migrate before cache repair, including a broken final
bow/tool where the surviving hand or original weapon cache identifies nesting.
Original-bow saves are covered, and a format marker prevents legacy ranged-hand
repair from replacing a recovered bow on later reloads. An old save with a broken
final hand, bow/tool predecessors and no identifying cache can represent two
different histories with identical fields. Recovery conserves both saved
survivors, but cannot guarantee original-hand identity or source order in that
irreducibly ambiguous case. Live navigation/combat does not resume from a swap.

## Verification

The Java 25 Fabric build passes **39 unit tests**. Fresh full-stack runtime and
saved-world replay both pass with the final original-bow migration checks and
swimming-safe fixtures (`.verification/smart-npc-workflows-v4`).
Synchronous native checks cover inventory/offhand/reserved-source bow nesting,
both interruption orders, original weapon-cache mirrors, custom components/live
wear, stale stops, repeated reload, legacy and broken-stack migration.

Home storage tests use actual chest contents and native need/transfer/deposit
methods. They require a non-fisher to withdraw one real rod, leave redundant
string in storage after finding it, withdraw exactly two string when no rod
exists, retain rod recipe supplies and preserve pending coal through deposits.
They also require ordinary coal transfer to resume after the task ends. These
checks test predicates and real accounting; they do not prove autonomous nighttime
navigation or a full selector arbitration scenario. The initial fixture needed
more than half its inventory occupied to exercise the unchanged deposit gate.

The strengthened same-actor food fixture now starts with **no crafting table**.
It supplies only two logs, two string, one coal and one carried furnace. Failed
rod recipe probes use a temporary table removed before the live actor starts.
The actor must craft/place its own table and craft its rod, then use unmodified
native bites/loot, pick up a fish and cook/retain it. Checks account for both logs,
placed table, rod, remaining planks/stick, exact string, fish and coal inputs.

The final fresh same-actor chain completed in **809 server ticks** (40.45 seconds at
20 ticks/second), including 80 ticks of retained cooked stock. It observed one
real salmon drop and one cooked salmon. The actor itself placed the table and
consumed both logs into real station/rod preparation: after rod crafting there
were one table, one rod, two planks and one stick, with no logs or string left.
Native cooking consumed the supplied coal; input/output checks conserve the fish.
This still supplies string, logs, coal and a furnace; it is not empty-inventory
survival or independent source acquisition.

Final fresh regression timings were wood prerequisites 1,104 ticks, cooking 1,654 ticks,
and first edible fishing pickup 1,564 ticks. These are individual controlled
fixture results, not a general speedup or population throughput benchmark.

The final saved-world chain completed in **842 ticks** (42.1 seconds at 20 ticks/second)
and retained one cooked cod. Its wood, cooking and first fishing regression
fixtures completed in 1,175, 1,805 and 897 ticks respectively. An earlier replay
failed the strict ingredient check because removing the previous fixture's
furnace released leftover fuel after the first item cleanup. The fixture now
cleans container drops after platform reset. A named stick in a real furnace
verifies that native container removal produces the drop and that cleanup removes
it before actor creation; ingredient and conservation assertions remain strict.

A final fresh regression also exposed missing fixture safety: the standalone
fishing selector retained food work but removed the registered `FloatGoal`, and
its actor drowned after following loot into the pond. Both fishing fixtures now
retain and assert that native safety goal. Production already registers it;
no swimming implementation, bite timer, loot override or forced catch was added.

## Remaining priorities

WF-01 remains open: cooking suppresses the profession gates that admit native
log/stone exploration and digging; the survival wrapper currently admits local
collectors only. A bounded fallback and authoritative collector handoff need
their own resource-poor-world test. String/wood acquisition for rods, physiology,
multi-day ordinary-selector life and autonomous home construction remain unfinished.
The audit also records food-source eligibility mismatch, suspended-time deadlines,
bed approach recovery and deliberation performance work. Shared weapon caches
still have legacy mirrored/reserved semantics outside the verified transactions.

Tests use disposable worlds with the full instance mod stack. Main-game Smart NPC
stays disabled, natural spawning is disabled only in test configuration, native
worker admission stays intact, and root NeoForge source/build remain unchanged.

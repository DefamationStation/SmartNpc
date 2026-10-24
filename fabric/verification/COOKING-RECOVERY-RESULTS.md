# Cooking route recovery — 24 October 2026

Target: Minecraft **26.4 Snapshot 5**, internal **26.4-alpha.5**, Java 25.
Build: `3.0.0-fabric.26.4-snapshot-5.3-cooking`.
Artifact SHA-256: `51de0a2cb2e97c25e72bec03d4188983207a9e5de31e1257da9cd06b4d163797`.

## Reproduction and correction

The previous food-chain replay left its actor at a crafting-table edge, carrying
raw fish, a furnace and coal. Cooking intent remained READY, but placement
repeatedly stopped. A simple tabletop scenario did not reproduce it; the exact
bank geometry and leftover recipe materials did. The old installed jar stalls
for 1,600 ticks in `CookingRecovery`. Its selected ground stand has a native
two-node route; a final diagnostic finds the 1% node budget reports `canReach=false`
even though its endpoint matches. The same route succeeds at 5% and 10%.

The active furnace route now gets a 5% node budget. The same scenario completes
with the corrected jar. Loaded-chunk corridor preflight, local follow range,
exact reachable endpoint, 20-tick repath delay, real movement, collision/support
checks, work admission and material/fuel consumption are preserved. Eligibility
still chooses physical candidates without doing a separate path search for each.
No per-tick broad scan, teleport, free fuel or synthetic output was added.

Archive entry comparison against the installed cabin fix changes only the
CookFoodGoal class family and version metadata. Every other class/resource and
nested dependency has identical contents, including the earlier cabin fix.
All **39 unit tests** and access-widener validation passed.

## Actual interrupted food-chain acceptance

Each fixture starts one actor with **two logs, two string, one carried furnace
and one coal**, plus prepared pond/bank terrain. It gets no table, rod or food.
The actor uses its registered goals to craft/place a table, craft a rod, cast,
catch actual native loot with rod wear, pick up fish, place/fuel a furnace and
collect cooked food. Assertions account for recipe materials, observed fish loot,
raw/cooked stock and the consumed coal. Cooked food remains for 80 ticks after
the task completes. Native swimming safety and startup/shared work admission
remain active; unrelated goals are excluded in these targeted fixtures.

Two separate worlds were actually saved and their game processes closed:

| Interruption | Checkpoint tick | Same actor completes at tick |
|---|---:|---:|
| Fish picked up, cooking pending, no furnace input | 492 | 897 |
| Furnace burning the real caught fish, no output yet | 940 | 1,077 |

On reopening, the original UUID, saved task and actual fish/furnace state are
checked before continuing. Only test observations (loot ledger and flags) are
rehydrated from a sidecar; Minecraft restores the entity, inventory and world.
No actor is replaced, moved or supplied again. Both resumed chains pass all
conservation and retention checks.

## Regression and limits

The full Functional suite covers cooking prerequisite gathering, wood/stone
search, tools, fishing, native loot/cooking, social/equipment behaviour, tickets,
networking and inspector UI. The initial fresh run passed the full chain, but
its replay exposed a fixture problem: an unrelated combat probe killed the
stationary NoAI serialization witness before saving. Only that witness now gets
permanent invulnerability after its actual player-damage evidence assertion;
the survival actors are unchanged. A fixture compile attempt used the old API
name; the corrected fixture uses Snapshot 5's `setPermanentlyInvulnerable`.
Historical failed outputs are retained. Final runtime outcomes are recorded in
the setup repository's `validation/smart-npc-cooking-20261024.json`.

The ordinary single-builder cabin regression completed 73/73 blocks and held
that result for 200 ticks at tick 2,501, then verified the same saved cabin for
another 200 ticks after reopening. The food-chain result proves the declared
material-to-food sequence and interrupted recovery, not gathering all initial
materials from wilderness, every terrain/layout, or indefinite self-sufficiency.

Preserve `CookingRecovery`, `FoodCheckpoint` and `FoodBurnCheckpoint` when
reconciling future Minecraft or upstream changes. The public runner documents
how to launch and reopen each disposable test; original player worlds are never
used.

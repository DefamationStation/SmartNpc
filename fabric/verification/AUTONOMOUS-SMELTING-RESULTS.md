# Automatic smelting fuel and command audit

1 November 2026, native Snapshot 6 `.10-autonomous-smelting`. All 122 unit tests
pass in final candidate
`781931e59d2650ba2a8f24ce2459e1c8511cddd538919fc993615021557addc2`.

The command audit found an implemented raw-metal furnace executor with no matching
automatic fuel demand. The same fuel backend was available through a manual coal
request and through ordinary food cooking. The correction extends the existing
automatic furnace prerequisite intention to supported raw ores and actual owned
furnace input/output. It preserves food urgency, the unfinished-shelter gate and
manual-task ownership. Already available output is collected before optional fuel.

The three direct furnace output-transfer paths now record exact output-item stock
before/after transfer. Only actually carried growth marks production; spilled
output or full inventory does not invent a completion. Metal cannot emit `COOK`
or grant `FOOD_PREPARED`; actual cooked food retains that path.

## Native evidence

- `npc6-smelting-negative` runs the earlier `.9` jar and fails with the expected
  missing automatic intention despite production observation of real visible coal.
  It remains an intentional failed outcome, not a passing release case.
- `npc6-autonomous-smelting` passes on the earlier integrated candidate identified
  below. Raw iron2 plus an
  empty usable owned furnace and sufficient food automatically produce a fuel
  intention. The NPC discovers, mines and picks up real coal, loads raw iron and
  fuel, smelts and collects actual iron. The pending-fuel and loaded-ore states
  each survive a separate process restart with the original actor and all81goals.
  Exactly two iron units and the actual worn pickaxe are conserved; food16 remains.
  `cookingProduced` is true and `FOOD_PREPARED` remains absent.

  That full-chain result uses the earlier integrated candidate
  `02e065558833f40b41df74629380ec5ec7a72ca6d9fadedc8b5df8410e9caf70`, not the final
  jar. `npc6-smelting-food-regression` passes on the same earlier candidate:
  raw pork2 through native coal/furnace work and both process restarts becomes
  cooked pork2, with the actual `FOOD_PREPARED` milestone and no lost portions.
- The ready-output control exposed a third output producer in
  `CheckHomeSuppliesGoal` that collected output without notifying the processing
  intention. Its real post-transfer callback is now connected. The original two
  ready-output trials remain failed receipts. A ready output taken before any
  formal intention starts does not need a fabricated intention/completion flag.
- `npc6-smelting-ready-final` passes on the final candidate. The NPC collects
  declared ready iron2, preserves both units and food16, grants no food milestone,
  and starts no fuel task or actual coal mining/furnace burn for100ticks after
  collection. Formal processing-intention completion is checked when admitted.

Decompressed-byte comparison proves the final candidate differs from the earlier
integrated candidate only in `CheckHomeSuppliesGoal` and its three nested classes;
all other classes/resources are identical. Earlier ore/food chain results retain
their original artifact identity. The changed producer has exact-final native
coverage; those earlier chains are not labeled final-jar reruns.

The scenario declares a furnished cabin, raw materials, food, tools and a bounded
supported generated yard. All subsequent actions come from ordinary production
AI. There are no NPC task commands, direct goal/event/observer calls, injected coal
memories, invented work or later inventory/movement grants. These declared starts
do not prove zero-start survival, the whole mining chain or the campaign.

`npc6-smelting-final-integration` passes on the exact final candidate with all28
player mods and17 Buddy mods: real food interaction/attribution, native inventory
GUI rendered40frames, real companion login stable120ticks, and all81 NPC goals.
Vanilla diagnostic assets are isolated from the user's unchanged graphics.
Installation and exact receipts are recorded in the setup repository's
`validation/smart-npc-autonomous-smelting-20261101.json`.

## Required workflow scope

`COMMAND_AUTOMATION_AUDIT.md` traces all seven command providers. Most commands
are diagnostics or explicit overrides over existing automatic paths. General
prepared-expedition purpose selection is not an implemented campaign planner.
The complete Nether/End/dragon chain remains unimplemented required work, rather
than behavior hidden behind a command. The root `AGENTS.md`, campaign sequence,
release workflow and roadmap now explicitly enforce the full autonomous goal.

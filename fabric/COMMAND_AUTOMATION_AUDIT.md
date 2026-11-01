# Command and autonomy audit — 1 November 2026

Reviewed the authoritative shared sources plus Fabric overlays and all seven
command providers, against ordinary selector/event callers. The product contract
is [the autonomous campaign](AUTONOMOUS_CAMPAIGN.md): no player task commands are
required from a fresh survival start through the Nether/End and dragon defeat.

| Command family under `/smart_npc` | Role | Ordinary automatic path / finding |
| --- | --- | --- |
| `survival ... coal` | Explicit resource-task override | Food fuel already starts from `SurvivalTasks.observe -> planFuel`, then the same `GatherCoalGoal`. Raw-metal smelting lacked equivalent fuel demand; the .10 correction extends that parent using actual supported inputs and furnace output. |
| `survival ... status/food/shelter` | Read-only diagnostics | Food, cooking, fishing, construction and shelter assessment run without these commands. |
| `journey ... home` | Return override | Native survival/trail/reserve rules, ordinary home return and the autonomous supply lifecycle already request returns themselves. |
| `journey ... go/cancel` | Explicit destination/cancellation override | Useful local supply journeys are selected automatically. An arbitrary operator destination is not a survival need or an autonomous purpose planner. |
| `expedition ... go/visit [water]` | Prepared-request diagnostics/override | Readiness and movement backends exist, but general purpose/destination discovery around this API is not implemented. This remains required C2 work. Automatically sending NPCs to arbitrary coordinates, or returning before doing useful work, would not fix it. |
| `progress`, `memory ... status`, `relationship ... status` | Read-only personal evidence | Native work, observations and exact-actor transactions produce saved evidence without these commands. |
| `spawn_player`, `tp` | Operator creation/teleport | Natural spawn registration exists. Teleporting the operator for inspection is not NPC travel or campaign acceptance. |
| `difficulty get/set` | World difficulty inspection/override | Independent of NPC-earned crafting. The legacy human/world-driven spawn provisioning is a separate progression debt. |
| `trace all on/off/status`, `resources`, `resource <name>` | Logging, CPU scheduler inspection/override | Normal worker probes, leases, fair queues and automatic limits do not require manual handoff. `resource` is a worker-slot override, not an inventory-material grant. |

Registration providers: `event/PlayerNpcCommandEvent`, `survival/SurvivalTasks`,
`survival/Journeys`, `survival/preparation/PreparedExpeditions`,
`survival/progression/NpcProgression`, `survival/memory/NpcMemories`, and
`survival/relationships/NpcRelationships`. Fabric overlays are authoritative for
the installed target; the root NeoForge variants are not additional installed
command providers.

## Concrete correction: ore-smelting fuel

`CookFoodGoal` and `FurnaceAi` already support raw metal processing, but the parent
fuel intention only recognized food. Thus an ore-only NPC could use the manual
coal backend yet fail to generate the same demand itself. Extend the existing
automatic prerequisite intention to supported raw ore and actual owned-furnace
input/output. Keep food urgency, shelter priority and explicit task ownership.
Ready output must be collected before gathering unnecessary extra fuel.

All three direct output-transfer paths (`FurnaceAi`, `CookFoodGoal` and
`CheckHomeSuppliesGoal`) record the exact output item's carried count
before/after transfer. Only actual carried growth marks production; smelted metal
cannot emit a cooked-food outcome or award `FOOD_PREPARED`. The targeted native
test has no NPC commands, direct goal/event calls or invented coal observations.
Its exact runtime outcomes and promotion are recorded separately in the setup
repository's `validation/smart-npc-autonomous-smelting-20261101.json`.

## Larger implementation gaps, not hidden commands

The complete campaign is not already present behind commands. No purpose-driven
portal construction/ignition/entry, cross-dimension campaign recovery, fortress
and blaze resource chain, eyes/stronghold/End-portal plan, or dragon strategy was
found. Generic mining, combat and flint-and-steel behaviors do not supply them.
These are required C3–C6 development, not features we can claim by adding a call.

The global `ProgressionUtil` follows player advancements/dimension changes and
world dragon history. `EquipmentDataLoader` and spawn inventory seeding use that
global difficulty, including internal equipment commands. These are not commands
the user must type, but their free spawn kits are not earned NPC progression.
Existing `CraftBasicGearGoal`/`CraftIronGearGoal` use actual materials and tools;
no human advancement gate was found on that execution. A tested independent
fresh-survivor provisioning policy remains necessary before full C0 acceptance.
Do not strip already loaded NPC inventories or pretend a granted kit was acquired.

The earlier roadmap sentence deferring NPCs completing the game has been removed.
The root `AGENTS.md`, campaign sequence and release workflow now make autonomous
campaign progression the governing requirement. All later task changes must
trace their automatic caller and be tested without issuing that task's command.

# Smart NPC: believable survival and progression

Status: approved direction, first resource-memory prototype in development, 2026-10-04. Most milestones below remain planned. See `RESOURCE_TASKS.md` for the implemented scope and verification limits.

## Direction

Confirmed user direction: **independent survival players who build, learn, trade and form relationships**. NPCs establish their own homes, learn useful skills, make plans, remember encounters and form relationships. Companionship and settlements grow from that foundation.

Autonomy guides feature decisions: NPCs have personal priorities, resources and commitments; player requests are considered alongside those priorities. Cooperation depends on circumstances and relationships, and NPCs can decline or negotiate. Trading and learning must support their own survival and ambitions. Settlement roles emerge from voluntary cooperation and specialisation. The first delivery remains the complete individual survival loop, followed by memory, personal progression and relationships.

The desired experience is observable cause and effect: an NPC returns from a dangerous expedition, stocks food, repairs its equipment, asks a trusted neighbour for help, and tries again better prepared. Personality changes how it responds. Its history survives a restart.

Human-like behaviour means consistent motivations, limited knowledge, credible mistakes, recovery and commitments. Believability should come from what NPCs do as well as what they say.

## Playtest concerns and required behaviour — 2026-10-04

The user observed a crowd of randomly spawned NPCs wandering without visible accomplishments, including an unprovoked attack. These are release blockers for the desired survival-player experience.

- **Peaceful encounters:** player-hunting or prank traits must never authorize an unprovoked attack on players, other NPCs or neutral village inhabitants. Retaliation requires evidence of assault, actual theft, damage to owned construction or defence of an attacked ally. Apply the rule to direct targeting, retained targets and attacks, not just the initial target selector. Cautious NPCs must not flee from every visitor.
- **Understandable property reactions:** opening, inspecting, rearranging or depositing in storage is distinct from taking supplies. Warn before an encounter can escalate. Attribute inventory removal to the real actor; do not blame the nearest player for hopper activity or another actor's action. Track actual ownership and permission, including shared storage. Base defence must refer to owned construction, not ordinary terrain inside a reserved plot. Add proportional responses, returning stolen goods, repairs, reconciliation and trusted access as relationships develop.
- **NPC-to-NPC consistency:** the same rules apply between NPCs. Disable default chest raiding until ownership and consent are available; personality must not manufacture a cycle of theft and revenge. Settlements need resource/project reservations and conflict resolution. Different personalities may react differently to a proven event without inventing offences.
- **Purpose and visible progress:** a survivor should be pursuing a concrete need or commitment: food, tools, shelter, storage, repair, a trade or a prepared expedition. Record why, the next prerequisite, completion evidence and the last failure. Short rests and recreation are credible; decorative wandering while queued for work must not disguise stalled execution. Repeated route/material failures should change the approach or expose a blocked condition.
- **Population and arrivals:** begin with a small configurable population. Existing config choices are preserved; fresh prototype configs now cap natural NPCs at four. Define where a newcomer came from, its initial survival priorities, home/work search and persistence. Expand population only after measuring successful useful activity and server cost, not just available spawn capacity.

Acceptance before re-enabling the main game: observe a small population over several Minecraft days in a suitable fresh world. Peaceful players and neighbouring NPCs receive no unsolicited attacks or theft. Every NPC completes useful survival work, rests for a reason, or explains and recovers from a real blocker. Include poor-resource terrain, interrupted work, save/reload, owned/shared/unowned containers, damage/repair, and verified offender identity. The main instance remains disabled until the user chooses to enable a validated build.

Implemented in this prototype: central defence evidence with expiry, removal of prank/raiding activation, peaceful cautious encounters, real chest-click theft attribution, first automatic cooking-fuel task, and attentive work pauses. The blueprint reader also had a concrete 26.4 compatibility defect: legacy `Name`/`Properties` keys silently decoded to air. Conversion now restores the bundled layouts; an original starter cabin is included. Constructed containers skip recorded inventories/loot tables to conserve earned resources. Broader survival-day behaviour and relationship responses remain milestones below.

## What exists already

Source inspection found:

- Jobs and traits in `PlayerNpcInterest`, including mining, farming, fishing, building, exploring, cautiousness, aggression and team formation. Interests are currently associated with configured names; appearance and identity should eventually be separable from behavioural state.
- Existing goals for gathering, crafting basic/iron equipment, cooking, healing, returning home, sleeping, managing farms and construction, escaping hazards and fighting.
- Daily job selection in `PlayerNpcEntity`; persisted homes, farms, teams and NPC data.
- Invitations, following and ally defence in `PlayerNpcTeamUpManager`.
- World-wide difficulty progression in `ProgressionUtil`/`ProgressionData`, driven by player dimension/advancement history and the dragon fight. This is distinct from individual NPC learning.
- A shared AI work budget, performance monitor, inspector, goal tracing and owned chunk tickets.
- A building layout loader whose initial port tests loaded **zero layouts**. Correcting legacy palette keys restored 67 bundled layouts; the prototype adds one starter cabin. Loading templates is a prerequisite, and complete resource-funded construction still needs its own long-running outcome test.

The Fabric port has passed targeted integration checks; long-running AI outcomes and dedicated-server operation still need validation. Reuse existing action implementations where practical, and verify their preconditions and completion reporting before adding planning above them.

## Milestones, in delivery order

### M0 — Establish a reliable simulation baseline

Deliver:

- Repeatable scenarios for gathering, crafting, eating, sleeping, mining, fishing, farming and construction, plus save/reload and chunk unload/reload during each activity.
- An inspector showing current intention, chosen action, reason, missing prerequisite, last failure and time since meaningful progress.
- Common action outcomes: completed, blocked, interrupted, failed. Bounded retries, recovery and explicit failure reasons.
- Versioned saved state with migration tests. Separate reversible feature settings for new systems. Disabling a feature preserves its stored data.
- CPU/pathfinding measurements and a dedicated-server test before claiming multiplayer readiness.

Acceptance: the baseline scenarios either complete or report a specific blocked condition without endless task switching, item duplication, ticket leaks or repeated identical failed searches. Record starting performance; do not invent improvement claims.

### M1 — One convincing survival day

Deliver one complete scenario using existing goals: obtain food, gather materials, craft tools, establish a camp, store supplies, rest, and resume the following day.

- Begin with food reserve, fatigue and perceived danger as distinct motivations. Preserve immediate healing and hazard responses. Add more needs only when they produce useful decisions.
- Add a small high-level decision layer that chooses an intention according to urgency, personal preference, expected benefit, travel cost and risk.
- Commit to a task long enough to finish useful work. Hunger, danger or a broken prerequisite can interrupt; an interrupted job retains a checkpoint.
- Add original starter layouts: emergency shelter, small cabin and basic storage/work area. Define material lists, footprint, access, bed location and construction order.
- Give NPCs varied work/rest schedules, short pauses, natural looking/turning behaviour and understandable reactions to weather and nightfall.
- Respect player property. NPC construction and harvesting need explicit ownership/permission rules; never infer permission merely from a building appearing empty.

Acceptance: across a fixed set of suitable seeds, a survivor with limited starter supplies completes the day/night cycle and resumes a sensible task after reload. In a resource-poor case it identifies the missing supply and changes approach. Protected player blocks and inventories remain intact. Quantify success rates after establishing the baseline.

### M2 — Memory and individual personality

Deliver:

- Persistent personal tendencies such as sociability, caution, generosity, curiosity and diligence, seeded per NPC UUID. Existing interests provide defaults and remain configurable.
- Bounded memories of observed resources, home, work sites, hazards, failed routes, help received and hostile encounters. Store source, time and confidence.
- Revalidation of stale knowledge. An exhausted mine or emptied chest triggers a new plan; it does not remain an infinite destination.
- Consistent differences: a cautious miner prepares an escape and retreats earlier; a curious explorer travels farther; a diligent builder finishes a section before a discretionary break.
- Event-based, contextual dialogue with per-NPC/player cooldowns. Statements must reflect actual knowledge and events.

Acceptance: comparable NPCs show repeatable personality differences across the same scenario, retain their histories after reload, and avoid repeatedly retrying known failures. NPCs cannot know unseen ore, remote events or another inventory without a defined observation/sharing mechanism. Memories remain within fixed storage limits.

### M3 — Earned personal progression

Deliver three separate concepts:

1. **World difficulty:** retain the existing world progression rules and settings.
2. **Personal capability:** demonstrated skills, known recipes, successful task history and equipment proficiency.
3. **Material readiness:** actual tools, food, shelter and resources available to this NPC or explicitly shared with it.

Initial progression path:

| Stage | Evidence of readiness | New behaviour |
|---|---|---|
| Survivor | Food source, safe rest and basic tools | Establish and maintain a camp |
| Established resident | Storage, repeatable supplies and work area | Maintain a home and replenish essentials |
| Specialist | Repeated successful work in a preferred profession | Plan larger projects; become a useful trading partner |
| Expedition-ready | Suitable equipment, provisions, destination knowledge and return plan | Organise mining/exploration trips or join a party |
| Veteran | Successful expeditions and sustained competence | Mentor, lead and undertake harder projects |

- Model advancement as a prerequisite graph; allow different professions to advance along different routes. These stages are design labels, not a forced identical life script.
- Award learning for verified outcomes. Repeatedly placing/breaking the same block, trading items back and forth or hitting allies must not provide unlimited progress.
- Start with practical improvements: better preparation, resource estimation, recipe knowledge, tool selection and fewer avoidable mistakes. Keep any numerical speed bonuses small and configurable.
- Add an optional survivor spawn preset with modest starting equipment; preserve the existing preset for compatibility. Existing NPCs migrate conservatively without wiping inventories or granting fabricated experience.
- Require real materials and recipes for upgrades. Defeating the dragon must not instantly equip every NPC with advanced gear.

Acceptance: a fresh NPC can visibly earn an upgrade through its own work; unavailable prerequisites stop advancement with a reason; progression and inventory survive restart; repetition exploits fail. Track learning separately from stored Minecraft XP until their relationship is deliberately designed.

### M4 — Relationships and cooperation

Deliver:

- Persistent, bounded relationships keyed by UUID: trust, familiarity and a small set of significant shared events.
- Trust changes through observed assistance, fair exchange, theft, injury and fulfilled/broken commitments. Include cooldowns and context so a trivial repeated gift cannot purchase unlimited loyalty.
- Extend existing teams with agreed roles, requests, shared expeditions, rescue and lending. Sharing an item does not silently transfer ownership of every chest.
- A small interaction menu: ask about plans, offer help, trade, invite, request a task and set shared-storage permissions.
- Concrete commitments such as “bring food before we leave.” NPCs can decline, explain missing resources, renegotiate or report failure.
- Build trades around local surplus and shortage, with atomic inventory transfer and no duplicated items. Start with direct exchange before introducing a larger economy.

Acceptance: relationships affect decisions, trading conserves items, promises complete or visibly fail, multiplayer players see consistent state, and two cooperating NPCs can complete a task that involves a real handoff.

### M5 — Homes that grow into small settlements

Deliver:

- Original cabin, farm, workshop and storage upgrades in a tested starter content pack.
- Projects with required materials, reserved work areas, staged construction and resumable completion.
- Shared task reservations for beds, workstations, resources and build sites so NPCs do not fight over the same target.
- Voluntary specialisation and surplus exchange; start with a pair, then a small group.
- Maintenance and repair informed by local needs. Expansion requires a reason and a configurable footprint/population limit.

Acceptance: a small group can build and maintain a functioning home area over several Minecraft days, with conserved materials, usable entrances, no overlapping reservations and no damage to protected player structures. Reload during construction must preserve progress without double-spending resources.

### M6 — Expeditions and emergent stories

Deliver:

- Prepare → depart → explore/work → return → recover as an explicit expedition lifecycle.
- Party roles, supply checks, rendezvous points, retreat triggers and a bounded lost-member recovery process.
- Requests generated from actual needs: replace a broken tool, escort a delivery, recover dropped equipment or secure a dangerous route.
- A short journal of significant events, milestones and relationships. Dialogue refers to those events.
- Consider Nether trips only after ordinary surface/cave expeditions reliably return. Decide configurable death, inheritance and replacement rules before long-term settlement persistence depends on them.

Acceptance: expeditions can succeed, retreat, or fail coherently; survivors remember the result; rewards correspond to actual actions and loot. Claims of rescue, construction or discovery must be backed by game state.

## Architecture and performance constraints

### Resource memory, reusable tasks and schematic construction

User direction: NPCs should individually record resources they encounter and use reusable operations such as mining a requested quantity or constructing a schematic, rather than requiring a separate script for every plan or building.

- Maintain a bounded personal knowledge store keyed by dimension and location/region: resource type, estimated availability, observation time, confidence, accessibility and known hazards. Record meaningful observations during existing perception/work updates. Existing ore-search caches are useful inputs; they are not yet a complete persistent personal memory system.
- Distinguish server access to world data from NPC knowledge. Loaded-chunk geometry may be shared for efficiency; resource knowledge is individual unless an NPC deliberately shares it. Recheck a remembered site before acting and mark depleted or inaccessible sites with an expiry/retry policy. Avoid full-world rescans and loading new chunks to populate memories.
- Keep persistent intentions and task checkpoints separately from resource observations. An NPC can remember “coal in the cave” while pursuing “stock fuel before smelting iron,” and later resume the interrupted task.
- Expose structured operations such as `AcquireResource(coal, additionalCount=10)`, `TravelTo(site)`, `Craft(recipe, count)` and `Build(layout, origin, rotation)`. Return progress, materials missing, completion and failure reasons. The same operations can have human-readable debug commands, but plans should call typed APIs rather than parse chat text.
- Quantity semantics must be explicit: ten additional coal items, ten coal items in inventory total and ten ore blocks broken are different requirements. Account for existing inventory, Fortune/Silk Touch, alternate ore variants, collected drops and storage delivery.
- Reuse Smart NPC's existing JSON and `.blueprint` layout loader/building execution first. Add a conversion/import path for a selected schematic format after confirming supported format versions, block-state rotation, multi-block placement, material accounting and unsupported blocks. Schematics supply the desired structure; reusable building logic supplies access, placement order, tool/material constraints, obstruction handling and resumability.

**Baritone integration requires a feasibility milestone.** Buddy already routes `@buddy` commands to a separate character: `BuddyLauncher` starts a second hidden Minecraft client and `BuddyRelay` invokes that client's Baritone. The primary Baritone instance in that process belongs to Buddy. The task-routing idea is directly applicable. However, its player context exposes `Minecraft` and `LocalPlayer`, while Smart NPC entities extend `PathfinderMob`; applying that execution backend to these entities still requires an actor adapter. The first prototype reuses Smart NPC's existing navigation, tool selection, mining and item pickup behind a durable task.

Prototype one NPC-specific execution backend behind the task interface: isolated actor context, movement/look control, inventory, mining/placement interactions, cancellation and resource-observation policy. Compare it with existing NPC navigation/mining using the same scenario. Gate broader adoption on correct actor isolation, independent concurrent tasks, survival-rule correctness, server compatibility and measured cost. A separate Minecraft client per NPC is not the target architecture for a population simulation.

First demonstration: an NPC observes an exposed coal deposit, remembers it across a save/reload, later decides it needs ten additional coal, equips a suitable tool, gathers the items, returns to storage and resumes a small schematic-based shelter project. If another actor depleted the deposit, it updates its memory and chooses another known source or an exploration task. Introduce this demonstration across M1–M3; it does not depend on committing to a full Baritone port.

- Keep action execution in existing goals. Add one high-level owner of intention/commitment, rather than a second controller competing with `GoalSelector` over movement, equipment and targets.
- Begin with a small scored decision model. Add bounded prerequisite planning for supply chains when the first survival scenario needs it; avoid a general-purpose planner rewrite at the outset.
- Keep new domain state and scoring logic separate from loader adapters. Use the Fabric module initially; expose narrow interfaces and versioned data so platform-neutral improvements can later be offered upstream without forcing a Fabric migration.
- Reconcile overlays when upstream changes. Do not copy the large NPC entity class again for every new subsystem.
- Proposed starting cadence: stagger routine deliberation roughly every 0.5–2 seconds or on a meaningful event; keep emergency reactions responsive on game ticks. Measure and tune rather than assuming this cadence fits every goal.
- Share bounded local observations where appropriate, limit searches and path retries, cache failed destinations with expiry, and retain the existing fair AI work budget.
- World reads/writes and entity mutations remain on the server thread. Only pure calculations over immutable snapshots are candidates for background work; revalidate before committing results.
- Loaded NPCs receive full action simulation. Unloaded NPCs initially retain plans without generating resources or executing work. Any later abstract simulation must have explicit conservation rules and must not force unexplored chunks to generate just to simulate daily life.
- Core decisions and progression run locally without an external AI service. Optional richer dialogue can be evaluated later, behind an interface that cannot invent authoritative world events or control inventory directly.

## Evaluation and release gates

Use fixed seeds, declared start equipment/personality, recorded configuration and isolated worlds. Measure 1, 8, 16, 32 and 64 active NPCs; these are test populations, not a demand that every population meets the same budget.

Track server median/p95/p99 tick time, incremental NPC CPU time, paths/searches per tick, memory, save growth, task-switch frequency, time stuck without progress, completion rates, survival and resource conservation. Run steady-state tests separately from active Distant Horizons terrain generation.

Provisional performance target on the current development machine: with 16 active NPCs in generated terrain, keep the new decision layer's p95 incremental cost within 5 ms and total server p95 below 40 ms where the baseline permits it. Confirm or revise this target after M0 measurements; it is not an existing result or a promise for all hardware.

Every milestone must pass migration/save-load tests, targeted gameplay scenarios and a multi-day soak before promotion beyond experimental. Include damage/death, sleep time-skips, invalidated paths, full inventories, missing resources, unavailable crafting stations, unloaded chunks, competing NPCs and protected-property cases.

Evaluate believability with short observed play sessions as well as telemetry: can a player explain what the NPC wants, why it changed plans, and how its history affected the choice?

## Player journey and current routine milestone

Use [the researched player journey](PLAYER_JOURNEY.md) to guide prerequisites,
base timing, repeatable resource trips, farm investment and capability milestones.
It distinguishes proposed NPC policy from a universal human progression and
records the Baritone/Buddy/Mineflayer reuse assessment.

The routine milestone initialises jobs after late spawn or a missed morning window
and makes existing home sleep independent of a building interest. See
[routine verification](verification/ROUTINE-RESULTS.md). This is a
targeted foundation improvement; full survival, shelter construction and earned
progression remain separate acceptance gates in that document.

## First implementation backlog

The current prerequisite milestone (`3.0.0-fabric.26.4-snapshot-2.5-prerequisites`)
adds a bounded cooking intention for wood → crafting access/pickaxe → furnace
stone → furnace → fuel → actual cooked output, skipping prerequisites already
fulfilled by carried supplies or usable stations. It reuses native gathering,
recipe, movement, placement, pickup and furnace systems rather than introducing
a second general execution controller. Persistent status names missing supplies
and records actual output collection; no complete survival-day claim follows
from this one chain.

The shelter diagnostic separately checks an existing home against bounded loaded
ground-floor geometry, actual stations, sleeping access, light and selected block
hazards. Temporary refuge and basic equipped shelter have distinct statuses.
The starter cabin now has crafting and lighting space, but assessing a fixture
does not prove autonomous construction. See [resource task scope](RESOURCE_TASKS.md)
for exact checks and limitations. Runtime results for this milestone must be
recorded before claiming its acceptance; main-game installation stays disabled.

Keep each item separately reviewable and gated:

1. Add a repeatable survival scenario and missing-state diagnostics; capture performance and behavioural baselines.
2. Introduce versioned personal state plus action outcomes, with old saves preserving existing behaviour by default.
3. Add intention selection, task commitment and bounded recovery for food → tools → shelter only.
4. Supply and validate one starter shelter layout and its real material chain.
5. Add a checkpointed day/night routine and inspector explanations; run the scenario across several suitable/resource-poor seeds.
6. Add remembered work sites and failures, then personal capability milestones. Expand jobs only after this loop is reliable.

The first demonstrable release should let one NPC start with limited supplies, explain its immediate plan, establish a usable shelter, sustain itself and resume correctly after a restart. Date estimates should follow that first measured implementation; the existing goals' reliability and content requirements are the main uncertainties.

## Deferred until the foundation works

Large cities, faction wars, unrestricted player-base modification, NPCs completing the whole game autonomously, reproduction/family simulation, and fully simulated off-screen economies. These multiply state and failure modes before the basic personal survival loop has earned confidence.

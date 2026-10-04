# Independent survival players: journey and automation design

Research and design review: 4 October 2026. Target implementation: experimental
Fabric 26.4 Snapshot 2 branch. Companion: [development roadmap](DEVELOPMENT_ROADMAP.md).

## What this document describes

An NPC should have a life that a nearby player can understand: acquire supplies,
establish a home, improve it, maintain food and tools, venture out for a reason,
return with something useful, and build relationships through actual encounters.
It should be possible to recognise yesterday's unfinished project today.

Minecraft is a sandbox with building and exploration alongside survival. Official
starter guidance emphasises food and shelter; first-night guidance permits a
simple enclosure or temporary underground refuge. These support early security
before expensive projects. They do not establish one compulsory progression
order. [Minecraft starter guide](https://www.minecraft.net/en-us/article/how-minecraft),
[first-night guide](https://www.minecraft.net/en-us/article/how-survive-your-first-night-minecraft).

The stages, priorities and example days below are **our proposed NPC design**,
informed by those mechanics and guides. They are not measurements of what most
human players do. A builder may settle before finding iron; a traveller may use
temporary camps; a farmer may develop food before armour. Finding useful supplies
through consensual trade can change the route. Every personality still needs
basic safety, supplies and a reason to move.

## Journey through a new world

| Stage | Immediate purpose | Typical work | Evidence that the next stage is practical |
| --- | --- | --- | --- |
| Arrival | Understand nearby opportunities and survive | Observe reachable trees, food, water, exposed stone and safe places; acquire modest supplies | Can obtain wood and food, or has a specific remembered place to try |
| Wood | Enable resource gathering | Logs → planks/sticks, crafting access, basic pickaxe; collect food | A usable pickaxe and a reachable stone source, with food or a food plan |
| Stone | Establish a dependable local foothold | Stone tools, furnace, cooking/lighting supplies, temporary shelter; choose a home site | Suitable tools, food reserve, recoverable shelter and a return route |
| Early iron | Improve safety and utility | Mine/smelt iron, prioritise tools and useful protection; furnish a modest base | Real iron equipment, fuel, food and a usable home, rather than an age label alone |
| Established base | Sustain repeatable daily life | Storage, crops/animals where viable, timber renewal, repair, supply trips, trade | Food replenishes, tools can be replaced, owned supplies are accessible |
| Diamond | Prepare more demanding projects and trips | Deliberate mining expeditions, useful equipment upgrades, enchanting preparation | Appropriate equipment and supplies, established return/deposit routine |
| Advanced projects | Pursue personal ambitions | Nether/End preparation, transport, specialist farms, larger builds, settlement cooperation | Project-specific prerequisites and tested execution; these are later milestones |

Treat wood → stone → iron → diamond as a useful capability progression, not a
clock. Do not equip an NPC with iron because the human reached an advancement,
or unlock an age merely because time passed. Current spawn provisioning still
needs an audit before claiming fully earned progression. Track what the NPC
actually crafted, obtained, used successfully, or learned from a real teacher.

### Arrival and the wooden phase

The first decision should name a shortage: “I need a pickaxe to obtain stone”,
“I need food before leaving”, or “Night is near; find a refuge”. Scan a bounded
local area and remember useful visible locations. Unknown terrain remains unknown.

Use a small initial task: collect sufficient logs for tools and crafting access,
then stop. Consume actual materials through the existing crafting routines.
Gathering should include item collection, not just breaking a block and leaving
the drops behind. If a tree is unreachable or belongs to someone else's build,
choose another remembered opportunity or a bounded scouting trip.

An NPC that spawns in the afternoon or loads after a time skip must initialise
its daily assignment immediately. Personality preferences must not leave it
waiting until tomorrow's morning roll. Routine work remains subject to the
existing startup and fair-work budget; combat and survival emergencies take priority.

### Stone: tools, cooking and a first safe place

A basic pickaxe enables a small cobblestone supply. Prepare a furnace and usable
fuel when food or smelting calls for them. Do not require coal if legitimate
alternative fuel is already available; charcoal is a useful planned fallback
when wood is available but coal is unknown. Charcoal acquisition is not yet
implemented as a durable task in this branch.

Start shelter work here when conditions make it useful. Early iron is also a
reasonable time to turn a temporary camp into a permanent home. Neither choice
requires diamonds. Prioritise a reachable entrance, covered space, lighting,
storage, crafting/cooking access and an owned sleeping place where available.
Missing wool must not prevent construction of a basic refuge.

Choose a small layout whose bill of materials is affordable. Reserve a tool and
food allowance before committing all wood or stone to construction. Build only
on an acceptable site; reject occupied/claimed property. Break a project into
material batches, place real blocks, and record its remaining work. A matching
layout is a construction target, not permission to copy its stored inventory.

“Has a home record” is insufficient evidence of shelter. A future readiness check
must inspect entrance access, usable interior, hazards, light, surviving stations
and sleeping access. The current starter cabin has bed, chest and furnace but
still needs a light/crafting plan and a complete construction test.

### Iron: useful upgrades and a dependable home

Iron supports tools, protection and utility items. The official iron article
describes its many equipment and crafting uses. Allocate scarce ingots according
to the current project rather than crafting every possible item at once.
[Minecraft: iron ingots](https://www.minecraft.net/en-us/article/taking-inventory--iron-ignot).

Suggested design priorities: a suitable mining tool when the next resource
requires it; protection for a risky trip; a bucket when irrigation or another
water task needs it; replacement tools when durability is low. These are NPC
policy choices, not a universal player recipe sequence. Use the target version's
recipe and harvest checks instead of hard-coded historical assumptions.

By this phase, home should be an actual destination: unload collected supplies,
cook/smelt, craft, check the next shortage, maintain the structure and rest.
Repeated resource trips need a start location, destination, desired inventory
delta, travel limit, return condition and deposit outcome. A full inventory is
a reason to return or defer, never permission to delete items.

### Established base: farms, maintenance and personal projects

Once food, tools and shelter are workable, reduce repeated gathering through
renewable supplies. Begin with one small crop plot or another locally viable
food source. Remember water and the crop source; obtain seeds and a hoe, prepare
the plot, plant, wait for growth, harvest and reserve planting stock. Official
guidance describes hoed farmland, water, light and seeds; growth and yield still
need verification in our target game version.
[Minecraft: seeds](https://www.minecraft.net/nb-no/article/taking-inventory--seeds),
[Minecraft: farmland](https://www.minecraft.net/nb-no/article/block-week-farmland).

An immature farm is a future investment, not available food. Keep another food
plan while it grows. Fishing, gathering or animal husbandry can fit different
locations and personalities. Plant replacement saplings where allowed. Prefer
small dependable systems before redstone factories or specialist mob farms.

Example rhythm: morning check supplies and plan a trip; gather known shortages;
return when the target is met, danger increases or inventory fills; store and
process goods; work on the home or farm; rest at night. Weather, attacks, hunger,
broken tools and interrupted projects change the rhythm. Rest is available to
all personalities, independent of the selected job.

Relationships develop through remembered encounters: trade, assistance, shared
work, damage and repayment. Neighbours and the human are neutral by default.
Opening a chest is not theft; moving items inside it is not theft. An actual
unauthorised removal or damage to tracked constructed property can provoke a
bounded defensive response. Later work should add permissions, restitution and
de-escalation, with the same rules for NPCs and players.

### Diamonds and later adventures

Diamonds should emerge from a purposeful expedition after the NPC can support
that trip. Leave with appropriate tools, food, available inventory space and a
known return destination. Mark failed routes and hazards; do not repeatedly
retry a dangerous mine just because it has the highest theoretical reward.

Separate permanent achievements (“successfully mined this resource before”)
from present readiness (“currently owns the required tool”). Losing equipment
can require recovery without erasing experience. Upgrades should improve a
specific ability or project and consume real resources. No exact ore depth or
recipe quantities are prescribed here; validate them against the current snapshot.

Later journeys can include enchanting, Nether resources, transport and shared
settlements. Each needs its own capability and safety checks. Autonomous boss
completion and unrestricted industrial farms remain deferred until basic daily
survival is reliable. A contented farmer or builder need not seek every dimension.

## How intentions become actions

Use one owner of a committed intention, with existing goals performing the work.
A proposed decision order is emergency → immediate food/rest → tool replacement
→ recover shelter → committed project shortage → renewable supplies → preferred
job/exploration. Scores and thresholds remain design work. Re-evaluate meaningful
interruptions, not every tick, and explain any change in the inspector.

For example: “cook food” → usable furnace → usable fuel → acquire two coal from
a remembered exposed deposit → equip suitable pickaxe → navigate/mine/pick up
→ return → cook. The first coal prerequisite already exists. Missing furnace or
tool currently produces an explanation; it does not yet recursively schedule
their supply chains. Next work must close those explicit gaps.

Persist intention, quantity semantics, prerequisite, origin/return site,
observations, failure cooldowns and completion evidence. Positions include a
dimension and timestamp. Revalidate remembered resources and property access
before interacting. Record observed depletion or failure, and bound memory and
retries. Never manufacture progress while the actor is unloaded.

## Reuse assessment

| Existing system | Reusable capability | Decision for this branch |
| --- | --- | --- |
| Smart NPC native goals | Tool selection, real breaking/placing, pickup, crafting, cooking, home construction, crops, sleep and navigation | First execution backend. Improve arbitration and outcomes before replacing working primitives |
| Bundled JSON / Structurize layouts | Existing structure catalogue and required-material planning | Reuse as build targets; verify target block states, orientation and real material use |
| Local Baritone Buddy | Commands routed to an independently controlled actor, mining errands, gather workflows, shared schematics | Useful behavioural reference and separate comparison actor; no new integration claimed |
| Baritone | Mining quantities, path goals, schematic building, bounded farming, cancellation | Potential backend after an entity adapter feasibility test; reuse existing formats and processes where possible |
| Mineflayer + collectblock/pathfinder | Collection combines travel, tools, digging and pickup; configurable movement and inventory handling | Useful reference for action contracts and external bot comparisons; not a Java server-mob drop-in |
| Voyager | Reusable skills, curriculum and feedback-driven improvement | Reference for compositional skills and outcomes; no external LLM dependency for core NPC survival |

Baritone documents mining, schematic building and farming commands. This is
strong reason to investigate its existing processes before writing another
schematic engine. A command alone does not provide our project intent,
ownership policy, personal memory or verified completion.
[Baritone usage](https://github.com/cabaletta/baritone/blob/master/USAGE.md).

The upstream player-context interface is tied to a Minecraft client/player.
Our local snapshot adaptation specifically expects `Minecraft`, `LocalPlayer`
and client-world operations; Smart NPC is a server-side `PathfinderMob`.
Buddy works because `BuddyLauncher` runs a separate hidden client and the relay
addresses that client's Baritone instance. Its current launch configuration
sets a 1536 MB maximum heap per client; that is a ceiling, not measured usage.
Launching a full client for every population NPC is not the chosen backend.
[Upstream context source](https://github.com/cabaletta/baritone/blob/master/src/api/java/baritone/api/utils/IPlayerContext.java).
Local evidence: `baritone/src/api/java/baritone/api/utils/IPlayerContext.java`,
`baritone/src/main/java/baritone/buddy/BuddyLauncher.java` in the sibling repository.

Before adopting Baritone for mobs, prototype one adapter with one actor and a
short path plus real mining. Audit player/controller assumptions, threading,
world queries, equipment, placement, cancellation and resource accounting.
Keep decisions separate from execution so this backend can be exchanged without
rewriting progression. Do not forward NPC requests to the human's primary
Baritone instance. Do not expose a shared world cache as personal knowledge.
If adapter cost is excessive, keep native mob execution and reuse interoperable
schematic inputs and proven algorithms where their licences permit.

Mineflayer's collection API illustrates the right unit of work: gather blocks
and drops, with movement policy, tools and full-inventory handling. Its protocol
bot environment differs from our entities; current 26.4 compatibility has not
been established. [Collectblock API](https://github.com/PrismarineJS/mineflayer-collectblock/blob/master/docs/api.md?plain=1),
[pathfinder source](https://github.com/PrismarineJS/mineflayer-pathfinder/blob/master/readme.md).
Voyager demonstrates a growing executable skill library and outcome feedback,
but its documented system uses GPT-4 and an external API. We adopt the design
lesson of composable, verified skills, not that deployment requirement.
[Voyager repository](https://github.com/MineDojo/Voyager).

## Milestones and observable acceptance

1. **Routine reliability:** jobs initialise after late spawn/unload/time skips;
   selected work stays stable within the day; all personalities can use home sleep.
2. **First usable home:** finish the starter shelter from supplied real materials,
   verify access/light/stations, then prove gathering missing materials end to end.
3. **Survival prerequisite chain:** connect missing wood, tools, stone, furnace
   and fuel to bounded tasks; add charcoal fallback; show exactly why work paused.
4. **Repeatable day:** complete food, supply trip, deposit, build/farm and rest
   across several days and a restart, with differing personalities and resources.
5. **Personal progression:** persist proven skills/capabilities; audit spawned
   equipment; distinguish historical achievement from current usable equipment.
6. **Relationships and advanced automation:** consensual trade/cooperative jobs,
   then separately test the Baritone adapter and more elaborate schematic projects.

For each scenario record initial supplies, personality, world seed, observations,
task outcomes, time without progress, inventory/material conservation and server
cost. Include depleted deposits, full inventories, missing beds, unavailable
stations, blocked routes, removed home blocks and competing actors. Keep fixed
fixture tests distinct from multi-day survival so a passing fixture is not
reported as proof of a complete autonomous life.

The experimental branch currently has peaceful targeting, witnessed property
offences, personal coal observations, one resumable coal task and automatic
cooking-fuel selection. It still lacks a complete prerequisite planner, earned
age progression, verified full-home construction and a multi-day autonomous
survival demonstration. Main-game installation remains disabled.

# Ordinary resident day and recovery — 24 October 2026

Target: Minecraft 26.4 Snapshot 5, internal `26.4-alpha.5`, Java 25.
Build: `3.0.0-fabric.26.4-snapshot-5.4-daily`.
Artifact SHA-256: `7522e300843a1fd3c8a6cfd1c342ecb6056be0d2b31e0861316558807b86a94c`.

## Integrated acceptance

One BUILDING resident retains **all 77 registered goals and all 3 target goals**.
The fixture never removes goals, drives a delegate, moves the actor after setup,
or supplies replacement resources. The actual clock advances through daylight,
night and the next morning. Initial inputs are a correct starter cabin, an owned
chest with eight coal and two string, two raw beef, an iron axe/pickaxe, and
64 andesite in 16 individually named surplus stacks. Prepared trees, source
water, deep mineable ground and a bounded yard are explicit environment inputs.
The observer stays away. The world is peaceful; combat targeting remains
registered, but this is not a hostile-combat test.

The resident must withdraw real supplies, gather wood, collect cooked food,
deposit surplus at night, sleep, wake and perform real morning work. At dawn,
a declared environmental event removes one roof plank; only the resident may
repair it from real materials. Completion requires at least four safe carried
food items, completed cooking, the repaired roof, preserved foreign storage and
all 64 surplus andesite accounted for across carried/stored/loose/placed blocks.
The final trials allow 40,000 observed simulation ticks. A storage pass on the
second night is accepted; this is not a guarantee that every chore finishes
within one 24,000-tick day. An earlier sleep-save trial was extended to 64,000
ticks and still failed its food/storage requirements. That is retained as a
failure; extending the deadline did not fix the underlying handoffs.

`Daily` saves while cooking is pending. `DailyNight` saves during actual sleep.
Each replay loads the original entity UUID, inventory, home and world with the
complete selectors still present. The sidecar restores test observations only.
Simulation is frozen at the checkpoint and during reload inspection, then
resumed normally, so graphics loading cannot advance the pending task before
it is checked. This pause is test-only; production does not freeze simulation.

Final per-case results and broader runtime checks are recorded in the setup
repository's `validation/smart-npc-daily-20261024.json`.

| Final-artifact case | Observed result |
| --- | --- |
| Daily, cooking checkpoint and actual replay | Full routine complete at tick 28,615; four safe food items, cooking complete, sleep, storage and morning roof repair |
| DailyNight, sleeping checkpoint and actual replay | Full routine complete at tick 31,666 with the same accounting requirements |
| Functional, fresh and actual replay | Survival assertions and original saved actor pass |
| Cabin, fresh and actual replay | All 73 required blocks match, stable for 200 ticks |
| HomeRepairChurn | One initial plank consumed for repair at tick 159, irrelevant paper conserved, no extra timber harvested |
| HomeEntry | Outside-wall resident enters, transfers surplus and performs native sleep; complete at tick 351 |
| HomeStorage | Already-in-range chest selection, real deposit and native rest pass at tick 377 |
| HomePit | Native escape and return to actual home bed at tick 602 |
| StorageRecovery | Blocked stand reopens without moving/supplying the actor; retried deposit, rest and conservation pass at tick 1,890 |
| SleepRecovery | Blocked approach releases; maximum observed consecutive blocked run 214 ticks; native sleep resumes after the entrance opens |
| CookHome | Native door-detour route and real furnace cooking pass |
| RenderBuddy | Selected Vanillaccurate/Hyper resources, Photon day/night/storm, shader off/on and resource reload; Sodium Escape rollback, Diorama/Punchy menus and real Buddy login/item synchronization pass; all eight screenshots visually reviewed |

All listed final runs use the SHA-256 above, including the four actual replays.
Historical positive trials are not substituted for these checks.

## Confirmed defects and corrections

- **Operating state was mistaken for construction damage.** A lit furnace no
  longer matched its unlit blueprint, preventing cooked-output collection while
  fuel remained. Open doors and occupied beds could also manufacture unfinished
  construction. Matching now normalizes only operating properties of furnaces,
  beds, doors, trapdoors and fence gates. Runtime assertions retain rejection of
  incorrect orientation, door/bed halves and genuinely absent roof blocks.
- **A valid home-door detour exceeded a straight-line route bound.** Increasing
  the node budget alone did not solve the route around a cabin wall: range 16
  ended one node short; range 24 reached the stand in eleven nodes. The one active
  owned-home furnace route now uses range 24, a 20% node budget and loaded-fringe
  preflight. Other furnace routes retain the previous 5% local search, exact
  reachable endpoint and retry rules. No speculative path per placement
  candidate or unbounded terrain scan was added. `CookHome` retains native door
  opening, which an earlier selected-goal fixture accidentally excluded.
- **An unreachable bed retained movement indefinitely.** The old sleep countdown
  ran only after arrival. The Fabric overlay bounds both total approach and lack
  of progress, releases movement with a reason and applies a retry delay. The
  recovery test then opens a real entrance and requires native sleep, so failure
  recovery cannot merely disable sleeping.
- **A loaded sleeping pose had no running sleep goal.** Other home goals could
  move a still-sleeping actor through the following day. The sleep goal now
  reattaches its lifecycle to a valid saved sleeping pose and performs normal
  wake-up; an invalid or unloaded sleeping location wakes conservatively. No
  live path or GoalSelector object is serialized.
- **Pillar-base recovery depended on an expired request and restarted path.**
  The existing bounded watcher now checks actual actor/base progress in every
  admitted pillar episode. Restarting navigation is not progress. It reuses the
  existing current/alternate-base and protected-reposition helpers, retaining
  admission, worker/emergency provenance, protection and real-material limits.
- **Funded home work waited for unrelated general stockpiles.** Existing-home
  work can proceed when its actual missing materials are available and existing
  bounded readiness says a block is placeable. Both searches remain cursor-sliced.
  New-site selection retains the previous supply requirement. `HomeRepair`
  consumes one actual plank without harvesting unrelated timber.
- **Returning home fought with pillar cleanup.** Arrival chooses a standable
  spot within the home's center 3 by 3 instead of an occupied bed. An uphill
  return retains temporary support through its final one-block rise. Known home
  returns reuse the confined open-shaft proof with radius five and a 24-stand cap:
  loaded cells, an enclosed component, no walking exit, four wall directions and
  a safe rim are still required. Ordinary open-ground/cliff guards remain.
  `HomePit` starts three blocks down in a 4 by 4 pit and requires actual sleep
  at home with every normal goal enabled. The previous candidate repeatedly
  removed its own support; the corrected recovery completed in 433 ticks.
- **Ready cooking yielded to work it had itself paused.** A committed cooking
  intention suspends general routine work, but the furnace goal could yield to
  an unfinished general stone-supply phase. Neither action could run until the
  intention timed out. The READY handoff now admits native furnace work while
  preserving safety, dimension, manual-task priority, recipes, fuel, reachable
  station and placement checks. Active gathering episodes and incomplete
  cooking prerequisites retain their existing admission rules. The sleep-save
  run exposed this carrying raw cod and a real furnace; its failure is retained.

- **A temporarily stowed rod looked missing to food admission.** The durable
  shared tool transaction retained the original fishing rod while another tool
  occupied the hand, but fishing checked only visible equipment and inventory.
  Admission now recognises that real retained item. The existing equip/recovery
  path retrieves it, without creating a rod or changing transaction ownership
  rules. A runtime probe switches rod to axe, verifies fishing admission, retrieves
  the rod, and verifies one original rod and one axe are conserved.
- **Home leisure could starve the nightly storage pass.** Home leisure and new
  sleep attempts now yield to a real pending own-home deposit. Leisure uses its
  existing bounded work-check cadence. The builder starts the storage episode
  after entering its home, so an outside-wall approach cannot consume its one
  nightly attempt. Candidate filtering, reserves, ownership, actual transfers
  and one successful nightly pass remain. Failed approaches use the bounded recovery below. Already-sleeping lifecycle recovery stays intact.

- **An already-reachable chest still required a path.** Chest stand selection
  required a route even when the actor was already within the range accepted
  by actual interaction. A rejected route could consume the night's one storage
  attempt. Selection now accepts the same in-range stand without pathfinding;
  eligibility, ownership, reach and transfers are unchanged. `HomeStorage`
  checks in-range selection before navigation setup, then requires actual storage
  followed by rest. It does not assume a particular path budget caused the
  rejected route in the earlier integrated run.

- **Home leisure could retain an unsuccessful last-mile approach all night.**
  Its approach now releases movement after bounded total/no-progress limits and
  retries after a delay. The one active nearby home route can use the existing
  loaded local door-detour helper; original safe-drop fallback remains. The
  existing cached useful-work check also yields to a committed READY cooking task.
- **One failed storage route suppressed the entire night.** Storage still permits
  one successful pass, with at most three approach attempts in a night. A failure
  cannot retry until 200 ticks pass and either the actual stand changes or the
  four adjacent stand-access bits change. The tiny read-only signature performs
  no pathfinding. Success prevents later passes; retry count/deadline/stand/access
  persist with ordinary NPC data. Old saves without retry metadata retain their
  previous attempted-pass semantics. `StorageRecovery` blocks the chest stand,
  reopens it as an environmental event without moving or supplying the actor,
  and requires a real retried transfer, rest observation and 64-item conservation.

- **Optional projects starved a declared food need.** Building, terrain prep,
  ordinary gear upgrades and trash work now yield to committed cooking or an
  actionable fishing opportunity. Every goal retains its identity, flags and
  original execution/cleanup. The food opportunity follows the existing 2,400-tick
  episode and 1,200-tick retry window, sampled once per 20 ticks across the actor's
  optional goals. Missing rods, unsafe conditions and unavailable/retry windows
  do not suspend routine work indefinitely. Completed burns finish their short
  action. Explicit requests and safety goals keep their own priority rules.
  Native jobs resume when the reserve is ready or the bounded attempt ends.

- **Inventory churn restarted cooperative readiness scans.** In-progress scans
  retain their building/layout context and cursor through held-item and unrelated
  inventory changes. Material ledgers retain their starting snapshot fingerprint;
  completed caches retain their bounds and same-tick consistency. Prior positive
  readiness remains an advisory hint during a refresh. Actual placement still
  resolves, validates and consumes current real stock. `HomeRepairChurn` changes
  only the name of an irrelevant initial paper item every tick; the resident must
  consume its one plank without harvesting any extra timber. The previous build
  failed at 3,000 ticks with the plank still unspent.
- **Cosmetic build motion could abandon a valid repair stand.** During the normal
  placement delay, a safe in-range builder now stays put. Real clipping/clearance
  recovery remains. No reach, delay or recipe is increased.
- **A furnished living floor was treated as a no-target shaft.** Owned-home floor
  routing now takes precedence over passive room pillaring. Explicit construction,
  team and actual combat requests retain their paths; lower pits remain eligible.
  Pillar-base retries count real feet progress, not a different/recreated plan,
  and reach the existing protected fallback after the bounded recovery count.
- **A new stockpile trip could repeatedly close food's admission window.** New
  optional log/stone trips yield during the bounded food opportunity. Already
  paused support/descent recovery and committed cooking prerequisites keep their
  existing admission, so this does not discard support cleanup or food inputs.

## Evidence boundaries

Old-jar negative controls reproduce the blocked-bed stall, home furnace-route
failure and sleeping-pose replay failure. Earlier weak acceptance completed a day
with only three food items; stronger acceptance exposed the remaining conflicts.
Superseded candidates and failures remain private evidence, not rewritten passes.
The previous starter-cabin clearance correction is retained alongside the new
funded-work admission rule.

All 39 module unit tests and access-widener checks pass. Archive comparison limits
production changes to sleep, cooking/home navigation and handoff, pillar recovery,
home arrival, funded construction, retained-rod admission, home storage arbitration,
structural matching and version metadata.
All other class/resource contents, layouts and nested dependencies match the
previous cooking release. No dependency or separate per-NPC client was added.

This establishes the declared resident scenario, not an empty-inventory wilderness
start, every house/terrain, autonomous string sourcing, physiology or indefinite
multi-resident self-sufficiency. Preserve both actual daily replays, blocked and
reopened bed recovery, home-door cooking, funded repair, pit return, full cabin
replay and normal survival/Buddy checks when upgrading or reconciling upstream.
Inspect explicit completion/failure receipts; launching successfully is not
acceptance.

# Survival journeys: first delivery

This delivery connects the existing resident work to persistent journeys and a
small resource-funded starter cabin. It is an initial M1 implementation, not a
claim that every wilderness start or island route is solved.

## Behaviour

- The resident prefers the original small survival cabin for its first home.
  Construction, crafting, gathering, furnaces, storage and sleeping still use
  the existing goals and real materials. Funded construction phases can proceed
  before optional stockpiles and additional equipment.
- The blueprint owns its bed, avoiding a utility bed consuming the only carried
  bed in the wrong place. A usable owned home furnace takes precedence over an
  inaccessible old campsite station; the old station and contents remain intact.
  An idle tracked camp station with no input/output can be reused for a missing
  blueprint furnace, with a real pickaxe and all unused fuel preserved. Its item
  is reserved for construction instead of opening another temporary camp.
- A timed-out grounded log approach is skipped even if a pillar plan exists.
  Worker suspension and actual occupied-pillar descent retain native cleanup.
- A journey retains home, destination, dimension, purpose, actual visited trail,
  progress and interruption state. Local routes are rebuilt after loading.
  Walking is split into bounded nearby searches rather than one enormous path.
- Water crossings use a real carried/crafted boat and native boat movement.
  Only the journey's own empty boat can be packed. Safe full-height shore exits,
  mounted worker tickets and paused AI are handled explicitly.
- Return retains its recorded shore crossings through the home region. Aboard
  retries rejoin remembered water; the final local home approach begins after
  consuming the trail. Boarding includes a supported shallow wade and native
  floating surface nodes; deep swimming is not accepted as a walking route.
  Boat search preserves actual hull fractions, follows cleared cardinal steps,
  uses a directed search and chooses physically accessible landing places.
- Close walking targets retain at least 24 blocks of local detour distance, with
  the original visited-node ceiling and a full loaded-region preflight. A
  changed or unsupported route can fail honestly. Repeating the same failed
  return from the same position is suppressed until conditions or the request
  change. No teleport fallback or cross-dimension route is supplied.
- Eligible builders can relocate toward observed nearby supplies. This is a
  bounded loaded-terrain search, not knowledge of hidden ores or the whole world.

## Operator inspection

Use an entity UUID or a selector resolving to one loaded NPC:

```mcfunction
/smart_npc journey @e[type=smart_npc:player_npc,sort=nearest,limit=1] status
/smart_npc journey @e[type=smart_npc:player_npc,sort=nearest,limit=1] go 200 70 200
/smart_npc journey @e[type=smart_npc:player_npc,sort=nearest,limit=1] home
/smart_npc journey @e[type=smart_npc:player_npc,sort=nearest,limit=1] cancel
```

Departure requires a known same-dimension home and prepared food. Manual travel
is a movement task; reaching a work site does not mean mining there is automated.

## Verification boundaries

Real-terrain tests run against disposable copies of a closed player world with
the installed common-mod stack. All 80 normal goals and three target goals stay
registered; no action is disabled to force the desired outcome. Fresh standard scenarios declare dawn/clear conditions; saved continuations
retain their actual clock/weather. `--keep-world-conditions` preserves the source
conditions for a fresh naturalistic trial. The single
resident starts with declared food, tools and limited materials. Movement and
materials are not injected after setup. Construction and travel are separate
scenarios. Travel uses a declared home anchor, not a constructed cabin.

The runner saves, stops the actual process and reloads the original actor at
outbound and return checkpoints. The AI-paused test is explicitly a return-only
excursion and does not claim arrival at its distant requested destination.

Accelerated runs are functional tests. Their MSPT excludes graphics and their
wall time is not a normal-game FPS benchmark. Failed accelerated forest and
poor-resource coastal trials are retained as failures. Their remaining search,
log approach and resource-access limitations prevent claiming universal M1
acceptance, empty-inventory survival, an indefinite food chain or a settlement
soak. Hostile combat, multiple residents, portal routing, bridge construction and
all terrain changes need separate acceptance.

## Faster maintenance

See [the release workflow](RELEASE-WORKFLOW.md). Warm incremental builds freeze
immutable jars; copied-world headless tests automatically restart checkpoints.
Focused regressions run during iteration; graphics/Buddy run on the final
candidate. Deployment checks the committed source, exact tested bytes, unit and
runtime receipts, closed clients and previous installed hashes, then backs up
and replaces only Smart NPC in the selected roles. Future upgrades discover the
current installation and preserve these behaviours and gates.

## Recorded delivery evidence — 25 October 2026

Version: `3.0.0-fabric.26.4-snapshot-5.5-journeys`, targeting
`26.4-alpha.5` / launcher Snapshot 5. The final artifact is
`81e2b53131315b33215147cb6062576359118db8802fa11d7f56c942fe381d55`.
Its 43 unit tests and access-widener checks passed. The final incremental build
took 14.23 seconds; copying the closed 428 MB source world took under a second.
These are local iteration measurements, not an in-game performance comparison.

| Scenario | Evidence |
| --- | --- |
| Native boat preparation, 100 TPS | `smartnpc5-m1-native-ship`: starts with logs and a table, crafts a real boat, reaches the 128-block site, reloads outbound and returning, arrives home with original UUID, food 16 and one boat |
| Long-distance transport, 200 TPS | `smartnpc5-m1-journey-ship`: 2,048-block destination, physical boat crossings, actual outbound and return process restarts, original actor and observed trail |
| Departure and retry guards | `smartnpc5-m1-release-guards`: no unprepared departure, foreign-dimension home rejection and bounded same-position blocked retry |
| Final combined client | `smartnpc5-m1-release-client-smoke`: actual Buddy joins; Vanillaccurate/Hyper resources, Photon day/night/storm, shader toggles/reload, Sodium Escape, Diorama/Punchy menus and map-item delivery pass; storm screenshot visually reviewed |

The preceding regression artifact
`9eae2e30448f9a585d7318d3f9628bcd8e3e64ccedc6a4b70985e0f5fe2c210a`
differs only by preparing a missing boat on supported ground before entering
water. The affected crafting, journey, guard and combined client checks above
were rerun on final bytes. The following unaffected checks retain their actual
preceding-artifact identity; they are not presented as final-byte reruns.

| Regression scenario | Recorded outcome |
| --- | --- |
| Fresh copied-forest limited start | `smartnpc5-m1-final-forest-survival`: all 73 cabin blocks after 23,729 simulation ticks; real reload, native sleep/wake and food reserve 18 after another 22,750 ticks |
| Full ordinary routine after sleeping reload | `smartnpc5-m1-final-daily-v2`: gathers wood, withdraws supplies, fishes, cooks and collects food, deposits surplus, sleeps and repairs the morning roof; completes at total tick 35,886, reserve 4 |
| Cabin client + actual reload | `smartnpc5-m1-final-cabin-client`: all 73 blocks remain correct for 200 ticks in both runs |
| Pit escape and funded repair | `smartnpc5-m1-final-pit`, `smartnpc5-m1-final-churn`: native home return and actual plank consumption without unrelated gathering |
| Blocked old station | `smartnpc5-m1-final-station`: cooks both inputs at the owned home while leaving the old blocked station and its contents intact |
| Idle station reuse | `smartnpc5-m1-final-furnace`: native camp-furnace relocation completes the blueprint and conserves all three unused coal; the identical fixture fails on earlier artifact `60d91004…` after 6,000 ticks |
| Native grounded log timeout | `smartnpc5-m1-final-logs`: focused contract passes; this is not a naturalistic harvesting scenario |
| Paused aboard + real reloads, 100 TPS | `smartnpc5-m1-final-paused-v2`: 2,000 paused world ticks do not spend active-work allowance; interrupted excursion returns with one boat; explicitly return-only, not destination arrival |

All normal outcome scenarios preserve the 80 ordinary goals and three target
goals. Original save fingerprints are checked during copying and before
deployment. The setup repository retains per-case tested hashes and installation
receipts, including the exact previous artifact and the unchanged role counts.

An additional fresh start, `smartnpc5-m1-final-cabin-terrain`, remains a failure:
it stops making building-approach progress with 41 blocks missing and reaches the
96,000-tick deadline. That failure and earlier failed terrain/shore cases are
retained. This delivery therefore advances M1 but does not close its broad
wilderness acceptance gate. The successful forest cabin also takes longer than
one Minecraft day when its restart/rest observation is included.

The lower-speed native-boat case initially failed on `9eae2e30…`: crafting was
requested after entering water, so table placement could not proceed. It aborted
and returned home safely. The final early-preparation change fixes that case;
the original failure remains recorded separately.

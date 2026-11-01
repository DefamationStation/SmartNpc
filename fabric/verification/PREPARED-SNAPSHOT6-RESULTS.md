# Prepared survival on native Snapshot 6

Release candidate `.8-prepared-survival`, 1 November 2026. Compiled directly for
Minecraft `26.4-snapshot-6` / `26.4-alpha.6`, Fabric Loader 0.20.1 and Fabric API
0.165.1+26.4. The shared upstream sources plus Fabric overlays remain authoritative.
No old gameplay classes are replaced with an author's stock mod.

## Changes

- Production initialization registers prepared expedition commands and conserved
  food offers; exact canonical offences update only the actual actor's relationship.
- Prepared departure requires the NPC's earned cooking and safe-rest history plus
  real food, tools, home and declared-water transport readiness. Declines preserve
  inventory and the existing commitment. Ordinary `go` stays one-way; explicit
  `visit` begins native return after physical arrival. Its HIGH-priority observer
  runs before optional shelter-supply travel and uses the existing persisted trip.
- Successful native owned-bed rest counts before dawn, after actually observed
  sleeping ticks. Breaking a bed or interrupting sleep cannot award a rest milestone;
  unloaded time does not count. The native sleep timer updates every actual tick.
- New relationship events after clock rollback retire conflicting future facts for
  the same actor, allowing the actual new event to survive codec round-trip.
  Unknown defense schema data remains opaque and unchanged across saves. Newly
  witnessed current-session offences can defend temporarily without writing into
  an unknown schema or blaming another nearby actor.
- The inventory screen uses Snapshot 6's native textured GUI pipeline. Negative
  progression schema versions receive the same read-only treatment as future ones.

## Frozen artifacts and evidence

Final candidate SHA-256:
`a8691648800030bf08c9d92cb21358010957f785f40dcd1745ee16f745b7e04b`.
All **118 unit tests** pass. Earlier integrated candidate
`4907efef5df68f71dc08626a0467ec32db6f5055c81916c8a3128b0bcb21d20b`
passed 116 tests before the two additional visit-policy tests.

Comparing every decompressed jar entry proves that only `PreparedExpeditions`
and its two nested classes changed between these artifacts. Both contain 577
classes; all other classes and resources are byte-identical. The following earlier
native outcomes retain their original artifact identity, rather than being claimed
as full regressions rerun on the final jar:

| Case on `4907efef…` | Observed result |
|---|---|
| `npc6-native-rest-v3` | Owned-bed nap finishes before dawn; actual sleeping save/process reload; original actor and all 80 goals |
| `npc6-rest-interrupted` | Occupied bed broken after 120 observed sleep ticks; no rest credit |
| `npc6-relationship-native` | Exact first attacker remembered, nearer peaceful bystander unblamed; opaque future defense attachment survives actual hurt and process reload |
| `npc6-food-safety-v2` | Real server interaction callback; conserved component-preserving one-unit transfer; raw/offhand/creative/full/remote/schema/disabled declines; influence window, reserve and bribery checks |
| `npc6-food-client` | Real crouched client interaction packet; giver 3→2, NPC 2→3; exact giver, help and bounded relationship survive actual saved-process reload |
| `npc6-daily-night` | Ordinary cooking/harvest/storage/sleep; save while asleep; native morning continuation and funded roof repair after reload |
| `npc6-integration` | Full 28-mod player and real 17-mod Buddy; exact candidate on both; real food packet, native inventory screen rendered and second-client login stable |

Final-candidate acceptance outcomes are recorded in the setup repository's
`validation/smart-npc-prepared-snapshot6-20261101.json`, including
ordinary `go`, explicit `visit`, arrival ordering and combined client/Buddy checks.
All four pass on the final jar: `npc6-go-final`, `npc6-visit-final`,
`npc6-visit-collision-seed-v2`, and `npc6-final-integration`. The last uses normal
client ticking, actual food packets, forty rendered inventory frames and real
second-client login stable for 120 ticks. Production graphics remain unchanged.
The 256-block visit and 128-block ordinary trip each physically walk outward and
home with real process restarts on both legs and conserve food/tools/UUID/goals.
The visit earns exactly one long-return exploration credit. Synthetic historical
cooking/rest inputs and flat supported geometry are declared admission-fixture
inputs; the separate native cooking/rest tests establish the actual producers.

The collision negative control `npc6-visit-collision-negative` uses frozen
`7f30984e…` (before the priority fix), and fails with the expected actual wood-supply
takeover. It is retained as a deliberate failed outcome, never counted as a pass.
The identical fixture passes on the final candidate and has an independent
positive control proving the resource observer really admits that situation.

Older cabin, pit, repair-churn and idle-furnace regressions retain the earlier
installed-lineage results; they were not rerun as new wilderness trials here.
Only the cooked-food helper's visibility changed in `SurvivalTasks`; its body,
the build/pit/furnace algorithms and ordinary route controller are unchanged.
Changed sleep completion has separate native success/interruption/reload and
ordinary daily-night coverage. No unrelated terrain/render benchmark is claimed.

## Test scope and failed trials

Dedicated tests use the installed common mod stack; clients retain all production
mods with vanilla diagnostic assets. User graphics, packs, settings and original
worlds are unchanged. Tick acceleration is recorded and is not an FPS benchmark.
The builder collision regression declares an arrival checkpoint and unfinished
cabins to exercise the actual observer chain; it makes no walking/reload claim.
Metadata-only synthetic fixture worlds regenerate terrain and are explicitly
distinct from the complete private copies used for route/reload tests.

Preserved failed harness trials: nonexistent native `Blocks.RED_BED`, a diagnostic
name exceeding 16 characters, nonexistent `ServerLevel.setGameTime`, and a seed-only
fixture missing the separate native `world_gen_settings.dat` metadata were fixed
in fixtures before successful reruns. Initial guard refusals remain refusals.
The shared test budget stays at 30 GiB with at least 50 GiB free; no test archives
were moved elsewhere to evade it. Launchers reject missing completion markers and
nonzero process exits. Frozen evidence is never overwritten by later builds.

## Remaining development

This integrates individual readiness and relationship foundations, not full M3/M4.
Prepared requests are explicit operator commands, not an autonomous expedition
planner. Relationship evidence is advisory and grants no teams, storage access,
trade or forgiveness. Broader poor-resource/hostile wilderness acceptance and
multi-resident cooperation remain open; controlled flat routes cannot establish
universal natural-terrain navigation.

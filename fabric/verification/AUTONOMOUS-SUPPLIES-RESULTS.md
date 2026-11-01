# Autonomous supplies: no player task commands

Snapshot 6 `.9-autonomous-supplies`, 1 November 2026. This corrects the earlier
command-first development scope. Self-playing NPCs select and perform their own
survival work; optional operator commands remain diagnostic overrides.

The implementation observes actual admitted native wood/stone collectors and
autonomously selected shelter-supply journeys. It persists the original material
baseline, observed resource, owned home and journey identity. A matching native
harvest only proves that drops were produced. Completion also requires actual
carried-resource growth, latched before legitimate crafting can spend it. Native
collectors finish their climbing/descent episode before successful return begins.
The existing native journey follows its observed trail home. Cancellation,
replacement, pause, danger, depletion and unsupported saved schemas retain bounded
or conservative behavior; no items, completed work or remote ore knowledge are granted.

The new bounded supply-search goal obtains its lease through the existing work
wrapper. It does not bypass the scheduler or create its own paths/scans. It gives
an unfunded builder a planning turn, then yields to travel, actual gathering,
funded construction or urgent needs. The full selector now contains the original
80 goals plus this one additional planning goal. Current fixtures expect 81.

Native collecting remains native collecting; it is not forced through an extra
departure command. Its saved intent can observe an idle native journey, then start
the existing return after real collection. Search cannot overwrite an active
autonomous intent. Cooking/explicit resource commitments retain precedence.

## Evidence

Exact artifacts, final acceptance markers and installation status are recorded in
the setup repository's `validation/smart-npc-autonomous-supplies-20261101.json`.
All 122 unit tests pass in candidate
`f1feab3a6aefd782abd8b882876cf2961201d5a1b35bc45507205b6561ef3ae9`.

`npc6-autonomous-wood-v5` passes on that candidate: an ordinary BUILDING resident
independently chooses the declared wood, physically travels, saves/reloads,
harvests the real source and picks up its drops, returns automatically, saves/reloads
again and reaches its owned cabin. All 81 goals remain enabled. Exactly one log's
eight half-plank units survive across inventory, equipment, dropped items, storage
and actual placement. Food16 and both original tools remain; the axe has real wear.
This first test ends upon delivery, with the missing plank not yet placed.
`npc6-autonomous-repair-v3` also passes on the same exact jar at100ticks/s: actual native repair replaces the missing plank after both saved-process checkpoints. The final settled inventory, equipment, drops, storage and placed plank total exactly eight half-plank units; food16 and real worn tools are conserved. The placement preview must settle within400ticks before strict final conservation is accepted. This is not an exemption for a persistent extra item.

No NPC command, trip request, direct goal invocation, manual event dispatch,
resource-memory injection, fake harvest or later material/movement grant is used.
The fixture declares an almost-complete owned cabin missing one plank, a source
log, food16 and stone tools. Dawn/clear/peaceful conditions and a supported bounded
test yard are declared. The generated world is isolated; real process replays reopen
its actual saves. This is not copied-terrain or hostile-wilderness acceptance.
Tick acceleration is a correctness-test setting, not a performance benchmark.

## Preserved failed trials and scope

The first fixture required a specific supply-journey implementation to run before
any native collector; that oracle was too restrictive because native collection
is legitimate autonomous behavior. Later trials revealed that a small elevated
yard allowed ordinary wandering to leave it and select unrelated natural trees far
below. The corrected yard is bounded and resource conservation remains strict.
The first two repair-oracle trials also counted the native temporary hand preview as spendable stock before the placement animation finished. The corrected oracle waits for finite native settlement and still requires exact final conservation. Those trials remain failed receipts; they are not relabeled passes. The explicit
supply-intent overwrite guard and native collecting integration are real production
fixes, independently of the test geometry corrections.

This is a local material-supply loop, not a claim that all wilderness survival,
equipment progression or multi-resident cooperation is complete. Stone integration
shares the native hook and conservation policy; do not claim an independent stone
outcome unless it appears in the release validation.

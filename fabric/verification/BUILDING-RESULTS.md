# Building approach recovery — 25 October 2026

Version `3.0.0-fabric.26.4-snapshot-5.6-building` addresses the unfinished
cabin retained in the first survival-journeys report. Original player worlds
are never used as writable test instances.

## Cause and change

The saved resident repeatedly requested a path to an unplaced wall block. The
native pathfinder reported success, but physical movement stopped at the cabin
edge. Small changes between neighbouring block positions reset the old progress
watchdog without getting the resident closer to its work.

Placement approaches now select a supported, full-height working position within
the existing four-block horizontal placement range. Candidates are loaded-only,
clear the intended block's collision, and clear both halves of a future bed.
Scoring prefers nearby positions on the resident's current level before costly
roof approaches. The chosen stand is retained while usable. Stalled approaches
try another stand on the existing repath cadence, with one bounded native path
search per attempt and the existing node budget. No teleport or construction
reach increase is added.

Progress requires a net reduction in distance to the working position. Issuing
another supposedly reachable path or oscillating across a block boundary cannot
by itself reset the failure counter. The existing blocked-target deferral and
ordinary resource, crafting and placement actions remain active.

## Evidence

Release SHA-256:
`c36f800d48269091bd5b348b44fec605d282cd9e8ba614946a79acbc8fd18247`.
The 43 unit tests and access-widener checks passed.

| Case | Outcome |
| --- | --- |
| `smartnpc5-build-old-control` | Installed predecessor `81e2b531…`, copied original failed actor, 4,000-tick observation: still 41 blocks missing and requesting the same wall target. Retained as a failed negative control. |
| `smartnpc5-build-saved-final` | Same saved actor, home, inventory and terrain: finishes all 73 blocks and passes native sleep/wake and food reserve 18 after 23,096 ticks. No material refill or actor relocation. |
| `smartnpc5-build-forest-final` | Fresh limited-start copied forest: completes all 73 blocks after 10,781 ticks; actual process restart restores the original actor, then native sleep/wake and food reserve 18 pass after another 11,698 ticks. |
| `smartnpc5-build-cabin-final` | Ordinary builder completes all 73 blocks in the client; all remain correct for 200 ticks, including after actual saved-world replay. |
| `smartnpc5-build-daily-final` | Ordinary-selector daily routine and sleeping replay; gathers, fishes, cooks, withdraws and deposits supplies, sleeps and performs the morning roof repair. |
| `smartnpc5-build-churn-final` | Funded roof repair consumes its actual plank without gathering unrelated reserves. |

All outcome cases use the normal 80 goals and three target goals. Frozen per-case
artifacts and fixture metadata distinguish these checks from the earlier
inspection probe, which froze the actor only to inspect native path results and
does not count as a survival pass. The installed setup's validation records the
remaining home-return and combined client/Buddy checks on the release bytes.

These are peaceful single-resident scenarios with declared tools, food and
limited supplies. Construction and expedition scenarios are separate. Results
do not establish universal terrain coverage, hostile survival or an indefinitely
self-sufficient settlement. Additional exploratory starts and any failures stay
recorded separately; this closes the identified entrance-approach defect, not
the entire wilderness acceptance gate. Accelerated MSPT is not a graphics/FPS
benchmark, and the fresh cabin's completion time is not a controlled speedup
measurement against another randomly evolving run.

An additional exploratory pre-release start, `smartnpc5-build-fresh-survival`,
reaches its 96,000-tick deadline with a chest, furnace and torch still missing.
It has not acquired enough accessible stone for its station. A separate
512-block expedition, `smartnpc5-build-travel-final`, reaches its destination
but repeats landing/reboarding at a riverbank on return. Neither is counted as
a successful full survival/route trial. They remain concrete next development
cases. Comparing compiled entries against the installed predecessor confirms
that only `BuildHouseGoal` and its nested classes differ; journey/boat code is
unchanged. The previously successful 2,048-block route is historical evidence,
not proof that this different bank is solved.

## Faster release maintenance

Warm builds now validate the jar's embedded mod ID/version against the declared
release, allowing old-version artifacts to coexist safely in the cache. Gradle
tracks the version as an explicit resource-processing input, preventing a renamed
jar from retaining stale embedded metadata after an incremental version bump.

Focused headless trials accept a recorded `--max-ticks` cap, inherited by actual
process replays. It can shorten the standard observation window but cannot
extend it. Deadline failures remain failures. The old comparison therefore takes
4,000 ticks rather than repeating the already recorded 96,000-tick stall.

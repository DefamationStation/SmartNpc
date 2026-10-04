# Peaceful survival prototype verification — 2026-10-04

Artifact: `SmartNpc-Fabric-3.0.0-fabric.26.4-snapshot-2.3-survival.jar`

SHA-256: `4b05d03a2a5a4f2217cfd663333fce0aca8d2aef2d9303a970f7e31ab0496b73`

## Concrete problems addressed

- Default player-hunting/prank personalities intentionally permitted unsolicited player/NPC attacks. Neutral targets now need recorded evidence, and prank/raid activation is removed.
- Cautious threat classification treated every nearby player or NPC as a threat. Visitors are now neutral; actual hostile encounters remain threats.
- Opening an owned chest immediately authorized combat. Opening now warns; actual inventory removal is attributed to the player's server-side container operation.
- A decorative random walk ran while NPCs waited for an AI work slot. This is replaced with a brief attentive pause.
- The old Structurize palette keys `Name`/`Properties` silently decoded as air in 26.4. Converting them to `id`/`properties` restored 67 bundled layouts. One original starter cabin raises the loaded catalogue to 68.
- Construction could import recorded container contents. Container block-entity data is skipped so built storage/workstations begin empty.
- Coal gathering previously required an operator request. A first automatic decision now gathers cooking fuel from personal observations when its prerequisites are present.

Fresh prototype configs cap natural NPCs at four. Existing config values are preserved. Main-instance disablement remains in effect.

## Automated checks

Java 25 / Gradle 9.6 build and access-widener validation passed. All 13 JUnit tests passed: config/events, resource memory, fuel decisions and bounded expiring defence evidence. Tests cover identity isolation, expiry, persistence, invalid evidence, real need/precondition ordering and automatic-task checkpoint fields.

The upstream NeoForge source tree, build configuration and wrapper remain unchanged against the original upstream base. These checks concern the experimental Fabric module.

## Runtime checks

The harness used isolated worlds with seed `60020261004`, the full development mod stack and its shader configuration. Source-instance saves were never copied or opened. The final fresh-world run completed at 11:38 local time; the latest artifact's saved-world replay completed at 11:41. Both completion markers were written, and the final replay used the hash above. Earlier successful checks and runs are retained in separate test directories.

Confirmed in game:

- Legacy blueprints contain real construction blocks and preserve non-default stair orientations. The catalogue loads 68 layouts, and the starter cabin includes bed, chest and furnace.
- Random prank, golem-troll, chest-raid and decorative waiting-stroll goals are absent from the NPC goal selector.
- A personality with player-hunting/prank interests cannot acquire a neutral player or NPC target. Direct melee against an innocent NPC is blocked. Neutral golems and peaceful visitors are also excluded.
- Actual NPC-on-NPC damage creates evidence and permits defensive targeting. The same attacker becomes a legitimate cautious threat; evidence expires after its defined interval.
- Player game-mode block breaking triggers the real property event path. Ordinary dirt in a reserved plot does not grant a grievance; a matching tracked construction block does.
- Opening owned storage warns without a grievance or target. Rearrangement and depositing do not count as removal. A real client-to-server quick-move operation removes supplies and records theft for that player's UUID.
- Applying recorded chest data through the construction backend cannot restore free supplies into empty constructed storage.
- A controlled NPC with raw food, furnace and pickaxe starts its own fuel task without a command, mines two exposed ores, collects two actual coal items and reports return completion. Enclosed ore remains unknown.
- Pending resource intent, observations, XP, attachments and defensive evidence survive entity serialization and full world restart. The defensive-evidence restart occurs within its expiry interval.
- Existing inspector/spectator networking, bow consumption and shared chunk-ticket checks continue to pass.

Local evidence is under `.verification/smart-npc-peaceful-survival-final`; the runner archives previous stdout/stderr on replay. The development instance's active mods directory contains no Smart NPC jar.

## Limits and next release gate

The resource fixture retains the real goal selector, work-budget/startup wrappers, navigation and pickup, while excluding unrelated jobs for repeatability. The building checks validate template loading, property reactions and resource conservation; they do not demonstrate a completed autonomous cabin. These are short functional trials, not a population benchmark or a multi-day survival simulation.

Still required: the complete food → tools → shelter → storage → rest loop; meaningful progress and recovery across resource-poor seeds; long navigation and combat interruptions; shared/double storage and detailed permissions; proportional warnings/defence, return/repair/reconciliation; dedicated-server trials and measured population cost. Taking supplies onto the cursor currently counts as removal, and returning them does not yet cancel a grievance. Nearby owners need line of sight to the offender; remote offences are not omnisciently attributed. Multi-run defence-evidence replay must account for its expiry rather than assuming permanent hostility.

The main game remains disabled pending a validated survival-loop build and the user's decision to enable it.

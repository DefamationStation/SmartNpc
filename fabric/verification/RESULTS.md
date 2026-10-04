# Fabric port validation — 2026-10-04

Target: Minecraft launcher version `26.4-snapshot-2` / internal `26.4-alpha.2`,
Fabric Loader 0.19.5, Fabric API 0.161.2+26.4, Java 25.0.4.1, Windows.
Upstream base: `554e2a704d43aaebb33aac363d5abb24d3139500` (26.1.2).

## Completed checks

- Gradle build, jar packaging and access-widener validation passed.
- Four unit tests passed: numeric TOML types, invalid config repair/preservation,
  event priority/cancellation, and propagation of handler failures.
- A fresh world ran for 90 seconds after NPC summoning with natural spawns and
  AI ticking, Sodium and Iris loaded, then saved and shut down normally.
- Actual game assertions passed for NPC persistent attachment and stored-XP
  serialization; furnace ownership serialization; command registration; inspector
  request/response over Fabric networking; and spectator activation/restoration
  on both client and server. First- and third-person views were exercised.
- A separate process reopened the saved world and found the same NPC UUID,
  attachment marker and stored XP, then repeated inspector/spectator checks.
- Real chunk-storage assertions confirmed that two NPC owners share a ticket,
  releasing one owner preserves it, and releasing the last owner removes it.
- The replacement bow controller drew and fired arrows in game, consuming
  ammunition and bow durability. Its test uses a non-cautious NPC and a passive
  target in open air, so peaceful difficulty and terrain do not invalidate the
  fixture. It exercises the controller directly, not every AI goal-selection case.
- The functional checks also passed with the development mod stack and
  `photon_v1.3b.zip` enabled, using OpenGL. Screenshots were visually inspected.

The full-stack run loaded Baritone 1.20.0-snapshot.26.4.2, BlueMap's local 26.4
build, Distant Horizons 3.3.4-snapshot.26.4.2, Carpet 26.4-beta-1+v261001,
Iris 1.11.7-snapshot.26.4.2, Sodium 0.9.2-snapshot.26.4.2, Xaero Minimap's local
26.5.3 build and Xaero World Map's local 1.46.4 build. BlueMap rendering was
inactive because its optional resource download was not configured in the new
test instance. Its mapping functionality is not part of the compatibility claim.

The original root Java/resources, Gradle build files and wrapper have no changes
relative to the upstream base. The original NeoForge build was not rerun;
preservation is established by the unchanged files, not a new runtime test.

## Boundaries

This is an experimental snapshot port, not a claim that every NPC goal or
third-party integration has been exhaustively tested. Better Combat animations
are disabled, dedicated-server operation is unverified, and migrating existing
NeoForge worlds is unverified. Short integration runs do not establish long-term
AI balance, performance or every farming/building/mining/fishing outcome.

The isolated offline test account produces expected authentication/Realms
errors. Existing shader property warnings and a missing vanilla post-effect
warning were also present; none prevented the functional assertions. No source
instance saves or account credentials are included in the repository or jar.

## Reproduction

Build the Fabric module, then use `run-smoke.ps1` with `-InstanceDirectory`,
`-LaunchTemplate`, `-JavaHome` and a new `-OutputDirectory`. Wait for clean exit
and `complete.txt`, then repeat with `-Replay`; success produces
`replay-complete.txt`. A failed assertion produces `failure.txt`. Use a new
directory for each initial run. The harness is a separate test mod and is not
included in the published Smart NPC jar.

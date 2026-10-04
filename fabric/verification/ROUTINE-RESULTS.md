# Daily routine development checks

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.4-routine`.
Companion: [player journey](../PLAYER_JOURNEY.md).

## Change and scope

Existing daily-job selection depended on a morning window ending at tick 12000.
A late spawn, unload or time skip could leave it unset/stale for the rest of the
day. Selection now repairs absent or invalid jobs immediately and catches a new
day after the morning was missed. A valid same-day job remains committed; a
jobless personality does not continually reroll. The tick-zero boundary and
clock rewind cases are handled explicitly.

The existing `SleepAtHomeGoal` is now registered without a building-interest
wrapper. It retains startup/work admission, night, health, combat, bed and
cooldown checks. This makes owned-home rest available to non-builders without
adding another sleeping implementation or a competing movement controller.

All production changes are Fabric overlays/new classes. The upstream NeoForge
source and build remain unchanged against upstream base `554e2a7`.

## Build and tests

Java 25 / Gradle 9.6 Fabric build completed successfully. All **16 unit tests**
passed: configuration 2, event bridge 2, resource memory 3, fuel decisions 2,
grievances 4, daily routine 3. Routine cases include late spawn, missed morning,
stable same-day work, tick-zero rollover, clock rewind and jobless actors.

The runtime harness uses a fresh isolated world and the full main-instance mod
stack, followed by a saved-world restart. It never copies the main saves or
installs the prototype into the main mods directory. Main Smart NPC remains
disabled.

Runtime acceptance checks:

- A server entity receives a job at time 18000 without waiting until morning.
- Repeated selection in the same day retains its assignment.
- A stale saved assignment and a job inconsistent with personality are repaired.
- The registered home-sleep delegate has no building-interest gate.
- A non-builder selects a real home bed and enters its sleeping pose at night.
- Existing real coal observation, mining, pickup and return complete; enclosed
  ore remains excluded from observation.
- Existing unprovoked neutral-target rejection, assault evidence, expiry,
  witnessed property damage and actual network chest theft checks pass.
- Existing blueprint decoding, inventory-copy rejection, bow/ammunition,
  inspector/spectator networking and shared chunk-ticket checks pass.
- Resource task, observations, attachment, XP and unexpired defensive evidence
  survive entity serialization and saved-world restart.

The offence fixture now clears a small arena in the isolated world and asserts
the owner's line of sight before testing witnessed damage. An earlier run failed
that damage assertion with a terrain-dependent spawn fixture; it did not establish
that the owner had witnessed the action. The explicit visibility precondition
prevents that ambiguity. Sleep checks exercise the registered delegate directly
after the night clock updates; they do not measure normal work-budget admission
or long-distance travel to a bed.

## Remaining limits

These are controlled functional fixtures, not a multi-day population or
performance benchmark. They do not prove a complete wood-to-diamond survival
journey, a fully constructed safe shelter, general prerequisite planning or a
Baritone entity integration. Existing interests still choose preferred jobs;
survival-driven supply-chain arbitration remains the next substantive milestone.

Artifact SHA256:
`34f8d6f210d57c3c27417d736388367399056ef61a880c1bde8ce3fcdfb47abd`.

# Personal resource memory prototype

Branch: `feature/personal-resource-memory`. Experimental Fabric-only addition; the upstream NeoForge source tree and build remain untouched.

Each ticking NPC with AI enabled observes a rotating sample of nearby blocks: 24 probes per second within four blocks horizontally and two vertically. Only exposed coal ore and deepslate coal ore passing a collision ray from the NPC's eyes are recorded. This is omnidirectional perception, not a camera field-of-view simulation. A stationary scan covers the volume in about 17 seconds. Reads are restricted to loaded chunks, including the ray's intervening chunk columns.

At most 64 personal observations are retained, keyed by dimension and position, with a last-seen timestamp. Entries expire after three Minecraft days. Refreshing a deposit replaces its old observation; oldest entries are evicted first. Versioned data is saved in the NPC's existing persistent attachment. No chunk tickets or terrain generation are added by this feature; the mod's existing force-tick configuration still applies.

## Try a coal task

Operator commands, targeting one loaded NPC (a UUID can replace the selector):

```mcfunction
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] coal 10
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] status
/smart_npc survival @e[type=smart_npc:player_npc,sort=nearest,limit=1] cancel
```

`coal 10` records the current coal inventory count plus ten as the target. The NPC selects a remembered deposit, equips a pickaxe, walks toward it, mines using the existing block-breaking rules, collects actual drops and returns to where the request was made. Completion requires the target count still to be present on return. Coal consumed or lost during work must be replenished; this is a net inventory target, not a lifetime mining counter. Silk Touch ore does not satisfy an item-coal target. The prototype keeps the coal in the NPC inventory; chest delivery is later work.

The request survives an interruption or save/reload. Actual movement, tools and partial block-break progress are reconstructed on resume. Emergency/combat goals can preempt it; it uses the existing startup gate and work budget. Missing tools, absent deposits and inaccessible routes produce waiting states. Failed destinations receive a temporary retry delay so another remembered deposit can be attempted. Cancellation stops the task at the next goal update. Active requests cannot be silently replaced by the command.

## Current boundaries

- Observation is automatic; starting this new gathering task is currently explicit. Autonomous need selection, crafting prerequisites, storage delivery, trading, learning and schematics are subsequent milestones.
- The task uses walking navigation and only considers remembered ore within 32 blocks. It does not tunnel, bridge, explore unknown terrain or port Baritone yet.
- Waiting tasks permit other routine goals to run. Those goals can consume resources; the task's inventory target accounts for that, but this is not yet a complete intention/prerequisite scheduler.
- Resource memory currently covers coal only. Separate NPC identities do not automatically share observations.
- Runtime checks use a controlled mining fixture. Long-route navigation, combat interruptions and large populations require broader validation before promoting this build to the default development instance.

## Why Buddy commands work

The local Buddy implementation launches a second hidden Minecraft client and routes commands to that client's Baritone. Reusing the high-level task pattern is useful. Running a full client per Smart NPC is not the intended population architecture. A future Baritone adapter should be judged against this native backend using the same actor-isolation, survival-rule and performance checks.

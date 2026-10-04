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

- Observation is automatic. The first automatic decision gathers two coal when the NPC has cookable food, lacks carried fuel, can use/craft a furnace, has a pickaxe and remembers nearby coal. Missing prerequisites are explained in status and the inspector. Manual requests remain available. The complete food/tool/shelter planner, storage delivery, trading and learning remain subsequent milestones.
- Automatic coal work pauses after one minute without completion and waits another minute before reconsidering. Manual requests remain resumable until cancelled. Cancellation also suppresses immediate automatic replanning. Timers and task purpose persist across saves.
- The task uses walking navigation and only considers remembered ore within 32 blocks. It does not tunnel, bridge, explore unknown terrain or port Baritone yet.
- Waiting tasks permit other routine goals to run. Those goals can consume resources; the task's inventory target accounts for that, but this is not yet a complete intention/prerequisite scheduler.
- Resource memory currently covers coal only. Separate NPC identities do not automatically share observations.
- Runtime checks use a controlled mining fixture. Long-route navigation, combat interruptions and large populations require broader validation before promoting this build to the default development instance.

## Peaceful survival prototype

Player/NPC/villager/golem targets require recorded defensive evidence. Prank hits, golem trolling, interrupting dancers with an attack and automatic chest raiding are disabled. Cautious NPCs treat visitors as neutral. Ownership reactions require a nearby owner that can see the offender; they grant no knowledge of remote events. Actual assault and tracked property damage can trigger defence; caution and existing ally rules still apply. Evidence is bounded to 16 offenders per NPC and expires after one minute without a new offence.

Opening an owned chest issues a rate-limited warning. A server-side hook compares its contents immediately before and after the player's real container input. Item/component totals distinguish removal from rearrangement and deposits. NPC inventory transfers report theft after items actually move. Shared/team access is respected by existing ally rules; a dedicated per-container permission system remains planned. Taking an item onto the cursor counts as removal in this first implementation. Returning supplies does not yet automatically reconcile a defensive encounter.

Idle NPCs queued for routine work take short attentive pauses instead of decorative random walks. Fresh configs cap natural spawns at four; existing configured values are preserved. The bundled legacy blueprint palettes now convert to 26.4 keys, restoring their blocks and orientations. An original 4×5 starter cabin includes a bed, chest, furnace and door. Full autonomous cabin construction and the multi-day survival loop are still to be validated.

## Why Buddy commands work

The local Buddy implementation launches a second hidden Minecraft client and routes commands to that client's Baritone. Reusing the high-level task pattern is useful. Running a full client per Smart NPC is not the intended population architecture. A future Baritone adapter should be judged against this native backend using the same actor-isolation, survival-rule and performance checks.

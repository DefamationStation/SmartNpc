# Smart NPC development contract

The user's required product is a self-playing survival NPC: start in a new world,
earn supplies and equipment, establish and maintain a base, prepare and enter the
Nether, obtain the resources for the End, find and open an End portal, enter the
End and defeat the dragon. This is the primary progression goal, not a deferred
optional experiment. See `fabric/AUTONOMOUS_CAMPAIGN.md` for the acceptance chain.

- No player command, waypoint, supplied destination, gift, advancement, manual
  scheduler assignment or external AI subscription may be required for ordinary
  survival or campaign progression. Commands may inspect, reproduce, override or
  cancel behavior, but a command executor is never the production decision owner.
- For each task, trace a real automatic need -> decision -> prerequisite execution
  -> conserved action -> useful completion -> recovery path. Reuse existing native
  goals and the fair work budget. Do not add a second movement controller, random
  automatic trips or successful-looking plans with no work executor.
- Distinguish missing automatic wiring from an unimplemented capability. Record
  the latter honestly; a generic pathfinder, monster attack or flint-and-steel goal
  does not establish Nether travel or a dragon strategy.
- NPC-earned progression must come from this NPC's actual work, resources and
  dimension transitions. Human/world difficulty and legacy spawn equipment are
  separate systems; never present their equipment grants as NPC-earned progress.
- Personality/profession may change priorities, risk and route, but essential
  survival/progression actions must be driven by need. Do not make a builder
  permanently unable to obtain food, tools or campaign supplies because it lacks
  a mining/fishing interest. Optional social work cannot replace campaign progress.
- Every progression release needs an acceptance case with all normal behaviors
  enabled and no NPC task commands/direct goal calls/fake work evidence. Targeted
  fixtures may declare initial supplies or terrain, but they do not prove a fresh
  survival start or complete campaign. Keep one NPC identity/inventory through
  real process reloads, and later real portal transitions. Completion means actual
  outputs and results, not only a status message or selected task.
- Preserve failed trials and unsupported cases. Never certify the full campaign
  until the NPC actually reaches the End and defeats the dragon using earned
  resources, with attributable fight evidence and no player/cheat assistance.
- Use `fabric/verification/RELEASE-WORKFLOW.md` for frozen builds, risk-based tests,
  exact-byte deployment, both instances, upgrade preservation and repository sync.
  The setup repository's 30 GiB disposable budget and minimum 50 GiB free apply.
  Keep unfinished source/worktrees outside disposable verification directories.

The authoritative target is the Fabric module: shared upstream sources plus its
explicit overlays. Check the active setup manifest and source branch on every run;
do not overwrite current customized code with an old checkout or a stock mod.

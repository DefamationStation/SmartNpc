# Final independent workflow audit and next steps

4 October 2026. Reviewed `feature/personal-resource-memory`, baseline `648dfaa`,
and the current uncommitted `3.0.0-fabric.26.4-snapshot-2.9-search` production
changes. This audit compared the new Fabric stone/dig overlays with their
inherited source, read the registered goal wrappers and survival predicates,
and reconciled `WORKFLOW_AUDIT.md`, `DEVELOPMENT_ROADMAP.md`,
`verification/WORKFLOW-RESULTS.md` and `verification/SEARCH-RESULTS.md`.
Only this document was created by the audit. No build, installation, save,
source mutation or gameplay run was performed by this reviewer.

Runtime follow-through recorded by the parent after this source review: the
final fresh search run passed wood exploration in 1,078 ticks and covered-stone
acquisition in 632 ticks, with a 970-tick food-chain regression. The final
configured-personality saved-world replay passed in 1,160, 829 and 935 ticks
respectively. See SEARCH-RESULTS for fixture corrections and evidence limits;
this independent source audit does not replace those runtime results.

## Release blockers first

No new confirmed P1 item-conservation or property-protection regression was
identified in the reviewed production diff. WF-01's missing cooking search
admission has an implemented, narrowly scoped successor. The exact cooking
shortage now admits existing exploration/digging; the shared collectors remain
the authority for target handoff. The new overlays preserve upstream protection,
loaded-world, movement, breaking and pickup code. Actual retained original-hand
stacks now count as tools, avoiding the false missing-pickaxe decision while
native dirt clearing temporarily empties the hand.

That closes the implemented gate defect within the targeted search scenario.
It does not close the following release gates:

1. The final corrected saved-world replay is recorded against the tested
   artifact. Saving a live search/extraction task and recovering its intention
   after restart remains a separate acceptance gap.
2. Run one ordinary-selector integration scenario before promoting beyond
   experimental. Current search fixtures remove goals outside their selected
   work/safety lists (`SmartNpcCookingSearchChecks.java:147`) and remove target
   goals. They prove registered startup admission for retained goals, but omit
   competing sleep, supplies, storage, construction and normal combat targeting.
3. Complete a multi-day soak and population measurements before enabling the
   main-game installation. Existing individual completion times are not
   throughput, survival-day or population performance evidence.

## Prioritized findings

### FA-01 — P1 milestone gap: rod wood and distant-water acquisition remain absent

Baseline WF-04 remains open. Cooking search needs `NEED_WOOD` or `NEED_STONE`
inside a committed raw-food cooking intention (`SurvivalTasks.java:53–60`). A
non-fisher with two string, no logs and no raw food cannot use that new wood
admission to bootstrap its rod. The water exploration registration remains
`FISHING`-gated (`PlayerNpcEntity.java:2262–2271`). Real stored rod/string
withdrawal now helps, but missing local water still has no survival successor.
No independent string-acquisition policy exists.

Next change: reuse the current collector/exploration admission pattern for an
explicit rod-material need, then reuse native water exploration with survival
food demand. Share the relevant selected target rather than introducing a second
proximity search. Keep string sourcing a separate policy decision and continue
reporting it as a blocker until a real source is implemented.

Acceptance: two carried string plus permitted reachable timber yields a real rod
without supplied wood; a rod-bearing non-fisher with no local water reaches a
permitted loaded water site or reports a bounded concrete failure. Exercise the
ordinary selector and conserve real material inputs.

### FA-02 — P2: initial probe fairness still depends on registration layout

`PlayerNpcEntity.addWorkGoal` offsets every `CookingPrerequisiteGatedGoal` by one
registration index, modulo the inherited eight slices. This fixes the observed
pickup/log-search and collector/dig collisions for the present registration.
The scheduler still allows one expensive batch per initial probe. A newly
inserted earlier work goal can make a different predicate consume the shifted
slice first. Assertions for the two known pairs establish their separation,
not general freedom from competing expensive predicates.

Next change: keep the current regression fixture and make probe ownership/fair
predicate rotation explicit in the existing work scheduler, or introduce a
documented stable slice assignment with collision checks for all expensive
predicates. Preserve the worker limit and per-probe batch limit. Reuse
`StartupWorkGatedGoal` and `PlayerNpcAiWorkBudget`; do not add a parallel scheduler.

Acceptance: a non-holder makes bounded progress through each eligible fallback
under an exhausted local search, after registration changes and with multiple
queued NPCs. Record which predicate consumed the probe and which goal earned the
lease. Treat this as a maintainability/liveness risk, not a demonstrated failure
of the current passing registration.

### FA-03 — P2: food reserve classification still differs at owned storage

Baseline WF-05 remains open. `CheckHomeSuppliesGoal.needsFood` counts every
`DataComponents.FOOD` item (`CheckHomeSuppliesGoal.java:564–565`), while survival
food demand uses its deliberate safe-staple policy. Rod/string storage changes
did not align these food definitions. Unsafe carried food can satisfy the supply
check while the survivor still wants safe food; daily supply cadence also leaves
an urgent depletion handoff unproved.

Next change: share the safe-food classifier/count with owned supply withdrawal
and add a bounded urgent-food recheck. Reuse native owned-chest discovery and
actual transfer methods. Avoid scanning chest contents or routes every tick.

Acceptance: unsafe carried stock cannot suppress withdrawal of available owned
safe staples; depletion after the normal daily check triggers a bounded revisit;
real transfers conserve contents and player-owned chests remain protected.

### FA-04 — P2: blocked bed approach and suspended deadlines remain unresolved

Baseline WF-06 and WF-07 remain open. Inherited `SleepAtHomeGoal` decrements its
sleep budget after the near-bed branch, and repeats navigation without checking
success (`SleepAtHomeGoal.java:88–103,122–126`). A blocked bed approach therefore
lacks its own no-progress bound. The cooking/coal intention deadlines still use
elapsed game time, including suspension, lease waits and time skips. That is
bounded but is not admitted-work checkpointing.

Next change: add a Fabric sleep overlay with loaded, usable bed/stand validation
and a no-progress timeout, retaining native sleep and movement. Separately decide
whether task deadlines measure elapsed time or admitted/no-progress time; keep a
hard overall bound and expose the reason for retry.

Acceptance: a blocked/occupied bed releases movement within a declared bound;
reachable beds work for non-builders. Exercise combat, lease waiting, a night
skip, unload/reload and dimension mismatch with the same live actor.

### FA-05 — P2 verification gap: shortage-ending recovery is delegated, not guaranteed

The new cooking gate allows a cooking-admitted delegate to run its own
continuation check after the prerequisite ends. That preserves any recovery the
delegate explicitly implements; it does not add recovery. Log gathering has an
explicit descent continuation. Stone gathering still returns false on an
inactive phase at its periodic check (`GatherStoneGoal.java:386,1749–1791`), and
digging stops when its phase ends (`DigDownForStoneGoal.checkContinueEligibility`).
The current covered-stone run proves normal access/extraction/pickup, but does
not prove completion of water/unsafe-stand/access recovery when another goal or
inventory change removes the shortage mid-action.

Next change: add targeted lifecycle acceptance before claiming a universal
recovery guarantee. End the shortage during pillar descent, stone egress, water
escape and dig-route clearing; test interruption and manual task replacement.
If a required recovery is lost, reuse its existing native helper with a bounded
recovery phase and stop extraction immediately. This is an identified evidence
and continuation-policy gap, not a demonstrated death or terrain-corruption bug.

## How to proceed

The highest-value next fixture keeps the complete ordinary goal/target selectors
on one configured non-fisher. Begin with an owned chest and a food shortage,
exercise withdrawal, missing wood, distant resource search, real table/rod or
cooking preparation, nighttime deposit/rest, interruption and live save/reload.
Record goal ownership, prerequisite, blocker, worker lease and real inventory/
chest/world changes at each handoff. Add a second actor competing for the same
station or route under the native worker budget after the single-actor case.

Reuse the existing functional runner, real-drop accounting and saved-world
automation. Add assertions for expected invariants and bounded outcomes rather
than fixed maximum initial resource targets: natural leaf-stick drops can reduce
the live log shortage, as the configured replay already demonstrated.

Then implement FA-03 and the bounded sleep part of FA-04 as small independent
changes; add rod wood/water successors for FA-01; address FA-02 with measured
queued-worker evidence. Keep personal observations separate from task state and
reuse the existing task/goal/navigation/tool backend throughout. Shelter
construction, physiology, real string sourcing, durable live fishing and earned
progression remain explicit roadmap milestones.

WF-02, WF-03 and WF-08 have implementation and targeted accounting/migration
evidence from the prior workflow milestone. Their ordinary-selector nighttime
and interruption behavior still belongs in the integrated fixture above. No
closed baseline finding should be reopened solely because its old audit text
describes the pre-fix implementation.

# Resource memory prototype verification — 2026-10-04

Artifact: `SmartNpc-Fabric-3.0.0-fabric.26.4-snapshot-2.2-memory.jar`

SHA-256: `cb63f37610896be68972517decde4001c28bbe8faddf58ed5931406091afd86d`

## Build and automated checks

- Java 25 / Gradle 9.6 Fabric build: passed.
- Seven JUnit tests: passed (four existing config/event tests, three new memory/task tests).
- New tests cover bounded per-actor observations, dimension isolation, refresh/eviction, quantity validation, pending/returning task serialization and non-execution of an unknown schema.
- Access widener validation: passed.
- Upstream NeoForge `src/`, build settings/properties and wrapper remain unchanged against the port's upstream base.

## In-game checks

The smoke harness created a fresh isolated world with seed `60020261004`. Its source instance's saves were never copied or opened. A second run reopened that test save with the full development mod stack and shader configuration.

Both successful runs verified:

- An NPC records exposed coal while an enclosed ore block is excluded by visibility checks.
- A controlled NPC begins with seven coal and an iron pickaxe, mines two exposed ores, collects real drops and reaches nine coal before reporting return completion.
- The gathering and pickup goals run through the real goal selector and existing startup/work-budget wrappers, with normal entity movement and pickup enabled. Other unrelated goals are removed from this fixture for reproducibility.
- The existing bow, attachment/XP, inspector/spectator networking and shared chunk-ticket checks continue to pass.

The initial run verified entity serialization of a pending coal task. The full-stack restart additionally verified that a separate saved NPC retains its pending task and personal observation across a complete world shutdown/reload. That persistence fixture has AI disabled deliberately so the pending request remains pending during the save.

Successful initial run: 11:00–11:01 local time. Full-stack restart: 11:02–11:03 local time. Both wrote their completion marker. Local evidence is under the workspace's `.verification/smart-npc-resource-memory-v2` directory; the runner archives prior stdout/stderr on replay.

An earlier fixture manually ticked the mining controller with all normal AI disabled. It failed to complete pickup of scattered drops. The fixture was corrected to exercise real navigation/pickup and goal scheduling; its failed-run evidence remains separately under `.verification/smart-npc-resource-memory`.

## Limits

This demonstrates a small, controlled resource task and persisted intent; it is not a long survival trial. Long return routes, combat interruptions, simultaneous gathering NPCs, resource starvation, full inventories, Silk Touch tools and population CPU budgets are not yet covered by integration tests. The default development instance still contains the prior released port; this feature jar was exercised in isolated test instances. Autonomous need selection, prerequisite crafting, chest delivery and schematic integration remain planned.

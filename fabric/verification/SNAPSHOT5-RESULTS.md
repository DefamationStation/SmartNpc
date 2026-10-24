# Snapshot 5 port and survival verification — 24 October 2026

Target: launcher `26.4-snapshot-5`, internal `26.4-alpha.5`, Java 25,
Fabric Loader 0.19.5 and Fabric API 0.165.0+26.4.

Artifact: `SmartNpc-Fabric-3.0.0-fabric.26.4-snapshot-5.1-survival.jar`.
SHA-256: `5f6ca6d70dd2c7ee44de9dd4677e539ec24711cf0525686d078be4642d304c6f`.
Final integration status: passed. The exact final artifact passed native inventory
menu networking/rendering, Photon day/night/storm and shader/resource reloads,
Diorama/Punchy menus, actual Buddy login/item synchronization, and the full
Options/DH/Sodium/Iris/YACL settings path. Relevant screenshots were reviewed.

## Source preservation

The starting point is the customized `feature/personal-resource-memory` branch,
commit `d0773e689d7c4d57207f7c96a889ee0bb52b0ebb`, not a stock author jar. All
451 unchanged tracked main-source/resource files remain present. Explicit
Fabric overlays adapt only affected APIs. The shared root NeoForge source and
build remain untouched. All 94 packaged data/asset entries are identical,
including the 68 loaded building layouts, original starter cabin and empty
constructed-container policy. GPLv3 and NOTICE are retained.

The port preserves the resource-memory, daily-routine, food, rod/equipment,
cooking-prerequisite, workflow and search milestones, together with the original
home, farming, building, storage, sleep, inspector, safety and AI-budget systems.
None of the unfinished roadmap features is implied to have been completed.

Snapshot changes: dynamic block sound sets and place sounds, Math.clamp,
client entity/menu registries, single-argument HUD/GUI extraction and relocated
GUI pipelines. Inventory labels now carry explicit opaque alpha. No survival
planner, goal priority, work budget or timing policy was replaced.

## Checks

- All **39 unit tests** pass, including config/events, resource memory, fuel,
  expiring defence evidence, daily routine, cooking/rod prerequisites, food,
  covered-stone planning and shelter readiness. Access-widener validation passes.
- Full-stack fresh and saved-world trials pass entity/furnace attachment and XP
  persistence, pending resource intent/observations, shared chunk-ticket ownership,
  bow ammunition/durability, inspector and spectator request/response, tool/bow
  migration, home supplies and non-builder sleeping.
- Real survival executors collect exposed coal, gather wood, place a table,
  craft and wear a pick, dig/pick up furnace stone, place/use a furnace and
  collect cooked beef. Closed ordinary job gates still admit the committed
  cooking log-search and enclosed-stone fallbacks.
- The same actor crafts/places a table, makes a rod from its actual logs/string,
  casts, picks up a real fish and cooks it. Native water, loot, tool wear,
  recipes, navigation, pickup and furnace processing are used.
- Peaceful targeting, no default prank/raid activation, actual network chest
  removal attribution, witnessed construction damage, evidence expiry and
  constructed-container conservation continue to pass.

Controlled functional fixtures retain their documented selected-goal subsets;
they are not substitutes for ordinary-selector testing. The **ordinary** four-NPC
fixture retains all goal selectors and runs **72,000 ticks / three simulated
days**, at up to 200 ticks/second. It supplies explicit food, tools, materials
and a prepared, bounded yard with deep ground, water and ore. All four survive;
no unsolicited player/NPC targeting is detected. Observed work includes mining,
item collection, fishing, farm setup, construction, home management, eating and
returning home. The builder places **65 of 73** required cabin blocks; **eight
remain unfinished**. This demonstrates activity and integration, not reliable
completion of the full independent survival loop or a completed farm.

The first population fixture had only a one-block artificial sky floor; its
miner dug through it and died from the fall. The corrected prepared yard has
mineable depth and a bedrock bottom. This is a fixture correction, not invulnerability
or changed production mining. Normal survival actors remain vulnerable.

An initial fishing run timed out without edible fish. Added bobber/inventory
observations showed real pufferfish/junk followed by salmon in the isolated
retry. The bounded fixture allowance is 12,000 ticks; production fishing intervals,
loot tables and food policies are unchanged. The wood/search replay checks now
read the retained live log target even when natural leaf sticks satisfy a recipe
and advance the task between samples. Real harvest, recipe and accounting checks
remain enabled; no supplies are injected to make a failing phase finish.

Functional fresh/replay runs use 60 ticks/second and restore 20 before saving.
These are correctness scenarios, not FPS or population-throughput benchmarks.
All worlds are disposable verification worlds; personal saves were not opened.

## Artifact lineage and update workflow

The full functional/replay jar and the ordinary-population jar have identical
survival class/resource entries to the final artifact. The sole later content
change is the inventory screen's opaque title; its final screen is tested
separately. Independent staged builds match entry contents. Loom still adds
variable timestamps to nested NightConfig ZIP entries, so whole-archive hashes
can differ across rebuilds; freeze the exact tested archive after verification.
The original NightConfig dependencies remain nested in the final jar.

The installed register records both player and Buddy roles and the separate
source repository, upstream author branch, custom patch set and native build
route. Future snapshot upgrades must rebuild/reconcile this full module and
rerun the relevant fixtures, not substitute an author release or treat old
Snapshot 5 metadata as proof of compatibility.

The old reports and `WORKFLOW_AUDIT_FOLLOWUP.md` still apply to autonomy gaps:
relationships, broad individual progression, arbitrary poor-resource terrain,
long interruptions and dedicated-server deployment are not established here.
The ordinary fixture did not complete its cabin. This port preserves the
implemented mechanics without claiming those gaps are solved.

# Personal observation and tendencies — first M2 layer

This layer stores bounded individual evidence and stable UUID-seeded tendencies.
It does not replace existing interests, ownership policy, combat evidence or
the native survival goals. Personal learning/progression is separate.

## Data and safety

- Five seeded tendencies: sociability, caution, generosity, curiosity and
  diligence. Saved base values survive reloading; current roster interests bias
  them, and server overrides take precedence. The initial concrete influence is
  preparation: caution can require zero, one or two extra cooked food items over
  the existing four-item departure floor. It cannot grant food, reduce that
  floor or increase a path/search budget.
- At most 64 observations per NPC, with additional per-kind caps. Home, resource,
  work-site, hazard, terminal route failure, successful route, help and hostile
  encounter evidence retain dimension, position, source, time and confidence.
  Confidence decays; facts and retry evidence expire. Generic resource churn
  cannot evict a known home. Queries exclude future and expired evidence.
- New resource facts require nearby loaded blocks and line of sight. Four new
  positions and one remembered fact can be checked per admitted second. No
  inventory is inspected by this observer. Loaded-but-hidden or unloaded facts
  remain uncertain. An actually observed exhausted resource is removed.
- A successful harvest records a **work site**, not a claim that the removed ore
  still exists. Verified work events are produced by authoritative completed
  native transactions. An intention or a `DONE` result for an already missing
  block is insufficient evidence.
- Help requires an actual attributed handoff/heal callback; a nearby player is
  never blamed or credited. Hostile history reuses canonical defensive evidence
  and does not grant combat permission itself. Existing grievance/ownership
  policy remains authoritative.
- Missing legacy data seeds a profile without inventing history. Old coal
  coordinates migrate only as lower-confidence historical work sites, without
  deleting the original resource task. Unknown future versions remain read-only
  and cannot be overwritten. Spatial facts cannot cross a changed world scope;
  personal tendencies survive it. A durable overworld SavedData UUID identifies
  the save; seed/name collisions cannot share spatial knowledge. World copies
  retain their identity on reload, while independently initialized worlds receive
  different IDs. Unknown future identity data is preserved.
- Disabling recording or personality effects preserves saved data. New settings
  live in `smart_npc-memory.toml`; `trait_overrides` values of `-1` retain seeded
  tendencies and current interests. Fixed observation caps remain in force.

## Integration contract

Call `NpcMemories.register()` once during server/common initialization; it loads
settings and registers the typed event subscriber. Call `NpcMemories.save(npc)`
alongside the existing journey save before the entity's attachment is serialized.
Dirty observation writes are normally batched on the admitted tick.

`NpcMemories.work(VerifiedWorkOutcome)` consumes the shared authoritative outcome
record. Native producers are owned by the central integration branch. Terminal
journey blocking can call `routeFailed(npc,destination,reason)`, arrival can call
`routeSucceeded(npc,origin,destination)`, and automatic retries may consult
`failedRouteRecently`. Explicit manual requests and revalidated changed terrain
must remain able to retry; this memory is not a permanent unreachable label.

`departureFoodReserve(npc)` supplies the preparation policy. Existing requester
and return checks must use the same policy, retaining their four-item minimum.
`knownResources` returns evidence only: the consumer must still call
`revalidateResource` and retain native path, protection and ownership checks.

The operator-only `/smart_npc memory <npc> status` reports tendencies, preparation
and bounded fact counts. Contextual social dialogue, additional tendency effects
and broad multi-NPC acceptance remain later M2 work; storing five numbers alone
does not claim those behaviours are delivered.

## Verification

Fourteen new model/codec tests cover identity stability and diversity, interest
biases and explicit overrides, the preparation floor/cap, per-kind/global caps,
same-origin/destination failure expiry, decaying confidence, clock rollback,
duplicate/older evidence, attributed encounters, NBT round-trip, missing legacy
state, future-version preservation, invalid saved entries and world isolation.
Four further world-identity tests cover independently initialized worlds,
identity round-trip, corrupt legacy identity and future saved-data preservation.
An unsupported future world identity also forces current NPC attachments into a
read-only, empty-spatial-view mode: an unreadable identity cannot erase or
rewrite existing facts through a changing fallback UUID. Together with the
existing tests, this core has 61 unit tests.

`SmartNpcMemoryScenario` is a controlled transaction/persistence fixture using a
disposable copied world and all normal 80 goals plus three target goals. It
creates two NPCs with the same current interests but different UUID tendencies.
Their preparation policy returns six versus four cooked items. A real native
coal harvest becomes a work site; removed, hidden and remote ores do not become
current destinations. A real one-food inventory handoff is attributed, with 32
food items conserved. An actual process restart restores both original actors,
profiles, observed work site and attributed help.

This is not wilderness survival or autonomous-trading proof. The fixture bridges
the same completed-transaction event as the production producer, and explicitly
flushes before its save checkpoint. Central initialization/save/producer hooks
and actual journey admission are tested again after integration. Early fixture
compilation/configuration failures remain private harness failures, not mod passes.

The durable-world follow-up artifact is
`fb13873bd39f449014d1f2230813d7fb2c24a48b235678f7c1dd0e46e4cff271`.
Both `smartnpc5-continuous-memory-world-a` and `smartnpc5-continuous-memory-world-b`
pass the real process replay. They start from identical seed/name/source-world
fingerprints without a pre-existing personal-memory world ID, receive different
saved UUIDs, and each retains its own UUID after reloading. Earlier core artifact
`20abea05…` passed the transaction/persistence case before the world UUID change;
it is not substituted for these follow-up checks.

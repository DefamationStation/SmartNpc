# Explicit NPC food offers: bounded relationship evidence

Snapshot 6 integration update: production registration and canonical relationship
wiring are now present. See [native prepared-survival results](PREPARED-SNAPSHOT6-RESULTS.md)
for current exact-artifact interaction, attribution and saved-process replay proof.
The leaf-commit statements below describe their original historical scope.

This is an initial relationship foundation: a real, attributed helpful action
can become remembered history. It does not implement the whole relationships
milestone, trust ranks, trade, shared storage, permission grants or reconciliation.

## Existing native paths audited

The Fabric InventoryViewerItem sends inspector snapshots, rather than opening
the legacy InventoryViewerMenu. No current gameplay openMenu path for that menu
was found. The legacy menu's shift-click transfer alone therefore cannot be
claimed as a working gift UI. Native EatHealingFoodGoal consumes the NPC's own
food. It supplies no exact player actor, so it is never credited as player help.
Opening a chest, proximity, inspection, automatic withdrawals and NPC self-healing
are not gift evidence.

## Interaction

Crouch-right-click an NPC with cooked beef, porkchop, chicken, mutton, rabbit,
cod, salmon or a baked potato in the main hand. This is distinct from crouching
with an empty hand to ask for a contextual reply. Acceptance transfers exactly
one real held food item, preserving its components, into the NPC inventory.

The connected server player must be alive, in Survival, in the same world,
within four blocks, mutually visible and not mounted. The NPC must be alive,
awake, safe, unmounted, not healing and free of a combat target. A known canonical
offense is not forgiven by offering food. The NPC accepts only while below its
existing personality-adjusted departure reserve of four to six cooked items and
only when a compatible stack or empty inventory slot has capacity. Other items
and ordinary non-crouched interactions pass through untouched.

`NpcFoodOffers.register()` registers one Fabric UseEntityCallback and loads
`smart_npc-food-offers.toml`. Registration belongs in central Fabric initialization
after personal-memory initialization. The leaf files require no mixin JSON edits.
The new namespace remains unregistered in the shared main branch until accepted
by central integration.

The config can disable explicit offers reversibly. It defaults to a 200-tick
player/NPC accepted-offer cooldown and a 20-tick whole-NPC accepted-offer cooldown.
Only successful transfers start these cooldowns. Each NPC persists at most sixteen
actor timestamps; unknown future ledger versions stay read-only. Unsupported
future personal-memory or world-identity formats cause the offer to be declined
without changing either inventory or existing saved evidence. Disabling memory
also disables the offer; disabling NPC chat suppresses its private acknowledgement.

After the conserved handoff completes, the exact acting UUID is recorded through
`NpcMemories.helpReceived` and the accepted-offer ledger is saved immediately in
the existing persistent attachment. No nearest actor is inferred. No relationship
rank, XP, items, team membership or property access is granted.

## Verification

Six pure ledger tests cover accepted-only cooldown admission, exact actor
round-trip, hard capacity, unknown future versions, malformed legacy entries and
clock rollback. The branch has 75 unit tests, passing in candidate
`d61d22d9728bcf48c4c383d6b2077d695461a9c85474da1b9b0175232225686a`.

Native adversarial server-callback and connected-client/save-replay fixtures are
included separately. Their runtime outcomes are recorded after execution below;
compiling the data model is not treated as native interaction proof.
## Native results

On the exact `d61d22d9…` candidate, `continuous-food-offer-client-v2` passes
actual native client interaction and saved-world process replay. The fixture
creates a new isolated Survival world with the full installed player mod stack,
then sends the real client MultiPlayerGameMode interaction packet while the
connected player holds crouch and cooked beef. The real player inventory changes
from three to two; the NPC inventory changes from two to three. Only the actual
connected player's UUID becomes ACTUAL_HANDOFF help evidence. All eighty NPC
goals remain present. The acknowledgement reaches the actual client's chat log.

The client cleanly exits and saves its own fresh world. A separate process then
loads that same world through native quick play. The original real player UUID,
player inventory of two foods, NPC inventory of three, exact help actor and
accepted-offer pair cooldown survive. The fixture does not explicitly flush
NpcMemories before closing: it uses the native accepted-offer save and normal
player/world persistence. No original player world is copied or modified.

The first client fixture version failed compilation because Snapshot 5's native
interaction method takes an EntityHitResult. The corrected second version uses
the actual native method. The failed harness case remains preserved; it is not
reported as a game pass. The client holds the crouch key every gesture tick so a
separate window changing focus does not silently clear the test's input.

`continuous-food-offer-safety-v1` passes adversarial real Fabric server callback
and transaction checks using captured synthetic ServerPlayer output. Raw food,
ordinary interaction, disabled feature or memory, offhand, creative source,
full recipient inventory, future ledger or memory schema, and remote offers
preserve items and produce no help history. A valid offer preserves its custom
item name/components, transfers exactly one unit, credits the actual giver rather
than a closer bystander, and cannot repeat within the accepted-pair cooldown.
The offer does not create team membership. This fixture is server callback
coverage; the preceding connected-client test provides the network proof.

All three passing logs contain no low-space or world-save failure warnings.
GUI tests use isolated shader-off settings, with production graphics untouched.
No deployment or shared initialization change is included in this leaf commit.

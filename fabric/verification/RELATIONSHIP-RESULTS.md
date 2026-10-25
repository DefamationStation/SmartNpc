# UUID relationship evidence: initial M4 foundation

This first implementation records attributed recent cooperation and canonical
offense evidence, and exposes advisory familiarity/trust. It grants no loyalty,
team membership, aggression permission, access, ownership, sharing or forgiveness.
Existing SocialSafety and property permissions remain authoritative. The complete
relationship milestone, explicit consent, trade and reconciliation remain open.

`RelationshipBook` holds at most sixteen UUID contacts, eight facts per contact
and sixty-four facts total. Facts retain their actual kind, source, registry
resource, units and original time. Actual accepted food is a CONSERVED_HANDOFF;
a future confirmed exchange can use its separate CONSERVED_EXCHANGE category.
ASSAULT, THEFT, VANDALISM and ALLY_DEFENCE retain distinct CANONICAL_DEFENCE causes.
Ally defense is never described as a direct assault on this NPC.

Positive influence is shared across all helpful action types for one actor over
a 1,200-tick window. Large units do not multiply it. There is no passive proximity,
chat, inventory inspection, chest opening or inferred nearest-player influence.
A repeated canonical cause refreshes only its latest actual evidence; it does not
create many stacked copies. Active negative history cannot be erased or outweighed
by positive spam, and is preferred over positive history during bounded eviction.

Assistance/confirmed exchange evidence expires after 72,000 ticks and offense
history after 24,000. Future-clock facts are ineligible without being presented as
current history. Familiarity and trust are derived from eligible evidence only.
An active recorded offense remains CAUTIOUS even if positive scores would otherwise
outweigh it. Advisory caution never creates or extends combat authority.

The versioned codec bounds decoding and skips malformed actors, invalid sources,
invalid registry IDs, invalid units and duplicate positive windows. Missing old
data starts empty. Unsupported future versions expose an empty read-only view
and are preserved without writes. Disabling relationship evidence preserves data.
The configuration lives in smart_npc-relationships.toml.

Root integration APIs:

- `NpcRelationships.register()` loads settings and registers one operator-only
  status command. The food-offer register method calls this idempotently.
- `acceptedFood(npc, actualGiverUUID, registryItem, actualUnits)` is called by the
  completed conserved food transaction, after both inventories have changed.
- `canonicalEncounter(npc, actualOffender)` must be called by central integration
  after successful SocialSafety.record. It checks the existing exact canonical
  cause again and never manufactures a permission.
- `advice(npc, exactActorUUID)` returns an advisory snapshot. The status command
  `/smart_npc relationship <npc> status` reports only the requesting operator's
  actual UUID relationship, with an explicit rights disclaimer.

Nine pure model/codec tests pass in artifact
`d9404ca6989c8021d838bc14abe67e9027b0c653718fb3a7a77241bcf2c86a10`,
bringing this branch to 84 tests. They cover exact UUID attribution, modest
influence, shared positive cooldowns and unit-independent influence, current
negative history under positive churn, trusted cause/source matching, expiry and
clock rollback, per-contact/global caps, exact codec round-trip, unknown future
preservation, malformed UUIDs and duplicate-window rejection. Native food/event
and save/reload evidence will be recorded after the actual follow-up tests;
these pure tests alone do not establish complete relationship behavior.

No barter gesture is added. A future exchange requires an explicit reviewed
offer and a deliberate accept action with an expiring token bound to the exact
actor/NPC, actual shortage/surplus and a conserved transaction. Holding a sample
item in the offhand will not silently convert an intended gift into a purchase.
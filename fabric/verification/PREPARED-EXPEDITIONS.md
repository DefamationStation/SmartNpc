# Optional prepared expeditions

This opt-in operator command adds a prepared commitment without replacing native
survival gathering, tool crafting, cooking, shelter work, or direct journey
commands:

`/smart_npc expedition <npc> go <x> <y> <z> [water]`

The optional `water` flag requests a real carried/owned mounted boat prerequisite;
it does not scan the world or promise that any route is safe or water-free.

Admission requires verified personal cooking and safe-rest milestones plus actual
current food reserve, carried usable tools, same-dimension owned home and any
requested boat. It also requires an alive, awake, unpaused resident who is not
healing, fighting, burning, following, escaping or already returning. Active
cooking/manual resource work and every journey phase except `idle` decline with
explicit reasons. This includes `at_destination`, which is an existing
commitment even though the native journey `active()` predicate excludes it.

The final native `Journeys.start` check remains authoritative for near-home,
food, supported dry departure and owned boat conditions. Accepted commitments
use the original native journey controller, worker budget, trail serialization,
return/cancel commands and inventory. History confers neither permanent access
nor gear: a previously capable resident can decline again after losing supplies.

Registration belongs to the integration owner:

`Events.register(PreparedExpeditions.class);`

The leaf package and pure policy tests do not register or intercept any goals.

## Verification scope

Four pure policy tests check that earned history and current materials are both
required, unsafe/busy actors cannot commit, and inspection cannot mutate or
unlock state. The private `1f45c9e6...` candidate passed 94 total unit tests.

`SmartNpcPreparedExpedition` is explicitly a controlled integration fixture:
its flat corridor and historical cooking/rest inputs are declared setup data,
not claims that the fixture earned those milestones naturally. It checks
missing-history/tool/boat, paused, stale-arrival and active-trip declines without
changing route or carried stock. The actor then physically walks 128 blocks and
returns through the normal goal selector, with outbound and returning process
restarts, original UUID, all 80 ordinary goals and unchanged food/tools.

Both complete cases pass against the same private candidate. The API case
is `smartnpc5-continuous-prepared-v1`. The command case
`smartnpc5-continuous-prepared-command-v2` additionally registers the provider in
the fixture and starts the native trip through actual Brigadier command dispatch.
These are private next-development candidates, not installed releases or
natural terrain/cooking/rest proofs. Separate final-release navigation cases
cover original terrain and real 2,048-block travel with both process restarts.


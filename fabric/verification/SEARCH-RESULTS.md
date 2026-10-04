# Cooking resource-search fallback

4 October 2026. Experimental version `3.0.0-fabric.26.4-snapshot-2.9-search`.
Addresses WF-01 from the [baseline workflow audit](../WORKFLOW_AUDIT.md).

## Implementation

The registered log exploration, stone exploration and dig-down fallback accept
the exact committed cooking shortage in addition to their existing profession
gate. This does not assign a daily job or admit unrelated work. The gate remains
an `InterestGatedGoal` subtype inside the normal startup/worker wrapper, including
its cheap eligibility preflight and performance accounting. After a cooking
admission, the outer gate delegates continuation rather than ending work solely
because the shortage predicate changed. Native stop/recovery predicates remain
authoritative; all shortage-ending recovery scenarios are not yet verified.

Survival and ordinary work share the same log and stone collector instances.
During cooking, exploration yields to the collector's retained usable target;
it does not launch an independent proximity search that can disagree with the
collector. The dig fallback reads the stone collector's published arbitration
cache during cooking. Its existing route selection, breaking, pickup, cooldowns,
protection, weather and loaded-chunk guards remain native.

The native stone phase and prepared-base checks recognise a current cooking
stone shortage without requiring a profession or built home. Cooking dig work
targets the missing quantity toward eight furnace-compatible stone items.
Other jobs retain their original phase and quantity policies.

The targeted empty-resource test exposed another admission handoff: before an
NPC owns a worker lease, only one expensive batch is allowed per probe tick.
At the existing registration slices, an empty pickup probe consumed the same
turn as log exploration, and an empty local collector consumed the same turn as
stone digging. The exact prerequisite wrappers now use the following probe
slice, with the original registration counter, eight-slice quota and priorities
unchanged. Tests inspect the actual registered slices for those two conflicts.
This is a narrow registration fix, not general fair batch arbitration among all
goals; later registration changes must preserve the tested separation.

The dirt-to-stone test also exposed tool ownership visibility. Native bare-hand
dirt clearing temporarily stores an original held pickaxe in the shared swap
transaction. Carried-tool eligibility now recognises that real pending stack,
so cooking and digging do not misclassify it as missing. No mirrored weapon or
synthetic replacement counts toward this read. Native stop still restores the
actual stack; synchronous checks cover wear/components, stale-owner stops and
repeated stops while cooking snapshot and stone eligibility remain ready.

## Verification status

The Java 25 build passes **39 unit tests**. The final fresh search fixture passes:
wood exploration and real drop pickup leading to starter-pick crafting completed
in **1,078 ticks**; covered-stone digging and collection completed in **632 ticks**.
The stone actor dug a native access route, then handed exposed stone to the
local collector and picked up eight real cobblestone with a worn wooden pickaxe.
Full-stack fresh run and final saved-world replay both pass
(`.verification/smart-npc-search-v6`).
The fixtures do not establish empty-inventory survival, general world resource
guarantees or multi-day ordinary-selector autonomy.

The two search phases use separate actors. The wood phase supplies only two raw
beef and places several canopy trees beginning 22 blocks from the starting stand,
outside the collector's 16-block horizontal window. The stone phase supplies two
raw beef, four logs and a wooden pickaxe to isolate digging from tool bootstrap;
it starts with no exposed fixture stone, cobblestone, table or furnace. It must
observe real digging, tool wear, naturally removed stone and eight picked-up
cobblestone. The retained selector includes the existing safety goals and selected
work goals, so it does not prove ordinary-selector arbitration with sleep,
storage, professions, relationships or competing construction work.

Final fresh regressions: prior wood prerequisites 1,144 ticks, full cooking
prerequisites 1,770 ticks, first edible fishing pickup 4,241 ticks, and the
table/rod/fish/cooked-food chain 970 ticks.
These are individual controlled runs,
not comparative performance or population throughput measurements.

The configured-personality replay completed wood exploration/preparation in
**1,160 ticks** and covered-stone collection in **829 ticks**. In both phases the
ordinary profession gate was asserted closed during the committed shortage.
The replay food chain retained cooked cod in **935 ticks**. The fresh chain
retained cooked salmon. Both runs include tool/storage accounting, social safety,
sleep, coal requests, networking, chunk-ticket and equipment migration regressions.

The final [independent follow-up audit](../WORKFLOW_AUDIT_FOLLOWUP.md) found no
confirmed new P1 conservation or property-protection regression and records
ordinary-selector, source-successor, recovery and scheduling work still required.

The current roster parser requires at least one profession on every configured
name. Setting an explicit runtime name does not change that: synchronized-name
cache reconstruction reloads configured interests. The initial jobless fixture
therefore failed its setup assertion before testing the fallback. The stress
fixture now initializes the normal name/revision cache, then substitutes a
LOOTING-only cached identity once during setup and checks that it stays jobless.
This is synthetic fixture setup, not newly supported jobless roster configuration
or persisted personality state. Worker/interest admission and goal execution are
not overridden by the fixture.

Saved-world replay uses an ordinary configured EXPLORING personality instead of
the synthetic identity. Both modes require the profession gate to remain closed
during the shortage while the prerequisite fallback runs through registered goals.

The first configured replay crafted a pick after collecting two logs plus two
natural leaf-drop sticks. Its setup supplied no sticks, but the test incorrectly
required the maximum initial three-log target even after those sticks reduced
the native recipe shortage. The wood assertion now follows the last active log
target and verifies real harvested logs, a placed table and the crafted pick.
This corrects fixture accounting; native loot and recipe decisions are unchanged.
The older local-wood regression had the same initial-target assumption and now
also follows the live target. Its wood-cost check distinguishes the nine planks
needed when sticks are crafted from the seven needed when natural sticks are
already available. The completed fresh run used the earlier stricter local-wood
assertion; the final replay compiles the corrected regression harness.

# Required autonomous survival campaign

User direction, reaffirmed 1 November 2026: Smart NPC is a self-playing Minecraft
survivor, from a fresh start through the Nether and the End to defeating the
dragon. This governs development priorities. Settlement decoration, relationships
and optional commands must not substitute for the progression chain.

## Campaign acceptance sequence

| Stage | NPC must decide and execute itself | Required evidence |
| --- | --- | --- |
| C0: Fresh arrival | Obtain wood, crafting access, starter tools, stone and food; choose safety and a base location | Declared fresh spawn policy; actual gathering/crafting/eating or food management; no gifted advanced kit, manual task or player advancement prerequisite |
| C1: Sustainable home | Build usable shelter, replenish food/fuel/tools, store and retrieve materials, rest, repair and recover from shortages | Useful completed work across ordinary days, scarcity, interruption and saved-process reload, with resource accounting |
| C2: Earned equipment and expeditions | Decide which material is missing, prepare the required tool/food/transport, find resources, gather and return, smelt and craft upgrades | Individual actual gear/materials, evidenced destination discovery, real work at destination, conserved output and original home return; no supplied waypoint |
| C3: Nether access | Acquire materials by a legitimate survival method, build or repair and ignite a portal, prepare for hazards and enter it | Actual frame/material use/ignition/portal transition; same NPC and inventory; saved endpoints and feasible return route |
| C4: Nether objectives | Discover and approach a fortress, handle hazards/combat, obtain blaze resources and return; obtain pearls through a legitimate implemented route | Actual observation/search/combat or conserved exchange, real item acquisition and return through portals; no hidden fortress coordinates or free rods/pearls |
| C5: End access | Craft eyes from earned resources, use legitimate locating behavior, find a stronghold, reach and fill its portal and enter | Consumed materials/eyes, real navigation and portal interaction, same saved actor across dimensions; no teleport or known-seed structure lookup presented as NPC knowledge |
| C6: Dragon | Prepare supplies/equipment, handle crystals and dragon phases, survive/recover and defeat the dragon | Actual NPC-controlled movement and combat, conserved resources and attributable dragon defeat; no player fight assistance or kill command |

These are capability requirements, not a timer or an age label. Use a prerequisite
graph with explicit blockers and bounded recovery. Different routes are permitted
when they use real Minecraft mechanics, owned/authorized resources and tested
execution. A larger farm, trade or settlement must not become an artificial
mandatory prerequisite if a smaller legitimate survival method suffices.
Personality and profession affect priorities, risk and timing; they cannot become
a permanent substitute for required survival/progression capability. Essential
actions must admit a real shortage even without the matching specialist interest.

## Required behavior and testing discipline

Each implemented step needs an ordinary automatic caller, a real executor,
post-success evidence, saved intent and failure/recovery behavior. Commands may
read state or override a task for diagnostics; executing them is never evidence
that the NPC independently selected or completed the step. Audit command handlers
against automatic call sites whenever adding or extending a task.

Stage tests can declare supplies and geometry to isolate a defect, but label that
scope. End-to-end campaign acceptance starts from a declared fresh NPC with no
undeclared advanced inventory, a single identity and conserved resources. Include
actual process closes/reloads during work and on both sides of portal travel;
changed/depleted resources, missing fuel, tool replacement, danger and returning
home must resolve without developer commands. Do not disable normal competing
behaviors to force a successful result. Use the existing AI scheduler and bounded
loaded-frontier travel rather than simulating a separate client per NPC.

Human advancements, global world difficulty, age labels and legacy generated spawn
kits are not this NPC's achievements. Keep them separate from personal earned
progression. A future fresh-survivor spawn mode needs its own zero-start acceptance
before replacing existing population behavior; don't strip loaded NPC inventories.

## Current implementation truth — 1 November 2026

- Local food, basic/iron equipment, construction, gathering, supply returns,
  cooking and selected saved-world routines have real implemented native paths and
  targeted evidence. This does not close every C0–C2 scenario.
- The `.9` no-command wood supply/repair loop is verified; food fuel gathering
  already has an automatic parent. The command audit found raw-material smelting
  missing equivalent fuel coverage; its correction has a separate release receipt.
- Explicit prepared `go/visit` requests have readiness and movement backends, but
  there is no general autonomous purpose/destination planner around that admission
  API. Useful task-specific travel exists; arbitrary auto-visits are not a fix.
- Legacy world progression and spawn kits are influenced by the human/world.
  They are not earned NPC capability and must not be used to claim C2 onward.
- Portal preparation/entry and cross-dimension campaign recovery, fortress/blaze
  work, eyes/stronghold/End entry and a dragon strategy remain unimplemented.
  They are required campaign backlog, not existing working features or deferred
  optional ambitions. Do not claim them on the strength of generic combat/travel.

Prioritize reliable C0–C2 automatic prerequisites and saved purpose selection,
then implement C3–C6 in that dependency order. Optional social/settlement work may
support this goal but must not displace it. The historical M0–M6 domain identifiers
in `DEVELOPMENT_ROADMAP.md` remain useful references; this campaign sequence is the
governing progression priority.

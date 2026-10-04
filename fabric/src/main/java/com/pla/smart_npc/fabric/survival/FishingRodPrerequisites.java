package com.pla.smart_npc.fabric.survival;

/** Food-driven rod admission only; actual recipes, stations and inventory commit stay native. */
public final class FishingRodPrerequisites {
    public enum State { NOT_NEEDED, BLOCKED_STRING, BLOCKED_WOOD, NEED_TABLE, READY }

    public record Snapshot(boolean foodNeedsRod, int string, int sticks, int plankEquivalent,
                           boolean tableAvailable) {
        public Snapshot {
            if (string < 0 || sticks < 0 || plankEquivalent < 0)
                throw new IllegalArgumentException("Material counts must be nonnegative");
        }
    }

    private FishingRodPrerequisites() { }

    public static State choose(Snapshot snapshot) {
        if (!snapshot.foodNeedsRod()) return State.NOT_NEEDED;
        if (snapshot.string() < 2) return State.BLOCKED_STRING;
        int stickPlanks = snapshot.sticks() >= 3 ? 0 : 2;
        int tablePlanks = snapshot.tableAvailable() ? 0 : 4;
        if (snapshot.plankEquivalent() < stickPlanks + tablePlanks) return State.BLOCKED_WOOD;
        return snapshot.tableAvailable() ? State.READY : State.NEED_TABLE;
    }
}

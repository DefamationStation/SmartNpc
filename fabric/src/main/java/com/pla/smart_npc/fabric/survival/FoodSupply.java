package com.pla.smart_npc.fabric.survival;

/** Pure admission policy for a small carried food reserve; execution stays in native goals. */
public final class FoodSupply {
    public static final int RESERVE = 4;
    public enum State { RESERVE_READY, UNSAFE, COOK_FIRST, NEED_ROD, FISH }
    public record Snapshot(int foodCount, boolean safe, boolean cooking, boolean rod) {
        public Snapshot {
            if (foodCount < 0) throw new IllegalArgumentException("Food count must be nonnegative");
        }
    }
    private FoodSupply() { }
    public static State choose(Snapshot snapshot) {
        if (!snapshot.safe()) return State.UNSAFE;
        if (snapshot.cooking()) return State.COOK_FIRST;
        if (snapshot.foodCount() >= RESERVE) return State.RESERVE_READY;
        return snapshot.rod() ? State.FISH : State.NEED_ROD;
    }
}

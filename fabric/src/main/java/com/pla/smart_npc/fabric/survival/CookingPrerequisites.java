package com.pla.smart_npc.fabric.survival;

/** Inventory-only cooking dependencies. Station discovery and movement belong to the goals. */
public final class CookingPrerequisites {
    public enum State { READY, NEED_WOOD, NEED_PICKAXE, NEED_STONE, NEED_FURNACE, NEED_FUEL }

    /**
     * Ready stations include carried stations that can be placed later. Fuel includes any
     * carried cooking fuel, not just coal. Plank equivalents count only convertible logs.
     */
    public record Snapshot(boolean cookingNeeded, boolean furnaceReady, boolean fuelReady,
                           boolean pickaxeReady, boolean craftingTableReady, int furnaceStone,
                           int plankEquivalent, int sticks) {
        public Snapshot {
            if (furnaceStone < 0 || plankEquivalent < 0 || sticks < 0) {
                throw new IllegalArgumentException("Material counts must be nonnegative");
            }
        }
    }

    private CookingPrerequisites() { }

    public static State choose(Snapshot inventory) {
        if (!inventory.cookingNeeded()) return State.READY;
        if (!inventory.furnaceReady()) {
            if (inventory.furnaceStone() < 8) {
                if (!inventory.pickaxeReady()) return pickaxePrerequisite(inventory);
                return State.NEED_STONE;
            }
            if (!inventory.craftingTableReady() && inventory.plankEquivalent() < 4) {
                return State.NEED_WOOD;
            }
            return State.NEED_FURNACE;
        }
        if (!inventory.fuelReady()) {
            if (!inventory.pickaxeReady()) return pickaxePrerequisite(inventory);
            return State.NEED_FUEL;
        }
        return State.READY;
    }

    private static State pickaxePrerequisite(Snapshot inventory) {
        int tablePlanks = inventory.craftingTableReady() ? 0 : 4;
        int stickPlanks = inventory.sticks() >= 2 ? 0 : 2;
        return inventory.plankEquivalent() >= tablePlanks + 3 + stickPlanks
                ? State.NEED_PICKAXE : State.NEED_WOOD;
    }
}

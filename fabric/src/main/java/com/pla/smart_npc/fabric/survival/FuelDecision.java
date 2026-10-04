package com.pla.smart_npc.fabric.survival;

/** The first explicit survival prerequisite: prepare cooking fuel from personal knowledge. */
public enum FuelDecision {
    NONE, NEED_FURNACE, NEED_TOOL, FIND_COAL, GATHER;
    public static FuelDecision choose(boolean cookingNeeded, boolean fuelReady, boolean furnaceReady,
                                     boolean pickaxeReady, boolean observedCoal) {
        if (!cookingNeeded || fuelReady) return NONE;
        if (!furnaceReady) return NEED_FURNACE;
        if (!pickaxeReady) return NEED_TOOL;
        return observedCoal ? GATHER : FIND_COAL;
    }
}

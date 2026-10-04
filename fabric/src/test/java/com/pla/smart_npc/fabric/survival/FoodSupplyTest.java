package com.pla.smart_npc.fabric.survival;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodSupplyTest {
    @Test void reserveRequiresFourActualItems() {
        assertEquals(FoodSupply.State.FISH, FoodSupply.choose(new FoodSupply.Snapshot(3, true, false, true)));
        assertEquals(FoodSupply.State.RESERVE_READY, FoodSupply.choose(new FoodSupply.Snapshot(4, true, false, true)));
    }
    @Test void emergencyAndCookingPreemptAcquisition() {
        assertEquals(FoodSupply.State.UNSAFE, FoodSupply.choose(new FoodSupply.Snapshot(0, false, true, true)));
        assertEquals(FoodSupply.State.COOK_FIRST, FoodSupply.choose(new FoodSupply.Snapshot(1, true, true, true)));
    }
    @Test void missingRodDoesNotAuthorizeFishing() {
        assertEquals(FoodSupply.State.NEED_ROD, FoodSupply.choose(new FoodSupply.Snapshot(0, true, false, false)));
        assertEquals(FoodSupply.State.RESERVE_READY, FoodSupply.choose(new FoodSupply.Snapshot(8, true, false, false)));
    }
    @Test void invalidStockIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FoodSupply.Snapshot(-1, true, false, true));
    }
}

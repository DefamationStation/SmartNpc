package com.pla.smart_npc.fabric.survival;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FishingRodPrerequisitesTest {
    @Test void missingStringBlocksEvenWithPlentifulWoodAndATable() {
        for (int string = 0; string < 2; string++)
            assertEquals(FishingRodPrerequisites.State.BLOCKED_STRING,
                    choose(true, string, 64, 64, true));
        assertEquals(FishingRodPrerequisites.State.READY, choose(true, 2, 3, 0, true));
    }

    @Test void tableAndStickCostsCannotSpendTheSameWoodTwice() {
        assertEquals(FishingRodPrerequisites.State.BLOCKED_WOOD, choose(true, 2, 0, 4, false));
        assertEquals(FishingRodPrerequisites.State.BLOCKED_WOOD, choose(true, 2, 2, 5, false));
        assertEquals(FishingRodPrerequisites.State.NEED_TABLE, choose(true, 2, 0, 6, false));
        assertEquals(FishingRodPrerequisites.State.NEED_TABLE, choose(true, 2, 3, 4, false));
    }

    @Test void carriedOrExistingTableRemovesOnlyTableWoodCost() {
        assertEquals(FishingRodPrerequisites.State.BLOCKED_WOOD, choose(true, 2, 2, 1, true));
        assertEquals(FishingRodPrerequisites.State.READY, choose(true, 2, 2, 2, true));
        assertEquals(FishingRodPrerequisites.State.READY, choose(true, 2, 3, 0, true));
    }

    @Test void cookingReserveAndUsableRodPreemptPreparation() {
        assertEquals(FishingRodPrerequisites.State.NOT_NEEDED, choose(false, 0, 0, 0, false));
        assertEquals(FishingRodPrerequisites.State.NOT_NEEDED, choose(false, 2, 3, 4, false));
    }

    @Test void invalidMaterialsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new FishingRodPrerequisites.Snapshot(true, -1, 0, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FishingRodPrerequisites.Snapshot(true, 0, -1, 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FishingRodPrerequisites.Snapshot(true, 0, 0, -1, false));
    }

    private static FishingRodPrerequisites.State choose(boolean need, int string, int sticks, int wood, boolean table) {
        return FishingRodPrerequisites.choose(new FishingRodPrerequisites.Snapshot(need, string, sticks, wood, table));
    }
}

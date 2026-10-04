package com.pla.smart_npc.fabric;

import com.pla.smart_npc.fabric.survival.CookingPrerequisites;
import com.pla.smart_npc.fabric.survival.CookingPrerequisites.Snapshot;
import org.junit.jupiter.api.Test;
import static com.pla.smart_npc.fabric.survival.CookingPrerequisites.State.*;
import static org.junit.jupiter.api.Assertions.*;

class CookingPrerequisitesTest {
    @Test void idleAndCompleteCookingNeedNoMiningTool() {
        assertEquals(READY, CookingPrerequisites.choose(new Snapshot(false, false, false, false, false, 0, 0, 0)));
        assertEquals(READY, CookingPrerequisites.choose(new Snapshot(true, true, true, false, false, 0, 0, 0)));
    }

    @Test void furnaceComesBeforeFuelEvenWhenFuelIsAlreadyCarried() {
        assertEquals(NEED_STONE, CookingPrerequisites.choose(new Snapshot(true, false, true, true, false, 7, 0, 0)));
        assertEquals(NEED_FURNACE, CookingPrerequisites.choose(new Snapshot(true, false, false, false, true, 8, 0, 0)));
        assertEquals(NEED_WOOD, CookingPrerequisites.choose(new Snapshot(true, false, true, true, false, 8, 3, 0)));
        assertEquals(NEED_FURNACE, CookingPrerequisites.choose(new Snapshot(true, false, true, true, false, 8, 4, 0)));
    }

    @Test void reservesTableAndStickMaterialsBeforeRequestingPickaxe() {
        assertEquals(NEED_WOOD, CookingPrerequisites.choose(new Snapshot(true, false, false, false, false, 0, 8, 0)));
        assertEquals(NEED_PICKAXE, CookingPrerequisites.choose(new Snapshot(true, false, false, false, false, 0, 9, 0)));
        assertEquals(NEED_WOOD, CookingPrerequisites.choose(new Snapshot(true, false, false, false, true, 0, 4, 1)));
        assertEquals(NEED_PICKAXE, CookingPrerequisites.choose(new Snapshot(true, false, false, false, true, 0, 3, 2)));
        assertEquals(NEED_PICKAXE, CookingPrerequisites.choose(new Snapshot(true, false, false, false, false, 0, 7, 2)));
    }

    @Test void existingFuelAndStationsSkipUnnecessaryResourceWork() {
        assertEquals(NEED_FUEL, CookingPrerequisites.choose(new Snapshot(true, true, false, true, false, 0, 0, 0)));
        assertEquals(NEED_PICKAXE, CookingPrerequisites.choose(new Snapshot(true, true, false, false, true, 0, 5, 0)));
        assertEquals(NEED_WOOD, CookingPrerequisites.choose(new Snapshot(true, true, false, false, false, 8, 8, 0)));
        assertEquals(READY, CookingPrerequisites.choose(new Snapshot(true, true, true, false, false, 0, 0, 0)));
    }

    @Test void rejectsInvalidResourceCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, false, false, false, false, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, false, false, false, false, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(true, false, false, false, false, 0, 0, -1));
    }
}

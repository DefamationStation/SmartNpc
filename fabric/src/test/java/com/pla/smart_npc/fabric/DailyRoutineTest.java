package com.pla.smart_npc.fabric;

import com.pla.smart_npc.fabric.survival.DailyRoutine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyRoutineTest {
    @Test void lateSpawnAndMissedMorningHaveWork() {
        assertTrue(DailyRoutine.needsSelection(3, 18000, -1, false, true));
        assertTrue(DailyRoutine.needsSelection(3, 18000, 2, true, true));
        assertTrue(DailyRoutine.needsSelection(3, 6000, 3, false, true));
    }
    @Test void preservesDailyCommitmentAndHandlesClockRewind() {
        assertFalse(DailyRoutine.needsSelection(3, 18000, 3, true, true));
        assertFalse(DailyRoutine.needsSelection(3, 0, 2, true, true));
        assertTrue(DailyRoutine.needsSelection(3, 1, 2, true, true));
        assertTrue(DailyRoutine.needsSelection(2, 18000, 3, true, true));
    }
    @Test void joblessPersonalityDoesNotRerollEveryTick() {
        assertFalse(DailyRoutine.needsSelection(3, 18000, 3, false, false));
        assertTrue(DailyRoutine.needsSelection(4, 18000, 3, false, false));
    }
}

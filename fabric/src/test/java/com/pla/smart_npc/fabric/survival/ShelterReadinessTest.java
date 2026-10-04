package com.pla.smart_npc.fabric.survival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelterReadinessTest {
    @Test
    void boundsRejectInvalidAndOversizedHomes() {
        assertTrue(ShelterReadiness.withinBounds(4, 5, 4));
        assertTrue(ShelterReadiness.withinBounds(16, 16, 8));
        assertFalse(ShelterReadiness.withinBounds(17, 5, 4));
        assertFalse(ShelterReadiness.withinBounds(4, Integer.MAX_VALUE, 4));
        assertFalse(ShelterReadiness.withinBounds(4, 5, 9));
        assertFalse(ShelterReadiness.withinBounds(-1, 5, 4));
        assertFalse(ShelterReadiness.withinBounds(4, 5, 2));
    }

    @Test
    void aHomeRecordOrEquipmentCannotSubstituteForAccessAndCover() {
        assertEquals(ShelterReadiness.Status.INCOMPLETE,
                ShelterReadiness.classify(false, true, true, true, true, true, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.INCOMPLETE,
                ShelterReadiness.classify(true, false, true, true, true, true, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.INCOMPLETE,
                ShelterReadiness.classify(true, true, false, true, true, true, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.INCOMPLETE,
                ShelterReadiness.classify(true, true, true, false, true, true, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.INCOMPLETE,
                ShelterReadiness.classify(true, true, true, true, true, true, true, true, true, 1));
    }

    @Test
    void bedlessOrUnequippedRefugeDoesNotClaimBasicShelterReadiness() {
        assertEquals(ShelterReadiness.Status.TEMPORARY_REFUGE,
                ShelterReadiness.classify(true, true, true, true, false, true, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.TEMPORARY_REFUGE,
                ShelterReadiness.classify(true, true, true, true, true, false, true, true, true, 0));
        assertEquals(ShelterReadiness.Status.TEMPORARY_REFUGE,
                ShelterReadiness.classify(true, true, true, true, true, true, false, true, true, 0));
        assertEquals(ShelterReadiness.Status.TEMPORARY_REFUGE,
                ShelterReadiness.classify(true, true, true, true, true, true, true, false, true, 0));
        assertEquals(ShelterReadiness.Status.TEMPORARY_REFUGE,
                ShelterReadiness.classify(true, true, true, true, true, true, true, true, false, 0));
        assertEquals(ShelterReadiness.Status.BASIC_SHELTER,
                ShelterReadiness.classify(true, true, true, true, true, true, true, true, true, 0));
    }
}

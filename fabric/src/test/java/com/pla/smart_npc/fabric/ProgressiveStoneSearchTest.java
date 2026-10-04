package com.pla.smart_npc.fabric;

import com.pla.smart_npc.entity.ai.ProgressiveStoneSearch;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ProgressiveStoneSearchTest {
    @Test void stationarySixteenProbeSlicesReachFourBlockAwayGroundWithinElevenSlices() {
        var cursor = new ProgressiveStoneSearch(24);
        var target = new ProgressiveStoneSearch.Offset(4, 0, 0);
        boolean found = false;
        for (int slice = 0; slice < 11 && !found; slice++) {
            for (int probe = 0; probe < 16; probe++) found |= cursor.next().equals(target);
        }
        assertTrue(found, "nearby foot-height stone must be reached within 176 probes");
    }

    @Test void entireFourBlockAwayFootLevelPerimeterIsReachedWithin179Probes() {
        var cursor = new ProgressiveStoneSearch(24);
        var perimeter = new HashSet<ProgressiveStoneSearch.Offset>();
        for (int probe = 0; probe < 179; probe++) {
            var offset = cursor.next();
            if (offset.y() == 0 && Math.max(Math.abs(offset.x()), Math.abs(offset.z())) == 4) {
                perimeter.add(offset);
            }
        }
        assertEquals(32, perimeter.size());
    }

    @Test void everyCoordinateIsVisitedExactlyOnceInIncreasingShellOrderBeforeSurveyRepeats() {
        for (int radius : new int[]{0, 1, 3, 12, 24}) assertCompleteSurvey(radius);
    }

    private static void assertCompleteSurvey(int radius) {
        var cursor = new ProgressiveStoneSearch(radius);
        var seen = new HashSet<ProgressiveStoneSearch.Offset>();
        var first = cursor.next();
        seen.add(first);
        int previousShell = 0;
        for (int probe = 1; probe < cursor.size(); probe++) {
            var offset = cursor.next();
            assertTrue(Math.abs(offset.x()) <= radius && Math.abs(offset.z()) <= radius && Math.abs(offset.y()) <= 6);
            int shell = Math.max(Math.max(Math.abs(offset.x()), Math.abs(offset.z())), 2 * Math.abs(offset.y()));
            assertTrue(shell >= previousShell, "the survey must reach nearer shells before farther shells");
            previousShell = shell;
            assertTrue(seen.add(offset), "a survey must not waste its limited slices on duplicates");
        }
        assertEquals((2 * radius + 1) * (2 * radius + 1) * 13, seen.size());
        assertEquals(first, cursor.next(), "exhaustion restarts a fresh survey to notice world changes");
        for (int probe = 1; probe < cursor.size(); probe++) {
            assertTrue(seen.remove(cursor.next()), "the next survey must cover the same coordinates once");
        }
        assertEquals(java.util.Set.of(first), seen);
    }

    @Test void narrowRadiusStillChecksEveryHeightAlternatingDownAndUp() {
        var cursor = new ProgressiveStoneSearch(1);
        int[] expected = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6};
        for (int y : expected) {
            for (int column = 0; column < 9; column++) assertEquals(y, cursor.next().y());
        }
    }

    @Test void radiusIsBoundedEvenForInvalidOrVeryLargeRequests() {
        assertEquals(0, new ProgressiveStoneSearch(-1).radius());
        var maximum = new ProgressiveStoneSearch(Integer.MAX_VALUE);
        assertEquals(24, maximum.radius());
        assertEquals(49 * 49 * 13, maximum.size());
    }
}

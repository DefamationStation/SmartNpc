package com.pla.smart_npc.fabric;

import com.pla.smart_npc.entity.ai.ProgressiveStoneSearch;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class ProgressiveStoneSearchTest {
    @Test void stationarySixteenProbeSlicesEventuallyReachFourBlockAwayStone() {
        var cursor = new ProgressiveStoneSearch(24);
        var target = new ProgressiveStoneSearch.Offset(4, 0, 0);
        boolean found = false;
        for (int slice = 0; slice < 50 && !found; slice++) {
            for (int probe = 0; probe < 16; probe++) found |= cursor.next().equals(target);
        }
        assertTrue(found, "stationary retries must advance past the original first sixteen probes");
    }

    @Test void everyCoordinateIsVisitedExactlyOnceBeforeSurveyRepeats() {
        var cursor = new ProgressiveStoneSearch(3);
        var seen = new HashSet<ProgressiveStoneSearch.Offset>();
        var first = cursor.next();
        seen.add(first);
        for (int probe = 1; probe < cursor.size(); probe++) {
            var offset = cursor.next();
            assertTrue(Math.abs(offset.x()) <= 3 && Math.abs(offset.z()) <= 3 && Math.abs(offset.y()) <= 6);
            assertTrue(seen.add(offset), "a survey must not waste its limited slices on duplicates");
        }
        assertEquals(49 * 13, seen.size());
        assertEquals(first, cursor.next(), "exhaustion restarts a fresh survey to notice world changes");
    }

    @Test void eachRingChecksFootLevelBeforeAlternatingDownAndUp() {
        var cursor = new ProgressiveStoneSearch(1);
        int[] expected = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5, -6, 6};
        for (int y : expected) assertEquals(y, cursor.next().y());
        for (int y : expected) {
            for (int column = 0; column < 8; column++) assertEquals(y, cursor.next().y());
        }
    }

    @Test void radiusIsBoundedEvenForInvalidOrVeryLargeRequests() {
        assertEquals(0, new ProgressiveStoneSearch(-1).radius());
        var maximum = new ProgressiveStoneSearch(Integer.MAX_VALUE);
        assertEquals(24, maximum.radius());
        assertEquals(49 * 49 * 13, maximum.size());
    }
}

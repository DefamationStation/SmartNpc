package com.pla.smart_npc.fabric;
import com.pla.smart_npc.fabric.survival.FuelDecision;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FuelDecisionTest {
    @Test void startsOnlyForAnActualUnmetCookingNeed() {
        assertEquals(FuelDecision.NONE, FuelDecision.choose(false, false, true, true, true));
        assertEquals(FuelDecision.NONE, FuelDecision.choose(true, true, true, true, true));
        assertEquals(FuelDecision.GATHER, FuelDecision.choose(true, false, true, true, true));
    }
    @Test void explainsMissingPrerequisitesInOrder() {
        assertEquals(FuelDecision.NEED_FURNACE, FuelDecision.choose(true, false, false, false, false));
        assertEquals(FuelDecision.NEED_TOOL, FuelDecision.choose(true, false, true, false, false));
        assertEquals(FuelDecision.FIND_COAL, FuelDecision.choose(true, false, true, true, false));
    }
}

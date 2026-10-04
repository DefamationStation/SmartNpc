package com.pla.smart_npc.fabric;

import com.pla.smart_npc.fabric.survival.ResourceMemory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceMemoryTest {
    @Test void observationsArePersonalBoundedAndDimensionScoped() {
        var first = new ResourceMemory(); var second = new ResourceMemory();
        for (int i = 0; i < 100; i++) first.observe("overworld", i, i);
        assertEquals(64, first.coal.size());
        assertEquals(36, first.coal.getFirst().position());
        first.observe("overworld", 99, 200);
        assertEquals(64, first.coal.size());
        assertEquals(200, first.coal.getLast().seen());
        first.observe("nether", 99, 201);
        assertEquals(2, first.coal.stream().filter(o -> o.position() == 99).count());
        assertTrue(second.coal.isEmpty());
    }
    @Test void interruptedTaskAndObservationsSurviveSerialization() {
        var before = new ResourceMemory();
        before.observe("overworld", 123, 456);
        before.request("overworld", 789, 7, 10);
        before.returning = true;
        before.automatic = true; before.purpose = "fuel for cooking food"; before.started = 42; before.nextDecision = 1242;
        var after = ResourceMemory.load(before.save());
        assertEquals(17, after.target);
        assertEquals(789, after.origin);
        assertTrue(after.active);
        assertTrue(after.returning);
        assertTrue(after.automatic); assertEquals(before.purpose, after.purpose);
        assertEquals(42, after.started); assertEquals(1242, after.nextDecision);
        assertEquals(before.coal, after.coal);
        assertThrows(IllegalArgumentException.class, () -> after.request("overworld", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> after.request("overworld", 0, 0, 65));
    }
    @Test void unsupportedSchemaDoesNotExecuteTask() {
        var tag = new CompoundTag(); tag.putInt("version", 99); tag.putBoolean("active", true);
        assertFalse(ResourceMemory.load(tag).active);
    }
    @Test void cookingIntentResumesWithoutInventingACompletion() {
        var before = new ResourceMemory();
        before.cookingActive = true; before.cookingDimension = "overworld";
        before.cookingStarted = 200; before.cookingNextDecision = 240; before.cookingOrigin = 123;
        before.cookingStep = "NEED_STONE"; before.cookingStatus = "collect furnace stone";
        before.cookingBaselineCooked = 3; before.cookingLogGoal = 2;
        var after = ResourceMemory.load(before.save());
        assertTrue(after.cookingActive); assertFalse(after.cookingProduced);
        assertEquals(before.cookingStep, after.cookingStep);
        assertEquals(before.cookingStatus, after.cookingStatus);
        assertEquals(200, after.cookingStarted); assertEquals(240, after.cookingNextDecision);
        assertEquals(123, after.cookingOrigin); assertEquals(3, after.cookingBaselineCooked);
        assertEquals(2, after.cookingLogGoal);
        var legacy = new CompoundTag(); legacy.putInt("version", 1);
        assertFalse(ResourceMemory.load(legacy).cookingActive);
        legacy.putBoolean("cookingActive", true);
        assertFalse(ResourceMemory.load(legacy).cookingActive, "missing dimension cannot activate a migrated intention");
    }
}

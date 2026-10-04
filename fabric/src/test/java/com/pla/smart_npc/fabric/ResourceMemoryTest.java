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
        var after = ResourceMemory.load(before.save());
        assertEquals(17, after.target);
        assertEquals(789, after.origin);
        assertTrue(after.active);
        assertTrue(after.returning);
        assertEquals(before.coal, after.coal);
        assertThrows(IllegalArgumentException.class, () -> after.request("overworld", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> after.request("overworld", 0, 0, 65));
    }
    @Test void unsupportedSchemaDoesNotExecuteTask() {
        var tag = new CompoundTag(); tag.putInt("version", 99); tag.putBoolean("active", true);
        assertFalse(ResourceMemory.load(tag).active);
    }
}

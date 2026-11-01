package com.pla.smart_npc.fabric;
import com.pla.smart_npc.fabric.survival.GrievanceMemory;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrievanceMemoryTest {
    @Test void defenceRequiresEvidenceForThatOffenderAndExpires() {
        var m = new GrievanceMemory(); var thief = UUID.randomUUID(); var visitor = UUID.randomUUID();
        assertTrue(m.cause(thief, 0).isEmpty());
        m.record(thief, GrievanceMemory.Cause.THEFT, 100);
        assertEquals(GrievanceMemory.Cause.THEFT, m.cause(thief, 101).orElseThrow());
        assertTrue(m.cause(visitor, 101).isEmpty());
        assertTrue(m.cause(thief, 100 + GrievanceMemory.DEFENCE_TICKS).isEmpty());
    }
    @Test void realRepeatedOffencesRefreshOneEntryAndSurviveReload() {
        var m = new GrievanceMemory(); var actor = UUID.randomUUID();
        m.record(actor, GrievanceMemory.Cause.THEFT, 0);
        m.record(actor, GrievanceMemory.Cause.ASSAULT, 100);
        var saved = m.save(); assertEquals(1, saved.getIntOr("count", 0));
        var restored = GrievanceMemory.load(saved);
        assertEquals(GrievanceMemory.Cause.ASSAULT, restored.cause(actor, 1201).orElseThrow());
        assertTrue(restored.cause(actor, 1300).isEmpty());
    }
    @Test void historyIsBoundedAndPersonal() {
        var first = new GrievanceMemory(); var second = new GrievanceMemory();
        var oldest = UUID.randomUUID(); first.record(oldest, GrievanceMemory.Cause.VANDALISM, 0);
        for (int i = 0; i < 16; i++) first.record(UUID.randomUUID(), GrievanceMemory.Cause.THEFT, 1);
        assertEquals(16, first.save().getIntOr("count", 0));
        assertTrue(first.cause(oldest, 1).isEmpty()); assertTrue(second.cause(oldest, 1).isEmpty());
    }
    @Test void unsupportedSavedDefenseIsPreservedWhileOnlyNewEventsGrantTransientAuthority() {
        for (int version : new int[]{-1, 99}) {
            var unknownActor = new UUID(1, 1); var actual = new UUID(2, 2);
            var tag = new CompoundTag(); tag.putInt("version", version); tag.putString("opaque", "preserve exactly");
            tag.putInt("count", 1);var entry = new CompoundTag();entry.putString("offender", unknownActor.toString());
            entry.putString("cause", "ASSAULT");entry.putLong("expires", Long.MAX_VALUE);tag.put("entry0", entry);
            var original = tag.copy();var memory = GrievanceMemory.load(tag);
            assertTrue(memory.cause(unknownActor, 100).isEmpty());
            memory.record(actual, GrievanceMemory.Cause.ASSAULT, 100);
            assertEquals(GrievanceMemory.Cause.ASSAULT, memory.cause(actual, 100).orElseThrow());
            assertTrue(memory.cause(actual, 100 + GrievanceMemory.DEFENCE_TICKS).isEmpty());
            assertEquals(original, memory.save());
            var returned = memory.save(); returned.putString("opaque", "changed caller copy");assertEquals(original, memory.save());
            var reloaded = GrievanceMemory.load(memory.save());assertTrue(reloaded.cause(actual, 101).isEmpty());
            assertTrue(reloaded.cause(unknownActor, 101).isEmpty());assertEquals(original, reloaded.save());
            var oldest = new UUID(3, 0);memory.record(oldest, GrievanceMemory.Cause.THEFT, 100);
            for (int i = 0; i < GrievanceMemory.LIMIT; i++) memory.record(new UUID(4, i), GrievanceMemory.Cause.THEFT, 101);
            assertTrue(memory.cause(oldest, 101).isEmpty());assertEquals(original, memory.save());
        }
    }
    @Test void invalidSavedEvidenceGrantsNoCombatPermission() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("count", 1);
        var e = new CompoundTag(); e.putString("offender", "bad uuid"); e.putString("cause", "ASSAULT"); tag.put("entry0", e);
        assertEquals(0, GrievanceMemory.load(tag).save().getIntOr("count", -1));
    }
}

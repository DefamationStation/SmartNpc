package com.pla.smart_npc.fabric.survival.memory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MemoryCodecTest {
    private final UUID id=new UUID(1,2);
    @Test void roundTripRetainsPersonalTendenciesAndSpecificObservedEvidence(){
        var m=new ObservedMemory();var origin=new ObservedMemory.Point(10,64,20);var dest=new ObservedMemory.Point(100,70,200);
        m.observe(new ObservedMemory.Entry(ObservedMemory.Kind.FAILED_ROUTE,"minecraft:overworld",origin,dest,null,"blocked shore",ObservedMemory.Source.TERMINAL_ROUTE_FAILURE,100,80));
        m.observe(new ObservedMemory.Entry(ObservedMemory.Kind.HELP,"minecraft:overworld",origin,null,id,"real food handoff",ObservedMemory.Source.ACTUAL_HANDOFF,101,100));
        var state=new MemoryCodec.Loaded(new NpcPersonality(10,20,30,40,50),m,"world-a",true);
        var loaded=MemoryCodec.load(MemoryCodec.save(state),new UUID(3,4),"world-a");
        assertEquals(state.personality(),loaded.personality());assertEquals(m.entries(),loaded.observations().entries());assertTrue(loaded.writable());
    }
    @Test void oldMissingStateMigratesConservativelyWithoutInventingFacts(){
        var loaded=MemoryCodec.load(new CompoundTag(),id,"world-a");
        assertEquals(NpcPersonality.seeded(id),loaded.personality());assertTrue(loaded.observations().entries().isEmpty());assertTrue(loaded.writable());
    }
    @Test void unknownFutureVersionIsReadOnlyAndCannotBeOverwritten(){
        var tag=new CompoundTag();tag.putInt("version",99);tag.putString("future_evidence","preserve me");
        var loaded=MemoryCodec.load(tag,id,"world-a");assertFalse(loaded.writable());assertThrows(IllegalStateException.class,()->MemoryCodec.save(loaded));
        assertEquals("preserve me",tag.getStringOr("future_evidence",""));assertEquals(99,tag.getIntOr("version",0));
    }
    @Test void anotherWorldRetainsIdentityButCannotReuseSpatialKnowledge(){
        var m=new ObservedMemory();m.observe(new ObservedMemory.Entry(ObservedMemory.Kind.RESOURCE,"minecraft:overworld",new ObservedMemory.Point(0,64,0),null,null,"minecraft:iron_ore",ObservedMemory.Source.DIRECT_SIGHT,100,100));
        var original=new MemoryCodec.Loaded(new NpcPersonality(1,2,3,4,5),m,"world-a",true);
        var migrated=MemoryCodec.load(MemoryCodec.save(original),id,"world-b");assertEquals(original.personality(),migrated.personality());assertTrue(migrated.observations().entries().isEmpty());
    }
    @Test void malformedSavedEntriesAreIgnoredAndCountsAreBounded(){
        var tag=new CompoundTag();tag.putInt("version",1);tag.putString("world","world-a");tag.putInt("count",Integer.MAX_VALUE);
        var entry=new CompoundTag();entry.putString("kind","UNKNOWN_KIND");tag.put("entry0",entry);
        assertTrue(MemoryCodec.load(tag,id,"world-a").observations().entries().isEmpty());
        var valid=new ObservedMemory();valid.observe(new ObservedMemory.Entry(ObservedMemory.Kind.HOME,"minecraft:overworld",new ObservedMemory.Point(1,64,1),null,null,"owned home",ObservedMemory.Source.OWN_HOME,100,100));
        var saved=MemoryCodec.save(new MemoryCodec.Loaded(NpcPersonality.seeded(id),valid,"world-a",true));
        saved.getCompoundOrEmpty("entry0").getCompoundOrEmpty("position").putInt("x",Integer.MAX_VALUE);
        assertTrue(MemoryCodec.load(saved,id,"world-a").observations().entries().isEmpty());
    }
}

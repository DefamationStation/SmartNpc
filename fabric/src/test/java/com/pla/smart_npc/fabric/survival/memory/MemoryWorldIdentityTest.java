package com.pla.smart_npc.fabric.survival.memory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MemoryWorldIdentityTest {
    @Test void independentWorldsDoNotDeriveIdentityFromMatchingSeedOrName(){
        var first=MemoryWorldIdentity.fresh();var second=MemoryWorldIdentity.fresh();
        assertNotEquals(first.identity(),second.identity());
        assertEquals(first.identity(),MemoryWorldIdentity.load(first.save()).identity());
    }
    @Test void invalidLegacyIdentityDoesNotBecomeAReusableConstant(){
        var bad=new CompoundTag();bad.putInt("version",1);bad.putString("identity","invalid");
        assertNotEquals(MemoryWorldIdentity.load(bad).identity(),MemoryWorldIdentity.load(bad).identity());
    }
    @Test void unknownFutureWorldDataIsNotDiscardedOrRewritten(){
        var data=MemoryWorldIdentity.fresh().save();data.putInt("version",99);data.putString("future","preserve");
        var loaded=MemoryWorldIdentity.load(data);
        assertEquals(data.getStringOr("identity",""),loaded.identity().toString());assertEquals(data,loaded.save());assertFalse(loaded.supported());
    }
    @Test void unsupportedWorldIdentityCannotDiscardOrRewriteCurrentNpcEvidence(){
        var observations=new ObservedMemory();var point=new ObservedMemory.Point(10,64,10);
        observations.observe(new ObservedMemory.Entry(ObservedMemory.Kind.RESOURCE,"minecraft:overworld",point,null,null,
                "minecraft:coal_ore",ObservedMemory.Source.DIRECT_SIGHT,100,100));
        var profile=new NpcPersonality(1,2,3,4,5);var id=java.util.UUID.randomUUID();
        var original=MemoryCodec.save(new MemoryCodec.Loaded(profile,observations,"original-world-id",true));var before=original.copy();
        var future=new CompoundTag();future.putInt("version",99);future.putString("future_identity","unreadable by this version");
        for(int load=0;load<2;load++){
            var world=MemoryWorldIdentity.load(future);assertFalse(world.supported());
            var state=MemoryCodec.load(original,id,world.identity().toString(),world.supported());
            assertEquals(profile,state.personality());assertFalse(state.writable());assertTrue(state.observations().entries().isEmpty());
            assertThrows(IllegalStateException.class,()->MemoryCodec.save(state));assertEquals(before,original);assertEquals(future,world.save());
        }
    }
}

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
        assertEquals(data.getStringOr("identity",""),loaded.identity().toString());assertEquals(data,loaded.save());
    }
}

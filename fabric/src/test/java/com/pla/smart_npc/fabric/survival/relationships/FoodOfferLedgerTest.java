package com.pla.smart_npc.fabric.survival.relationships;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class FoodOfferLedgerTest {
    final UUID giver=new UUID(1,2),other=new UUID(3,4);
    @Test void onlyAcceptedTransfersStartPerActorAndWholeNpcCooldowns(){
        var ledger=new FoodOfferLedger();assertTrue(ledger.ready(giver,100,200,20));assertTrue(ledger.ready(giver,100,200,20));assertEquals(0,ledger.size());
        ledger.accepted(giver,100);assertFalse(ledger.ready(giver,101,200,20));assertFalse(ledger.ready(other,119,200,20));
        assertTrue(ledger.ready(other,120,200,20));assertFalse(ledger.ready(giver,299,200,20));assertTrue(ledger.ready(giver,300,200,20));
    }
    @Test void acceptedActorCooldownAndIdentitySurviveRoundTrip(){
        var ledger=new FoodOfferLedger();ledger.accepted(giver,100);var loaded=FoodOfferLedger.load(ledger.save());
        assertEquals(1,loaded.size());assertFalse(loaded.ready(giver,250,200,20));assertTrue(loaded.ready(other,250,200,20));assertTrue(loaded.ready(giver,300,200,20));
    }
    @Test void manyVisitorsCannotGrowPersonalLedgerWithoutBound(){
        var ledger=new FoodOfferLedger();for(int i=0;i<1000;i++)ledger.accepted(new UUID(9,i),i*100L);
        assertEquals(FoodOfferLedger.LIMIT,ledger.size());assertEquals(FoodOfferLedger.LIMIT,FoodOfferLedger.load(ledger.save()).size());
    }
    @Test void unknownFutureSchemaCannotBeOverwritten(){
        var future=new CompoundTag();future.putInt("version",FoodOfferLedger.VERSION+1);future.putString("untouched","future-data");var copy=future.copy();
        var loaded=FoodOfferLedger.load(future);assertFalse(loaded.writable());assertFalse(loaded.ready(giver,100,200,20));
        assertThrows(IllegalStateException.class,loaded::save);assertEquals(copy,future);
    }
    @Test void missingAndMalformedOldEntriesDoNotInventPastGifts(){
        assertEquals(0,FoodOfferLedger.load(new CompoundTag()).size());var tag=new CompoundTag();tag.putInt("version",1);tag.putInt("count",Integer.MAX_VALUE);
        var bad=new CompoundTag();bad.putString("uuid","untrusted");bad.putLong("accepted",100);tag.put("actor0",bad);
        assertEquals(0,FoodOfferLedger.load(tag).size());assertTrue(FoodOfferLedger.load(tag).ready(giver,100,200,20));
    }
    @Test void rollbackAllowsOneNewAcceptedOfferThenEnforcesCooldownAgain(){
        var ledger=new FoodOfferLedger();ledger.accepted(giver,10000);assertTrue(ledger.ready(giver,100,200,20));ledger.accepted(giver,100);
        assertFalse(ledger.ready(giver,101,200,20));assertFalse(ledger.ready(other,101,200,20));assertFalse(ledger.ready(other,-1,200,20));
    }
}
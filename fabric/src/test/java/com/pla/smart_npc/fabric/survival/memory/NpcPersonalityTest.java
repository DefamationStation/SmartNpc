package com.pla.smart_npc.fabric.survival.memory;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class NpcPersonalityTest {
    @Test void identityIsStableAndProducesIndividualProfiles(){
        var seen=new HashSet<NpcPersonality>();
        for(int i=0;i<128;i++){
            var id=new UUID(21,i);var p=NpcPersonality.seeded(id);
            assertEquals(p,NpcPersonality.seeded(UUID.fromString(id.toString())));seen.add(p);
            assertTrue(p.caution()>=25&&p.caution()<=75);
        }
        assertTrue(seen.size()>120);
    }
    @Test void currentInterestsBiasSeededTendenciesAndExplicitOverridesWin(){
        var p=new NpcPersonality(40,40,40,40,40);
        var interested=p.effective(new NpcPersonality.Interests(true,true,true,true,true),NpcPersonality.Overrides.SEEDED);
        assertEquals(new NpcPersonality(55,75,50,55,55),interested);
        assertEquals(new NpcPersonality(5,6,7,8,9),p.effective(new NpcPersonality.Interests(true,true,true,true,true),new NpcPersonality.Overrides(5,6,7,8,9)));
        assertEquals(p,p.effective(new NpcPersonality.Interests(false,false,false,false,false),NpcPersonality.Overrides.SEEDED));
    }
    @Test void preparationIsSmallConfigurableAndNeverWeakensExistingSafetyFloor(){
        assertEquals(4,new NpcPersonality(50,0,50,50,50).departureFoodReserve(2));
        assertEquals(5,new NpcPersonality(50,60,50,50,50).departureFoodReserve(2));
        assertEquals(6,new NpcPersonality(50,90,50,50,50).departureFoodReserve(4));
        assertEquals(5,new NpcPersonality(50,90,50,50,50).departureFoodReserve(1));
        assertEquals(4,new NpcPersonality(50,90,50,50,50).departureFoodReserve(0));
        assertEquals(new NpcPersonality(0,100,0,100,100),new NpcPersonality(-1,101,-5,500,120));
    }
}

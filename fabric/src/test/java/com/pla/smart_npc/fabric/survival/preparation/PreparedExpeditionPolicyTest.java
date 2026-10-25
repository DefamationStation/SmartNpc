package com.pla.smart_npc.fabric.survival.preparation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.pla.smart_npc.fabric.survival.readiness.ExpeditionReadiness.*;
class PreparedExpeditionPolicyTest {
    private final Assessment ready=new Assessment(List.of());
    @Test void verifiedPastWorkAndPresentReadinessAreBothRequired(){
        assertTrue(PreparedExpeditionPolicy.assess(new PreparedExpeditionPolicy.Evidence(true,true,true,true,ready)).accepted());
        var noHistory=PreparedExpeditionPolicy.assess(new PreparedExpeditionPolicy.Evidence(true,true,false,false,ready));
        assertFalse(noHistory.accepted());assertEquals(List.of(PreparedExpeditionPolicy.Reason.EARN_COOKED_FOOD,PreparedExpeditionPolicy.Reason.EARN_SAFE_REST),noHistory.reasons());
    }
    @Test void earnedHistoryDoesNotReplaceCurrentFoodOrTools(){
        var needs=List.of(new Missing(Code.FOOD_RESERVE,null,null,0,6),new Missing(Code.TOOL_MISSING,Tool.PICKAXE,Tier.STONE,0,1));
        var result=PreparedExpeditionPolicy.assess(new PreparedExpeditionPolicy.Evidence(true,true,true,true,new Assessment(needs)));
        assertFalse(result.accepted());assertEquals(needs,result.materials());
    }
    @Test void unsafeOrOccupiedResidentsCannotBeDoubleCommitted(){
        var result=PreparedExpeditionPolicy.assess(new PreparedExpeditionPolicy.Evidence(false,false,true,true,ready));
        assertEquals(List.of(PreparedExpeditionPolicy.Reason.UNSAFE_OR_UNAVAILABLE,PreparedExpeditionPolicy.Reason.EXISTING_COMMITMENT),result.reasons());
    }
    @Test void adviceIsImmutableAndRepeatedInspectionsHaveNoUnlockState(){
        var facts=new PreparedExpeditionPolicy.Evidence(true,true,false,false,ready);
        assertEquals(PreparedExpeditionPolicy.assess(facts),PreparedExpeditionPolicy.assess(facts));
        assertThrows(UnsupportedOperationException.class,()->PreparedExpeditionPolicy.assess(facts).reasons().clear());
    }
}

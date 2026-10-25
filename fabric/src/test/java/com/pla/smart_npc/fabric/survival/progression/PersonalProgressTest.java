package com.pla.smart_npc.fabric.survival.progression;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.pla.smart_npc.fabric.survival.progression.PersonalProgress.*;

class PersonalProgressTest {
    @Test void repeatedWorkAtOneProofCannotFarmCreditAcrossReload(){
        var p=new PersonalProgress();assertTrue(p.record(Skill.MINING,"overworld@10,60,10:stone",1,50));
        p=PersonalProgress.restore(p.snapshot());
        assertFalse(p.record(Skill.MINING,"overworld@10,60,10:stone",64,100));assertEquals(1,p.total(Skill.MINING));
    }
    @Test void uniqueWorkIsCappedAndClockReversalDoesNotRefillAllowance(){
        var p=new PersonalProgress();for(int i=0;i<100;i++)p.record(Skill.MINING,"site"+i,1,WINDOW_TICKS+1);
        assertEquals(WINDOW_ALLOWANCE,p.total(Skill.MINING));
        assertFalse(p.record(Skill.MINING,"other",1,0));assertEquals(1,p.workWindows());
        assertTrue(p.record(Skill.MINING,"new-day",1,WINDOW_TICKS*2));assertEquals(2,p.workWindows());
    }
    @Test void exhaustingProofCapacityCannotEvictEarlierDedupEvidence(){
        var p=new PersonalProgress();int i=0;
        for(var s:Skill.values())for(int j=0;j<32;j++)p.record(s,"site"+(i++),1,0);
        assertEquals(PROOF_LIMIT,p.snapshot().proofs().size());assertFalse(p.record(Skill.FORAGING,"overflow",1,0));
        assertFalse(p.record(Skill.FORAGING,"site0",1,0));assertEquals(32,p.total(Skill.FORAGING));
    }
    @Test void readinessLabelsNeedEarnedFoodRestAndHomeEvidence(){
        var p=new PersonalProgress();p.record(Skill.MINING,"mine",8,0);assertEquals(Stage.STARTER,p.stage());
        p.mark(Milestone.FOOD_PREPARED);p.mark(Milestone.SAFE_REST);assertEquals(Stage.SURVIVOR,p.stage());
        p.mark(Milestone.HOME_COMPLETE);assertEquals(Stage.RESIDENT,p.stage());
        p.record(Skill.EXPLORATION,"trip",1,WINDOW_TICKS);assertEquals(Stage.EXPEDITION_READY,p.stage());
    }
    @Test void veteranNeedsMultipleVerifiedReturnsAcrossWorkWindows(){
        var p=new PersonalProgress();p.mark(Milestone.FOOD_PREPARED);p.mark(Milestone.SAFE_REST);p.mark(Milestone.HOME_COMPLETE);
        p.record(Skill.FORAGING,"logs",8,0);p.record(Skill.EXPLORATION,"trip1",1,0);
        assertNotEquals(Stage.VETERAN,p.stage());
        p.record(Skill.EXPLORATION,"trip2",1,WINDOW_TICKS);p.record(Skill.EXPLORATION,"trip3",1,WINDOW_TICKS*2);
        assertEquals(Stage.VETERAN,p.stage());assertEquals(Stage.VETERAN,PersonalProgress.restore(p.snapshot()).stage());
    }
    @Test void knownCraftHistoryIsBoundedAndSurvivesRestoration(){
        var p=new PersonalProgress();for(int i=0;i<100;i++)p.rememberCraft("mod:recipe"+i);
        assertEquals(CRAFT_LIMIT,p.snapshot().knownCrafts().size());assertTrue(PersonalProgress.restore(p.snapshot()).knowsCraft("mod:recipe0"));
    }
    @Test void corruptedCountsAreClampedWithoutGrantingMilestones(){
        var p=PersonalProgress.restore(new Snapshot(0,Integer.MAX_VALUE,0,Map.of(Skill.MINING,Integer.MAX_VALUE),
                Map.of(Skill.MINING,-9),List.of(),Set.of(),List.of()));
        assertEquals(TOTAL_LIMIT,p.total(Skill.MINING));assertEquals(Stage.STARTER,p.stage());
    }
    @Test void invalidAndUnverifiedEmptyOutcomesAreRejected(){
        var p=new PersonalProgress();assertFalse(p.record(Skill.MINING,"",1,0));assertFalse(p.record(Skill.MINING,"x",0,0));
        assertFalse(p.record(Skill.MINING,"x",1,-1));assertEquals(0,p.workWindows());
    }
}

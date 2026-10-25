package com.pla.smart_npc.fabric.survival.progression;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressCodecTest {
    @Test void realNbtRoundTripPreservesDedupAndMilestones(){
        var p=new PersonalProgress();p.record(PersonalProgress.Skill.BUILDING,"home@1,64,1",1,50);
        p.mark(PersonalProgress.Milestone.HOME_COMPLETE);p.rememberCraft("minecraft:stone_pickaxe");
        var loaded=ProgressCodec.load(ProgressCodec.save(p));assertEquals(p.snapshot(),loaded.snapshot());
        assertFalse(loaded.record(PersonalProgress.Skill.BUILDING,"home@1,64,1",1,70));
    }
    @Test void missingLegacyDataGivesNoFabricatedExperience(){
        var loaded=ProgressCodec.load(new CompoundTag());assertEquals(PersonalProgress.Stage.STARTER,loaded.stage());
        assertTrue(loaded.snapshot().milestones().isEmpty());
    }
    @Test void futureVersionCannotBeTreatedAsWritableCurrentData(){
        var tag=new CompoundTag();tag.putInt("version",99);tag.putString("futureEvidence","keep");
        assertFalse(ProgressCodec.supported(tag));assertEquals("keep",tag.getStringOr("futureEvidence",""));
    }
    @Test void corruptCollectionSizesStayBounded(){
        var tag=new CompoundTag();tag.putInt("version",1);tag.putInt("proofCount",Integer.MAX_VALUE);
        tag.putInt("craftCount",Integer.MAX_VALUE);tag.putInt("workWindows",-1);
        var loaded=ProgressCodec.load(tag);assertTrue(loaded.snapshot().proofs().size()<=PersonalProgress.PROOF_LIMIT);
        assertTrue(loaded.snapshot().knownCrafts().size()<=PersonalProgress.CRAFT_LIMIT);assertEquals(0,loaded.workWindows());
    }
}

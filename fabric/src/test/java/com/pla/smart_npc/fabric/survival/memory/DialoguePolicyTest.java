package com.pla.smart_npc.fabric.survival.memory;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class DialoguePolicyTest {
    final UUID listener=new UUID(1,2),other=new UUID(3,4);final String dim="minecraft:overworld";
    final NpcPersonality neutral=new NpcPersonality(50,50,50,50,50);
    ObservedMemory.Entry fact(ObservedMemory.Kind kind,ObservedMemory.Source source,UUID actor,long now){
        return new ObservedMemory.Entry(kind,dim,new ObservedMemory.Point(0,64,0),kind==ObservedMemory.Kind.FAILED_ROUTE?new ObservedMemory.Point(20,64,20):null,actor,"observed",source,now,100);
    }
    @Test void noEvidenceDoesNotInventAnAdventure(){assertEquals(DialogueSelection.Type.UNKNOWN,DialogueSelection.choose(listener,dim,10,neutral,false,List.of()).type());}
    @Test void helpOrOffenseIsNeverAttributedToTheNearestWrongPlayer(){
        var help=fact(ObservedMemory.Kind.HELP,ObservedMemory.Source.ACTUAL_HANDOFF,other,1);
        var hostile=fact(ObservedMemory.Kind.HOSTILE,ObservedMemory.Source.CANONICAL_DEFENCE,other,1);
        assertEquals(DialogueSelection.Type.UNKNOWN,DialogueSelection.choose(listener,dim,10,neutral,false,List.of(help,hostile)).type());
        assertEquals(DialogueSelection.Type.HOSTILE_HISTORY,DialogueSelection.choose(other,dim,10,neutral,false,List.of(help,hostile)).type());
        assertEquals(DialogueSelection.Type.THANKS,DialogueSelection.choose(other,dim,10,neutral,false,List.of(help)).type());
    }
    @Test void expiredFutureAndOtherDimensionFactsDoNotProduceClaims(){
        var failed=fact(ObservedMemory.Kind.FAILED_ROUTE,ObservedMemory.Source.TERMINAL_ROUTE_FAILURE,null,100);
        assertEquals(DialogueSelection.Type.UNKNOWN,DialogueSelection.choose(listener,dim,99,neutral,false,List.of(failed)).type());
        assertEquals(DialogueSelection.Type.UNKNOWN,DialogueSelection.choose(listener,dim,1300,neutral,false,List.of(failed)).type());
        assertEquals(DialogueSelection.Type.UNKNOWN,DialogueSelection.choose(listener,"minecraft:the_nether",101,neutral,false,List.of(failed)).type());
    }
    @Test void tendencyChangesTruthfulChoiceWithoutFabricatingKnowledge(){
        var resource=fact(ObservedMemory.Kind.RESOURCE,ObservedMemory.Source.DIRECT_SIGHT,null,1);
        var home=fact(ObservedMemory.Kind.HOME,ObservedMemory.Source.OWN_HOME,null,1);
        assertEquals(DialogueSelection.Type.RESOURCE,DialogueSelection.choose(listener,dim,2,new NpcPersonality(50,50,100,100,50),false,List.of(home,resource)).type());
        assertEquals(DialogueSelection.Type.HOME,DialogueSelection.choose(listener,dim,2,new NpcPersonality(50,50,0,0,50),false,List.of(home,resource)).type());
        assertEquals(DialogueSelection.Type.BUSY,DialogueSelection.choose(listener,dim,2,new NpcPersonality(50,50,50,50,80),true,List.of(home,resource)).type());
        assertNotEquals(DialogueSelection.Type.BUSY,DialogueSelection.choose(listener,dim,2,new NpcPersonality(50,50,50,50,80),false,List.of(home,resource)).type());
    }
    @Test void listenerAndWholeNpcCooldownsBoundRepeatedQuestions(){
        var ledger=new DialogueLedger();assertTrue(ledger.allow(listener,100,400));
        assertFalse(ledger.allow(other,200,400));assertTrue(ledger.allow(other,500,400));
        assertFalse(ledger.allow(listener,900,400));assertTrue(ledger.allow(listener,1300,400));
        for(int i=0;i<1000;i++)ledger.allow(new UUID(9,i),2000L+i*400L,400);
        assertTrue(ledger.size()<=DialogueLedger.LIMIT);
    }
    @Test void clockRollbackDoesNotTrapOrUnlockAnUnboundedReplyLoop(){
        var ledger=new DialogueLedger();assertTrue(ledger.allow(listener,10000,400));
        assertTrue(ledger.allow(listener,100,400));assertFalse(ledger.allow(listener,101,400));
        assertFalse(ledger.allow(other,-1,400));
    }
}

package com.pla.smart_npc.fabric.survival.memory;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ObservedMemoryTest {
    private final String dim="minecraft:overworld";
    private ObservedMemory.Point p(int x){return new ObservedMemory.Point(x,64,0);}
    private ObservedMemory.Entry resource(int x,long now){return new ObservedMemory.Entry(ObservedMemory.Kind.RESOURCE,dim,p(x),null,null,"minecraft:stone",ObservedMemory.Source.DIRECT_SIGHT,now,100);}
    private ObservedMemory.Entry failure(int x,long now){return new ObservedMemory.Entry(ObservedMemory.Kind.FAILED_ROUTE,dim,p(x),p(100),null,"shore blocked",ObservedMemory.Source.TERMINAL_ROUTE_FAILURE,now,100);}
    @Test void repeatedFactRefreshesEvidenceButOlderReportsCannotRejuvenateIt(){
        var m=new ObservedMemory();assertTrue(m.observe(resource(1,100)));assertFalse(m.observe(resource(1,100)));
        assertFalse(m.observe(resource(1,50)));assertEquals(100,m.entries().getFirst().seen());
        assertTrue(m.observe(resource(1,200)));assertEquals(1,m.size());assertEquals(200,m.entries().getFirst().seen());
    }
    @Test void resourceChurnIsBoundedAndPreservesDistinctHomeKnowledge(){
        var m=new ObservedMemory();m.observe(new ObservedMemory.Entry(ObservedMemory.Kind.HOME,dim,p(0),null,null,"owned home",ObservedMemory.Source.OWN_HOME,0,100));
        for(int i=1;i<1000;i++)m.observe(resource(i,i));
        assertEquals(33,m.size());assertEquals(1,m.current(ObservedMemory.Kind.HOME,dim,1000,1).size());
        assertFalse(m.entries().contains(resource(1,1)));assertTrue(m.entries().contains(resource(999,999)));
    }
    @Test void globalCapacityAndPerKindLimitsHoldAcrossMixedEvidence(){
        var m=new ObservedMemory();
        for(int i=0;i<200;i++)for(var kind:ObservedMemory.Kind.values()){
            var dest=kind==ObservedMemory.Kind.FAILED_ROUTE||kind==ObservedMemory.Kind.SUCCESSFUL_ROUTE?p(i+1):null;
            var subject=kind==ObservedMemory.Kind.HELP||kind==ObservedMemory.Kind.HOSTILE?new UUID(1,i):null;
            m.observe(new ObservedMemory.Entry(kind,dim,p(i),dest,subject,"observed",ObservedMemory.Source.SELF_ACTION,i,100));
        }
        assertTrue(m.size()<=ObservedMemory.LIMIT);
        for(var kind:ObservedMemory.Kind.values())assertTrue(m.entries().stream().filter(e->e.kind()==kind).count()<=kind.limit);
        assertEquals(1,m.entries().stream().filter(e->e.kind()==ObservedMemory.Kind.HOME).count());
    }
    @Test void failedRouteNeedsMatchingDestinationDimensionAndNearbyOriginThenExpires(){
        var m=new ObservedMemory();m.observe(failure(0,100));
        assertTrue(m.failedRecently(dim,p(4),p(100),101));
        assertFalse(m.failedRecently(dim,p(5),p(100),101));
        assertFalse(m.failedRecently(dim,p(0),p(101),101));
        assertFalse(m.failedRecently("minecraft:the_nether",p(0),p(100),101));
        assertFalse(m.failedRecently(dim,p(0),p(100),1300));
        assertTrue(m.clearRouteFailure(dim,p(100)));assertFalse(m.failedRecently(dim,p(0),p(100),110));
    }
    @Test void staleConfidenceFallsAndFutureOrExpiredEvidenceCannotBeUsed(){
        var m=new ObservedMemory();m.observe(resource(0,100));
        assertEquals(100,m.entries().getFirst().confidenceAt(100));
        assertEquals(50,m.entries().getFirst().confidenceAt(12100));
        assertTrue(m.current(ObservedMemory.Kind.RESOURCE,dim,99,1).isEmpty());
        assertTrue(m.current(ObservedMemory.Kind.RESOURCE,dim,24100,1).isEmpty());
        assertTrue(m.current(ObservedMemory.Kind.RESOURCE,dim,24100,0).isEmpty());
        assertTrue(m.expire(99));assertEquals(0,m.size());
    }
    @Test void evidenceMustHaveAnActualActorOrDestinationWhereRequired(){
        assertThrows(IllegalArgumentException.class,()->new ObservedMemory.Entry(ObservedMemory.Kind.HELP,dim,p(0),null,null,"help",ObservedMemory.Source.ACTUAL_HANDOFF,0,100));
        assertThrows(IllegalArgumentException.class,()->new ObservedMemory.Entry(ObservedMemory.Kind.FAILED_ROUTE,dim,p(0),null,null,"route",ObservedMemory.Source.TERMINAL_ROUTE_FAILURE,0,100));
        assertThrows(IllegalArgumentException.class,()->resource(1,-1));
        assertThrows(IllegalArgumentException.class,()->new ObservedMemory.Point(Integer.MAX_VALUE,64,0));
    }
}

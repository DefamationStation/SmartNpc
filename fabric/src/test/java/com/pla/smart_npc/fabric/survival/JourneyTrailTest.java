package com.pla.smart_npc.fabric.survival;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class JourneyTrailTest {
    private JourneyTrail.Point p(int x){return new JourneyTrail.Point(x,64,0);}
    @Test void retainsAnchoredHistoryAndClosesRealLoops(){var t=new JourneyTrail();t.observe(p(0));t.observe(p(4));t.observe(p(8));t.observe(p(0));assertEquals(java.util.List.of(p(0)),t.points());}
    @Test void teleportDoesNotCreateFictionalReturnEdge(){var t=new JourneyTrail();t.observe(p(0));t.observe(p(100));assertEquals(java.util.List.of(p(100)),t.points());}
    @Test void capacityPreservesBeginningInsteadOfEvictingHome(){var t=new JourneyTrail();for(int x=0;x<JourneyTrail.LIMIT+50;x++)t.observe(p(x*4));assertTrue(t.full());assertEquals(p(0),t.points().getFirst());assertEquals(JourneyTrail.LIMIT,t.points().size());}
    @Test void nearbyMovementIsNotRepeatedlyStored(){var t=new JourneyTrail();t.observe(p(0));assertFalse(t.observe(p(1)));assertTrue(t.observe(p(4)));}
}

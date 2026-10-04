package com.pla.smart_npc.fabric;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventsTest {
    static final class Damage extends Events.Cancelable { final List<String> calls=new ArrayList<>(); }
    static final class Handlers {
        @Events.SubscribeEvent(priority=Events.EventPriority.HIGH)
        void protect(Damage event) { event.calls.add("protect"); event.setCanceled(true); }
        @Events.SubscribeEvent
        void skipped(Damage event) { event.calls.add("should not run"); }
        @Events.SubscribeEvent(priority=Events.EventPriority.LOW,receiveCanceled=true)
        void observe(Damage event) { event.calls.add("observe"); }
    }
    @Test void honorsPriorityAndCancellation() {
        Events.register(new Handlers());
        var event=Events.post(new Damage());
        assertTrue(event.isCanceled());
        assertEquals(List.of("protect","observe"),event.calls);
    }

    static final class BrokenEvent {}
    static final class BrokenHandler {
        @Events.SubscribeEvent void run(BrokenEvent event) { throw new IllegalArgumentException("original cause"); }
    }
    @Test void preservesHandlerFailureCause() {
        Events.register(new BrokenHandler());
        var error=assertThrows(IllegalStateException.class,()->Events.post(new BrokenEvent()));
        assertInstanceOf(IllegalArgumentException.class,error.getCause());
        assertEquals("original cause",error.getCause().getMessage());
    }
}

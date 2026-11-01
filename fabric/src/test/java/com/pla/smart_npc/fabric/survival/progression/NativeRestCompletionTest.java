package com.pla.smart_npc.fabric.survival.progression;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NativeRestCompletionTest {
    @Test void ordinaryTimerCompletionBeforeDawnEarnsRest(){
        assertTrue(NativeRestCompletion.earned(true,true,400));
    }
    @Test void interruptionsAndUnsafeOrTooBriefSleepNeverEarnRest(){
        assertFalse(NativeRestCompletion.earned(false,true,1200));
        assertFalse(NativeRestCompletion.earned(true,false,1200));
        assertFalse(NativeRestCompletion.earned(true,true,99));
        assertFalse(NativeRestCompletion.earned(false,true,0));
    }
}

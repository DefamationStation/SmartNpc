package com.pla.smart_npc.fabric.survival.progression;

/** Completion policy counts actual native sleeping ticks, including saved observations. */
public final class NativeRestCompletion {
    public static final int MIN_OBSERVED_TICKS=100;
    private NativeRestCompletion() { }
    public static boolean earned(boolean nativeCompletion,boolean safeNight,int observedTicks){
        return nativeCompletion&&safeNight&&observedTicks>=MIN_OBSERVED_TICKS;
    }
}

package com.pla.smart_npc.fabric.survival;

/** Repair missed daily selection without changing a valid job repeatedly during a day. */
public final class DailyRoutine {
    private DailyRoutine() {}

    public static boolean needsSelection(long day, long timeOfDay, long selectedDay,
                                         boolean validJob, boolean availableJobs) {
        if (selectedDay == day && (validJob || !availableJobs)) return false;
        if (!validJob || selectedDay > day) return true;
        // Keep yesterday's assignment through tick zero, then select even if the morning
        // window was skipped by sleep, an unload, a time command or a late spawn.
        return selectedDay < day && timeOfDay >= 1;
    }
}

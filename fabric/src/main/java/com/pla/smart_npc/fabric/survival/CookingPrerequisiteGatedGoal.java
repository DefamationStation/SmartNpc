package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.function.BooleanSupplier;

/** Adds exact cooking prerequisite admission to an existing native profession gate. */
public final class CookingPrerequisiteGatedGoal extends InterestGatedGoal {
    private final BooleanSupplier prerequisite;
    private boolean admittedForCooking;
    private final PlayerNpcEntity npc;

    public CookingPrerequisiteGatedGoal(PlayerNpcEntity npc, Goal delegate,
            BooleanSupplier prerequisite, PlayerNpcInterest... interests) {
        super(npc, delegate, interests);
        this.prerequisite = prerequisite;
        this.npc = npc;
    }

    @Override
    public void start() {
        this.admittedForCooking = this.prerequisite.getAsBoolean();
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.admittedForCooking) return super.canContinueToUse();
        // Native predicates stop extraction/search when the prerequisite ends while allowing
        // their bounded water/drop/access recovery to finish before yielding movement.
        long timing = com.pla.smart_npc.util.PlayerNpcPerformanceMonitor.beginAuxiliaryTiming();
        try {
            return this.getDelegateGoal().canContinueToUse();
        } finally {
            com.pla.smart_npc.util.PlayerNpcPerformanceMonitor.recordGoalWork(
                    this.npc, this.getDelegateGoal().getClass().getSimpleName() + ".canContinue", timing);
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.admittedForCooking = false;
    }

    @Override
    public boolean isInterestGateActive() {
        return this.prerequisite.getAsBoolean() || super.isInterestGateActive();
    }
}

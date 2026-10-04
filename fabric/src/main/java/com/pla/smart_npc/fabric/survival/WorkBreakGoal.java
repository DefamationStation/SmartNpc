package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** A brief attentive pause while routine work is queued, without a decorative random path. */
public final class WorkBreakGoal extends Goal {
    private final PlayerNpcEntity npc;
    private int remaining, nextBreak;
    public WorkBreakGoal(PlayerNpcEntity npc) { this.npc = npc; setFlags(EnumSet.of(Flag.LOOK)); }
    private boolean waiting() {
        return npc.isAlive() && !npc.isNoAi() && !npc.isHealing() && !npc.isSleeping()
            && !npc.isPassenger() && npc.getTarget() == null && PlayerNpcAiWorkBudget.isWaitingForTurn(npc);
    }
    @Override public boolean canUse() { return npc.tickCount >= nextBreak && waiting(); }
    @Override public boolean canContinueToUse() { return remaining > 0 && waiting(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { remaining = 60; npc.setIdleTraceDetail("taking a short break before continuing work", 60); }
    @Override public void tick() {
        if (--remaining % 20 != 0) return;
        var nearby = npc.level().getNearestPlayer(npc, 6);
        if (nearby != null && npc.hasLineOfSight(nearby)) npc.getLookControl().setLookAt(nearby, 30, 30);
    }
    @Override public void stop() { nextBreak = npc.tickCount + 100; }
}

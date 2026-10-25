package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Keep optional gear upgrades behind a bounded, actionable survival need. */
public final class RoutineWorkGoal extends Goal {
    private final PlayerNpcEntity npc;
    private final Goal delegate;
    public RoutineWorkGoal(PlayerNpcEntity npc, Goal delegate) {
        this.npc = npc; this.delegate = delegate; setFlags(delegate.getFlags());
    }
    @Override public boolean canUse() { return !npc.shouldYieldRoutineForSurvival() && !shelterHasFirstClaim() && delegate.canUse(); }
    private boolean shelterHasFirstClaim(){
        if(!(delegate instanceof com.pla.smart_npc.entity.goal.CraftBasicGearGoal)
                ||!npc.hasInterest(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING)
                ||!(npc.level() instanceof net.minecraft.server.level.ServerLevel level)
                ||!npc.hasCarriedTool(net.minecraft.tags.ItemTags.PICKAXES)
                ||!npc.hasCarriedTool(net.minecraft.tags.ItemTags.AXES))return false;
        return !com.pla.smart_npc.entity.goal.BuildHouseGoal.isHomeLayoutFinished(npc,level);
    }
    @Override public boolean canContinueToUse() { return !npc.shouldYieldRoutineForSurvival() && delegate.canContinueToUse(); }
    @Override public void start() { delegate.start(); }
    @Override public void tick() { delegate.tick(); }
    @Override public void stop() { delegate.stop(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
}

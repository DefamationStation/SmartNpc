package com.pla.smart_npc.fabric.survival;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.WaterEscapeAi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** Borrow movement for actual low-air danger; normal work keeps its destination-aware recovery. */
public final class SurvivalWaterGoal extends Goal {
    private final PlayerNpcEntity npc;
    private final WaterEscapeAi escape;
    private int ticks,retry;
    private boolean done;
    public SurvivalWaterGoal(PlayerNpcEntity n){npc=n;escape=new WaterEscapeAi(n);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){return npc.tickCount>=retry&&npc.isAlive()&&!npc.isNoAi()&&!npc.isPassenger()&&(npc.isInWater()||npc.isEyeInFluid(net.minecraft.tags.FluidTags.WATER))&&npc.getAirSupply()<200;}
    @Override public boolean canContinueToUse(){return !done&&ticks<120&&npc.isAlive()&&(npc.isInWater()||npc.isEyeInFluid(net.minecraft.tags.FluidTags.WATER))&&!npc.isPassenger();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){ticks=0;done=false;npc.getNavigation().stop();}
    @Override public void tick(){if(!(npc.level() instanceof ServerLevel l))return;ticks++;var result=escape.tick(l,1);if(npc.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)){var motion=npc.getDeltaMovement();npc.setDeltaMovement(motion.x,Math.max(.22,motion.y),motion.z);npc.getJumpControl().jump();}npc.setCurrentAiState("ai.player_npc.water_recovery");npc.setCurrentAiDetail(escape.detail());done=result==WaterEscapeAi.TickResult.DONE||result==WaterEscapeAi.TickResult.NOT_NEEDED;}
    @Override public void stop(){escape.stop();retry=npc.tickCount+20;}
}

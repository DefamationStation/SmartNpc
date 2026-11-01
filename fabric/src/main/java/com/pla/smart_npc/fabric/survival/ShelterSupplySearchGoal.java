package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.BuildHouseGoal;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.entity.goal.GatherStoneGoal;
import com.pla.smart_npc.fabric.survival.memory.NpcMemories;
import com.pla.smart_npc.util.PlayerNpcBuildLayoutLoader;
import com.pla.smart_npc.util.PlayerNpcBuildMaterialUtil;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** A bounded planning action gives an unfunded builder an ordinary worker lease.
 * Register through addWorkGoal: its wrapper owns all scheduler admission/release.
 * ShelterSupplyTravel remains the only resource scanner and departure writer;
 * this goal neither calls it directly nor searches paths, moves or grants items.
 * A pending native missing-material cursor is useful planning work, not a claim
 * that a shortage has already been established. Both phases yield periodically. */
public final class ShelterSupplySearchGoal extends Goal {
    private static final int MAX_TICKS=200,RECHECK_TICKS=20,RETRY_TICKS=200;
    private final PlayerNpcEntity npc;
    private long retryAt,nextDemandCheck;
    private int elapsed;
    private boolean needed;
    public ShelterSupplySearchGoal(PlayerNpcEntity npc){this.npc=npc;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}

    /** Cheap inventory/commitment safety checks before any bounded material query. */
    private boolean eligible(ServerLevel level){
        return npc.hasInterest(PlayerNpcInterest.BUILDING)&&npc.isAlive()&&!npc.isNoAi()&&!npc.isPassenger()
                &&!npc.isHealing()&&!npc.isSleeping()&&!npc.isOnFire()&&npc.getTarget()==null
                &&npc.getHealth()>npc.getMaxHealth()*.5F&&!npc.isTeamFollower()
                &&npc.getUpwardEscapeTarget()==null&&npc.getHoleEscapeCooldown()<=0
                &&!npc.hasExplorationReturnHomeRequest()&&!level.isDarkOutside()&&!level.isThundering()
                &&Journeys.state(npc).phase.equals("idle")&&!AutonomousSupplyTrip.active(npc)
                &&!SurvivalTasks.cookingActive(npc)&&!npc.shouldYieldRoutineForSurvival()
                &&!(SurvivalTasks.memory(npc).active&&!SurvivalTasks.memory(npc).automatic)
                &&!GatherLogsGoal.isLogGatheringEpisodeActive(npc)&&!GatherStoneGoal.isStoneGatheringEpisodeActive(npc)
                &&SurvivalTasks.cookedFoodCount(npc)>=NpcMemories.departureFoodReserve(npc);
    }
    private boolean needsPlanning(ServerLevel level){
        if(PlayerNpcHomeUtil.getHome(npc).isEmpty()
                ||PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout).isEmpty())return false;
        // These native queries share their existing eight-block cursor and
        // scheduler budget. An unfinished cursor must not recreate the deadlock.
        boolean wood=PlayerNpcBuildMaterialUtil.needsLogsForCurrentBuild(level,npc);
        boolean stone=!wood&&npc.hasCarriedTool(ItemTags.PICKAXES)
                &&PlayerNpcBuildMaterialUtil.needsStoneForCurrentBuild(level,npc);
        boolean pending=PlayerNpcBuildMaterialUtil.isMissingBuildMaterialSearchPending(npc);
        return (wood||stone||pending)&&!BuildHouseGoal.hasReadyStarterPhase(npc,level);
    }
    @Override public boolean canUse(){
        if(!(npc.level() instanceof ServerLevel level)||level.getGameTime()<retryAt||!eligible(level))return false;
        retryAt=level.getGameTime()+RECHECK_TICKS;
        return needsPlanning(level);
    }
    @Override public boolean canContinueToUse(){
        return elapsed<MAX_TICKS&&needed&&npc.level() instanceof ServerLevel level&&eligible(level);
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){
        elapsed=0;needed=true;nextDemandCheck=0;
        // Hold position only for this bounded, admitted planning action.
        npc.getNavigation().stop();
    }
    @Override public void tick(){
        if(!(npc.level() instanceof ServerLevel level))return;
        elapsed++;
        if(level.getGameTime()>=nextDemandCheck){nextDemandCheck=level.getGameTime()+RECHECK_TICKS;needed=needsPlanning(level);}
        npc.setCurrentAiState("ai.player_npc.shelter_supply_search");
        npc.setCurrentAiDetail("checking missing shelter supplies and observed routes; bounded planning "+elapsed+"/"+MAX_TICKS);
    }
    @Override public void stop(){
        needed=false;
        if(npc.level() instanceof ServerLevel level)retryAt=level.getGameTime()+RETRY_TICKS;
        if(Journeys.state(npc).phase.equals("idle")&&npc.getTarget()==null)npc.setCurrentAiState(PlayerNpcEntity.AI_IDLE);
    }
}

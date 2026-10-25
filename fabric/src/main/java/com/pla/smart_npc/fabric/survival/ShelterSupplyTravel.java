package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.WeakHashMap;

/** Relocate an unfunded builder toward actually visible wood; native tree work remains authoritative. */
public final class ShelterSupplyTravel {
    private static final ArrayList<int[]> COLUMNS=new ArrayList<>();
    private static final Map<PlayerNpcEntity,Scan> SCANS=new WeakHashMap<>();
    static {for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)COLUMNS.add(new int[]{x,z});COLUMNS.sort(Comparator.comparingInt(p->Math.max(Math.abs(p[0]),Math.abs(p[1]))));}
    private static final class Scan {BlockPos origin;int cursor;long retry;}
    @Events.SubscribeEvent public static void observe(Events.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof PlayerNpcEntity n)||!(n.level() instanceof ServerLevel l)
                ||n.tickCount%20!=Math.floorMod(n.getUUID().hashCode(),20)
                ||!n.hasInterest(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING)
                ||n.isNoAi()||!n.isAlive()||n.isPassenger()||n.isHealing()||n.isSleeping()
                ||n.getTarget()!=null||n.getUpwardEscapeTarget()!=null||n.getHoleEscapeCooldown()>0
                ||SurvivalTasks.cookingActive(n)||Journeys.active(n)||SurvivalTasks.cookedFoodCount(n)<4
                ||GatherLogsGoal.isLogGatheringEpisodeActive(n)||!PlayerNpcAiWorkBudget.hasActiveWorkerSlot(n))return;
        if(com.pla.smart_npc.entity.goal.BuildHouseGoal.isHomeLayoutFinished(n,l)
                ||com.pla.smart_npc.entity.goal.BuildHouseGoal.hasReadyStarterPhase(n,l))return;
        boolean buildWood=com.pla.smart_npc.util.PlayerNpcBuildMaterialUtil.needsLogsForCurrentBuild(l,n);
        boolean stone=!buildWood&&n.shouldPrioritizeCobblestoneGathering()&&n.hasCarriedTool(net.minecraft.tags.ItemTags.PICKAXES);
        if(!stone&&!n.shouldPrioritizeLogGathering()&&!buildWood)return;
        var scan=SCANS.computeIfAbsent(n,k->new Scan());long now=l.getGameTime();
        if(com.pla.smart_npc.util.PlayerNpcHomeUtil.getHome(n).isPresent()
                &&!buildWood&&!stone)return;
        if(now<scan.retry)return;
        if(scan.origin==null||scan.origin.distSqr(n.blockPosition())>48*48){scan.origin=n.blockPosition();scan.cursor=0;}
        if(!PlayerNpcAiWorkBudget.tryAcquire(l,n))return;
        for(int count=0;count<16&&scan.cursor<COLUMNS.size();count++,scan.cursor++){
            var offset=COLUMNS.get(scan.cursor);BlockPos column=scan.origin.offset(offset[0],0,offset[1]);
            if(!l.hasChunkAt(column))continue;
            for(int dy=stone?-12:-2;dy<=16;dy++){
                var log=column.offset(0,dy,0);
                var block=l.getBlockState(log);
                boolean desired=stone?(block.is(net.minecraft.world.level.block.Blocks.STONE)||block.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)||block.is(net.minecraft.world.level.block.Blocks.DEEPSLATE)||block.is(net.minecraft.world.level.block.Blocks.BLACKSTONE)):block.is(BlockTags.LOGS);
                if(!desired||!SurvivalTasks.visible(n,log))continue;
                BlockPos stand=null;double best=Double.MAX_VALUE;
                for(Direction direction:Direction.Plane.HORIZONTAL)for(int y=-3;y<=1;y++){
                    var p=log.relative(direction).offset(0,y,0);
                    if(JourneyBoats.dry(l,p)&&p.distSqr(n.blockPosition())<best){stand=p;best=p.distSqr(n.blockPosition());}
                }
                if(stand!=null&&best>6*6){
                    var s=Journeys.state(n);
                    if(s.home==null){s.home=n.blockPosition().immutable();s.dimension=SurvivalTasks.dimension(n);}
                    s.destination=stand;s.dimension=SurvivalTasks.dimension(n);s.phase="outbound";s.reason="visible "+(stone?"stone":"wood")+" for shelter supplies";s.needsBoat=false;
                    s.status="travelling to observed supply; native collector will validate it";s.progressGoal=null;s.bestDistance=Double.MAX_VALUE;s.activeNoProgressTicks=0;
                    s.trail.clear();s.trail.observe(Journeys.point(n.blockPosition()));s.visits.clear();Journeys.save(n);scan.retry=now+2400;return;
                }
            }
        }
        if(scan.cursor>=COLUMNS.size()){scan.cursor=0;scan.retry=now+200;}
    }
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event){SCANS.clear();}
}

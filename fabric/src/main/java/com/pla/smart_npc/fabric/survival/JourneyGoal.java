package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.PathNavigationAi;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Comparator;

/** One bounded local path per admitted planning slice; no distant destination scan or teleport. */
public final class JourneyGoal extends Goal {
    private final PlayerNpcEntity npc;
    private int nextPath, candidate, stagnant;
    private BlockPos progress;
    private long lastVisit=Long.MIN_VALUE;
    private final com.pla.smart_npc.entity.ai.ReturnPositionAi recovery;
    private BlockPos recoveryTarget;
    private int recoveryUntil;
    private long lastTick=Long.MIN_VALUE;
    private static final int[] ANGLES={0,30,-30,60,-60,90,-90,120,-120,150,-150,180};
    public JourneyGoal(PlayerNpcEntity n){npc=n;recovery=new com.pla.smart_npc.entity.ai.ReturnPositionAi(n,1);setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){boolean owned=Journeys.ownsBoat(npc,npc.getVehicle());return Journeys.active(npc)&&npc.isAlive()&&!npc.isNoAi()&&(!npc.isPassenger()||owned)&&!npc.isSleeping()&&(owned||!npc.isHealing()&&npc.getTarget()==null);}
    @Override public boolean canContinueToUse(){return canUse()&&PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc);}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){nextPath=0;candidate=0;stagnant=0;progress=npc.blockPosition();npc.planningReversibleJourneyPath=Journeys.state(npc).phase.equals("outbound");}
    @Override public void stop(){recovery.stop();recoveryTarget=null;JourneyBoats.stop(npc);npc.planningReversibleJourneyPath=false;npc.getNavigation().stop();Journeys.save(npc);}
    /** Mounted mobs skip normal goal execution. Reuse this same registered controller and budget. */
    public void mountedTick(){
        if(!Journeys.ownsBoat(npc,npc.getVehicle()))return;
        if((npc.isHealing()||npc.getTarget()!=null)&&Journeys.state(npc).phase.equals("outbound"))Journeys.returnHome(npc,"danger on crossing; retreat toward home");
        if(!canUse()){JourneyBoats.stop(npc);return;}
        if(!PlayerNpcAiWorkBudget.canContinueWork(npc)){
            if(!PlayerNpcAiWorkBudget.canStartWork(npc,0,1)){JourneyBoats.stop(npc);return;}
            PlayerNpcAiWorkBudget.onWorkStarted(npc);
        }
        if(progress==null)start();
        tick();
    }
    @Override public void tick(){
        if(!(npc.level() instanceof ServerLevel l)||!PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc))return;
        if(lastTick==l.getGameTime())return;lastTick=l.getGameTime();
        var s=Journeys.state(npc);if(!s.dimension.equals(SurvivalTasks.dimension(npc))){Journeys.block(npc,"dimension mismatch");return;}
        BlockPos feet=npc.blockPosition();
        npc.setCurrentAiState("ai.player_npc.journey");npc.setCurrentAiDetail(s.phase+": "+s.status);
        BlockPos goal=s.destination;
        if(s.phase.equals("returning")){
            var trail=s.trail.points();
            while(s.cursor>=0){
                BlockPos mark=Journeys.block(trail.get(s.cursor));int reach=9;
                if(npc.isPassenger()&&s.cursor>0){
                    BlockPos shore=Journeys.block(trail.get(s.cursor-1));
                    // The walking breadcrumb at a launch can be too shallow
                    // for the returning hull. Hand off to its observed shore
                    // within boarding reach instead of sailing into the bank.
                    if(l.hasChunkAt(mark)&&l.hasChunkAt(shore)
                            &&l.getFluidState(mark).is(net.minecraft.tags.FluidTags.WATER)
                            &&JourneyBoats.dry(l,shore))reach=25;
                }
                if(!npc.isPassenger()&&s.cursor>0){
                    BlockPos next=Journeys.block(trail.get(s.cursor-1));
                    if(l.hasChunkAt(mark)&&l.hasChunkAt(next)&&l.getFluidState(mark).isEmpty()
                            &&l.getFluidState(next).is(net.minecraft.tags.FluidTags.WATER))reach=1;
                }
                // The last dry bank is an actual travel-mode handoff. Skipping
                // it from three blocks away can board across a strip of land.
                if(feet.distSqr(mark)>reach)break;
                s.cursor--;
                if(s.cursor<0){s.visits.clear();s.progressGoal=null;}
                Journeys.save(npc);
            }
            if(s.cursor>=0)goal=Journeys.block(trail.get(s.cursor));
            // Keep the recorded crossings through the final river or inlet.
            // After the trail is consumed, destination supplies the local home
            // approach, including a home established since departure.
        }
        if(goal==null){Journeys.block(npc,"missing destination");return;}
        s.activeNoProgressTicks++;
        if(recoveryTarget!=null){
            if(npc.tickCount>=recoveryUntil||feet.distSqr(recoveryTarget)<4){recovery.stop();recoveryTarget=null;candidate=0;}
            else{
                recovery.tickBuilderHomeReturn(l,recoveryTarget,p->{var b=l.getBlockState(p);return l.getBlockEntity(p)!=null||!(b.is(net.minecraft.tags.BlockTags.DIRT)||b.is(net.minecraft.tags.BlockTags.LEAVES)||b.is(net.minecraft.world.level.block.Blocks.SAND)||b.is(net.minecraft.world.level.block.Blocks.GRAVEL));},"recovering journey segment","clearing natural journey obstruction");
                s.status=recovery.detail("recovering journey segment");return;
            }
        }
        long cell=new BlockPos(Math.floorDiv(feet.getX(),4),0,Math.floorDiv(feet.getZ(),4)).asLong();
        if(cell!=lastVisit){lastVisit=cell;s.visits.merge(cell,1,Integer::sum);if(s.visits.size()>512)s.visits.remove(s.visits.keySet().iterator().next());}
        double remaining=Math.sqrt(feet.distSqr(goal));
        if(!goal.equals(s.progressGoal)){s.progressGoal=goal;s.bestDistance=remaining;s.progressAt=l.getGameTime();s.activeNoProgressTicks=0;}
        if(remaining<s.bestDistance-3){s.bestDistance=remaining;s.progressAt=l.getGameTime();s.activeNoProgressTicks=0;Journeys.save(npc);}
        if(s.activeNoProgressTicks>1600){
            if(s.phase.equals("outbound")&&s.trail.points().size()>1)Journeys.returnHome(npc,"no net route progress; returning by observed trail");
            else Journeys.block(npc,"remembered segment obstructed; no net route progress");
            return;
        }
        boolean resourceLanding=!s.reason.startsWith("visible ")||JourneyBoats.standing(npc,l);
        if(feet.distSqr(s.destination)<=9&&resourceLanding&&(s.phase.equals("outbound")||!npc.isPassenger())){
            if(s.phase.equals("outbound")){
                com.pla.smart_npc.fabric.survival.memory.NpcMemories.routeSucceeded(npc,s.home,s.destination);
                s.lastArrival=s.destination;s.phase="at_destination";s.status="arrived; ready for local work or return";
            }else {
                if(s.lastArrival!=null){
                    com.pla.smart_npc.fabric.survival.memory.NpcMemories.routeSucceeded(npc,s.lastArrival,s.destination);
                    if(s.lastArrival.distSqr(s.destination)>=256*256)VerifiedWorkOutcome.emit(npc,VerifiedWorkOutcome.Kind.JOURNEY_RETURN,
                            s.destination,"native:journey_return/"+Long.toUnsignedString(s.lastArrival.asLong()),1);
                }
                s.lastArrival=null;s.phase="idle";s.status="home reached";s.trail.clear();npc.clearExplorationReturnHomeRequest();
            }
            Journeys.save(npc);npc.getNavigation().stop();return;
        }
        double waterDx=goal.getX()-feet.getX(),waterDz=goal.getZ()-feet.getZ(),waterLength=Math.max(1,Math.hypot(waterDx,waterDz));
        var waterAhead=feet.offset((int)Math.round(waterDx/waterLength*2),0,(int)Math.round(waterDz/waterLength*2));
        var currentPath=npc.getNavigation().getPath();
        if(currentPath!=null&&!currentPath.isDone())waterAhead=currentPath.getNextNodePos();
        boolean crossing=l.hasChunkAt(waterAhead)&&(l.getFluidState(waterAhead).is(net.minecraft.tags.FluidTags.WATER)||l.getFluidState(waterAhead.below()).is(net.minecraft.tags.FluidTags.WATER));
        if(s.phase.equals("returning")&&feet.distSqr(goal)<24*24
                &&l.getFluidState(goal).is(net.minecraft.tags.FluidTags.WATER))crossing=true;
        if(crossing&&JourneyBoats.standing(npc,l)&&s.boatId==null
                &&npc.getInventory().getItems().stream().noneMatch(item->item.getItem() instanceof net.minecraft.world.item.BoatItem)){
            // Prepare at a real table while a supported placement stand still
            // exists. Waiting until deep water strands the crafting action.
            if(!s.needsBoat){s.needsBoat=true;Journeys.save(npc);}
            npc.getNavigation().stop();s.status="preparing boat before entering water";return;
        }
        if(JourneyBoats.tick(npc,l,goal,npc.isPassenger()||npc.isInWater()||stagnant>100||crossing)){
            if(!npc.isPassenger()){progress=npc.blockPosition();stagnant=0;nextPath=0;candidate=0;return;}
            if(feet.distSqr(progress)>=4){progress=feet;stagnant=0;}else stagnant++;
            if(stagnant>600){if(s.phase.equals("outbound")&&s.trail.points().size()>1)Journeys.returnHome(npc,"boat shore obstructed; returning by observed crossing");else Journeys.block(npc,"boat made no progress; shore or route obstructed");stagnant=0;}
            return;
        }
        if(feet.distSqr(progress)>=16){progress=feet;stagnant=0;}else stagnant++;
        if(stagnant>600){
            if(s.phase.equals("outbound")&&s.trail.points().size()>1)Journeys.returnHome(npc,"outbound blocked; returning by observed route");
            else Journeys.block(npc,"no local progress; route obstructed or unsupported crossing");
            stagnant=0;return;
        }
        if(nextPath-- >0||!npc.getNavigation().isDone())return;
        if(!PlayerNpcAiWorkBudget.tryAcquire(l,npc)||!PlayerNpcAiWorkBudget.tryAcquireNavigationPathStart(npc))return;
        nextPath=10;
        // Boat breadcrumbs are water positions, not walking destinations. On
        // return, approach a nearby observed bank before launching again; a
        // taller bank can put the water beyond normal boarding reach.
        if(s.phase.equals("returning")&&!npc.isPassenger()
                &&l.getFluidState(goal).is(net.minecraft.tags.FluidTags.WATER)){
            var trail=s.trail.points();var distinctBanks=new java.util.LinkedHashSet<BlockPos>();
            for(int i=0;i<8;i++){
                int index=s.cursor+1+i;if(index<0||index>=trail.size())continue;
                BlockPos remembered=Journeys.block(trail.get(index));
                // A walking breadcrumb need not be close enough to board from.
                // Boarding and breadcrumb sampling can leave a narrow sandbar
                // between the recorded walking point and the usable dock.
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=1;y++){
                    BlockPos bank=remembered.offset(x,y,z);
                    if(bank.getY()-goal.getY()>3||bank.distSqr(feet)>24*24||!boardingStand(l,bank))continue;
                    distinctBanks.add(bank);
                }
            }
            var banks=new ArrayList<>(distinctBanks);
            final BlockPos waterGoal=goal;
            banks.sort(Comparator.comparingDouble(p->p.distSqr(waterGoal)+p.distSqr(feet)*.1));
            if(!banks.isEmpty()){
                BlockPos bank=banks.get(Math.floorMod(candidate++/4,banks.size()));
                // Ground navigation with canFloat targets the water surface,
                // not the submerged bottom of a one-block-deep wading stance.
                BlockPos navigationStand=l.getFluidState(bank).is(net.minecraft.tags.FluidTags.WATER)?bank.above():bank;
                var approach=PathNavigationAi.createBoundedPath(npc,navigationStand,0.2F);
                // Floating navigation may end at the supported wading floor
                // rather than mark the air-level surface target as reached.
                boolean safe=approach!=null&&approach.getNodeCount()>1
                        &&approach.getEndNode()!=null
                        &&approach.getEndNode().asBlockPos().distSqr(bank)<=2;
                if(safe)for(int node=1;node<approach.getNodeCount();node++){
                    if(approach.getNode(node-1).y-approach.getNode(node).y>3
                            ||!dryWalkNode(l,approach.getNode(node).asBlockPos()))safe=false;
                }
                if(safe){
                    npc.getNavigation().moveTo(approach,1);s.status="approaching observed boat launch bank";return;
                }
                s.status="observed launch bank route unavailable";return;
            }
        }
        // Let vanilla search the *local* region toward the distant intention.
        // A useful partial path chooses terrain-aware intermediate nodes rather
        // than projecting a straight-line endpoint onto the wrong hillside.
        if(candidate==0){
            var nav=npc.getNavigation();net.minecraft.world.level.pathfinder.Path partial=null;
            int range=24;boolean loaded=true;
            for(int cx=(feet.getX()-range-8)>>4;cx<=((feet.getX()+range+8)>>4);cx++)
                for(int cz=(feet.getZ()-range-8)>>4;cz<=((feet.getZ()+range+8)>>4);cz++)
                    if(!l.hasChunkAt(new BlockPos(cx*16,feet.getY(),cz*16)))loaded=false;
            if(loaded){
                nav.setMaxVisitedNodesMultiplier(0.5F);
                // A far destination's elevation is not a local climbing order.
                // Approach horizontally and resolve its height in the final region.
                double horizontal=Math.hypot(goal.getX()-feet.getX(),goal.getZ()-feet.getZ());
                BlockPos localIntention=horizontal>32?new BlockPos(goal.getX(),feet.getY(),goal.getZ()):goal;
                npc.planningReversibleJourneyPath=maxSegmentDrop(s,feet,goal)==1;
                try{partial=nav.createPath(localIntention,0,range);}
                finally{nav.resetMaxVisitedNodesMultiplier();npc.planningReversibleJourneyPath=s.phase.equals("outbound");}
                if(partial!=null&&partial.getNodeCount()>1&&partial.getEndNode()!=null
                    &&feet.distSqr(partial.getEndNode().asBlockPos())>=4
                    &&partial.getEndNode().asBlockPos().distSqr(goal)<feet.distSqr(goal)-4
                    &&s.visits.getOrDefault(new BlockPos(Math.floorDiv(partial.getEndNode().x,4),0,Math.floorDiv(partial.getEndNode().z,4)).asLong(),0)<2){
                    boolean safe=true;int drop=maxSegmentDrop(s,feet,goal);
                    for(int i=1;i<partial.getNodeCount();i++)if(partial.getNode(i-1).y-partial.getNode(i).y>drop||!dryWalkNode(l,partial.getNode(i).asBlockPos()))safe=false;
                    if(safe){nav.moveTo(partial,1);s.status="following bounded terrain-aware segment";return;}
                }
            }
            candidate=1;return; // One synchronous path search per admitted slice.
        }
        double dx=goal.getX()-feet.getX(),dz=goal.getZ()-feet.getZ(),distance=Math.sqrt(dx*dx+dz*dz);
        double step=Math.min(12,Math.max(2,distance));
        var choices=new ArrayList<BlockPos>();
        int segmentY=distance>32?feet.getY():feet.getY()+(int)Math.round((goal.getY()-feet.getY())*Math.min(1,step/Math.max(1,distance)));
        for(int angle:ANGLES){double heading=Math.atan2(dz,dx)+Math.toRadians(angle);choices.add(angle==0&&distance<=12?goal:new BlockPos(feet.getX()+(int)Math.round(Math.cos(heading)*step),segmentY,feet.getZ()+(int)Math.round(Math.sin(heading)*step)));}
        final BlockPos finalGoal=goal;
        choices.sort(Comparator.comparingDouble(p->Math.sqrt(p.distSqr(finalGoal))+24*s.visits.getOrDefault(new BlockPos(Math.floorDiv(p.getX(),4),0,Math.floorDiv(p.getZ(),4)).asLong(),0)));
        if(s.phase.equals("returning")&&distance<=24){choices.remove(goal);choices.addFirst(goal);}
        BlockPos target=choices.get(candidate++%choices.size());
        BlockPos stand=null;
        for(int i=0;i<=16;i++){int dy=i==0?0:(i+1)/2*(i%2==1?1:-1);BlockPos p=target.offset(0,dy,0);
            if(!l.hasChunkAt(p)||!l.getWorldBorder().isWithinBounds(p))continue;
            if(l.getBlockState(p).getCollisionShape(l,p).isEmpty()&&l.getFluidState(p).isEmpty()
                &&l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()
                &&!l.getBlockState(p.below()).getCollisionShape(l,p.below()).isEmpty()
                &&l.getBlockState(p.below()).getCollisionShape(l,p.below()).max(net.minecraft.core.Direction.Axis.Y)>=0.99
                &&!l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                &&!l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)){stand=p;break;}
        }
        if(stand==null){s.status="finding dry reachable segment (no boat route yet)";return;}
        net.minecraft.world.level.pathfinder.Path path;
        npc.planningReversibleJourneyPath=maxSegmentDrop(s,feet,goal)==1;
        try{path=PathNavigationAi.createBoundedPath(npc,stand,0.2F);}
        finally{npc.planningReversibleJourneyPath=s.phase.equals("outbound");}
        boolean reversible=path!=null&&path.canReach();
        if(reversible){
            // Vanilla permits drops that walking cannot reverse. Do not earn a
            // return promise by taking a one-way cliff shortcut on the outbound leg.
            int maxDrop=maxSegmentDrop(s,feet,goal);
            for(int i=1;i<path.getNodeCount();i++)if(path.getNode(i-1).y-path.getNode(i).y>maxDrop||!dryWalkNode(l,path.getNode(i).asBlockPos())){reversible=false;break;}
        }
        if(reversible){
            npc.getNavigation().moveTo(path,1.0);s.status="walking local segment to "+stand.toShortString();
            candidate=0;
        }else{
            s.status="local route unavailable; checking detour";
            if(stagnant>100&&stand.getY()>feet.getY()&&stand.distSqr(feet)<=16*16){
                recoveryTarget=stand;recoveryUntil=npc.tickCount+200;recovery.start(stand);s.status="bounded native uphill recovery";
            }
        }
    }
    private static int maxSegmentDrop(Journeys.State state,BlockPos feet,BlockPos goal){
        // A closer horizontal endpoint below a rising forest route can be a
        // one-way shortcut off its ledge. Preserve the climb instead of taking
        // a drop that the next local segment cannot reverse.
        return state.phase.equals("outbound")||goal.getY()>=feet.getY()-1?1:3;
    }
    private static boolean dryWalkNode(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p)||!l.getFluidState(p).isEmpty()&&!l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)
                ||!l.getFluidState(p.above()).isEmpty())return false;
        if(!l.getBlockState(p.below()).getCollisionShape(l,p.below()).isEmpty())return true;
        if(!l.getFluidState(p).isEmpty()||!l.getFluidState(p.below()).is(net.minecraft.tags.FluidTags.WATER))return false;
        var bottom=l.getBlockState(p.below(2)).getCollisionShape(l,p.below(2));
        return !bottom.isEmpty()&&bottom.max(net.minecraft.core.Direction.Axis.Y)>=.99;
    }
    private boolean boardingStand(ServerLevel level,BlockPos feet){
        if(JourneyBoats.dry(level,feet))return true;
        // A wide sandbar needs a short supported wade before a boat is in
        // boarding reach. Do not confuse that safe stance with deep swimming.
        if(!level.hasChunkAt(feet)||!level.getWorldBorder().isWithinBounds(feet)
                ||!level.getFluidState(feet).is(net.minecraft.tags.FluidTags.WATER)
                ||!level.getFluidState(feet.above()).isEmpty())return false;
        var floor=level.getBlockState(feet.below()).getCollisionShape(level,feet.below());
        if(floor.isEmpty()||floor.max(net.minecraft.core.Direction.Axis.Y)<.99)return false;
        double half=Math.max(.3,npc.getBbWidth()/2)+.03;
        var body=new net.minecraft.world.phys.AABB(feet.getX()+.5-half,feet.getY(),feet.getZ()+.5-half,
                feet.getX()+.5+half,feet.getY()+npc.getBbHeight()+.05,feet.getZ()+.5+half);
        return level.noCollision(npc,body);
    }
}

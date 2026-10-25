package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicitly owned physical boat, consumed once and packed only when empty and in reach. */
public final class JourneyBoats {
    private JourneyBoats(){}
    public static void stop(PlayerNpcEntity n){if(n.getVehicle() instanceof AbstractBoat b&&Journeys.ownsBoat(n,b)){b.setInput(false,false,false,false);b.setPaddleState(false,false);}}
    public static boolean tick(PlayerNpcEntity n,ServerLevel l,BlockPos goal,boolean mayLaunch){
        var s=Journeys.state(n);
        if(!n.isPassenger()&&s.landingTarget!=null){
            if(s.boatId!=null&&l.getEntity(s.boatId) instanceof AbstractBoat hull&&hull.getPassengers().isEmpty()&&n.distanceToSqr(hull)<16&&!s.boatItem.isEmpty()){
                var remainder=n.getInventory().addItem(s.boatItem.copy());
                if(remainder.isEmpty()){hull.discard();s.boatId=null;s.boatItem=ItemStack.EMPTY;Journeys.save(n);}
            }
            if(standing(n,l)){
                if(s.landingRecovery!=null)s.landingRecovery.stop();s.landingRecovery=null;s.landingTarget=null;Journeys.save(n);
                // A completed physical landing must get a walking opportunity
                // before adjacent water can trigger another launch.
                s.nextBoatCheck=l.getGameTime()+100;s.progressGoal=null;
            }else{
                if(l.getGameTime()-s.landingStarted>400){Journeys.block(n,"could not reach observed shore after dismount");return true;}
                if(s.landingRecovery==null)s.landingRecovery=new com.pla.smart_npc.entity.ai.WaterEscapeAi(n);
                if(!n.isInWater()){
                    if(n.getNavigation().isDone()&&n.tickCount%10==0){
                        var path=com.pla.smart_npc.entity.ai.PathNavigationAi.createBoundedPath(n,s.landingTarget,.25F);
                        if(path!=null&&path.canReach())n.getNavigation().moveTo(path,1);
                    }
                    if(n.getNavigation().isDone()){
                        n.getMoveControl().setWantedPosition(s.landingTarget.getX()+.5,s.landingTarget.getY(),s.landingTarget.getZ()+.5,1);
                        n.getJumpControl().jump();
                    }
                    s.status="walking a bounded shore landing route";
                }else{s.landingRecovery.tick(l,1,s.landingTarget);s.status=s.landingRecovery.detail();}
                return true;
            }
        }
        if(n.getVehicle() instanceof AbstractBoat b&&Journeys.ownsBoat(n,b)){
            if(l.getGameTime()>=s.nextBoatPlan){
                s.boatWaypoint=waterWaypoint(l,b,goal,s,n,false);
                s.boatSteering=s.boatWaypoint==null?null:new net.minecraft.world.phys.Vec3(
                        b.getX()+s.boatWaypoint.getX()-b.blockPosition().getX(),b.getY(),
                        b.getZ()+s.boatWaypoint.getZ()-b.blockPosition().getZ());
                // Widen only a stalled local search; search four times less
                // often to retain the normal average node allowance.
                s.nextBoatPlan=l.getGameTime()+(s.activeNoProgressTicks>200?20:5);
            }
            var steering=s.boatSteering==null?net.minecraft.world.phys.Vec3.atCenterOf(goal):s.boatSteering;
            double dx=steering.x-b.getX(),dz=steering.z-b.getZ();
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));float delta=Mth.wrapDegrees(yaw-b.getYRot());
            var ahead=BlockPos.containing(b.getX()+dx/Math.max(1,Math.hypot(dx,dz))*2,b.getY()+.5,b.getZ()+dz/Math.max(1,Math.hypot(dx,dz))*2);
            // Water breadcrumbs are sailing instructions. Premature shore exits
            // repeatedly packed and relaunched the boat beside the same bank.
            if(wantsShore(l,b,goal,s)||b.onGround()){
            boolean dryExit=false;BlockPos landing=null;
            for(int dy=-1;dy<=2;dy++){var p=ahead.offset(0,dy,0);if(p.distSqr(goal)<b.blockPosition().distSqr(goal)-4&&dry(l,p)&&n.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)<16){dryExit=true;landing=p;}}
            // Observe landing places around the hull, not only one forward column.
            double bestLanding=Double.MAX_VALUE;
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=2;y++){
                var p=b.blockPosition().offset(x,y,z);
                double d=n.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5);
                if(d>10||p.distSqr(goal)>=b.blockPosition().distSqr(goal)-4||!dry(l,p))continue;
                double cost=p.distSqr(goal)+d*4;
                if(cost<bestLanding){bestLanding=cost;dryExit=true;landing=p;}
            }
            var exit=b.getDismountLocationForPassenger(n);
            // A nearby dry bank does not guarantee vanilla's actual dismount
            // position is dry: its fallback can be the bottom of a deep inlet.
            double half=Math.max(.3,n.getBbWidth()/2)+.03;
            boolean plannedExit=false;
            if(s.boatLanding!=null){
                var v=net.minecraft.world.phys.Vec3.atBottomCenterOf(s.boatLanding);
                if(dry(l,s.boatLanding)&&Math.hypot(v.x-b.getX(),v.z-b.getZ())<=1.8&&v.y-b.getY()<=1.5){
                    exit=v;plannedExit=true;
                }
            }
            // Keep vanilla's normal dismount operation, but allow a nearby loaded
            // standing shore when its stock fallback selects the submerged floor.
            // This is bounded boat-exit reach, never an expedition teleport.
            if(!plannedExit)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=2;y++){
                var p=b.blockPosition().offset(x,y,z);var v=net.minecraft.world.phys.Vec3.atBottomCenterOf(p);
                if(!dry(l,p)||Math.hypot(v.x-b.getX(),v.z-b.getZ())>1.8||v.y-b.getY()>1.5||p.distSqr(goal)>=b.blockPosition().distSqr(goal))continue;
                var box=new net.minecraft.world.phys.AABB(v.x-half,v.y,v.z-half,v.x+half,v.y+Math.max(1.95,n.getBbHeight()),v.z+half);
                if(l.noCollision(n,box)){exit=v;dryExit=true;break;}
            }
            var standingExit=new net.minecraft.world.phys.AABB(exit.x-half,exit.y,exit.z-half,exit.x+half,exit.y+Math.max(1.95,n.getBbHeight()),exit.z+half);
            if(dry(l,BlockPos.containing(exit))&&l.noCollision(n,standingExit)&&(plannedExit||dryExit||b.onGround())){
                s.landingTarget=BlockPos.containing(exit);s.landingStarted=l.getGameTime();
                stop(n);s.dismountPosition=exit;try{n.stopRiding();}finally{s.dismountPosition=null;}
                resetWalking(n);s.status="disembarking toward observed shore";Journeys.save(n);return true;
            }
            }
            if(!l.hasChunkAt(ahead)){stop(n);s.status="waiting for moving chunk frontier";return true;}
            b.setInput(delta<-5,delta>5,s.boatWaypoint!=null&&Math.abs(delta)<55,false);b.setPaddleState(true,true);
            n.setYRot(yaw);n.getLookControl().setLookAt(goal.getX()+.5,n.getEyeY(),goal.getZ()+.5);
            s.status="sailing owned boat through loaded water";return true;
        }
        if(s.boatId!=null&&l.getEntity(s.boatId) instanceof AbstractBoat b&&b.getPassengers().isEmpty()&&n.distanceToSqr(b)<16){
            if(standing(n,l)&&!s.boatItem.isEmpty()){
                ItemStack remaining=n.getInventory().addItem(s.boatItem.copy());
                if(remaining.isEmpty()){b.discard();s.boatId=null;s.boatItem=ItemStack.EMPTY;Journeys.save(n);}
            }else if(mayLaunch&&n.startRiding(b,true,true)){b.positionRider(n);com.pla.smart_npc.util.PlayerNpcForceTickManager.track(n);return true;}
        }
        if(!mayLaunch||s.boatId!=null)return false;
        // Return trails mix walking and sailing. Reach the recorded launch bank
        // before boarding; nearby water on the far side of land is not the next
        // sailing segment. The allowance covers boarding reach plus trail spacing.
        if(s.phase.equals("returning")&&standing(n,l)&&l.getFluidState(goal).is(FluidTags.WATER)
                &&n.blockPosition().distSqr(goal)>7*7)return false;
        if(l.getGameTime()<s.nextBoatCheck)return false;s.nextBoatCheck=l.getGameTime()+20;
        BlockPos best=null;double score=Double.MAX_VALUE;
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-3;dy<=1;dy++){
            BlockPos p=n.blockPosition().offset(dx,dy,dz);
            if(!l.hasChunkAt(p)||!l.canSeeSky(p.above())||!l.getFluidState(p).is(FluidTags.WATER)||!l.getFluidState(p.below()).is(FluidTags.WATER)||!l.getFluidState(p.above()).isEmpty()||!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty())continue;
            if(n.distanceToSqr(p.getX()+.5,p.getY()+.8,p.getZ()+.5)>12)continue;
            var boardingBox=new net.minecraft.world.phys.AABB(p.getX()-.25,p.getY()+.8,p.getZ()-.25,
                    p.getX()+1.25,p.getY()+.8+Math.max(2,n.getBbHeight())+.3,p.getZ()+1.25);
            if(!l.noCollision(n,boardingBox))continue;
            double cost=p.distSqr(goal);
            if(cost<score){score=cost;best=p;}
        }
        if(best==null)return false;
        int slot=-1;
        for(int i=0;i<n.getInventory().getContainerSize();i++)if(n.getInventory().getItem(i).getItem() instanceof net.minecraft.world.item.BoatItem){slot=i;break;}
        if(slot<0){if(!s.needsBoat){s.needsBoat=true;Journeys.save(n);}n.getNavigation().stop();s.status="water crossing needs a boat; prepare at a real table";return true;}
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(n.getInventory().getItem(slot).getItem()));
        if(type==null)return false;var entity=type.create(l,EntitySpawnReason.MOB_SUMMONED);if(!(entity instanceof AbstractBoat boat))return false;
        boat.setPos(best.getX()+.5,best.getY()+.8,best.getZ()+.5);
        if(!l.noCollision(boat,boat.getBoundingBox().expandTowards(0,Math.max(2,n.getBbHeight())+.3,0)))return false;
        // Adjacent water can be on the wrong side of a peninsula. For a nearby
        // recorded water segment, prove local water connectivity before spending
        // the boat item; otherwise walk to the nearer observed launch bank.
        if(best.distSqr(goal)<12*12&&l.getFluidState(goal).is(FluidTags.WATER)
                &&waterWaypoint(l,boat,goal,s,n,true)==null){s.status="approach a bank connected to the remembered water segment";return false;}
        s.boatItem=n.getInventory().getItem(slot).copyWithCount(1);s.boatId=boat.getUUID();
        if(!l.addFreshEntity(boat)){s.boatId=null;s.boatItem=ItemStack.EMPTY;return false;}
        n.getInventory().getItem(slot).shrink(1);s.needsBoat=false;Journeys.save(n);
        s.nextBoatPlan=0;s.boatWaypoint=null;s.boatSteering=null;s.boatLanding=null;
        if(!n.startRiding(boat,true,true)){s.status="own boat launched; boarding pending";return true;}
        boat.positionRider(n);com.pla.smart_npc.util.PlayerNpcForceTickManager.track(n);
        n.getNavigation().stop();s.status="boarded owned boat";return true;
    }
    private static void resetWalking(PlayerNpcEntity n){
        n.getNavigation().stop();n.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        n.xxa=0;n.yya=0;n.zza=0;n.setSpeed(0);n.setJumping(false);
        n.getMoveControl().setWantedPosition(n.getX(),n.getY(),n.getZ(),0);
    }
    /** Bounded loaded-water search around banks, using the physical hull clearance. */
    private static BlockPos waterWaypoint(ServerLevel l,AbstractBoat b,BlockPos goal,Journeys.State state,PlayerNpcEntity n,boolean requireGoal){
        var origin=b.blockPosition();var steps=new java.util.HashMap<BlockPos,Integer>();steps.put(origin,0);
        // Search toward the remembered segment instead of flooding every open
        // water cell equally. A bend can need a temporary move away from it.
        var queue=new java.util.PriorityQueue<BlockPos>(java.util.Comparator.comparingDouble(
                p->steps.get(p)+Math.sqrt(p.distSqr(goal))));
        var first=new java.util.HashMap<BlockPos,BlockPos>();queue.add(origin);first.put(origin,origin);
        BlockPos best=origin;double score=Double.MAX_VALUE;
        boolean shoreSearch=wantsShore(l,b,goal,state);
        BlockPos shoreWater=null,shoreStand=null;double shoreScore=Double.MAX_VALUE;
        state.boatLanding=null;
        boolean wider=state.activeNoProgressTicks>200;
        int radius=wider?24:12,allowance=wider?1024:256;
        for(int count=0;count<allowance&&!queue.isEmpty();count++){
            var p=queue.remove();double cost=p.distSqr(goal);
            if(!p.equals(origin)&&cost<score){score=cost;best=p;}
            if(shoreSearch){
                BlockPos shore=landingAt(l,b,n,p,origin,goal);
                if(shore!=null){double value=shore.distSqr(goal)+steps.get(p)*.05;
                    if(value<shoreScore){shoreScore=value;shoreWater=p;shoreStand=shore;}}
            }
            if(!shoreSearch&&!p.equals(origin)&&cost<=4)break;
            for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);
                if(Math.abs(q.getX()-origin.getX())>radius||Math.abs(q.getZ()-origin.getZ())>radius||first.containsKey(q)||!l.hasChunkAt(q))continue;
                if(!l.getFluidState(q).is(FluidTags.WATER)||!l.getFluidState(q.above()).isEmpty())continue;
                // Keep the hull's actual fractional position. Snapping test
                // boxes to block centres can invent collisions at a bank corner
                // and reject every escape from an otherwise valid boat position.
                var box=b.getBoundingBox().move(q.getX()-origin.getX(),0,q.getZ()-origin.getZ());
                if(!l.noCollision(b,box.expandTowards(0,Math.max(2,n.getBbHeight())+.3,0)))continue;
                first.put(q,p);steps.put(q,steps.get(p)+1);queue.add(q);
            }
        }
        if(shoreWater!=null){best=shoreWater;state.boatLanding=shoreStand;}
        if(best.equals(origin)||requireGoal&&best.distSqr(goal)>9)return null;
        // A multi-cell lookahead cuts across solid bank corners even when the
        // searched cardinal water route itself has full hull clearance.
        while(!first.get(best).equals(origin))best=first.get(best);
        return best;
    }
    private static boolean wantsShore(ServerLevel level,AbstractBoat boat,BlockPos goal,Journeys.State state){
        return !level.getFluidState(goal).is(FluidTags.WATER)&&!level.getFluidState(goal.below()).is(FluidTags.WATER)
                &&(Math.hypot(goal.getX()-boat.getX(),goal.getZ()-boat.getZ())<32||state.activeNoProgressTicks>100);
    }
    private static BlockPos landingAt(ServerLevel level,AbstractBoat boat,PlayerNpcEntity npc,
                                      BlockPos water,BlockPos origin,BlockPos goal){
        BlockPos best=null;double cost=Double.MAX_VALUE;
        double x=boat.getX()+water.getX()-origin.getX(),z=boat.getZ()+water.getZ()-origin.getZ();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=0;y<=2;y++){
            var stand=water.offset(dx,y,dz);
            if(stand.getY()-boat.getY()>1.5||Math.hypot(stand.getX()+.5-x,stand.getZ()+.5-z)>1.8||!dry(level,stand))continue;
            double score=stand.distSqr(goal);if(score<cost){cost=score;best=stand;}
        }
        return best;
    }
    public static boolean standing(PlayerNpcEntity n,ServerLevel l){return !n.isPassenger()&&!n.isInWater()&&dry(l,n.blockPosition())&&(n.onGround()||Math.abs(n.getY()-n.blockPosition().getY())<.04);}
    public static boolean dry(ServerLevel l,BlockPos p){return l.hasChunkAt(p)&&l.getFluidState(p).isEmpty()&&l.getFluidState(p.above()).isEmpty()&&l.getBlockState(p).getCollisionShape(l,p).isEmpty()&&l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP);}
}

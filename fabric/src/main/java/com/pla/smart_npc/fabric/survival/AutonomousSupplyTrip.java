package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.entity.goal.GatherStoneGoal;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Saved lifecycle around an already admitted autonomous local supply journey.
 * Native goals alone move, harvest and pick up items. Arrival releases JourneyGoal
 * to those collectors; only real harvest PLUS carried growth can complete work.
 * Ownership comparisons prevent this observer reviving canceled/replaced trips.
 * No prior cooking/rest, commands, inventory grants, scans or paths are needed. */
public final class AutonomousSupplyTrip {
    private static final String KEY="SmartNpcAutonomousSupplyTrip";
    private static final String RETURN_REASON="autonomous supply return";
    private static final int VERSION=1,MAX_WORK_TICKS=3600;
    private static final Set<String> STONE=Set.of("minecraft:stone","minecraft:cobblestone","minecraft:deepslate","minecraft:cobbled_deepslate","minecraft:blackstone");
    private AutonomousSupplyTrip() { }

    /** Call immediately after ShelterSupplyTravel has assigned its native trip. */
    public static boolean begin(PlayerNpcEntity npc,BlockPos resource,boolean stone){
        if(!(npc.level() instanceof ServerLevel)||resource==null)return false;
        var trip=Journeys.state(npc);
        if(!trip.phase.equals("outbound")||trip.home==null||trip.destination==null||!trip.dimension.equals(SurvivalTasks.dimension(npc)))return false;
        var old=read(npc);if(old.contains("version")&&old.getIntOr("version",0)!=VERSION)return false;
        var tag=new CompoundTag();tag.putInt("version",VERSION);tag.putBoolean("active",true);
        tag.putString("dimension",trip.dimension);tag.putString("reason",trip.reason);
        tag.putLong("destination",trip.destination.asLong());tag.putLong("home",trip.home.asLong());tag.putLong("resource",resource.asLong());
        tag.putBoolean("stone",stone);tag.putInt("baseline",count(npc,stone));tag.putInt("departureFood",SurvivalTasks.cookedFoodCount(npc));
        tag.putString("stage","outward");tag.putString("status","travelling to needed "+(stone?"stone":"wood"));write(npc,tag);return true;
    }
    /** Observe an already admitted real native collector without creating travel.
     * Reattachment after restart retains the original baseline and evidence. */
    public static boolean beginCollecting(PlayerNpcEntity npc,BlockPos resource,boolean stone){
        if(!(npc.level() instanceof ServerLevel)||resource==null||!npc.hasInterest(PlayerNpcInterest.BUILDING)
                ||SurvivalTasks.cookingActive(npc)||(SurvivalTasks.memory(npc).active&&!SurvivalTasks.memory(npc).automatic))return false;
        var trip=Journeys.state(npc);var home=PlayerNpcHomeUtil.getHome(npc);
        if(!trip.phase.equals("idle")||home.isEmpty()||!trip.dimension.equals(SurvivalTasks.dimension(npc)))return false;
        var old=read(npc);if(old.contains("version")&&!supported(old))return false;
        if(old.getBooleanOr("active",false))return old.getStringOr("stage","").equals("collecting")
                &&old.getBooleanOr("stone",false)==stone&&ownsCollecting(old,trip);
        var tag=new CompoundTag();tag.putInt("version",VERSION);tag.putBoolean("active",true);tag.putString("stage","collecting");
        tag.putString("dimension",SurvivalTasks.dimension(npc));tag.putString("reason",trip.reason);
        tag.putLong("destination",resource.asLong());tag.putLong("resource",resource.asLong());
        var anchor=home.get().origin().offset(home.get().width()/2,1,home.get().depth()/2);tag.putLong("home",anchor.asLong());
        if(trip.home!=null)tag.putLong("idleHome",trip.home.asLong());if(trip.destination!=null)tag.putLong("idleDestination",trip.destination.asLong());
        tag.putString("idleStatus",trip.status);tag.putBoolean("stone",stone);tag.putInt("baseline",count(npc,stone));
        tag.putInt("departureFood",SurvivalTasks.cookedFoodCount(npc));tag.putString("status","native collector admitted for shelter "+(stone?"stone":"wood"));
        write(npc,tag);return true;
    }
    /** Optional explicit manual-cancel hook; repeated identical idle cancel
     * commands otherwise have no distinguishable native journey revision. */
    public static void cancel(PlayerNpcEntity npc,String reason){var tag=read(npc);if(supported(tag)&&tag.getBooleanOr("active",false))close(npc,tag,"interrupted: "+reason);}
    public static boolean active(PlayerNpcEntity npc){var tag=read(npc);return supported(tag)&&tag.getBooleanOr("active",false);}
    public static String describe(PlayerNpcEntity npc){return snapshot(npc).status();}
    /** Read-only fixture/inspector view; calling it creates no task or progress. */
    public record Snapshot(boolean active,String stage,String status,boolean stone,int baseline,boolean harvested,boolean collected,
                           int workTicks,String dimension,BlockPos resource,BlockPos destination,BlockPos home){ }
    public static Snapshot snapshot(PlayerNpcEntity npc){
        var tag=read(npc);return new Snapshot(supported(tag)&&tag.getBooleanOr("active",false),tag.getStringOr("stage",""),
                tag.getStringOr("status","no autonomous supply trip"),tag.getBooleanOr("stone",false),tag.getIntOr("baseline",0),
                tag.getBooleanOr("harvested",false),tag.getBooleanOr("collected",false),tag.getIntOr("workTicks",0),
                tag.getStringOr("dimension",""),position(tag,"resource"),position(tag,"destination"),position(tag,"home"));
    }
    private static BlockPos position(CompoundTag tag,String key){return tag.contains(key)?BlockPos.of(tag.getLongOr(key,0)):null;}
    private static CompoundTag read(PlayerNpcEntity npc){return PersistentData.get(npc).getCompoundOrEmpty(KEY);}
    private static boolean supported(CompoundTag tag){return tag.getIntOr("version",0)==VERSION;}
    private static void write(PlayerNpcEntity npc,CompoundTag tag){PersistentData.get(npc).put(KEY,tag);}
    private static int count(PlayerNpcEntity npc,boolean stone){
        // Goal predicates may already have cached a pre-pickup count this tick.
        // Read actual carried stacks and count an aliased held slot only once.
        Set<ItemStack> seen=Collections.newSetFromMap(new IdentityHashMap<>());int total=0;
        total+=countStack(npc.getMainHandItem(),stone,seen);total+=countStack(npc.getOffhandItem(),stone,seen);
        for(var stack:npc.getInventory().getItems())total+=countStack(stack,stone,seen);
        return total;
    }
    private static int countStack(ItemStack stack,boolean stone,Set<ItemStack> seen){
        if(stack.isEmpty()||!seen.add(stack))return 0;
        boolean matching=stone?(stack.is(Items.COBBLESTONE)||stack.is(Items.COBBLED_DEEPSLATE)||stack.is(Items.BLACKSTONE)
                ||stack.is(Items.STONE)||stack.is(Items.DEEPSLATE)):stack.is(ItemTags.LOGS);
        return matching?stack.getCount():0;
    }
    static boolean collected(int baseline,int current,boolean harvested){return harvested&&current>baseline;}
    static boolean matchesResource(boolean stone,String id){
        return stone?STONE.contains(id):id.startsWith("minecraft:")&&(id.endsWith("_log")||id.endsWith("_wood")||id.endsWith("_stem")||id.endsWith("_hyphae"));
    }
    /** Identical saved identity is required until this owner requests return. */
    record Identity(String dimension,String reason,BlockPos destination,BlockPos home){
        boolean matches(Journeys.State trip){return dimension.equals(trip.dimension)&&reason.equals(trip.reason)
                &&Objects.equals(destination,trip.destination)&&Objects.equals(home,trip.home);}
    }
    private static Identity identity(CompoundTag tag){return new Identity(tag.getStringOr("dimension",""),tag.getStringOr("reason",""),
            BlockPos.of(tag.getLongOr("destination",0)),BlockPos.of(tag.getLongOr("home",0)));}
    private static boolean ownsOutward(CompoundTag tag,Journeys.State trip){return (trip.phase.equals("outbound")||trip.phase.equals("at_destination"))&&identity(tag).matches(trip);}
    record IdleIdentity(String dimension,String reason,String status,BlockPos destination,BlockPos home){
        boolean matches(Journeys.State trip){return trip.phase.equals("idle")&&dimension.equals(trip.dimension)&&reason.equals(trip.reason)
                &&status.equals(trip.status)&&Objects.equals(destination,trip.destination)&&Objects.equals(home,trip.home);}
    }
    private static boolean ownsCollecting(CompoundTag tag,Journeys.State trip){return new IdleIdentity(tag.getStringOr("dimension",""),
            tag.getStringOr("reason",""),tag.getStringOr("idleStatus",""),position(tag,"idleDestination"),position(tag,"idleHome")).matches(trip);}
    private static boolean ownsWork(CompoundTag tag,Journeys.State trip){return tag.getStringOr("stage","").equals("collecting")?ownsCollecting(tag,trip):ownsOutward(tag,trip);}
    private static boolean atWork(CompoundTag tag,Journeys.State trip){return tag.getStringOr("stage","").equals("collecting")?ownsCollecting(tag,trip)
            :trip.phase.equals("at_destination")&&ownsOutward(tag,trip);}
    private static void close(PlayerNpcEntity npc,CompoundTag tag,String status){tag.putBoolean("active",false);tag.putString("status",status);write(npc,tag);}

    @Events.SubscribeEvent public static void harvested(VerifiedWorkOutcome event){
        if(event.kind()!=VerifiedWorkOutcome.Kind.HARVEST)return;
        var npc=event.npc();var tag=read(npc);if(!supported(tag)||!tag.getBooleanOr("active",false)||tag.getStringOr("stage","").equals("returning"))return;
        var trip=Journeys.state(npc);
        if(!atWork(tag,trip)||!tag.getStringOr("dimension","").equals(SurvivalTasks.dimension(npc))
                ||!matchesResource(tag.getBooleanOr("stone",false),event.resourceId())
                ||event.position().distSqr(BlockPos.of(tag.getLongOr("resource",0)))>24*24)return;
        // Block-break evidence means drops existed, not that this NPC picked them up.
        tag.putBoolean("harvested",true);tag.putString("status","native harvest observed; waiting for carried supplies and safe collector recovery");write(npc,tag);
    }
    @Events.SubscribeEvent(priority=Events.EventPriority.HIGH) public static void observe(Events.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof PlayerNpcEntity npc)||!(npc.level() instanceof ServerLevel level))return;
        var tag=read(npc);if(!supported(tag)||!tag.getBooleanOr("active",false))return;
        var trip=Journeys.state(npc);boolean stone=tag.getBooleanOr("stone",false);
        // Native pickup can hand materials to crafting before the next slow
        // deliberation. Latch real carried growth on each active task tick.
        // The bounded carried-slot view bypasses same-tick predicate caches.
        if(!npc.isNoAi()&&!tag.getStringOr("stage","").equals("returning")&&ownsWork(tag,trip)
                &&!tag.getBooleanOr("collected",false)&&collected(tag.getIntOr("baseline",0),count(npc,stone),tag.getBooleanOr("harvested",false))){
            tag.putBoolean("collected",true);write(npc,tag);
        }
        if(npc.tickCount%20!=Math.floorMod(npc.getUUID().hashCode(),20))return;
        if(tag.getStringOr("stage","").equals("returning")){
            boolean identity=RETURN_REASON.equals(trip.reason)&&trip.dimension.equals(tag.getStringOr("dimension",""))
                    &&Objects.equals(trip.destination,BlockPos.of(tag.getLongOr("returnHome",0)))&&Objects.equals(trip.home,trip.destination);
            if(!identity){close(npc,tag,"interrupted: native return was canceled or replaced");return;}
            if(trip.phase.equals("idle")){
                boolean delivered=npc.blockPosition().distSqr(trip.destination)<=9&&tag.getBooleanOr("successful",false)
                        &&tag.getBooleanOr("collected",false)&&tag.getBooleanOr("harvested",false);
                close(npc,tag,delivered?"complete: gathered supply task returned home (materials may be used by native crafting/building)":"returned without completing supply delivery: "+tag.getStringOr("outcome","unknown"));
            }else if(!trip.phase.equals("returning"))close(npc,tag,"blocked/interrupted native return: "+trip.status);
            return;
        }
        if(!ownsWork(tag,trip)||!trip.dimension.equals(SurvivalTasks.dimension(npc))){close(npc,tag,"interrupted: autonomous journey was canceled, blocked or replaced");return;}
        if(!npc.isAlive()){close(npc,tag,"failed: resident died");return;}
        if(npc.isNoAi()||npc.isSleeping()||!PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc))return;
        boolean danger=npc.isOnFire()||npc.isHealing()||npc.getTarget()!=null||npc.getHealth()<=npc.getMaxHealth()*.35F;
        boolean depleted=tag.getIntOr("departureFood",0)>=2&&SurvivalTasks.cookedFoodCount(npc)<2;
        if(danger||depleted){returnHome(npc,tag,false,danger?"danger interrupted supply work":"carried return reserve depleted");return;}
        if(!atWork(tag,trip))return;
        boolean episode=stone?GatherStoneGoal.isStoneGatheringEpisodeActive(npc):GatherLogsGoal.isLogGatheringEpisodeActive(npc);
        boolean acquired=tag.getBooleanOr("collected",false);
        int worked=Math.min(MAX_WORK_TICKS,tag.getIntOr("workTicks",0)+20);tag.putInt("workTicks",worked);
        if(acquired&&!episode){returnHome(npc,tag,true,"personally harvested supplies collected");return;}
        var resource=BlockPos.of(tag.getLongOr("resource",0));
        boolean stale=!tag.getBooleanOr("harvested",false)&&level.hasChunkAt(resource)
                &&!matchesResource(stone,BuiltInRegistries.BLOCK.getKey(level.getBlockState(resource).getBlock()).toString());
        if((stale||worked>=MAX_WORK_TICKS)&&!episode){returnHome(npc,tag,false,stale?"observed resource depleted before collection":"native gathering made no completed supply delivery within active work limit");return;}
        tag.putString("status",acquired?"supplies collected; waiting for native gather recovery":worked>=MAX_WORK_TICKS
                ?"gathering time limit reached; waiting for safe native recovery before return":"at supply site; waiting for real native harvest and pickup");write(npc,tag);
    }
    private static void returnHome(PlayerNpcEntity npc,CompoundTag tag,boolean successful,String outcome){
        Journeys.returnHome(npc,RETURN_REASON);var trip=Journeys.state(npc);
        if(!trip.phase.equals("returning")||!trip.reason.equals(RETURN_REASON)){close(npc,tag,"blocked: native return could not start; "+outcome);return;}
        tag.putString("stage","returning");tag.putLong("returnHome",trip.home.asLong());tag.putBoolean("successful",successful);
        tag.putString("outcome",outcome);tag.putString("status","returning: "+outcome);write(npc,tag);
    }
}

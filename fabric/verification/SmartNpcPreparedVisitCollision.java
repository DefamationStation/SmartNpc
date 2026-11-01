import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.memory.NpcMemories;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Focused real event-dispatch ordering regression, not a journey outcome test.
 * Declared inputs: two BUILDING residents at physically coincident saved-arrival
 * checkpoints; flat supported geometry; owned otherwise-complete starter cabins
 * each missing one plank; food16, axe/pickaxe, no wood; visible logs ten blocks
 * away. Worker admission and observer cadence are explicitly controlled.
 * Positive control MUST let the real supply observer replace its visit. A
 * separate equivalent actor MUST retain its visit through the actual production
 * EntityTick.Post handler chain and begin native return. No annotation inspection,
 * replacement handlers, forged progression or synthetic success outcomes. */
public final class SmartNpcPreparedVisitCollision implements DedicatedServerModInitializer {
    private PlayerNpcEntity control,resident;private boolean controlPassed,done;private int ticks;
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("VisitControl|BUILDING","VisitResident|BUILDING"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        // Production initialization owns registration/order of every real handler.
        ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;try{tick(server);}catch(Throwable error){error.printStackTrace();finish(server,"failure.txt",error.toString());}});
    }
    private void tick(MinecraftServer server)throws Exception{
        ticks++;var level=server.overworld();server.tickRateManager().setTickRate(200);
        if(control==null){
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
            control=create(level,"VisitControl",0);resident=create(level,"VisitResident",32);return;
        }
        var actor=controlPassed?resident:control;actor.setNoAi(false);
        // No movement/goal execution is injected. Align only the observer's
        // documented stagger and admit one controlled worker for this dispatch.
        actor.tickCount=20*ticks+Math.floorMod(actor.getUUID().hashCode(),20);
        PlayerNpcAiWorkBudget.onWorkStarted(actor);
        check(PlayerNpcAiWorkBudget.hasActiveWorkerSlot(actor),"fixture worker not admitted");
        if(!PlayerNpcBuildMaterialUtil.needsLogsForCurrentBuild(level,actor)){
            actor.setNoAi(true);PlayerNpcAiWorkBudget.onWorkStopped(actor);
            check(ticks<400,"declared missing-plank cabin never produced real wood prerequisite");return;
        }
        check(!com.pla.smart_npc.entity.goal.BuildHouseGoal.isHomeLayoutFinished(actor,level),"fixture cabin unexpectedly complete");
        check(NpcMemories.observeResource(actor,new BlockPos(10,280,controlPassed?32:0)),"declared log was not actually visible/observable");
        var trip=Journeys.state(actor);
        check(trip.phase.equals("at_destination")&&trip.reason.equals("prepared visit")&&trip.destination.equals(actor.blockPosition()),"declared visit checkpoint changed before dispatch");
        if(!controlPassed){
            ShelterSupplyTravel.observe(new Events.EntityTickEvent.Post(actor));
            if(!trip.reason.equals("visible wood for shelter supplies")){
                actor.setNoAi(true);PlayerNpcAiWorkBudget.onWorkStopped(actor);
                check(ticks<400,"positive control did not exercise actual supply takeover");return;
            }
            check(trip.phase.equals("outbound")&&!trip.destination.equals(actor.blockPosition()),"positive control did not create real supply commitment");
            controlPassed=true;actor.setNoAi(true);PlayerNpcAiWorkBudget.onWorkStopped(actor);
            System.out.println("VISIT_COLLISION_CONTROL nativeSupplyTakeover=true destination="+trip.destination);return;
        }
        // This is the real registered production observer chain. With NORMAL
        // visit priority the earlier supply observer hijacks this exact actor.
        Events.post(new Events.EntityTickEvent.Post(actor));
        check(trip.phase.equals("returning")&&trip.reason.equals("completed prepared visit"),"production arrival dispatch lost explicit visit to "+trip.phase+": "+trip.reason);
        check(trip.lastArrival.equals(actor.blockPosition()),"native return lost physical arrival evidence");
        check(actor.getInventory().countItem(Items.COOKED_BEEF)==16&&actor.getInventory().countItem(Items.OAK_LOG)==0,"arrival dispatch changed declared supplies");
        actor.setNoAi(true);PlayerNpcAiWorkBudget.onWorkStopped(actor);
        finish(server,"complete.txt","PASS real missing-wood builder supply takeover positive control; separate matched prepared arrival dispatched through production observers returned before supply takeover; declared checkpoints/geometry/admission only, no walking/reload claim");
    }
    private PlayerNpcEntity create(ServerLevel level,String name,int z){
        var layout=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();
        var base=new BlockPos(-20,279,z-2);
        for(int x=-24;x<=14;x++)for(int dz=z-7;dz<=z+9;dz++)for(int y=279;y<=286;y++)level.setBlockAndUpdate(new BlockPos(x,y,dz),y==279?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
        var missing=layout.blocks().stream().filter(b->b.state().is(BlockTags.PLANKS)).findFirst().orElseThrow();
        for(var block:layout.blocks())level.setBlockAndUpdate(block.toWorld(base),block==missing?Blocks.AIR.defaultBlockState():block.state());
        level.setBlockAndUpdate(new BlockPos(10,280,z),Blocks.OAK_LOG.defaultBlockState());
        var npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername(name);npc.setUUID(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));npc.setPersistenceRequired();npc.setPos(.5,280,z+.5);npc.setNoAi(true);
        npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(base,layout.width(),layout.depth()));PlayerNpcHomeUtil.setHomeLayoutId(npc,layout.id());level.addFreshEntity(npc);
        var trip=Journeys.state(npc);trip.dimension=SurvivalTasks.dimension(npc);trip.home=base;trip.destination=npc.blockPosition();trip.lastArrival=trip.destination;trip.phase="at_destination";trip.reason="prepared visit";trip.trail.observe(Journeys.point(base));trip.trail.observe(Journeys.point(trip.destination));Journeys.save(npc);
        return npc;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception error){throw new RuntimeException(error);}server.halt(false);}
}

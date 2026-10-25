import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Controlled native consumer admission, then ordinary-selector travel; not a wilderness-start claim. */
public class SmartNpcMemorySupplyScenario implements DedicatedServerModInitializer {
    final UUID id=new UUID(5,5);final BlockPos resource=new BlockPos(9,260,0);
    PlayerNpcEntity npc;boolean done,setup;int ticks;double startX,maxX;long admitted;
    public void onInitializeServer(){
        NpcMemories.register();MemorySettings.MEMORY_ENABLED.set(true);MemorySettings.PERSONALITY_ENABLED.set(true);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("MemorySupplier|BUILDING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread thread=Thread.currentThread();new Thread(()->{try{thread.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable failure){failure.printStackTrace();finish(s,"failure.txt",failure.toString());}});
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;var level=server.overworld();server.tickRateManager().setTickRate(100);
        if(!setup){
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){
                level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());
                for(int y=260;y<=264;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("MemorySupplier");npc.setUUID(id);npc.setPersistenceRequired();npc.setPos(.5,260,.5);
            npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
            npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));level.addFreshEntity(npc);
            check(npc.tickCount==0&&Math.floorMod(id.hashCode(),20)==0,"test admission phase changed");
            check(npc.shouldPrioritizeLogGathering(),"builder has no native wood need");
            level.setBlockAndUpdate(resource,Blocks.OAK_LOG.defaultBlockState());
            check(NpcMemories.observeResource(npc,resource),"real visible log observation rejected");
            // Claim a legitimate existing scheduler slot before invoking the normal consumer,
            // rather than removing goals or loosening its worker/food/visibility checks.
            PlayerNpcAiWorkBudget.onWorkStarted(npc);check(PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc),"routine slot not granted");
            var state=Journeys.state(npc);state.lastArrival=new BlockPos(-100,260,0);state.blockedPosition=new BlockPos(-101,260,0);state.cursor=42;
            ShelterSupplyTravel.observe(new Events.EntityTickEvent.Post(npc));
            check(state.phase.equals("outbound")&&state.destination!=null,"native memory consumer did not admit supply trip");
            check(state.destination.distSqr(resource)<=2&&state.reason.contains("wood"),"selected something other than actual remembered wood");
            check(state.lastArrival==null&&state.blockedPosition==null&&state.cursor==-1,"fresh supply trip retained stale lifecycle state");
            check(npc.getInventory().countItem(Items.COOKED_BEEF)==16&&npc.getInventory().countItem(Items.OAK_LOG)==0,"admission granted or consumed supplies");
            check(goals()==80,"native goals removed");admitted=level.getGameTime();startX=npc.getX();maxX=startX;setup=true;
            Files.writeString(Path.of("admission.txt"),"Native ShelterSupplyTravel selected real revalidated remembered oak at "+resource+" through ordinary budget and physical admission; original80goals retained");
        }
        if(setup)maxX=Math.max(maxX,npc.getX());
        if(setup&&level.getGameTime()-admitted>=400){
            check(goals()==80,"ordinary goals changed during travel");
            check(maxX>startX+2,"native trip did not physically leave toward remembered supply");
            finish(server,"complete.txt","PASS controlled native supply-memory choice and lifecycle reset; all80normal goals then make real physical progress toward observed wood, without teleporting actor or granting items");
        }
        if(ticks>1000)throw new AssertionError("supply deadline");
    }
    int goals()throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((net.minecraft.world.entity.ai.goal.GoalSelector)f.get(npc)).getAvailableGoals().size();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void finish(MinecraftServer server,String name,String message){try{Files.writeString(Path.of(name),message);}catch(Exception e){throw new RuntimeException(e);}done=true;server.halt(false);}
}
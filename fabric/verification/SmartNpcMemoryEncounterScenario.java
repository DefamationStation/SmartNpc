import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Actual first successful native damage and save/reload, with all ordinary NPC goals retained. */
public class SmartNpcMemoryEncounterScenario implements DedicatedServerModInitializer {
    final boolean replay=Boolean.getBoolean("journey.replay");
    final UUID victimId=new UUID(123,1),offenderId=new UUID(123,2),bystanderId=new UUID(123,3);
    PlayerNpcEntity victim,offender,bystander;int ticks;boolean done,setup;
    public void onInitializeServer(){
        NpcMemories.register();MemorySettings.MEMORY_ENABLED.set(true);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("MemoryVictim|EXPLORING|CAUTIOUS","MemoryOffender|EXPLORING|CAUTIOUS","MemoryWitness|EXPLORING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread thread=Thread.currentThread();new Thread(()->{try{thread.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable failure){failure.printStackTrace();finish(s,"failure.txt",failure.toString());}});
    }
    PlayerNpcEntity spawn(MinecraftServer server,String name,UUID id,int x){
        var level=server.overworld();var n=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
        n.setUsername(name);n.setUUID(id);n.setPersistenceRequired();n.setPos(x+.5,260,.5);
        n.getInventory().clearContent();for(var slot:EquipmentSlot.values())n.setItemSlot(slot,ItemStack.EMPTY);
        n.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));level.addFreshEntity(n);return n;
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;var level=server.overworld();server.tickRateManager().setTickRate(100);
        if(!setup){
            if(replay){victim=(PlayerNpcEntity)level.getEntity(victimId);if(victim==null){if(ticks>1000)throw new AssertionError("saved victim missing");return;}setup=true;}
            else{
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty normal");
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                    level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());
                    for(int y=260;y<=263;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                victim=spawn(server,"MemoryVictim",victimId,0);offender=spawn(server,"MemoryOffender",offenderId,2);bystander=spawn(server,"MemoryWitness",bystanderId,1);
                check(!NpcMemories.encounterConfirmed(victim,bystander),"peaceful bystander accepted as offender");
                check(NpcMemories.observations(victim).stream().noneMatch(e->e.kind()==ObservedMemory.Kind.HOSTILE),"proximity invented hostility");
                float before=victim.getHealth();check(victim.hurtServer(level,level.damageSources().mobAttack(offender),2),"first native damage rejected");
                check(victim.getHealth()<before,"native damage did not change health");verify();setup=true;
            }
        }
        if(setup&&ticks>=100){verify();NpcMemories.save(victim);finish(server,replay?"complete.txt":"checkpoint.txt",replay?"PASS first real native hurt is remembered after process replay for exact committed offender; peaceful bystander remains unblamed":"Saved first real native hurt with exact canonical offender; no proximity accusation");}
        if(ticks>1200)throw new AssertionError("encounter deadline");
    }
    void verify()throws Exception{
        var selector=Mob.class.getDeclaredField("goalSelector");selector.setAccessible(true);
        check(((net.minecraft.world.entity.ai.goal.GoalSelector)selector.get(victim)).getAvailableGoals().size()==80,"normal victim goals changed");
        var hostile=NpcMemories.observations(victim).stream().filter(e->e.kind()==ObservedMemory.Kind.HOSTILE).toList();
        check(hostile.stream().anyMatch(e->offenderId.equals(e.subject())&&e.detail().equals("ASSAULT")&&e.source()==ObservedMemory.Source.CANONICAL_DEFENCE),"first successful native assault lacks exact committed memory");
        check(hostile.stream().noneMatch(e->bystanderId.equals(e.subject())),"nearest peaceful NPC blamed");
    }
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void finish(MinecraftServer server,String name,String message){try{Files.writeString(Path.of(name),message);}catch(Exception e){throw new RuntimeException(e);}done=true;server.halt(false);}
}
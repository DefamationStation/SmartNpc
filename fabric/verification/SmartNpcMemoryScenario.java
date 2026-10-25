import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.VerifiedWorkOutcome;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import com.pla.smart_npc.config.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Controlled memory/real-handoff persistence test, not wilderness-survival proof. */
public class SmartNpcMemoryScenario implements DedicatedServerModInitializer {
    final boolean replay=Boolean.getBoolean("journey.replay");boolean done;int ticks,stage;
    PlayerNpcEntity a,b;UUID aId,bId;Properties ledger=new Properties();
    final BlockPos visible=new BlockPos(3,260,0),hidden=new BlockPos(3,257,0);
    public void onInitializeServer(){
        NpcMemories.register();MemorySettings.MEMORY_ENABLED.set(true);MemorySettings.PERSONALITY_ENABLED.set(true);
        MemorySettings.EXTRA_DEPARTURE_FOOD.set(2);MemorySettings.CAUTION.set(-1);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("MemoryA|EXPLORING|CAUTIOUS","MemoryB|EXPLORING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread thread=Thread.currentThread();new Thread(()->{try{thread.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable failure){failure.printStackTrace();finish(s,"failure.txt",failure.toString());}});
    }
    UUID identity(boolean cautious){
        for(int i=0;i<10000;i++){var id=new UUID(0x6d656d6f7279L,i);int caution=NpcPersonality.seeded(id).caution()+20;
            if(cautious?caution>=80:caution<60)return id;}
        throw new AssertionError("No deterministic test identity");
    }
    PlayerNpcEntity spawn(MinecraftServer server,String name,UUID id,int x)throws Exception{
        var level=server.overworld();var n=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
        n.setUsername(name);n.setUUID(id);n.setPersistenceRequired();n.setPos(x+.5,260,.5);n.getRandom().setSeed(id.getLeastSignificantBits());
        n.getInventory().clearContent();for(var slot:EquipmentSlot.values())n.setItemSlot(slot,ItemStack.EMPTY);
        n.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));n.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_PICKAXE));
        PlayerNpcHomeUtil.setHome(n,new PlayerNpcHomeUtil.HomeArea(new BlockPos(x-2,259,-2),5,5));
        level.addFreshEntity(n);check(n.hasInterest(com.pla.smart_npc.clazz.PlayerNpcInterest.CAUTIOUS),"test interest missing");return n;
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;var level=server.overworld();server.tickRateManager().setTickRate(Integer.getInteger("journey.tickRate",100));
        if(stage==0){
            if(replay){try(var in=Files.newInputStream(Path.of("actor.properties"))){ledger.load(in);}aId=UUID.fromString(ledger.getProperty("a"));bId=UUID.fromString(ledger.getProperty("b"));stage=1;}
            else{
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
                    level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());
                    for(int y=260;y<=264;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(visible,Blocks.COAL_ORE.defaultBlockState());level.setBlockAndUpdate(hidden,Blocks.IRON_ORE.defaultBlockState());
                aId=identity(true);bId=identity(false);a=spawn(server,"MemoryA",aId,0);b=spawn(server,"MemoryB",bId,-5);
                check(goals(a)==80&&goals(b)==80,"normal goals removed");check(NpcMemories.departureFoodReserve(a)==6,"cautious real reserve");
                check(NpcMemories.departureFoodReserve(b)==4,"less cautious real reserve");
                check(NpcMemories.observeResource(a,visible),"visible actual resource not observed");check(!NpcMemories.observeResource(a,hidden),"hidden ore became known");
                var evidence=NpcMemories.knownResources(a,id->id.equals("minecraft:coal_ore"));check(evidence.size()==1,"visible coal evidence");
                check(!NpcMemories.observeResource(a,new BlockPos(100000,64,100000)),"remote observation");
                var old=level.getBlockState(visible);check(PlayerNpcBlockBreakUtil.destroyBlock(level,visible,old,a),"native harvest failed");
                // The fixture bridges the same verified successful transaction as the production producer.
                VerifiedWorkOutcome.emit(a,VerifiedWorkOutcome.Kind.HARVEST,visible,"minecraft:coal_ore",1);
                check(NpcMemories.revalidateResource(a,evidence.getFirst())==NpcMemories.Validation.EXHAUSTED,"removed resource did not invalidate");
                check(NpcMemories.knownResources(a,id->id.equals("minecraft:coal_ore")).isEmpty(),"exhausted ore still a destination");
                int totalBefore=a.getInventory().countItem(Items.COOKED_BEEF)+b.getInventory().countItem(Items.COOKED_BEEF);
                int source=0;while(source<b.getInventory().getContainerSize()&&!b.getInventory().getItem(source).is(Items.COOKED_BEEF))source++;
                check(source<b.getInventory().getContainerSize(),"handoff source missing");var handed=b.getInventory().removeItem(source,1);
                check(!handed.isEmpty(),"handoff lacks real source");check(a.getInventory().addItem(handed).isEmpty(),"handoff not accepted");
                check(a.getInventory().countItem(Items.COOKED_BEEF)==17&&b.getInventory().countItem(Items.COOKED_BEEF)==15,"handoff counts");
                check(totalBefore==a.getInventory().countItem(Items.COOKED_BEEF)+b.getInventory().countItem(Items.COOKED_BEEF),"handoff duplicated food");
                NpcMemories.helpReceived(a,bId,"one cooked beef handed over");
                ledger.setProperty("a",aId.toString());ledger.setProperty("b",bId.toString());
                ledger.setProperty("aProfile",NpcMemories.personality(a).toString());ledger.setProperty("bProfile",NpcMemories.personality(b).toString());
                try(var out=Files.newOutputStream(Path.of("actor.properties"))){ledger.store(out,"controlled memory actor identities");}
                System.out.println("MEMORY_SETUP a="+aId+" b="+bId+" aReserve=6 bReserve=4 goals=80 targets="+targets(a));stage=2;
            }
        }
        if(stage==1){a=(PlayerNpcEntity)level.getEntity(aId);b=(PlayerNpcEntity)level.getEntity(bId);if(a==null||b==null){if(ticks>2000)throw new AssertionError("saved actors absent");return;}
            check(ledger.getProperty("aProfile").equals(NpcMemories.personality(a).toString()),"personality changed on reload");
            check(ledger.getProperty("bProfile").equals(NpcMemories.personality(b).toString()),"other personality changed on reload");
            check(NpcMemories.departureFoodReserve(a)==6&&NpcMemories.departureFoodReserve(b)==4,"individual preparation changed on reload");stage=2;
        }
        if(stage==2&&ticks>=200){
            check(goals(a)==80&&targets(a)==3&&goals(b)==80&&targets(b)==3,"selectors changed");
            var facts=NpcMemories.observations(a);check(facts.size()<=ObservedMemory.LIMIT,"unbounded memory");
            check(facts.stream().anyMatch(e->e.kind()==ObservedMemory.Kind.WORK_SITE&&e.position().equals(NpcMemories.point(visible))&&e.detail().equals("harvest:minecraft:coal_ore")),"actual work site missing");
            check(facts.stream().anyMatch(e->e.kind()==ObservedMemory.Kind.HELP&&bId.equals(e.subject())&&e.source()==ObservedMemory.Source.ACTUAL_HANDOFF),"attributed real handoff missing");
            check(facts.stream().noneMatch(e->e.kind()==ObservedMemory.Kind.RESOURCE&&e.position().equals(NpcMemories.point(hidden))),"hidden resource learned");
            check(facts.stream().noneMatch(e->e.kind()==ObservedMemory.Kind.RESOURCE&&e.position().equals(NpcMemories.point(visible))),"harvested ore claimed present");
            check(a.getInventory().countItem(Items.COOKED_BEEF)+b.getInventory().countItem(Items.COOKED_BEEF)==32,"food changed/duplicated in peaceful fixture");
            NpcMemories.save(a);NpcMemories.save(b);
            finish(server,replay?"complete.txt":"checkpoint.txt",replay?"PASS original actors retain distinct preparation profiles, actual harvested-site and attributed real-handoff memories after process reload; hidden/exhausted ore absent; all selectors and food conserved":"Saved original actors with distinct preparation profiles and verified observed evidence");
        }
        if(ticks>2500)throw new AssertionError("memory fixture deadline");
    }
    int goals(PlayerNpcEntity n)throws Exception{return selector(n,"goalSelector").getAvailableGoals().size();}
    int targets(PlayerNpcEntity n)throws Exception{return selector(n,"targetSelector").getAvailableGoals().size();}
    GoalSelector selector(PlayerNpcEntity n,String name)throws Exception{var f=Mob.class.getDeclaredField(name);f.setAccessible(true);return (GoalSelector)f.get(n);}
    void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    void finish(MinecraftServer server,String name,String message){try{Files.writeString(Path.of(name),message);}catch(Exception e){throw new RuntimeException(e);}done=true;server.halt(false);}
}

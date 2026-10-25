import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.FurnaceAi;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import java.nio.file.*;
import java.util.*;

/** All normal selectors: reuse one earned idle furnace, conserving unused fuel. */
public final class SmartNpcFurnaceRelocation implements DedicatedServerModInitializer {
    PlayerNpcEntity npc;int ticks,goals,targets;boolean done;BlockPos old=new BlockPos(8,261,8);
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("FurnaceMover|BUILDING"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread t=Thread.currentThread();new Thread(()->{try{t.join();System.exit(0);}catch(InterruptedException e){Thread.currentThread().interrupt();}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable e){e.printStackTrace();finish(s,"failure.txt",e.toString());}});
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(200);var level=server.overworld();
        if(npc==null){
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1000");
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,260,z),Blocks.BEDROCK.defaultBlockState());}
            var layout=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();var base=new BlockPos(0,260,0);
            for(var b:layout.blocks())level.setBlockAndUpdate(b.toWorld(base),b.state().is(Blocks.FURNACE)?Blocks.AIR.defaultBlockState():b.state());
            level.setBlockAndUpdate(old,Blocks.FURNACE.defaultBlockState());var stale=(FurnaceBlockEntity)level.getBlockEntity(old);stale.setItem(1,new ItemStack(Items.COAL,3));
            npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("FurnaceMover");npc.setPersistenceRequired();npc.setPos(1.5,261,1.5);npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
            npc.setUUID(UUID.nameUUIDFromBytes("furnace-relocation".getBytes(java.nio.charset.StandardCharsets.UTF_8)));npc.getRandom().setSeed(0);
            npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));
            npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
            PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(base,layout.width(),layout.depth()),layout.id());
            var data=PersistentData.get(npc);data.putInt(FurnaceAi.TEMP_FURNACE_X,old.getX());data.putInt(FurnaceAi.TEMP_FURNACE_Y,old.getY());data.putInt(FurnaceAi.TEMP_FURNACE_Z,old.getZ());data.putString(FurnaceAi.TEMP_FURNACE_KIND,FurnaceAi.TEMP_FURNACE_KIND_COOKING);
            level.addFreshEntity(npc);goals=count("goalSelector");targets=count("targetSelector");System.out.println("STATION_SETUP goals="+goals+" targets="+targets+" idle owned camp furnace; blueprint furnace missing; no eight spare stones");
        }
        check(npc.isAlive(),"resident died");check(count("goalSelector")==goals&&count("targetSelector")==targets,"normal selectors changed");
        var home=PlayerNpcHomeUtil.getHome(npc).orElseThrow();var layout=PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout).orElseThrow();
        int missing=PlayerNpcBuildLayoutLoader.countMissingRequired(level,layout,home.origin());
        if(missing==0&&level.getBlockState(old).isAir()){
            int fuel=npc.getInventory().countItem(Items.COAL);
            for(var block:layout.blocks())if(level.getBlockEntity(block.toWorld(home.origin())) instanceof net.minecraft.world.Container container)fuel+=container.countItem(Items.COAL);
            check(fuel==3,"unused fuel lost or duplicated: "+fuel);check(npc.getInventory().countItem(Items.FURNACE)==0,"extra carried furnace after relocation");
            finish(server,"complete.txt","PASS real owned camp furnace moved into its blueprint slot; all three unused coal conserved; all 73 blocks complete; goals="+goals+" targets="+targets+" ticks="+ticks);return;
        }
        if(ticks>6000)throw new AssertionError("Idle furnace not reused: missing="+missing+" ai="+npc.getCurrentAiState()+" "+npc.getCurrentAiDetail()+" inventory="+npc.getInventory().getItems());

    }
    int count(String name)throws Exception{var f=Mob.class.getDeclaredField(name);f.setAccessible(true);return ((GoalSelector)f.get(npc)).getAvailableGoals().size();}
    void check(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    void finish(MinecraftServer s,String name,String text){done=true;try{Files.writeString(Path.of(name),text);}catch(Exception e){throw new RuntimeException(e);}s.halt(false);}
}

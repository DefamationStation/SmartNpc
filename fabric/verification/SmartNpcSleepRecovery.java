import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import java.nio.file.*;
import java.util.*;

/** Full ordinary selector: a real bed behind an impassable enclosure must not monopolize MOVE. */
public final class SmartNpcSleepRecovery implements ClientModInitializer {
    int stage,ticks,blockedTicks,maxBlockedTicks; volatile boolean done; volatile Throwable failure;
    PlayerNpcEntity npc; GoalSelector selector; ServerLevel level; boolean sawAttempt,sawRelease;
    public void onInitializeClient(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("SleepResident|EXPLORING"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ClientTickEvents.END_CLIENT_TICK.register(this::client);
        ServerTickEvents.END_SERVER_TICK.register(s->{if(stage!=3||npc==null||done||failure!=null)return;try{
            ticks++;if(!npc.isAlive())throw new AssertionError("Bed fixture resident died");boolean running=selector.getAvailableGoals().stream().anyMatch(w->w.isRunning()&&unwrap(w.getGoal()) instanceof SleepAtHomeGoal);
            if(running&&!npc.isSleeping()){sawAttempt=true;blockedTicks++;maxBlockedTicks=Math.max(blockedTicks,maxBlockedTicks);}else{if(sawAttempt&&blockedTicks>0)sawRelease=true;blockedTicks=0;}
            if(npc.isSleeping()&&ticks<2400)throw new AssertionError("NPC slept through an impassable enclosure");
            if(ticks%200==0)System.out.println("SLEEP_RECOVERY ticks="+ticks+" running="+running+" consecutive="+blockedTicks+" sawRelease="+sawRelease+" pos="+npc.position()+" ai="+npc.getCurrentAiState()+" detail="+npc.getCurrentAiDetail()+" idle="+npc.getIdleTraceDetail());
            if(blockedTicks>800)throw new AssertionError("Unreachable bed monopolizes movement for over 800 ticks");
            if(ticks==2400){if(!sawRelease&&!npc.getIdleTraceDetail().contains("bed"))throw new AssertionError("No observed bounded bed rejection/release");for(int y=260;y<=261;y++)level.setBlockAndUpdate(new BlockPos(6,y,0),Blocks.AIR.defaultBlockState());System.out.println("SLEEP_RECOVERY opened real enclosure entrance");}
            if(ticks>2400&&npc.isSleeping())done=true;
            if(ticks>=4400&&!done)throw new AssertionError("Usable bed did not resume after entrance reopened");
        }catch(Throwable t){failure=t;}});
    }
    static Goal unwrap(Goal g){while(g instanceof StartupWorkGatedGoal w)g=w.getDelegateGoal();return g;}
    void setup(ServerLevel world)throws Exception{
        level=world;var p=level.getServer().getPlayerList().getPlayers().getFirst();var base=new BlockPos(0,260,0);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){level.getChunk(base.offset(x,0,z));level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.BEDROCK.defaultBlockState());for(int y=0;y<4;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());}
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)if(Math.abs(x)==12||Math.abs(z)==12)for(int y=0;y<3;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.GLASS.defaultBlockState());
        var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST).setValue(BedBlock.PART,BedPart.FOOT);level.setBlockAndUpdate(base,bed);level.setBlockAndUpdate(base.east(),bed.setValue(BedBlock.PART,BedPart.HEAD));
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(Math.abs(x)==6||Math.abs(z)==6)for(int y=0;y<3;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.GLASS.defaultBlockState());
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("SleepResident");npc.setPersistenceRequired();npc.setPos(9.5,260,.5);npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,8));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(base,2,1));level.addFreshEntity(npc);
        var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);selector=(GoalSelector)f.get(npc);
        p.teleportTo(10,260,10);level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),"time set 18000");level.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),"difficulty peaceful");level.getServer().tickRateManager().setTickRate(200);
    }
    void client(Minecraft mc){try{
        if(failure!=null)throw new RuntimeException(failure);
        if(stage==0&&mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
        if(stage==1&&mc.gui.screen() instanceof CreateWorldScreen screen){stage=2;var ui=screen.getUiState();ui.setName("SmartNpcSleepRecovery");ui.setSeed("20261024005");ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);ui.setAllowCommands(true);var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}
        if(stage==2&&mc.player!=null&&mc.getSingleplayerServer()!=null){stage=3;mc.getSingleplayerServer().execute(()->{try{setup(mc.getSingleplayerServer().overworld());}catch(Throwable t){failure=t;}});}
        if(done){stage=4;Files.writeString(mc.gameDirectory.toPath().resolve("complete.txt"),"PASS full-selector blocked-bed movement released; maxBlockedTicks="+maxBlockedTicks);mc.stop();}
    }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}}
}

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.level.block.Blocks;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.init.SmartNpcModEntities;
import java.nio.file.*;
import java.util.*;

/** Focused contract check; complete real-terrain survival is a separate gate. */
public final class SmartNpcLogTimeoutRegression implements DedicatedServerModInitializer {
    boolean done;
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("LogResident|BUILDING"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread t=Thread.currentThread();new Thread(()->{try{t.join();System.exit(0);}catch(InterruptedException e){Thread.currentThread().interrupt();}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{check(s);}catch(Throwable t){t.printStackTrace();finish(s,"failure.txt",t.toString());}});
    }
    void check(MinecraftServer server)throws Exception{
        var l=server.overworld();l.getChunk(0,0);BlockPos feet=new BlockPos(8,261,8),log=feet.above(5);l.setBlockAndUpdate(feet.below(),Blocks.BEDROCK.defaultBlockState());l.setBlockAndUpdate(log,Blocks.OAK_LOG.defaultBlockState());
        var n=SmartNpcModEntities.PLAYER_NPC.get().create(l,EntitySpawnReason.COMMAND);n.setUsername("LogResident");n.setPos(8.5,261,8.5);l.addFreshEntity(n);
        var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);var selector=(GoalSelector)field.get(n);
        var goal=(GatherLogsGoal)selector.getAvailableGoals().stream().map(w->{var g=w.getGoal();while(g instanceof com.pla.smart_npc.entity.goal.StartupWorkGatedGoal q)g=q.getDelegateGoal();while(g instanceof com.pla.smart_npc.entity.goal.InterestGatedGoal q)g=q.getDelegateGoal();return g;}).filter(g->g instanceof GatherLogsGoal).findFirst().orElseThrow();
        set(goal,"targetPos",log);set(goal,"standPos",feet);set(goal,"gatherTicks",600);set(goal,"pillarRouteAuthorized",true);
        var authorized=GatherLogsGoal.class.getDeclaredMethod("hasAuthorizedPillarPlanForTarget");authorized.setAccessible(true);if(!(Boolean)authorized.invoke(goal))throw new AssertionError("fixture did not create an authorized pillar approach");
        goal.stop();var ignored=GatherLogsGoal.class.getDeclaredField("ignoredLogTargets");ignored.setAccessible(true);
        if(!((Set<?>)ignored.get(goal)).contains(log))throw new AssertionError("Timed-out grounded pillar plan reselects the same unreachable log");
        finish(server,"complete.txt","PASS timed-out authorized ground approach uses native bounded ignored-log list; no selector removed; focused contract only");
    }
    void set(Object object,String name,Object value)throws Exception{var f=GatherLogsGoal.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception e){throw new RuntimeException(e);}server.halt(false);}
}

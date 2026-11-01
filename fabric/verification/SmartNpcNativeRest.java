import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.fabric.survival.progression.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import java.nio.file.*;
import java.util.*;

/** Real ordinary-selector owned-bed nap, deliberately completed before dawn.
 * First process stops during actual sleep; replay must retain that observation
 * and finish naturally. No direct goal invocation or synthetic work outcomes.
 * Prepared home/bed/food are declared fixture supplies, not earned cooking.
 * Optional -Drest.interrupt=true breaks the occupied bed after 120 observed
 * ticks and verifies that native interruption earns no rest milestone. */
public final class SmartNpcNativeRest implements DedicatedServerModInitializer {
    private static final UUID ID=UUID.nameUUIDFromBytes("ordinary-native-rest".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final BlockPos FOOT=new BlockPos(1,280,0),HEAD=FOOT.east();
    private final boolean replay=Boolean.getBoolean("journey.replay"),interrupt=Boolean.getBoolean("rest.interrupt");
    private PlayerNpcEntity npc;private int ticks,brokenAt=-1;private boolean done;
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("RestResident|EXPLORING"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;try{tick(server);}catch(Throwable error){error.printStackTrace();finish(server,"failure.txt",error.toString());}});
    }
    private void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(200);var level=server.overworld();
        if(npc==null){
            if(!replay){setup(server,level);return;}
            npc=(PlayerNpcEntity)level.getEntity(ID);if(npc==null){check(ticks<2000,"saved original actor absent");return;}
            check(npc.isSleeping(),"native sleeping pose lost during real process restart");
            check(observed()>=120,"actual sleeping observation lost during restart");
            check(!earned(),"sleep checkpoint already granted completion");
            System.out.println("NATIVE_REST_RESUMED uuid="+ID+" observed="+observed());
        }
        check(npc.isAlive()&&goalCount()==80,"actor or full normal selector changed");
        check(level.isDarkOutside(),"before-dawn fixture advanced into daylight");
        check(npc.getInventory().countItem(Items.COOKED_BEEF)==16,"declared food changed unexpectedly");
        if(interrupt){
            if(brokenAt<0&&observed()>=120){
                check(npc.isSleeping()&&!earned(),"interruption did not start from uncredited real sleep");
                level.setBlockAndUpdate(HEAD,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(FOOT,Blocks.AIR.defaultBlockState());brokenAt=ticks;
            }
            if(brokenAt>=0){
                check(!earned(),"interrupted owned-bed sleep granted rest");
                if(ticks-brokenAt>100){check(!npc.isSleeping(),"broken bed retained sleeping actor");finish(server,"complete.txt","PASS ordinary native occupied-bed interruption earned no rest; all80goals enabled");return;}
            }
        }else{
            if(!replay&&observed()>=120){
                check(npc.isSleeping()&&!earned(),"checkpoint not during uncompleted native sleep");
                finish(server,"checkpoint.txt","Saved original ordinary-selector sleeping resident after120 actual sleep ticks; no earned milestone yet");return;
            }
            if(earned()){
                check(!npc.isSleeping(),"completion emitted before native stop woke resident");
                finish(server,"complete.txt","PASS original resident completed ordinary safe owned-bed nap BEFORE DAWN after real process restart; observed ticks persisted; all80goals and food16 retained; no synthetic completion");return;
            }
        }
        if(ticks%200==0)System.out.println("NATIVE_REST_PROGRESS ticks="+ticks+" sleeping="+npc.isSleeping()+" observed="+observed());
        check(ticks<6000,"ordinary selector did not complete native rest");
    }
    private void setup(MinecraftServer server,ServerLevel level){
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 14000");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
        for(int x=-4;x<=6;x++)for(int z=-4;z<=4;z++)for(int y=279;y<=283;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),y==279?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
        var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("red_bed")).defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        level.setBlockAndUpdate(FOOT,bed.setValue(BedBlock.PART,BedPart.FOOT));
        level.setBlockAndUpdate(HEAD,bed.setValue(BedBlock.PART,BedPart.HEAD));
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("RestResident");npc.setUUID(ID);npc.setPersistenceRequired();npc.setPos(.5,280,.5);npc.getRandom().setSeed(0);
        npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(new BlockPos(-2,279,-2),7,5));
        level.addFreshEntity(npc);check(!earned(),"fresh resident inherited rest history");
    }
    private int observed(){return PersistentData.get(npc).getCompoundOrEmpty("SurvivalPersonalProgress").getIntOr("sleepObservedTicks",0);}
    private boolean earned(){return NpcProgression.snapshot(npc).milestones().contains(PersonalProgress.Milestone.SAFE_REST);}
    private int goalCount()throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((GoalSelector)f.get(npc)).getAvailableGoals().size();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception error){throw new RuntimeException(error);}server.halt(false);}
}

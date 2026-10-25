import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.pla.smart_npc.config.SmartNpcConfig;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.Journeys;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Controlled unavailable-home negative, not a naturalistic survival success.
 * All normal selectors run. Bedrock makes the declared high home unreachable;
 * the resident must report a bounded failure and retain it across real reload.
 * A declared physical stair/bridge change then permits explicit retry; the
 * actor must climb it and survive another process restart without relocation. */
public final class SmartNpcUnavailableHomeRegression implements DedicatedServerModInitializer {
    private static final UUID ID=UUID.nameUUIDFromBytes("smartnpc-unavailable-home".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final boolean replay=Boolean.getBoolean("journey.replay");
    private PlayerNpcEntity npc;
    private int ticks;
    private boolean boatUsed,done,reopened,changedCheckpointSaved;
    private String finishedMarker;

    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("HomeGuard|EXPLORING"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread server=Thread.currentThread();new Thread(()->{
            try{server.join();var f=Journeys.class.getDeclaredField("STATES");f.setAccessible(true);
                check(((java.util.Map<?,?>)f.get(null)).isEmpty(),"stopped journey cache retained actors");
                Files.writeString(Path.of("cache-cleared-"+finishedMarker),"PASS stopped-server journey cache empty");System.exit(0);
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("failure.txt"),error.toString());}catch(Exception ignored){}System.exit(1);}
        },"Unavailable-home shutdown").start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable error){error.printStackTrace();finish(s,"failure.txt",error.toString());}});
    }

    private void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(Integer.getInteger("journey.tickRate",200));var level=server.overworld();
        if(npc==null){
            if(replay){npc=(PlayerNpcEntity)level.getEntity(ID);if(npc==null){check(ticks<2000,"saved actor absent");return;}
                var saved=new Properties();try(var in=Files.newInputStream(Path.of("actor.properties"))){saved.load(in);}boatUsed=Boolean.parseBoolean(saved.getProperty("boatUsed","false"));
                reopened=Boolean.parseBoolean(saved.getProperty("reopened","false"));changedCheckpointSaved=Boolean.parseBoolean(saved.getProperty("returnCheckpoint","false"));
                check(reopened?Journeys.state(npc).active():Journeys.state(npc).phase.equals("blocked"),"saved blocked/changed-route phase lost");
                System.out.println("UNAVAILABLE_RESUMED uuid="+npc.getUUID()+" phase="+Journeys.state(npc).phase);
            }else{setup(server,level);return;}
        }
        check(npc.isAlive(),"resident died");check(goalCount(npc)==80,"ordinary selector changed");
        var state=Journeys.state(npc);boatUsed|=state.boatId!=null;
        if(!reopened)check(npc.blockPosition().distSqr(state.home)>9,"unreachable home reached without real supported route");
        if(reopened){
            check(!state.phase.equals("blocked"),"physical changed route still blocked: "+state.status);
            if(state.phase.equals("idle")&&npc.blockPosition().distSqr(state.home)<=9){
                checkStock(level);check(state.lastArrival==null,"controlled return fabricated a completed expedition");
                finish(server,"complete.txt","PASS unavailable home blocks, reload retains blocked state, explicit retry after real stair/bridge change climbs home across another saved process restart; UUID, 80 goals, food16 and boat1 retained; ticks="+ticks);return;
            }
            if(!changedCheckpointSaved&&!npc.isPassenger()&&npc.getY()>=263){
                changedCheckpointSaved=true;storeLedger();finish(server,"return-checkpoint.txt","Saved original actor while climbing the declared changed route; no actor relocation or inventory refill");return;
            }
        }
        if(state.phase.equals("blocked")){
                // The resident may resume ordinary autonomous work after a
                // failed trip. Check the identical-origin retry guard directly,
                // not an invented requirement that all normal movement stop.
                Journeys.returnHome(npc,"automatic unavailable retry");check(state.phase.equals("blocked"),"same-position trip restarted automatically");
                checkStock(level);
                if(replay){
                    Files.writeString(Path.of("blocked-replay.txt"),"PASS original blocked phase retained after reload and identical-origin automatic retry refused");
                    openPhysicalRoute(level);reopened=true;storeLedger();Journeys.returnHome(npc,"requested return");check(state.active(),"explicit changed-route request refused");return;
                }
                storeLedger();finish(server,"checkpoint.txt","PASS unavailable physical home route reports blocked without repeated landing resets; all 80 goals, UUID, food16 and boat1 retained; ticks="+ticks+" status="+state.status);return;
        }
        if(ticks%200==0)System.out.println("UNAVAILABLE_PROGRESS ticks="+ticks+" pos="+npc.position()+" phase="+state.phase+" progress="+state.activeNoProgressTicks+" status="+state.status);
        check(ticks<6000,"unavailable route did not terminate");
    }

    private void checkStock(ServerLevel level){
        var state=Journeys.state(npc);int boats=npc.getInventory().getItems().stream().filter(i->i.getItem() instanceof BoatItem).mapToInt(ItemStack::getCount).sum()
                +(state.boatId!=null&&level.getEntity(state.boatId)!=null?1:0);
        check(boats==1&&boatUsed,"owned boat lost/duplicated or transport not exercised");
        check(npc.getInventory().countItem(Items.COOKED_BEEF)==16,"food lost/duplicated");
    }
    private void storeLedger()throws Exception{
        var saved=new Properties();saved.setProperty("uuid",ID.toString());saved.setProperty("boatUsed",Boolean.toString(boatUsed));saved.setProperty("reopened",Boolean.toString(reopened));saved.setProperty("returnCheckpoint",Boolean.toString(changedCheckpointSaved));
        try(var out=Files.newOutputStream(Path.of("actor.properties"))){saved.store(out,"observed negative/changed-route outcome");}
    }
    private void openPhysicalRoute(ServerLevel level){
        // This is a declared environmental change, like opening a blocked door.
        // Only native movement may bring the existing actor onto it.
        for(int z=-1;z>=-7;z--)for(int y=256;y<260-z;y++)level.setBlockAndUpdate(new BlockPos(6,y,z),Blocks.BEDROCK.defaultBlockState());
        for(int x=0;x<=6;x++)level.setBlockAndUpdate(new BlockPos(x,266,-7),Blocks.BEDROCK.defaultBlockState());
        for(int z=-7;z<=0;z++)level.setBlockAndUpdate(new BlockPos(0,266,z),Blocks.BEDROCK.defaultBlockState());
        System.out.println("UNAVAILABLE_ROUTE_OPENED physical stairs/bridge; actor="+npc.position());
    }

    private void setup(MinecraftServer server,ServerLevel level)throws Exception{
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
        for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){
            for(int y=255;y<=269;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),y==255?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
            if(Math.abs(x)<=5&&Math.abs(z)<=5){for(int y=256;y<=259;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState());}
            else for(int y=256;y<=259;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.BEDROCK.defaultBlockState());
        }
        for(int y=256;y<267;y++)level.setBlockAndUpdate(new BlockPos(0,y,0),Blocks.BEDROCK.defaultBlockState());
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("HomeGuard");npc.setUUID(ID);npc.getRandom().setSeed(0);npc.setPersistenceRequired();npc.setPos(3.5,260,0.5);
        npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));npc.getInventory().addItem(new ItemStack(Items.OAK_BOAT));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(new BlockPos(-2,266,-2),5,5));level.addFreshEntity(npc);
        // Known home only: no unsupported-air outbound breadcrumb is fabricated.
        Journeys.returnHome(npc,"declared unavailable home regression");
        var ledger=new Properties();ledger.setProperty("uuid",ID.toString());try(var out=Files.newOutputStream(Path.of("actor.properties"))){ledger.store(out,"controlled negative actor");}
        System.out.println("UNAVAILABLE_SETUP uuid="+ID+" goals="+goalCount(npc)+" highHome="+Journeys.state(npc).home+" artificialBedrock=true");
    }

    private int goalCount(PlayerNpcEntity n)throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((GoalSelector)f.get(n)).getAvailableGoals().size();}
    private static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
    private void finish(MinecraftServer server,String marker,String result){done=true;finishedMarker=marker;try{Files.writeString(Path.of(marker),result);}catch(Exception error){throw new RuntimeException(error);}server.halt(false);}
}

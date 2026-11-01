import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.preparation.PreparedExpeditions;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.*;
import java.util.*;

/** Controlled commitment integration: history milestones and flat geometry are
 * explicitly declared fixture inputs, NOT evidence of natural cooking/rest or
 * terrain navigation. The accepted actor must actually walk, save, reload and
 * return with every normal native goal enabled and no later supplies/movement. */
public final class SmartNpcPreparedExpedition implements DedicatedServerModInitializer {
    private static final UUID ID=UUID.nameUUIDFromBytes("prepared-expedition-contract".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private PlayerNpcEntity npc;private boolean done,returning,outboundCheckpoint;private int ticks;
    private final boolean replay=Boolean.getBoolean("journey.replay"),visit=Boolean.getBoolean("prepared.visit");
    private final int distance=visit?Integer.getInteger("journey.distance",320):128;
    public void onInitializeServer(){
        // Production initialization must register the real command provider.
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("PreparedResident|EXPLORING"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;try{tick(server);}catch(Throwable error){error.printStackTrace();finish(server,"failure.txt",error.toString());}});
    }
    private void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(200);var level=server.overworld();
        if(npc==null){
            if(!replay){setup(server,level);return;}
            npc=(PlayerNpcEntity)level.getEntity(ID);if(npc==null){check(ticks<2000,"saved original actor absent");return;}
            var ledger=new Properties();try(var in=Files.newInputStream(Path.of("actor.properties"))){ledger.load(in);}
            returning=Boolean.parseBoolean(ledger.getProperty("returning","false"));outboundCheckpoint=true;
            var state=Journeys.state(npc);check(state.phase.equals(returning?"returning":"outbound"),"native commitment phase lost after restart");
            check(state.home.equals(new BlockPos(0,280,0))&&!state.trail.points().isEmpty(),"home or observed trail lost on restart");
            System.out.println("PREPARED_RESUMED uuid="+ID+" phase="+state.phase+" pos="+npc.position());
        }
        check(npc.isAlive()&&goalCount(npc)==81,"actor or full normal selector changed");checkStock();
        var state=Journeys.state(npc);check(!state.phase.equals("blocked"),"accepted native expedition stalled: "+state.status);
        if(!returning){
            if(!outboundCheckpoint&&npc.getX()>24){
                outboundCheckpoint=true;saveLedger();finish(server,"checkpoint.txt","Saved actually walking original prepared resident outbound; declared history/geometry only, no later relocation or supplies");return;
            }
            if(state.phase.equals("at_destination")||visit&&state.phase.equals("returning")){
                check(npc.blockPosition().distSqr(new BlockPos(distance,280,0))<=9,"arrival has no physical actor");
                String before=route(npc),stock=stock(npc);
                check(!PreparedExpeditions.request(npc,new BlockPos(256,280,0),false).started(),"at_destination double admission accepted");
                check(route(npc).equals(before)&&stock(npc).equals(stock),"declined double admission mutated route or stock");
                Files.writeString(Path.of("arrived.txt"),"PASS original resident physically reached accepted target and at_destination double admission declined unchanged");
                if(visit){
                    check(state.lastArrival!=null&&state.lastArrival.equals(new BlockPos(distance,280,0)),"automatic return lacks native physical-arrival evidence");
                    if(!state.phase.equals("returning"))return;
                    check(state.reason.equals("completed prepared visit"),"fixture did not observe automatic visit return");
                }else Journeys.returnHome(npc,"requested return");
                returning=true;return;
            }
        }else if(state.phase.equals("idle")&&npc.blockPosition().distSqr(new BlockPos(0,280,0))<=9){
            checkStock();if(visit)check(com.pla.smart_npc.fabric.survival.progression.NpcProgression.snapshot(npc).totals().getOrDefault(com.pla.smart_npc.fabric.survival.progression.PersonalProgress.Skill.EXPLORATION,0)==1,"actual long return did not earn exactly one exploration credit");finish(server,"complete.txt","PASS optional prepared commitment: explicit missing-history/material/water/occupied/unsafe declines preserved stock and route; declared history then real native outward/home walk, automatic return for explicit visit mode, both process restarts, UUID80goals and food16/tools retained");return;
        }else if(replay&&!Files.exists(Path.of("return-checkpoint.txt"))&&npc.getX()<distance-20){
            saveLedger();finish(server,"return-checkpoint.txt","Saved original actually returning resident; native phase/home/trail must survive a second process restart");return;
        }
        if(ticks%200==0)System.out.println("PREPARED_PROGRESS ticks="+ticks+" phase="+state.phase+" pos="+npc.position());
        check(ticks<8000,"prepared native trip did not finish");
    }
    private void setup(MinecraftServer server,ServerLevel level)throws Exception{
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
        for(int x=-4;x<=distance+4;x++)for(int z=-3;z<=3;z++)for(int y=279;y<=283;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),y==279?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("PreparedResident");npc.setUUID(ID);npc.setPersistenceRequired();npc.setPos(.5,280,.5);npc.getRandom().setSeed(0);
        npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(new BlockPos(-2,279,-2),5,5));
        var notAdded=PreparedExpeditions.request(npc,new BlockPos(distance,280,0),false);check(!notAdded.started()&&notAdded.message().contains("not available"),"unregistered actor accepted commitment");
        level.addFreshEntity(npc);
        String original=route(npc),stock=stock(npc);
        var refusal=PreparedExpeditions.request(npc,new BlockPos(distance,280,0),false);
        check(!refusal.started()&&refusal.message().contains("own native cooking")&&refusal.message().contains("safe night"),"missing earned history not explained");unchanged(original,stock);
        // Deliberately declared synthetic historical inputs for this contract;
        // production grants these only through native authoritative producers.
        VerifiedWorkOutcome.emit(npc,VerifiedWorkOutcome.Kind.COOK,npc.blockPosition(),"minecraft:cooked_beef",1);
        VerifiedWorkOutcome.emit(npc,VerifiedWorkOutcome.Kind.SAFE_REST,npc.blockPosition(),"native:owned_bed",1);
        refusal=PreparedExpeditions.request(npc,new BlockPos(distance,280,0),false);
        check(!refusal.started()&&refusal.message().contains("pickaxe"),"history replaced missing real tool");unchanged(original,stock);
        npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));stock=stock(npc);
        refusal=PreparedExpeditions.request(npc,new BlockPos(distance,280,0),true);check(!refusal.started()&&refusal.message().contains("boat"),"declared water crossing accepted without boat");unchanged(original,stock);
        npc.setNoAi(true);refusal=PreparedExpeditions.request(npc,new BlockPos(distance,280,0),false);check(!refusal.started(),"paused resident committed");unchanged(original,stock);npc.setNoAi(false);
        var state=Journeys.state(npc);state.phase="at_destination";original=route(npc);check(!PreparedExpeditions.request(npc,new BlockPos(distance,280,0),false).started(),"stale arrival overwritten");unchanged(original,stock);state.phase="idle";
        check(server.getCommands().getDispatcher().execute("smart_npc expedition "+ID+(visit?" visit ":" go ")+distance+" 280 0",server.createCommandSourceStack())==1&&Journeys.state(npc).phase.equals("outbound"),"registered command refused ready safe resident or did not create native trip");
        original=route(npc);check(!PreparedExpeditions.request(npc,new BlockPos(256,280,0),false).started(),"active trip double admitted");unchanged(original,stock);
        saveLedger();System.out.println("PREPARED_SETUP declaredHistory=true artificialFlatGeometry=true uuid="+ID+" goals="+goalCount(npc));
    }
    private void checkStock(){check(npc.getInventory().countItem(Items.COOKED_BEEF)==16&&npc.getInventory().countItem(Items.STONE_PICKAXE)==1&&npc.getInventory().countItem(Items.STONE_AXE)==1,"stock lost or duplicated");}
    private void unchanged(String route,String stock){check(route(npc).equals(route)&&stock(npc).equals(stock),"declined request changed native route or carried inventory");}
    private static String route(PlayerNpcEntity n){var s=Journeys.state(n);return s.phase+"|"+s.home+"|"+s.destination+"|"+s.lastArrival+"|"+s.trail.points()+"|"+s.cursor;}
    private static String stock(PlayerNpcEntity n){return n.getInventory().getItems().stream().map(s->s.toString()+":"+s.getDamageValue()).toList().toString();}
    private int goalCount(PlayerNpcEntity n)throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((GoalSelector)f.get(n)).getAvailableGoals().size();}
    private void saveLedger()throws Exception{var p=new Properties();p.setProperty("returning",Boolean.toString(returning));try(var out=Files.newOutputStream(Path.of("actor.properties"))){p.store(out,"controlled declared-history commitment observations");}}
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception error){throw new RuntimeException(error);}server.halt(false);}
}




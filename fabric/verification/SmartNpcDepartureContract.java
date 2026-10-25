import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.Journeys;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.nio.file.Path;

/** Controlled start contract, not natural survival evidence. Geometry, actors,
 * ownership and native mounted state are declared fixture inputs. No ongoing
 * expedition is moved, supplied or credited as completed by this test. */
public final class SmartNpcDepartureContract implements DedicatedServerModInitializer {
    private boolean done;
    public void onInitializeServer(){
        ServerTickEvents.END_SERVER_TICK.register(server->{if(done)return;done=true;
            try{
                var level=server.overworld();var feet=new BlockPos(0,280,0);
                for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=278;y<=286;y++)
                    level.setBlockAndUpdate(new BlockPos(x,y,z),y==279?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
                var ground=actor(level,feet);ground.setOnGround(false);
                check(!ground.onGround(),"setup must expose supported spawn before onGround update");
                check(Journeys.start(ground,new BlockPos(100,280,0)).equals("Journey started"),"physically supported ground rejected");
                check(Journeys.state(ground).home.equals(feet)&&Journeys.state(ground).trail.points().size()==1,"ground anchor incorrect");
                check(Journeys.state(ground).lastArrival==null,"start invented completed trip");repeatReturnContract(ground,level);ground.discard();
                var air=actor(level,feet.above(3));var state=Journeys.state(air);
                check(Journeys.start(air,new BlockPos(100,280,0)).contains("supported dry ground"),"unsupported air accepted");
                check(!state.active()&&state.trail.points().isEmpty()&&state.destination==null,"rejected start altered route");
                check(air.getInventory().countItem(Items.COOKED_BEEF)==16,"rejected start changed stock");air.discard();
                for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)level.setBlockAndUpdate(new BlockPos(x,280,z),Blocks.WATER.defaultBlockState());
                var rider=actor(level,feet.above());
                var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.OAK_BOAT));
                var hull=(AbstractBoat)type.create(level,EntitySpawnReason.COMMAND);hull.setPos(.5,280.8,.5);check(level.addFreshEntity(hull),"boat spawn failed");
                check(rider.startRiding(hull,true,true),"native boarding failed");hull.positionRider(rider);
                check(Journeys.start(rider,new BlockPos(100,280,0)).contains("supported dry ground"),"unowned boat accepted");
                state=Journeys.state(rider);state.boatId=hull.getUUID();state.boatItem=new ItemStack(Items.OAK_BOAT);
                check(Journeys.start(rider,new BlockPos(100,280,0)).equals("Journey started"),"owned mounted boat rejected");
                check(state.home.equals(hull.blockPosition()),"boat departure saved passenger air position");
                check(state.trail.points().size()==1&&Journeys.block(state.trail.points().getFirst()).equals(hull.blockPosition()),"boat trail anchor incorrect");
                check(rider.getVehicle()==hull&&!hull.isRemoved()&&state.boatId.equals(hull.getUUID())&&rider.getInventory().countItem(Items.OAK_BOAT)==0,"start changed real boat ownership or duplicated stock: mounted="+(rider.getVehicle()==hull)+" removed="+hull.isRemoved()+" inventory="+rider.getInventory().countItem(Items.OAK_BOAT));
                check(rider.getInventory().countItem(Items.COOKED_BEEF)==16&&state.lastArrival==null,"boat start changed supplies or fabricated arrival");
                Files.writeString(Path.of("complete.txt"),"PASS controlled departure contract: supported ground with onGround false accepted; unsupported air and unowned boat rejected without route/stock changes; real owned mounted boat accepted with hull-level breadcrumb and one real boat conserved; declared completed-trip history credits once and same-home manual return next simulation day earns no second expedition");
            }catch(Throwable error){error.printStackTrace();try{Files.writeString(Path.of("failure.txt"),error.toString());}catch(Exception ignored){}}
            server.halt(false);
        });
    }
    /** Synthetic exploit regression: the prior completed outbound arrival is
     * declared state, not evidence that this fixture travelled 300 blocks. */
    private void repeatReturnContract(PlayerNpcEntity n,ServerLevel level){
        var state=Journeys.state(n);state.phase="returning";state.destination=n.blockPosition();
        state.lastArrival=n.blockPosition().offset(300,0,0);state.cursor=-1;state.trail.clear();
        com.pla.smart_npc.util.PlayerNpcAiWorkBudget.onWorkStarted(n);
        check(com.pla.smart_npc.util.PlayerNpcAiWorkBudget.hasActiveWorkerSlot(n),"controlled native return has no admitted worker");
        new com.pla.smart_npc.fabric.survival.JourneyGoal(n).tick();
        var before=com.pla.smart_npc.fabric.survival.progression.NpcProgression.snapshot(n);
        var skill=com.pla.smart_npc.fabric.survival.progression.PersonalProgress.Skill.EXPLORATION;
        check(state.phase.equals("idle")&&before.totals().getOrDefault(skill,0)==1,"declared completed return did not receive exactly one native credit");
        // Advance the actual simulation clock beyond the dedup window: a
        // missing trip-consumption guard must not hide behind daily proof caps.
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+24_000);
        Journeys.returnHome(n,"requested return");
        new com.pla.smart_npc.fabric.survival.JourneyGoal(n).tick();
        var after=com.pla.smart_npc.fabric.survival.progression.NpcProgression.snapshot(n);
        check(after.totals().getOrDefault(skill,0)==1&&after.workWindows()==before.workWindows(),"same-home manual return earned a fake second-day expedition");
        check(state.lastArrival==null,"completed arrival token was not consumed");
        com.pla.smart_npc.util.PlayerNpcAiWorkBudget.onWorkStopped(n);
    }
    private PlayerNpcEntity actor(ServerLevel level,BlockPos feet){
        var n=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
        n.setPos(feet.getX()+.5,feet.getY(),feet.getZ()+.5);n.setPersistenceRequired();n.getInventory().clearContent();n.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));
        PlayerNpcHomeUtil.setHome(n,new PlayerNpcHomeUtil.HomeArea(new BlockPos(-2,279,-2),5,5));check(level.addFreshEntity(n),"actor spawn failed");return n;
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}



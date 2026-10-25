import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.config.SmartNpcConfig;
import java.nio.file.Files;

/** Controlled real-client input, interaction packet and private chat delivery.
 * Declared platform/supplies; not an autonomous survival or cooperation proof. */
public final class SmartNpcMemoryClient implements ClientModInitializer {
    volatile int entityId=-1;
    volatile Throwable failure;
    volatile boolean prepared;
    int stage,ticks,replies;
    long began=System.nanoTime();
    public void onInitializeClient(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
        SmartNpcConfig.TURN_ON_NPC_CHAT.set(true);
        ClientReceiveMessageEvents.GAME.register((message,overlay)->{
            if(message.getString().contains("<MemoryWitness>")){
                replies++;System.out.println("MEMORY_CLIENT_REPLY "+message.getString());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    void tick(Minecraft mc){try{
        if(failure!=null)throw new RuntimeException(failure);
        if(System.nanoTime()-began>240_000_000_000L)throw new AssertionError("client dialogue deadline stage="+stage);
        if(stage==0&&mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
        if(stage==1&&mc.gui.screen() instanceof CreateWorldScreen screen){
            stage=2;var state=screen.getUiState();state.setName("SmartNpcMemoryClient");state.setSeed("60020261004");
            state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);state.setAllowCommands(true);state.setBonusChest(false);
            var create=CreateWorldScreen.class.getDeclaredMethod("onCreate");create.setAccessible(true);create.invoke(screen);
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(stage==2&&++ticks>40){
            stage=3;ticks=0;mc.getSingleplayerServer().execute(()->{try{
                var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
                var base=new BlockPos(0,260,0);
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                player.getInventory().clearContent();player.teleportTo(level,.5,260,-2.5,java.util.Set.of(),0,0,true);
                var npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
                npc.setCustomName(Component.literal("MemoryWitness"));npc.setPos(.5,260,.5);npc.setPersistenceRequired();
                npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));level.addFreshEntity(npc);entityId=npc.getId();
                var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
                check(((net.minecraft.world.entity.ai.goal.GoalSelector)field.get(npc)).getAvailableGoals().size()==80,"normal goals missing");
                prepared=true;
            }catch(Throwable error){failure=error;}});
        }
        if(stage==3&&prepared&&mc.level.getEntity(entityId)!=null){
            mc.options.keyShift.setDown(true);stage=4;ticks=0;
        }
        if(stage==4&&++ticks==15){
            check(mc.player.isShiftKeyDown(),"real client crouch input missing");
            var npc=mc.level.getEntity(entityId);check(npc!=null&&mc.player.distanceToSqr(npc)<36,"NPC left local interaction range");
            mc.gameMode.interact(mc.player,npc,new EntityHitResult(npc),InteractionHand.MAIN_HAND);stage=5;ticks=0;
        }
        if(stage==5&&replies==1){
            var npc=mc.level.getEntity(entityId);
            mc.gameMode.interact(mc.player,npc,new EntityHitResult(npc),InteractionHand.MAIN_HAND);stage=6;ticks=0;
        }
        if(stage==6&&++ticks==40){
            check(replies==1,"cooldown did not prevent duplicate network replies");
            mc.options.keyShift.setDown(false);net.minecraft.client.Screenshot.grab(mc,false);
            Files.writeString(mc.gameDirectory.toPath().resolve("complete.txt"),"PASS real crouch input -> client interaction -> server callback -> private system chat -> client receipt; duplicate packet throttled; normal80goals retained at setup. Controlled platform and declared supplies, not survival proof. Screenshot requires visual review.");
            stage=7;mc.stop();
        }
    }catch(Throwable error){error.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),error.toString());}catch(Exception ignored){}stage=7;mc.options.keyShift.setDown(false);mc.stop();}}
}

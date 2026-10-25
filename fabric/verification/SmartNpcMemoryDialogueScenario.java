import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Real Fabric server callback with captured server-player output; no network/client claim. */
public class SmartNpcMemoryDialogueScenario implements DedicatedServerModInitializer {
    boolean done;int ticks,stage;long firstReplyAt;PlayerNpcEntity npc;
    CapturingPlayer giver,other;
    static class CapturingPlayer extends ServerPlayer {
        final List<String> replies=new ArrayList<>();
        CapturingPlayer(ServerLevel level,UUID id,String name){super(level.getServer(),level,new GameProfile(id,name),ClientInformation.createDefault());}
        @Override public void sendSystemMessage(Component text){replies.add(text.getString());}
    }
    public void onInitializeServer(){
        NpcMemories.register();MemorySettings.MEMORY_ENABLED.set(true);MemorySettings.CONTEXTUAL_DIALOGUE.set(true);
        MemorySettings.DILIGENCE.set(-1);MemorySettings.SOCIABILITY.set(-1);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("MemoryTalker|EXPLORING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread thread=Thread.currentThread();new Thread(()->{try{thread.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable failure){failure.printStackTrace();finish(s,"failure.txt",failure.toString());}});
    }
    PlayerNpcEntity spawn(ServerLevel level,int x)throws Exception{
        var n=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);n.setUsername("MemoryTalker");
        n.setUUID(new UUID(0x6d656d6f7279L,x));n.setPersistenceRequired();n.setPos(x+.5,260,.5);
        n.getInventory().clearContent();for(var slot:EquipmentSlot.values())n.setItemSlot(slot,ItemStack.EMPTY);
        n.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));
        PlayerNpcHomeUtil.setHome(n,new PlayerNpcHomeUtil.HomeArea(new BlockPos(x-2,259,-2),5,5));level.addFreshEntity(n);return n;
    }
    InteractionResult click(CapturingPlayer player){return UseEntityCallback.EVENT.invoker().interact(player,player.level(),InteractionHand.MAIN_HAND,npc,null);}
    void follow(CapturingPlayer player){player.setPos(npc.getX()+1,npc.getY(),npc.getZ());}
    void tick(MinecraftServer server)throws Exception{
        ticks++;var level=server.overworld();server.tickRateManager().setTickRate(200);
        if(stage==0){
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
            for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
                level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());
                for(int y=260;y<=263;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            npc=spawn(level,0);giver=new CapturingPlayer(level,new UUID(1,2),"MemoryGiver");other=new CapturingPlayer(level,new UUID(3,4),"MemoryOther");
            follow(giver);follow(other);giver.setShiftKeyDown(true);other.setShiftKeyDown(true);
            giver.getInventory().setItem(1,new ItemStack(Items.COOKED_BEEF));
            var food=giver.getInventory().removeItem(1,1);check(npc.getInventory().addItem(food).isEmpty(),"actual handoff rejected");
            check(npc.getInventory().countItem(Items.COOKED_BEEF)==17&&giver.getInventory().countItem(Items.COOKED_BEEF)==0,"actual handoff not conserved");
            NpcMemories.helpReceived(npc,giver.getUUID(),"one cooked beef handed over");stage=1;
        }
        if(stage==1&&ticks>=20){
            follow(giver);MemorySettings.MEMORY_ENABLED.set(false);check(click(giver)==InteractionResult.PASS&&giver.replies.isEmpty(),"memory disable ignored");MemorySettings.MEMORY_ENABLED.set(true);SmartNpcConfig.TURN_ON_NPC_CHAT.set(false);check(click(giver)==InteractionResult.PASS&&giver.replies.isEmpty(),"chat mute ignored");
            SmartNpcConfig.TURN_ON_NPC_CHAT.set(true);giver.setShiftKeyDown(false);check(click(giver)==InteractionResult.PASS&&giver.replies.isEmpty(),"ordinary interaction intercepted");
            giver.setShiftKeyDown(true);giver.getInventory().setItem(0,new ItemStack(Items.STICK));
            check(click(giver)==InteractionResult.PASS&&giver.replies.isEmpty(),"held-item interaction intercepted");giver.getInventory().setItem(0,ItemStack.EMPTY);
            check(click(giver)==InteractionResult.SUCCESS_SERVER&&giver.replies.size()==1,"explicit local request did not reply");
            check(giver.replies.getFirst().contains("Thanks for the help earlier"),"actual giver was not recognized");
            for(int i=0;i<10;i++)click(giver);check(giver.replies.size()==1,"repeated click spam");
            check(MemoryConversations.selectionFor(npc,other.getUUID()).type()!=DialogueSelection.Type.THANKS,"wrong player credited");
            firstReplyAt=level.getGameTime();stage=2;
        }
        if(stage==2&&level.getGameTime()-firstReplyAt>=1000){
            follow(giver);follow(other);check(click(giver)==InteractionResult.PASS&&giver.replies.size()==1,"per-pair cooldown ignored");
            check(click(other)==InteractionResult.SUCCESS_SERVER&&other.replies.size()==1,"another listener after NPC cooldown was blocked");
            check(!other.replies.getFirst().contains("Thanks for the help earlier"),"help assigned to wrong listener");
            var original=npc;npc=spawn(level,5);follow(other);check(click(other)==InteractionResult.PASS&&other.replies.size()==1,"player global throttle ignored");npc=original;
            check(goals(npc)==80&&npc.getInventory().countItem(Items.COOKED_BEEF)==17,"conversation altered goals or food");
            finish(server,"complete.txt","PASS explicit local Fabric callback; chat mute, normal/held-item interaction, true-giver attribution, pair/NPC/player throttles and ordinary goals/inventory preserved; captured server-player output only, not a client-network test");
        }
        if(ticks>2000)throw new AssertionError("dialogue fixture deadline");
    }
    int goals(PlayerNpcEntity n)throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((GoalSelector)f.get(n)).getAvailableGoals().size();}
    void check(boolean result,String message){if(!result)throw new AssertionError(message);}
    void finish(MinecraftServer server,String name,String message){try{Files.writeString(Path.of(name),message);}catch(Exception e){throw new RuntimeException(e);}done=true;server.halt(false);}
}

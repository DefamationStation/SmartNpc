import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import com.mojang.authlib.GameProfile;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.fabric.survival.relationships.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import java.nio.file.*;

/** Adversarial real server callback transactions; actual network proof is in the client fixture. */
public class SmartNpcFoodOfferSafety implements DedicatedServerModInitializer {
    boolean done;PlayerNpcEntity npc;
    static class Actor extends ServerPlayer{
        boolean creative;
        Actor(ServerLevel l,UUID id,String name){super(l.getServer(),l,new GameProfile(id,name),ClientInformation.createDefault());}
        @Override public void sendSystemMessage(Component text){}
        @Override public boolean isCreative(){return creative;}
    }
    public void onInitializeServer(){
        NpcMemories.register();NpcFoodOffers.register();MemorySettings.MEMORY_ENABLED.set(true);MemorySettings.CAUTION.set(100);MemorySettings.EXTRA_DEPARTURE_FOOD.set(2);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("SafetyReceiver|EXPLORING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread thread=Thread.currentThread();new Thread(()->{try{thread.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{run(s);finish(s,"complete.txt","PASS actual callback raw/ordinary/disabled/creative/full/future/range refusals preserve items and history; accepted one component-preserving food has exact source, bounded cooldown, no invented nearest actor");}catch(Throwable t){t.printStackTrace();finish(s,"failure.txt",t.toString());}});
    }
    InteractionResult click(Actor actor){return UseEntityCallback.EVENT.invoker().interact(actor,actor.level(),InteractionHand.MAIN_HAND,npc,null);}
    void run(MinecraftServer s){
        var l=s.overworld();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){l.getChunk(x>>4,z>>4);l.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());for(int y=260;y<=263;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(l,EntitySpawnReason.COMMAND);npc.setUsername("SafetyReceiver");npc.setUUID(new UUID(7,7));npc.setPos(.5,260,.5);npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);l.addFreshEntity(npc);
        Actor giver=new Actor(l,new UUID(1,2),"ActualGiver"),other=new Actor(l,new UUID(3,4),"CloserBystander");giver.setPos(.5,260,2.5);other.setPos(.5,260,1.5);giver.setShiftKeyDown(true);
        giver.getInventory().setItem(0,new ItemStack(Items.BEEF,3));check(click(giver)==InteractionResult.PASS,"raw food intercepted");
        var offered=new ItemStack(Items.COOKED_BEEF,3);offered.set(DataComponents.CUSTOM_NAME,Component.literal("Actual meal"));giver.getInventory().setItem(0,offered);
        giver.setShiftKeyDown(false);check(click(giver)==InteractionResult.PASS,"normal interaction intercepted");giver.setShiftKeyDown(true);
        FoodOfferSettings.ENABLED.set(false);check(click(giver)==InteractionResult.PASS,"disabled offer accepted");FoodOfferSettings.ENABLED.set(true);
        MemorySettings.MEMORY_ENABLED.set(false);check(click(giver)==InteractionResult.PASS,"memory-disabled offer accepted");MemorySettings.MEMORY_ENABLED.set(true);
        check(UseEntityCallback.EVENT.invoker().interact(giver,l,InteractionHand.OFF_HAND,npc,null)==InteractionResult.PASS,"offhand intercepted");
        giver.creative=true;check(!NpcFoodOffers.offer(npc,giver),"creative conjured a gift");giver.creative=false;
        for(int i=0;i<npc.getInventory().getContainerSize();i++)npc.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        check(!NpcFoodOffers.offer(npc,giver)&&offered.getCount()==3,"full inventory lost source or accepted gift");npc.getInventory().clearContent();
        var future=new net.minecraft.nbt.CompoundTag();future.putInt("version",99);future.putString("preserve","future-offers");PersistentData.get(npc).put(NpcFoodOffers.KEY,future.copy());
        check(!NpcFoodOffers.offer(npc,giver)&&PersistentData.get(npc).getCompoundOrEmpty(NpcFoodOffers.KEY).equals(future),"future ledger was overwritten");PersistentData.get(npc).remove(NpcFoodOffers.KEY);
        PersistentData.get(npc).put(NpcMemories.KEY,future.copy());check(!NpcFoodOffers.offer(npc,giver)&&PersistentData.get(npc).getCompoundOrEmpty(NpcMemories.KEY).equals(future),"future memory schema was overwritten");PersistentData.get(npc).remove(NpcMemories.KEY);
        giver.setPos(20,260,20);check(!NpcFoodOffers.offer(npc,giver),"remote offer accepted");giver.setPos(.5,260,2.5);
        check(NpcMemories.observations(npc).stream().noneMatch(e->e.kind()==ObservedMemory.Kind.HELP),"refused offer fabricated help");
        npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,2));check(click(giver)==InteractionResult.SUCCESS_SERVER,"safe native offer not accepted");
        check(offered.getCount()==2&&npc.getInventory().countItem(Items.COOKED_BEEF)==3,"one-unit conservation failed");
        check(npc.getInventory().getItems().stream().anyMatch(stack->stack.getCount()==1&&ItemStack.isSameItemSameComponents(stack,offered)),"offered components lost");
        check(click(giver)==InteractionResult.PASS&&offered.getCount()==2&&npc.getInventory().countItem(Items.COOKED_BEEF)==3,"same-pair replay duplicated gift");
        var help=NpcMemories.observations(npc).stream().filter(e->e.kind()==ObservedMemory.Kind.HELP).toList();check(help.size()==1&&help.getFirst().subject().equals(giver.getUUID())&&!help.getFirst().subject().equals(other.getUUID()),"wrong actual giver attribution");
        check(!npc.isTeamAlliedWith(giver)&&!npc.isTeamAlliedWith(other),"gift forced team membership");
    }
    void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    void finish(MinecraftServer s,String name,String message){try{Files.writeString(Path.of(name),message);}catch(Exception e){throw new RuntimeException(e);}done=true;s.halt(false);}
}
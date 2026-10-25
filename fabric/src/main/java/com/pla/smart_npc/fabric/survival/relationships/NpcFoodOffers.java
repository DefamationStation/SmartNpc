package com.pla.smart_npc.fabric.survival.relationships;

import com.pla.smart_npc.config.SmartNpcConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.SocialSafety;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.fabric.survival.memory.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Explicit server-authoritative conserved handoff. No inspector/chest action is inferred to be a gift. */
public final class NpcFoodOffers {
    public static final String KEY="SmartNpcAcceptedFoodOffers";
    private static boolean registered;
    private NpcFoodOffers(){}
    public static synchronized void register(){
        if(registered)return;registered=true;NpcRelationships.register();
        FoodOfferSettings.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("smart_npc-food-offers.toml"));
        UseEntityCallback.EVENT.register((player,level,hand,target,hit)->{
            if(!(target instanceof PlayerNpcEntity npc)||hand!=InteractionHand.MAIN_HAND||!player.isShiftKeyDown()
                    ||!FoodOfferSettings.ENABLED.get()||!MemorySettings.MEMORY_ENABLED.get()||!usefulFood(player.getItemInHand(hand)))return InteractionResult.PASS;
            if(level.isClientSide())return InteractionResult.SUCCESS;
            return player instanceof ServerPlayer serverPlayer&&offer(npc,serverPlayer)?InteractionResult.SUCCESS_SERVER:InteractionResult.PASS;
        });
    }
    /** Match the native cooked-food reserve policy; raw/poisonous foods and special healing items stay untouched. */
    public static boolean usefulFood(ItemStack stack){
        return stack.is(Items.COOKED_BEEF)||stack.is(Items.COOKED_PORKCHOP)||stack.is(Items.COOKED_CHICKEN)
                ||stack.is(Items.COOKED_MUTTON)||stack.is(Items.COOKED_RABBIT)||stack.is(Items.COOKED_COD)
                ||stack.is(Items.COOKED_SALMON)||stack.is(Items.BAKED_POTATO);
    }
    public static boolean offer(PlayerNpcEntity npc,ServerPlayer player){
        if(!FoodOfferSettings.ENABLED.get()||!MemorySettings.MEMORY_ENABLED.get()||!player.isShiftKeyDown()
                ||npc.level()!=player.level()||!npc.isAlive()||npc.isNoAi()||npc.isSleeping()||npc.isHealing()
                ||npc.isOnFire()||npc.isInLava()||npc.getTarget()!=null||npc.isPassenger()||player.isPassenger()
                ||!player.isAlive()||player.isSpectator()||player.isCreative()||npc.distanceToSqr(player)>4*4
                ||!npc.hasLineOfSight(player)||!player.hasLineOfSight(npc)||SocialSafety.hasCause(npc,player)
                ||!MemoryWorldIdentity.supported(player.level().getServer()))return false;
        var attachment=PersistentData.get(npc);var memory=attachment.getCompoundOrEmpty(NpcMemories.KEY);
        int memoryVersion=memory.getIntOr("version",0);if(memoryVersion<0||memoryVersion>MemoryCodec.VERSION)return false;
        var held=player.getMainHandItem();if(held.isEmpty()||!usefulFood(held)
                ||SurvivalTasks.cookedFoodCount(npc)>=NpcMemories.departureFoodReserve(npc))return false;
        long now=npc.level().getGameTime();var ledger=FoodOfferLedger.load(attachment.getCompoundOrEmpty(KEY));
        if(!ledger.ready(player.getUUID(),now,FoodOfferSettings.PAIR_COOLDOWN.get(),FoodOfferSettings.NPC_COOLDOWN.get()))return false;
        var inventory=npc.getInventory();int destination=-1;
        // Resolve capacity before mutating either side; transfer exactly one complete matching stack unit.
        for(int i=0;i<inventory.getContainerSize();i++){
            var current=inventory.getItem(i);
            if(!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,held)&&current.getCount()<current.getMaxStackSize()){destination=i;break;}
            if(current.isEmpty()&&destination<0)destination=i;
        }
        if(destination<0)return false;
        var one=held.copyWithCount(1);var current=inventory.getItem(destination);
        inventory.setItem(destination,current.isEmpty()?one:current.copyWithCount(current.getCount()+1));
        held.shrink(1);player.getInventory().setChanged();inventory.setChanged();
        ledger.accepted(player.getUUID(),now);attachment.put(KEY,ledger.save());
        NpcMemories.helpReceived(npc,player.getUUID(),"accepted one "+BuiltInRegistries.ITEM.getKey(one.getItem()));NpcMemories.save(npc);
        NpcRelationships.acceptedFood(npc,player.getUUID(),BuiltInRegistries.ITEM.getKey(one.getItem()).toString(),1);
        if(SmartNpcConfig.TURN_ON_NPC_CHAT.get())player.sendSystemMessage(Component.literal("<").append(npc.getDisplayName()).append("> Thanks, I can use this food."));
        return true;
    }
}
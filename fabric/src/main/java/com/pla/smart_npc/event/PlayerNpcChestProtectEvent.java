package com.pla.smart_npc.event;

import com.pla.smart_npc.fabric.Events.BreakBlockEvent;

import com.pla.smart_npc.SmartNpc;
import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import com.pla.smart_npc.fabric.Events.PlayerInteractEvent;
import com.pla.smart_npc.fabric.Events.BlockEvent;
import com.pla.smart_npc.fabric.Events.EventPriority;
import com.pla.smart_npc.fabric.Events.SubscribeEvent;

/** Server-side reactions to another living entity disturbing an NPC's tracked chest. */

public final class PlayerNpcChestProtectEvent {
    private PlayerNpcChestProtectEvent() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel serverLevel)
                || !serverLevel.getBlockState(event.getPos()).is(Blocks.CHEST)) {
            return;
        }
        reportOffense(serverLevel, event.getPos(), event.getEntity(), "opened");
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            com.pla.smart_npc.fabric.survival.SocialSafety.opened(player, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.isCanceled()
                || !(event.getLevel() instanceof ServerLevel serverLevel)
                || !event.getState().is(Blocks.CHEST)) {
            return;
        }
        reportOffense(serverLevel, event.getPos(), event.getPlayer(), "broke");
    }

    public static void reportOffense(ServerLevel serverLevel, BlockPos chestPos, LivingEntity offender, String action) {
        if (serverLevel == null || chestPos == null || offender == null || !offender.isAlive()) {
            return;
        }

        net.minecraft.world.Container container = null;
        var state = serverLevel.getBlockState(chestPos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest)
            container = net.minecraft.world.level.block.ChestBlock.getContainer(chest, state, serverLevel, chestPos, true);
        for (var owner : serverLevel.getEntitiesOfClass(PlayerNpcEntity.class, offender.getBoundingBox().inflate(32))) {
            if (!com.pla.smart_npc.fabric.survival.SocialSafety.witnesses(owner, offender)
                    || !(owner.isOwnedChest(chestPos) || container != null
                    && com.pla.smart_npc.fabric.survival.SocialSafety.ownsContainer(owner, container))) continue;
            if ("opened".equals(action)) {
                com.pla.smart_npc.fabric.survival.SocialSafety.warn(owner, offender);
                continue;
            }
            if (!("stole".equals(action) || "broke".equals(action))) continue;
            com.pla.smart_npc.fabric.survival.SocialSafety.defend(owner, offender,
                    "stole".equals(action) ? com.pla.smart_npc.fabric.survival.GrievanceMemory.Cause.THEFT
                    : com.pla.smart_npc.fabric.survival.GrievanceMemory.Cause.VANDALISM);
            if (owner.getTarget() == offender) owner.setCurrentAiState("ai.player_npc.protecting_chest");
        }
    }
}

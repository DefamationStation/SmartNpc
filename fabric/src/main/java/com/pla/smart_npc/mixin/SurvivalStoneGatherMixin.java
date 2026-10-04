package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.BuildHouseGoal;
import com.pla.smart_npc.entity.goal.CraftBasicGearGoal;
import com.pla.smart_npc.entity.goal.GatherStoneGoal;
import com.pla.smart_npc.entity.goal.MiningNightCampGoal;
import com.pla.smart_npc.entity.ai.ToolAi;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Deque;

/** Only replaces job/supply arbitration; native stone safety and collection stay authoritative. */
@Mixin(value = GatherStoneGoal.class, remap = false)
public abstract class SurvivalStoneGatherMixin {
    @Shadow @Final private PlayerNpcEntity playerNpc;
    @Shadow @Final private ToolAi toolAi;
    @Shadow @Final private Deque<BlockPos> stoneQueue;
    @Shadow private BlockPos targetPos;
    @Shadow private BlockPos stoneEgressPos;
    @Shadow private int gatherTicks;
    @Shadow @Final private static int MAX_GATHER_TICKS;

    @Shadow private boolean canContinueStoneWork(ServerLevel level) { throw new AssertionError(); }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void player_npc$consumeConnectedQueueAfterBreak(CallbackInfoReturnable<Boolean> cir) {
        if (this.targetPos != null || this.stoneEgressPos != null || this.stoneQueue.isEmpty()
                || !SurvivalTasks.needsCookingStone(this.playerNpc)) return;
        // Native mineTarget deliberately clears targetPos after committing a break, but
        // its early continuation guard otherwise stops before tick can consume the queue.
        // Permit only that pending handoff, with every immediate native safety guard and
        // a fresh native phase/weather check. This never extends an empty search route.
        if (this.gatherTicks < MAX_GATHER_TICKS
                && PlayerNpcAiWorkBudget.hasActiveWorkerSlot(this.playerNpc)
                && this.playerNpc.getUpwardEscapeTarget() == null
                && this.playerNpc.getHoleEscapeCooldown() <= 0
                && this.toolAi.hasTool(ItemTags.PICKAXES)
                && this.playerNpc.level() instanceof ServerLevel level
                && !MiningNightCampGoal.shouldPauseMiningForNightCamp(this.playerNpc, level)
                && this.canContinueStoneWork(level)) cir.setReturnValue(true);
    }

    @Inject(method = "isStoneSupplyPhaseActiveWithAvailablePickaxe", at = @At("HEAD"), cancellable = true)
    private static void player_npc$cookingStoneDemand(PlayerNpcEntity npc, ServerLevel level,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (npc != null && SurvivalTasks.cookingActive(npc)) {
            cir.setReturnValue(SurvivalTasks.needsCookingStone(npc));
        }
    }

    @Inject(method = "hasPreparedBaseForStone", at = @At("HEAD"), cancellable = true)
    private static void player_npc$cookingNeedsNoDailyJobBase(PlayerNpcEntity npc, ServerLevel level,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (npc != null && SurvivalTasks.cookingActive(npc)) {
            cir.setReturnValue(SurvivalTasks.needsCookingStone(npc));
        }
    }

    @Inject(method = "shouldYieldToFarmCropWork", at = @At("HEAD"), cancellable = true)
    private void player_npc$finishCookingPrerequisiteBeforeCrops(ServerLevel level,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (SurvivalTasks.needsCookingStone(this.playerNpc)) cir.setReturnValue(false);
    }

    @Redirect(method = "canUse", at = @At(value = "INVOKE",
            target = "Lcom/pla/smart_npc/entity/goal/BuildHouseGoal;hasReadyHomeBuildWork(Lcom/pla/smart_npc/entity/PlayerNpcEntity;Lnet/minecraft/server/level/ServerLevel;)Z"))
    private boolean player_npc$finishCookingPrerequisiteBeforeBuilding(PlayerNpcEntity npc, ServerLevel level) {
        return !SurvivalTasks.needsCookingStone(npc) && BuildHouseGoal.hasReadyHomeBuildWork(npc, level);
    }

    @Redirect(method = "canUse", at = @At(value = "INVOKE",
            target = "Lcom/pla/smart_npc/entity/goal/CraftBasicGearGoal;shouldPrioritizeGearCrafting(Lcom/pla/smart_npc/entity/PlayerNpcEntity;Lnet/minecraft/server/level/ServerLevel;)Z"))
    private boolean player_npc$finishCookingPrerequisiteBeforeUnrelatedGear(PlayerNpcEntity npc, ServerLevel level) {
        return !SurvivalTasks.needsCookingStone(npc) && CraftBasicGearGoal.shouldPrioritizeGearCrafting(npc, level);
    }

    @Inject(method = "isAllowedStoneSearchPos", at = @At("RETURN"), cancellable = true)
    private static void player_npc$searchOnlyFurnaceMaterial(PlayerNpcEntity npc, BlockPos center, BlockPos pos,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && SurvivalTasks.cookingActive(npc)
                && !player_npc$isFurnaceMaterialSource(npc, npc.level().getBlockState(pos))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isMineableTargetState", at = @At("RETURN"), cancellable = true)
    private void player_npc$breakOnlyFurnaceMaterial(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && SurvivalTasks.cookingActive(this.playerNpc)
                && !player_npc$isFurnaceMaterialSource(this.playerNpc, state)) cir.setReturnValue(false);
    }

    private static boolean player_npc$isFurnaceMaterialSource(PlayerNpcEntity npc, BlockState state) {
        if (!state.is(Blocks.STONE) && !state.is(Blocks.COBBLESTONE)
                && !state.is(Blocks.DEEPSLATE) && !state.is(Blocks.COBBLED_DEEPSLATE)) return false;
        ItemStack pickaxe = player_npc$selectedPickaxe(npc);
        if (pickaxe.isEmpty() || !pickaxe.isCorrectToolForDrops(state)) return false;
        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE)) return true;
        // Silk Touch stone/deepslate yield their raw blocks, which the parent does not
        // count as furnace ingredients. Do not destroy them for an unfulfillable shortage.
        return pickaxe.getEnchantments().keySet().stream().noneMatch(enchantment ->
                enchantment.is(Enchantments.SILK_TOUCH) && pickaxe.getEnchantments().getLevel(enchantment) > 0);
    }

    /** Mirrors ToolAi's selection order without equipping or consuming any item during search. */
    private static ItemStack player_npc$selectedPickaxe(PlayerNpcEntity npc) {
        if (npc.getMainHandItem().is(ItemTags.PICKAXES)) return npc.getMainHandItem();
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            ItemStack stack = npc.getInventory().getItem(slot);
            if (stack.is(ItemTags.PICKAXES)) return stack;
        }
        if (npc.getOffhandItem().is(ItemTags.PICKAXES)) return npc.getOffhandItem();
        if (npc.getMainWeaponItem().is(ItemTags.PICKAXES)) return npc.getMainWeaponItem();
        if (npc.getOffWeaponItem().is(ItemTags.PICKAXES)) return npc.getOffWeaponItem();
        return ItemStack.EMPTY;
    }
}

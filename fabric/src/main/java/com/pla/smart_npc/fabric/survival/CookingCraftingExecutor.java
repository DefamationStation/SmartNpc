package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.util.PlayerNpcCraftingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * A narrow native-recipe backend. The caller owns movement, action cadence and station placement.
 * Preparatory recipes and the requested result are committed together, so missing ingredients,
 * recipes or inventory capacity never consume the NPC's real materials on a failed attempt.
 */
public final class CookingCraftingExecutor {
    public enum Action { CRAFTING_TABLE, WOODEN_PICKAXE, FURNACE, FISHING_ROD }
    private static final double TABLE_REACH_SQUARED = 2.25D * 2.25D;

    private CookingCraftingExecutor() { }

    public static boolean tryCraft(PlayerNpcEntity npc, Action action, BlockPos tablePos) {
        if (npc == null || action == null || !(npc.level() instanceof ServerLevel level)
                || !npc.isAlive() || npc.isNoAi() || npc.isPassenger() || npc.isHealing()
                || npc.isSleeping() || npc.isOnFire() || npc.getTarget() != null) return false;
        boolean tableRequired = action != Action.CRAFTING_TABLE;
        if (tableRequired && (tablePos == null || !level.hasChunkAt(tablePos)
                || !level.getBlockState(tablePos).is(Blocks.CRAFTING_TABLE)
                || npc.distanceToSqr(Vec3.atCenterOf(tablePos)) > TABLE_REACH_SQUARED
                || !SurvivalTasks.visible(npc, tablePos))) return false;

        SimpleContainer real = npc.getInventory();
        SimpleContainer prepared = new SimpleContainer(real.getContainerSize());
        for (int slot = 0; slot < real.getContainerSize(); slot++) {
            prepared.setItem(slot, real.getItem(slot).copy());
        }
        boolean crafted = switch (action) {
            case CRAFTING_TABLE -> preparePlanks(level, prepared, 4)
                    && PlayerNpcCraftingUtil.tryCraft(level, prepared, Items.CRAFTING_TABLE, false);
            case WOODEN_PICKAXE -> preparePickaxe(level, prepared)
                    && PlayerNpcCraftingUtil.tryCraft(level, prepared, Items.WOODEN_PICKAXE, true);
            case FURNACE -> PlayerNpcCraftingUtil.tryCraftFurnace(level, prepared);
            case FISHING_ROD -> prepared.countItem(Items.STRING) >= 2
                    && prepareSticks(level, prepared, 3)
                    && PlayerNpcCraftingUtil.tryCraft(level, prepared, Items.FISHING_ROD, true);
        };
        if (!crafted) return false;
        for (int slot = 0; slot < real.getContainerSize(); slot++) {
            real.setItem(slot, prepared.getItem(slot).copy());
        }
        real.setChanged();
        return true;
    }

    private static boolean preparePickaxe(ServerLevel level, SimpleContainer inventory) {
        if (PlayerNpcCraftingUtil.countSticks(inventory) < 2) {
            if (!preparePlanks(level, inventory, 2)
                    || !PlayerNpcCraftingUtil.tryCraft(level, inventory, Items.STICK, false)) return false;
        }
        return preparePlanks(level, inventory, 3);
    }

    private static boolean prepareSticks(ServerLevel level, SimpleContainer inventory, int count) {
        while (PlayerNpcCraftingUtil.countSticks(inventory) < count) {
            int previous = PlayerNpcCraftingUtil.countSticks(inventory);
            if (!preparePlanks(level, inventory, 2)
                    || !PlayerNpcCraftingUtil.tryCraft(level, inventory, Items.STICK, false)
                    || PlayerNpcCraftingUtil.countSticks(inventory) <= previous) return false;
        }
        return true;
    }

    private static boolean preparePlanks(ServerLevel level, SimpleContainer inventory, int count) {
        while (PlayerNpcCraftingUtil.countPlanks(inventory) < count) {
            // Native recipe matching handles log species and datapack outputs. The predicate
            // also prevents converting unrelated carried materials just to obtain planks.
            SimpleContainer logs = new SimpleContainer(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (PlayerNpcCraftingUtil.isLogs(stack)) logs.setItem(slot, stack.copy());
            }
            var result = PlayerNpcCraftingUtil.craftItemMatching(level, logs,
                    stack -> stack.is(ItemTags.PLANKS), false);
            if (result.isEmpty()) return false;
            // Re-run on the transaction inventory using the recipe's actual output item.
            int previous = PlayerNpcCraftingUtil.countPlanks(inventory);
            if (!PlayerNpcCraftingUtil.tryCraft(level, inventory, result.get().getItem(), false)
                    || PlayerNpcCraftingUtil.countPlanks(inventory) <= previous) return false;
        }
        return true;
    }
}

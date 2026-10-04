package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.FishingRodItem;

/** Bridges personal survival needs to the existing home supply/deposit workflow. */
public final class SurvivalHomeSupplies {
    private SurvivalHomeSupplies() { }

    public static boolean needsFoodRod(PlayerNpcEntity npc) {
        return SurvivalFishingGoal.foodCount(npc) < FoodSupply.RESERVE
                && !PlayerNpcFishingGoal.hasFishingRod(npc);
    }

    public static boolean needsFoodString(PlayerNpcEntity npc) {
        return needsFoodRod(npc) && npc.getInventory().countItem(Items.STRING) < 2;
    }

    public static boolean keepForSurvival(PlayerNpcEntity npc, ItemStack stack) {
        // Keep reusable food equipment across reserve-ready and night-time states.
        if (stack.getItem() instanceof FishingRodItem) return true;
        if (stack.is(Items.COAL) && (SurvivalTasks.memory(npc).active
                || SurvivalTasks.cookingActive(npc))) return true;
        if (!needsFoodRod(npc)) return false;
        if (stack.is(Items.STRING)) return true;
        return npc.getInventory().countItem(Items.STRING) >= 2
                && (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK));
    }
}

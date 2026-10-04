package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One native fishing executor shared by normal fishing work and a bounded food need. */
public final class SurvivalFishingGoal extends Goal {
    private static final long EPISODE_TICKS = 20 * 120;
    private static final long RETRY_TICKS = 20 * 60;
    private final PlayerNpcEntity npc;
    private final PlayerNpcFishingGoal delegate;
    private boolean demandActive;
    private boolean runningForFood;
    private long deadline;
    private long retryAfter;
    private long lastClock = Long.MIN_VALUE;

    public SurvivalFishingGoal(PlayerNpcEntity npc) {
        this.npc = npc;
        this.delegate = new PlayerNpcFishingGoal(npc, () -> demandActive && decision(npc) == FoodSupply.State.FISH);
        setFlags(delegate.getFlags());
    }

    public Goal getDelegateGoal() { return delegate; }

    public static String describe(PlayerNpcEntity npc) {
        String purpose = switch (decision(npc)) {
            case RESERVE_READY -> "carried food reserve ready";
            case UNSAFE -> "food acquisition paused: unsafe or busy";
            case COOK_FIRST -> "cook carried raw food before fishing";
            case NEED_ROD -> FishingRodCraftGoal.describe(npc);
            case FISH -> "fish nearby loaded water to replenish food";
        };
        return purpose + "; food=" + foodCount(npc) + "/" + FoodSupply.RESERVE;
    }

    public String runtimeStatus() {
        long now = npc.level().getGameTime();
        if (decision(npc) == FoodSupply.State.FISH && now < retryAfter)
            return describe(npc) + "; retry after " + (retryAfter - now) + " ticks";
        if (runningForFood) return describe(npc) + "; native fishing episode active";
        if (decision(npc) == FoodSupply.State.FISH && deadline > now)
            return describe(npc) + "; waiting for reachable water or native cooldown";
        return describe(npc);
    }

    /** Count actual safe staple items, including raw inputs that the cooking chain can process. */
    public static int foodCount(PlayerNpcEntity npc) {
        int count = useful(npc.getMainHandItem()) ? npc.getMainHandItem().getCount() : 0;
        if (useful(npc.getOffhandItem())) count += npc.getOffhandItem().getCount();
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++) {
            ItemStack stack = npc.getInventory().getItem(i);
            if (useful(stack)) count += stack.getCount();
        }
        return count;
    }

    private static boolean useful(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.SALMON)
            || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP)
            || stack.is(Items.MUTTON) || stack.is(Items.RABBIT) || stack.is(Items.POTATO)
            || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)
            || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)
            || stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_MUTTON)
            || stack.is(Items.COOKED_RABBIT) || stack.is(Items.BAKED_POTATO)
            || stack.is(Items.BREAD) || stack.is(Items.CARROT) || stack.is(Items.APPLE)
            || stack.is(Items.BEETROOT) || stack.is(Items.MELON_SLICE)
            || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)
            || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.MUSHROOM_STEW)
            || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.RABBIT_STEW);
    }

    public static FoodSupply.State decision(PlayerNpcEntity npc) {
        boolean safe = npc.level() instanceof ServerLevel level && !level.isDarkOutside()
            && !level.isThundering() && npc.isAlive() && !npc.isNoAi() && !npc.isPassenger()
            && npc.getTarget() == null && !npc.isOnFire() && !npc.isHealing() && !npc.isSleeping()
            && !npc.isTeamFollower() && npc.getHealth() > npc.getMaxHealth() * 0.5F
            && npc.getUpwardEscapeTarget() == null && npc.getHoleEscapeCooldown() <= 0
            && !(SurvivalTasks.memory(npc).active && !SurvivalTasks.memory(npc).automatic);
        boolean cooking = npc.level() instanceof ServerLevel level
            && (SurvivalTasks.cookingActive(npc) || SurvivalTasks.cookingNeeded(npc, level));
        return FoodSupply.choose(new FoodSupply.Snapshot(foodCount(npc), safe, cooking,
            PlayerNpcFishingGoal.hasFishingRod(npc)));
    }

    @Override public boolean canUse() {
        long now = npc.level().getGameTime();
        if (now < lastClock) { deadline = 0; retryAfter = 0; }
        lastClock = now;
        FoodSupply.State decision = decision(npc);
        if (decision == FoodSupply.State.COOK_FIRST) {
            deadline = 0;
            demandActive = false;
            return false;
        }
        if (decision == FoodSupply.State.UNSAFE) return false;
        if (decision != FoodSupply.State.FISH) {
            demandActive = false;
            deadline = 0;
        } else {
            if (deadline != 0 && now >= deadline) {
                deadline = 0;
                retryAfter = now + RETRY_TICKS;
                demandActive = false;
                npc.setIdleTraceDetail("food supply paused: fishing attempt time limit reached", 40);
                return false;
            }
            if (now < retryAfter) return false;
            if (deadline == 0) deadline = now + EPISODE_TICKS;
            demandActive = true;
        }
        runningForFood = demandActive;
        return delegate.canUse();
    }

    @Override public boolean canContinueToUse() {
        long now = npc.level().getGameTime();
        if (now < lastClock) { deadline = runningForFood ? now + EPISODE_TICKS : 0; retryAfter = 0; }
        lastClock = now;
        FoodSupply.State decision = decision(npc);
        if (decision == FoodSupply.State.COOK_FIRST || decision == FoodSupply.State.RESERVE_READY) deadline = 0;
        if (decision == FoodSupply.State.UNSAFE || decision == FoodSupply.State.COOK_FIRST) return false;
        if (runningForFood && (decision != FoodSupply.State.FISH || now >= deadline)) return false;
        return delegate.canContinueToUse();
    }
    @Override public void start() {
        delegate.start();
    }
    @Override public void tick() { delegate.tick(); }
    @Override public void stop() {
        delegate.stop();
        runningForFood = false;
        demandActive = false;
    }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
}

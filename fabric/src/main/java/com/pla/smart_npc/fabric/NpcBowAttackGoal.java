package com.pla.smart_npc.fabric;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import java.util.EnumSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BowItem;

/** Bow controller for a friendly Mob: vanilla's controller requires a Monster. */
public class NpcBowAttackGoal extends Goal {
    private final PlayerNpcEntity npc;
    private final double speed;
    private final double radiusSquared;
    private final int interval;
    private int visibleTicks;
    private int cooldown;
    private int strafeTicks;
    private boolean clockwise;
    private boolean retreat;

    public NpcBowAttackGoal(PlayerNpcEntity npc, double speed, int interval, float radius) {
        this.npc = npc;
        this.speed = speed;
        this.interval = interval;
        radiusSquared = radius * radius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    protected boolean isHoldingBow() { return npc.isHolding(s -> s.getItem() instanceof BowItem); }
    @Override public boolean canUse() { return npc.getTarget() != null && npc.getTarget().isAlive(); }
    @Override public boolean canContinueToUse() { return canUse() && isHoldingBow(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { npc.setAggressive(true); cooldown = interval; }
    @Override public void stop() {
        npc.setAggressive(false);
        npc.stopUsingItem();
        npc.getNavigation().stop();
        visibleTicks = strafeTicks = 0;
    }

    @Override public void tick() {
        var target = npc.getTarget();
        if (target == null) return;
        boolean visible = npc.getSensing().hasLineOfSight(target);
        visibleTicks = visible ? Math.max(0, visibleTicks) + 1 : Math.min(0, visibleTicks) - 1;
        double distance = npc.distanceToSqr(target);
        if (distance <= radiusSquared && visibleTicks >= 20) {
            npc.getNavigation().stop();
            if (++strafeTicks >= 20) {
                if (npc.getRandom().nextFloat() < 0.3f) clockwise = !clockwise;
                if (npc.getRandom().nextFloat() < 0.3f) retreat = !retreat;
                strafeTicks = 0;
            }
            if (distance > radiusSquared * 0.75) retreat = false;
            if (distance < radiusSquared * 0.25) retreat = true;
            npc.getMoveControl().strafe(retreat ? -0.5f : 0.5f, clockwise ? 0.5f : -0.5f);
        } else {
            npc.getNavigation().moveTo(target, speed);
            strafeTicks = 0;
        }
        npc.getLookControl().setLookAt(target, 30, 30);
        if (npc.isUsingItem()) {
            if (visibleTicks < -60) npc.stopUsingItem();
            else if (visible && npc.getTicksUsingItem() >= 20) {
                float power = BowItem.getPowerForTime(npc.getTicksUsingItem());
                npc.stopUsingItem();
                npc.performRangedAttack(target, power);
                cooldown = interval;
            }
        } else if (--cooldown <= 0 && visibleTicks >= -60) {
            npc.startUsingItem(npc.getMainHandItem().getItem() instanceof BowItem
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
    }
}

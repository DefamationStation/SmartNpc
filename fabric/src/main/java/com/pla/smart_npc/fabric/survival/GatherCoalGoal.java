package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.BreakingBlockAi;
import com.pla.smart_npc.entity.ai.ToolAi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A first task backend for server-side NPCs. Survival/combat goals may interrupt it safely. */
public final class GatherCoalGoal extends Goal {
    private final PlayerNpcEntity npc;
    private final ToolAi tools;
    private final BreakingBlockAi breaking;
    private BlockPos destination;
    private int retryAt, ticks;
    private final Map<Long, Integer> blockedUntil = new HashMap<>();

    public GatherCoalGoal(PlayerNpcEntity npc) {
        this.npc = npc;
        tools = new ToolAi(npc);
        breaking = new BreakingBlockAi(npc, tools);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    private ResourceMemory memory() { return SurvivalTasks.memory(npc); }
    private void status(String value) {
        if (!memory().status.equals(value)) { memory().status = value; SurvivalTasks.save(npc); }
        npc.setCurrentAiDetail(value);
    }
    private boolean safeToWork() {
        return memory().active && npc.getTarget() == null && !npc.isOnFire()
            && npc.getHealth() > npc.getMaxHealth() * 0.35F;
    }
    @Override public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level) || !safeToWork() || npc.tickCount < retryAt) return false;
        var m = memory(); destination = null;
        blockedUntil.values().removeIf(until -> npc.tickCount >= until);
        if (!m.dimension.equals(SurvivalTasks.dimension(npc))) { status("waiting in task dimension"); return false; }
        if (npc.getInventory().countItem(Items.COAL) >= m.target) {
            m.returning = true; SurvivalTasks.save(npc);
        }
        if (m.returning) {
            destination = BlockPos.of(m.origin);
            if (!level.hasChunkAt(destination)) { status("waiting for return area to load"); return false; }
            return true;
        }
        var candidates = new ArrayList<>(m.coal);
        candidates.sort(Comparator.comparingDouble(o -> npc.distanceToSqr(Vec3.atCenterOf(BlockPos.of(o.position())))));
        boolean changed = false;
        for (var observation : candidates) {
            if (!observation.dimension().equals(m.dimension)) continue;
            var pos = BlockPos.of(observation.position());
            if (blockedUntil.containsKey(pos.asLong())) continue;
            if (level.getGameTime() - observation.seen() > ResourceMemory.MAX_AGE) {
                m.coal.remove(observation); changed = true; continue;
            }
            if (!level.hasChunkAt(pos) || npc.distanceToSqr(Vec3.atCenterOf(pos)) > 32 * 32) continue;
            if (!SurvivalTasks.isCoal(level.getBlockState(pos))) {
                m.coal.remove(observation); changed = true; continue;
            }
            destination = pos; break;
        }
        if (changed) SurvivalTasks.save(npc);
        if (destination == null) { status("waiting for visible coal nearby"); retryAt = npc.tickCount + 40; return false; }
        if (!tools.hasPreferredToolFor(level.getBlockState(destination))) {
            status("waiting for a pickaxe"); retryAt = npc.tickCount + 100; return false;
        }
        return true;
    }
    @Override public boolean canContinueToUse() {
        return destination != null && safeToWork() && memory().dimension.equals(SurvivalTasks.dimension(npc));
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { ticks = 0; }
    @Override public void stop() {
        breaking.stop(); tools.restoreMainHand(); npc.getNavigation().stop(); destination = null;
    }
    private void waitFor(String reason) {
        if (destination != null) blockedUntil.put(destination.asLong(), npc.tickCount + 200);
        status(reason); retryAt = npc.tickCount + 40; stop();
    }
    @Override public void tick() {
        if (!(npc.level() instanceof ServerLevel level) || destination == null) return;
        ticks++;
        var m = memory();
        if (!level.hasChunkAt(destination)) { waitFor("waiting for target area to load"); return; }
        if (!m.returning && npc.getInventory().countItem(Items.COAL) >= m.target) {
            m.returning = true; SurvivalTasks.save(npc); stop(); return;
        }
        double distance = npc.distanceToSqr(Vec3.atCenterOf(destination));
        if (m.returning && distance < 4) {
            // Consumption during an interruption must not produce a false success.
            if (npc.getInventory().countItem(Items.COAL) < m.target) {
                m.returning = false; status("replenishing coal consumed during task"); SurvivalTasks.save(npc); stop(); return;
            }
            m.active = false; status("complete: coal collected and returned"); SurvivalTasks.save(npc); stop(); return;
        }
        if (!m.returning) {
            var state = level.getBlockState(destination);
            if (!SurvivalTasks.isCoal(state)) { forget(); stop(); return; }
            if (distance < 9 && SurvivalTasks.visible(npc, destination)) {
                npc.getNavigation().stop(); tools.equipBestToolFor(state);
                if (!npc.getMainHandItem().isCorrectToolForDrops(state)) { waitFor("waiting for a suitable pickaxe"); return; }
                status("mining remembered coal");
                var result = breaking.tick(level, destination, SurvivalTasks::isCoal, 0, "gathering coal");
                if (result == BreakingBlockAi.TickResult.DONE) { forget(); stop(); }
                else if (result == BreakingBlockAi.TickResult.FAILED) waitFor("waiting: mining failed");
                return;
            }
        }
        if (ticks > 200) { waitFor("waiting: route timed out"); return; }
        if (ticks == 1 || ticks % 40 == 0) {
            var path = npc.getNavigation().createPath(destination, m.returning ? 0 : 1);
            if (path == null || !path.canReach()) { waitFor("waiting: no safe walking route"); return; }
            npc.getNavigation().moveTo(path, 1.0);
        }
        status(m.returning ? "returning with coal" : "walking to remembered coal");
    }
    private void forget() {
        long pos = destination.asLong();
        memory().coal.removeIf(o -> o.dimension().equals(SurvivalTasks.dimension(npc)) && o.position() == pos);
        SurvivalTasks.save(npc);
    }
}

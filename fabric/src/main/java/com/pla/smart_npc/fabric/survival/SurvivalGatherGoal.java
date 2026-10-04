package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.entity.goal.GatherStoneGoal;
import com.pla.smart_npc.entity.goal.GatheringGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Admits the native collectors for a survival prerequisite without assigning a daily job.
 * Native delegates retain all target, movement, breaking, pickup and recovery behavior.
 */
public final class SurvivalGatherGoal extends Goal implements GatheringGoal {
    private final PlayerNpcEntity npc;
    private final GatherLogsGoal logs;
    private final GatherStoneGoal stone;
    private Goal selected;
    private boolean started;

    public SurvivalGatherGoal(PlayerNpcEntity npc) {
        this(npc, new GatherLogsGoal(npc, 1.0D));
    }

    /** Reuse the entity's log selector so exploration sees the same retained target. */
    public SurvivalGatherGoal(PlayerNpcEntity npc, GatherLogsGoal logs) {
        this(npc, logs, new GatherStoneGoal(npc, 1.0D));
    }

    /** Share both authoritative selectors with their exploration fallbacks. */
    public SurvivalGatherGoal(PlayerNpcEntity npc, GatherLogsGoal logs, GatherStoneGoal stone) {
        this.npc = npc;
        this.logs = logs;
        this.stone = stone;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.selected = null;
        if (SurvivalTasks.needsCookingLogs(this.npc)) {
            if (this.logs.canUse()) this.selected = this.logs;
        } else if (SurvivalTasks.needsCookingStone(this.npc)) {
            if (this.stone.canUse()) this.selected = this.stone;
        }
        return this.selected != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Let native delegates finish necessary pillar/unsafe-stand recovery when the
        // shortage disappears. Their phase predicates stop further resource extraction.
        return this.started && this.selected != null && this.selected.canContinueToUse();
    }

    @Override
    public void start() {
        if (this.selected != null) {
            this.started = true;
            this.selected.start();
        }
    }

    @Override
    public void tick() {
        if (this.started && this.selected != null) this.selected.tick();
    }

    @Override
    public void stop() {
        if (this.started && this.selected != null) this.selected.stop();
        this.started = false;
        this.selected = null;
    }

    @Override
    public boolean isInterruptable() {
        return this.selected == null || this.selected.isInterruptable();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return this.selected != null && this.selected.requiresUpdateEveryTick();
    }
}

package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A planned pillar is not evidence that its approach made progress. */
@Mixin(value = GatherLogsGoal.class, remap = false)
public abstract class SurvivalLogGatherMixin {
    @Redirect(method = "stop", at = @At(value = "INVOKE",
            target = "Lcom/pla/smart_npc/entity/goal/GatherLogsGoal;hasAuthorizedPillarPlanForTarget()Z"))
    private boolean player_npc$abandonTimedOutGroundApproach(GatherLogsGoal goal) {
        // This call is reached only after the native gather deadline, on safe
        // ground, with a still-valid log target. Keep the original bounded
        // ignore list instead of selecting the same unreachable pillar forever.
        // Native worker-pause and occupied-pillar cleanup guards run first.
        return false;
    }
}

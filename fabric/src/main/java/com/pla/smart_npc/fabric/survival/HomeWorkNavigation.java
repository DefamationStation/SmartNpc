package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.PathNavigationAi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

/** A small home can require a door detour much longer than the straight-line distance. */
public final class HomeWorkNavigation {
    private HomeWorkNavigation() { }

    public static Path createPath(PlayerNpcEntity npc, BlockPos stand) {
        if (stand == null || !(npc.level() instanceof ServerLevel level)
                || npc.blockPosition().distSqr(stand) > 24 * 24
                || !PathNavigationAi.hasLoadedChunkCorridor(level, npc.blockPosition(), stand, 2)) return null;
        var navigation = npc.getNavigation();
        navigation.setMaxVisitedNodesMultiplier(0.2F);
        try {
            // Still local and bounded, rather than the NPC's full 48-block follow range.
            return navigation.createPath(stand, 0, 24);
        } finally {
            navigation.resetMaxVisitedNodesMultiplier();
        }
    }
}

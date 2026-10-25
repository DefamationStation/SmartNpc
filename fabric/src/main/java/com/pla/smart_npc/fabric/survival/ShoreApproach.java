package com.pla.smart_npc.fabric.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/** Loaded-only coarse walking connectivity for choosing a useful boat landing.
 * Native navigation still owns actual walking; this never moves an entity. */
public final class ShoreApproach {
    private ShoreApproach() { }

    public static void refresh(ServerLevel level, BlockPos goal, BlockPos boat, Journeys.State state) {
        if (goal.equals(state.shoreRouteGoal) && level.getGameTime() < state.nextShoreRouteCheck) return;
        state.shoreRouteGoal=goal.immutable();state.nextShoreRouteCheck=level.getGameTime()+80;
        if (!JourneyBoats.dry(level,goal)) { state.shoreRoutes=Map.of();return; }
        var distance=new HashMap<BlockPos,Integer>();distance.put(goal.immutable(),0);
        var queue=new PriorityQueue<BlockPos>(Comparator.comparingDouble(
                p->distance.get(p)+Math.sqrt(p.distSqr(boat))));queue.add(goal.immutable());
        for(int expanded=0;expanded<512&&!queue.isEmpty();expanded++) {
            var p=queue.remove();int steps=distance.get(p)+1;
            for(var direction:Direction.Plane.HORIZONTAL) for(int y=-1;y<=1;y++) {
                var next=p.relative(direction).offset(0,y,0);
                if(distance.size()>=1024||distance.containsKey(next)
                        ||Math.abs(next.getX()-goal.getX())>24||Math.abs(next.getZ()-goal.getZ())>24
                        ||Math.abs(next.getY()-goal.getY())>12
                        ||!level.getWorldBorder().isWithinBounds(next)||!JourneyBoats.dry(level,next))continue;
                distance.put(next,steps);queue.add(next);
            }
        }
        state.shoreRoutes=Map.copyOf(distance);
    }

    public static double cost(Journeys.State state, BlockPos stand, BlockPos goal) {
        var steps=state.shoreRoutes.get(stand);
        return steps==null?4096+Math.sqrt(stand.distSqr(goal)):steps;
    }
}

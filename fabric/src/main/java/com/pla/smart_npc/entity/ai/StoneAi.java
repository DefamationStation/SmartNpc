package com.pla.smart_npc.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class StoneAi {
    // This is one admitted time slice, not a complete ore survey. Keep the synchronous slice small;
    // a failed nearest-first pass resumes at the next probe after the goal throttle.
    private static final int MAX_CLUSTER_BLOCKS = 8;
    private static final int MAX_SEARCH_CLUSTERS = 2;
    private static final int MAX_SEARCH_POSITIONS = 16;
    private static final int MAX_SEARCH_CURSORS_PER_LEVEL = 128;
    private static final long CURSOR_MAX_IDLE_TICKS = 20 * 60;
    // Values contain only positions/cursors/timestamps, never references back to their weak level key.
    private static final Map<ServerLevel, LinkedHashMap<Long, SearchState>> SEARCH_CURSORS = new WeakHashMap<>();

    private static final class SearchState {
        final ProgressiveStoneSearch cursor;
        long lastUsed;
        SearchState(int radius, long now) { this.cursor = new ProgressiveStoneSearch(radius); this.lastUsed = now; }
    }

    private StoneAi() {
    }

    public static List<StoneCluster> findNearby(
            ServerLevel serverLevel,
            BlockPos center,
            int radius,
            Predicate<BlockPos> allowedStone
    ) {
        Set<BlockPos> visited = new HashSet<>();
        List<StoneCluster> clusters = new ArrayList<>();

        SearchState state = searchState(serverLevel, center, radius);
        for (int inspected = 0; inspected < Math.min(MAX_SEARCH_POSITIONS, state.cursor.size())
                && clusters.size() < MAX_SEARCH_CLUSTERS; inspected++) {
            var offset = state.cursor.next();
            BlockPos pos = center.offset(offset.x(), offset.y(), offset.z());
            // An unloaded probe also consumes the slice: no chunk generation or unbounded
            // attempts to walk around unloaded columns. Predicates are evaluated fresh for
            // each caller, so sharing a cursor never shares another NPC's permissions.
            if (visited.contains(pos) || !serverLevel.isInWorldBounds(pos)
                    || !canUseStone(serverLevel, pos, allowedStone)) continue;
            StoneCluster cluster = scan(serverLevel, pos, visited, allowedStone);
            if (!cluster.stones().isEmpty()) clusters.add(cluster);
        }

        clusters.sort(Comparator.comparingDouble(cluster -> cluster.nearestTo(center).distSqr(center)));
        return clusters;
    }

    private static synchronized SearchState searchState(ServerLevel level, BlockPos center, int requestedRadius) {
        int radius = Math.clamp(requestedRadius, 0, ProgressiveStoneSearch.MAX_RADIUS);
        long now = level.getGameTime();
        var cursors = SEARCH_CURSORS.computeIfAbsent(level, unused -> new LinkedHashMap<>(16, 0.75F, true));
        cursors.values().removeIf(state -> now < state.lastUsed || now - state.lastUsed > CURSOR_MAX_IDLE_TICKS);
        long origin = center.asLong();
        var state = cursors.get(origin);
        if (state == null || state.cursor.radius() != radius) {
            state = new SearchState(radius, now);
            cursors.put(origin, state);
        }
        state.lastUsed = now;
        while (cursors.size() > MAX_SEARCH_CURSORS_PER_LEVEL) cursors.pollFirstEntry();
        return state;
    }

    public static boolean isStone(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    private static StoneCluster scan(
            ServerLevel serverLevel,
            BlockPos start,
            Set<BlockPos> globalVisited,
            Predicate<BlockPos> allowedStone
    ) {
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> localVisited = new HashSet<>();
        List<BlockPos> stones = new ArrayList<>();
        open.add(start.immutable());

        while (!open.isEmpty() && stones.size() < MAX_CLUSTER_BLOCKS) {
            BlockPos pos = open.poll();
            if (!localVisited.add(pos) || !canUseStone(serverLevel, pos, allowedStone)) {
                continue;
            }

            stones.add(pos);
            globalVisited.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!localVisited.contains(next) && canUseStone(serverLevel, next, allowedStone)) {
                    open.add(next.immutable());
                }
            }
        }

        return new StoneCluster(stones);
    }

    private static boolean canUseStone(ServerLevel serverLevel, BlockPos pos, Predicate<BlockPos> allowedStone) {
        return serverLevel.hasChunkAt(pos)
                && isStone(serverLevel.getBlockState(pos))
                && (allowedStone == null || allowedStone.test(pos));
    }

    public static final class StoneCluster {
        private final List<BlockPos> stones;

        private StoneCluster(List<BlockPos> stones) {
            this.stones = List.copyOf(stones);
        }

        public List<BlockPos> stones() {
            return this.stones;
        }

        public BlockPos nearestTo(BlockPos origin) {
            return this.stones.stream()
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                    .orElse(origin)
                    .immutable();
        }

        public List<BlockPos> stonesNearestFirst(BlockPos origin) {
            List<BlockPos> sorted = new ArrayList<>(this.stones);
            sorted.sort(Comparator
                    .comparingDouble((BlockPos pos) -> pos.distSqr(origin))
                    .thenComparingInt(BlockPos::getY));
            return sorted;
        }
    }
}

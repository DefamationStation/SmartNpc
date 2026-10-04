package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.util.PlayerNpcBuildLayout;
import com.pla.smart_npc.util.PlayerNpcBuildLayoutLoader;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Read-only, bounded ground-floor evidence. This is not a navigation or safety guarantee. */
public final class ShelterReadiness {
    private static final int MAX_SIDE = 16;
    private static final int MAX_HEIGHT = 8;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private ShelterReadiness() {}

    public enum Status { NO_HOME, UNKNOWN, INCOMPLETE, TEMPORARY_REFUGE, BASIC_SHELTER }

    public record Snapshot(Status status, boolean loaded, boolean entrance, boolean interior,
                           boolean coveredSleepingPlace, boolean usableBed, boolean furnace,
                           boolean crafting, boolean storage, boolean light, int detectedHazards,
                           String detail) {
        public String describe() {
            return "shelter=" + status + ", loaded=" + loaded + ", entrance=" + entrance
                    + ", interior=" + interior + ", coveredSleepingPlace=" + coveredSleepingPlace
                    + ", usableBed=" + usableBed + ", furnace=" + furnace + ", crafting=" + crafting
                    + ", storage=" + storage + ", lightSource=" + light + ", observedHazards="
                    + detectedHazards + " (" + detail + ")";
        }
    }

    public static Snapshot assess(PlayerNpcEntity npc, ServerLevel level) {
        var home = PlayerNpcHomeUtil.getHome(npc);
        if (home.isEmpty()) return unknown(Status.NO_HOME, "no recorded home");
        var layout = PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout);
        return assess(level, home.get(), layout.orElse(null));
    }

    public static Snapshot assess(ServerLevel level, PlayerNpcHomeUtil.HomeArea home, PlayerNpcBuildLayout layout) {
        int height = layout == null ? 6 : layout.height();
        if (!withinBounds(home.width(), home.depth(), height)) {
            return unknown(Status.UNKNOWN, "home exceeds ground-floor scan bound 16x16x8");
        }
        BlockPos origin = home.origin();
        // Check a two-block margin before ANY world/block entity reads. Never request chunks.
        for (int x = -2; x < home.width() + 2; x++) {
            for (int z = -2; z < home.depth() + 2; z++) {
                if (!level.hasChunkAt(origin.offset(x, 0, z))) {
                    return unknown(Status.UNKNOWN, "home or entrance margin is not loaded");
                }
            }
        }
        Set<BlockPos> reachable = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (int x = 0; x < home.width(); x++) {
            for (int z = 0; z < home.depth(); z++) {
                if (x != 0 && z != 0 && x != home.width() - 1 && z != home.depth() - 1) continue;
                BlockPos pos = origin.offset(x, 1, z);
                if (!canStand(level, pos)) continue;
                for (Direction side : SIDES) {
                    BlockPos outside = pos.relative(side);
                    if (!PlayerNpcHomeUtil.isInsideFootprint(home, outside) && canStand(level, outside)) {
                        if (reachable.add(pos)) queue.add(pos);
                    }
                }
            }
        }
        boolean entrance = !queue.isEmpty();
        while (!queue.isEmpty()) {
            BlockPos pos = queue.remove();
            for (Direction side : SIDES) {
                BlockPos next = pos.relative(side);
                if (PlayerNpcHomeUtil.isInsideFootprint(home, next) && !reachable.contains(next)
                        && canStand(level, next)) {
                    reachable.add(next);
                    queue.add(next);
                }
            }
        }
        boolean interior = false, covered = false, bed = false, furnace = false, crafting = false;
        boolean storage = false, light = false, enclosure = true;
        int hazards = 0;
        for (int x = 0; x < home.width(); x++) {
            for (int z = 0; z < home.depth(); z++) {
                BlockPos feet = origin.offset(x, 1, z);
                boolean inside = x > 0 && z > 0 && x < home.width() - 1 && z < home.depth() - 1;
                if (inside) enclosure &= covered(level, feet, origin.getY() + height - 1);
                else {
                    for (int wallY = 1; wallY <= 2; wallY++) {
                        BlockPos wall = origin.offset(x, wallY, z);
                        BlockState wallState = level.getBlockState(wall);
                        enclosure &= wallState.isSolidRender()
                                || wallState.is(BlockTags.WOODEN_DOORS) && clear(level, wall);
                    }
                }
                if (inside && reachable.contains(feet)) {
                    interior = true;
                    covered |= covered(level, feet, origin.getY() + height - 1);
                }
                for (int y = 0; y < height; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (hazard(state)) hazards++;
                    light |= state.getLightEmission() > 0;
                    // Stations on other floors do not imply access from this entrance.
                    if (y != 1 || !adjacent(reachable, pos)) continue;
                    furnace |= state.is(Blocks.FURNACE) && level.getBlockEntity(pos) instanceof Container;
                    crafting |= state.is(Blocks.CRAFTING_TABLE);
                    storage |= (state.is(Blocks.CHEST) || state.is(Blocks.BARREL))
                            && level.getBlockEntity(pos) instanceof Container
                            && (!state.is(Blocks.CHEST) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                            && !level.getBlockState(pos.above()).isSolidRender());
                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                        BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
                        if (!PlayerNpcHomeUtil.isInsideFootprint(home, head)) continue;
                        BlockState headState = level.getBlockState(head);
                        boolean pair = headState.is(state.getBlock()) && headState.getValue(BedBlock.PART) == BedPart.HEAD
                                && headState.getValue(BedBlock.FACING) == state.getValue(BedBlock.FACING);
                        boolean usable = pair && !state.getValue(BedBlock.OCCUPIED) && !headState.getValue(BedBlock.OCCUPIED)
                                && dimensionAllowsBed(level) && clear(level, pos.above()) && clear(level, head.above())
                                && level.getBlockState(pos.below()).isSolidRender()
                                && level.getBlockState(head.below()).isSolidRender()
                                && covered(level, pos, origin.getY() + height - 1)
                                && covered(level, head, origin.getY() + height - 1);
                        bed |= usable;
                        covered |= usable;
                    }
                }
            }
        }
        Status status = classify(entrance, interior, covered, enclosure, bed, furnace, crafting, storage, light, hazards);
        return new Snapshot(status, true, entrance, interior, covered, bed, furnace, crafting, storage,
                light, hazards, "flat ground-floor access; enclosure=" + enclosure
                + "; wooden doors assumed operable; solid roof above each interior column and two-block perimeter; "
                + "single chest/barrel only; light source only; no mob, route-to-home, sleep-time or spawn-proof guarantee");
    }

    static Status classify(boolean entrance, boolean interior, boolean covered, boolean enclosure, boolean bed,
                           boolean furnace, boolean crafting, boolean storage, boolean light, int hazards) {
        if (!entrance || !interior || !covered || !enclosure || hazards != 0) return Status.INCOMPLETE;
        return bed && furnace && crafting && storage && light ? Status.BASIC_SHELTER : Status.TEMPORARY_REFUGE;
    }

    static boolean withinBounds(int width, int depth, int height) {
        return width >= 3 && depth >= 3 && height >= 3 && width <= MAX_SIDE && depth <= MAX_SIDE && height <= MAX_HEIGHT;
    }

    private static Snapshot unknown(Status status, String reason) {
        return new Snapshot(status, false, false, false, false, false, false, false, false, false, 0,
                reason + "; checks unavailable, false values are not evidence of absence");
    }

    private static boolean adjacent(Set<BlockPos> reachable, BlockPos pos) {
        for (Direction side : SIDES) if (reachable.contains(pos.relative(side))) return true;
        return false;
    }

    private static boolean dimensionAllowsBed(ServerLevel level) {
        // Dimension value avoids spatial biome queries/chunk reads and intentionally ignores current sleep time.
        BedRule rule = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.BED_RULE);
        return rule.canSleep() != BedRule.Rule.NEVER && !rule.destroyOnUse() && !rule.destroyOnLeave();
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        BlockState floor = level.getBlockState(feet.below());
        return floor.isSolidRender() && !hazard(floor) && clear(level, feet) && clear(level, feet.above());
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || hazard(state)) return false;
        if (state.is(BlockTags.WOODEN_DOORS)) {
            boolean lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            BlockState partner = level.getBlockState(lower ? pos.above() : pos.below());
            return partner.is(state.getBlock()) && partner.getValue(DoorBlock.HALF) != state.getValue(DoorBlock.HALF)
                    && partner.getValue(DoorBlock.FACING) == state.getValue(DoorBlock.FACING);
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean covered(ServerLevel level, BlockPos feet, int topY) {
        for (int y = feet.getY() + 2; y <= topY; y++) {
            if (level.getBlockState(new BlockPos(feet.getX(), y, feet.getZ())).isSolidRender()) return true;
        }
        return false;
    }

    private static boolean hazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE);
    }
}

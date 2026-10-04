package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.FarmAi;
import com.pla.smart_npc.entity.ai.PathNavigationAi;
import com.pla.smart_npc.entity.ai.PlacingBlockAi;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal;
import com.pla.smart_npc.util.InventoryUtils;
import com.pla.smart_npc.util.PlayerNpcCraftingUtil;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;

/** A bounded food prerequisite using carried materials and a real reachable crafting table. */
public final class FishingRodCraftGoal extends Goal {
    private static final String AI_STATE = "ai.player_npc.crafting";
    private final PlayerNpcEntity npc;
    private final PlacingBlockAi placing;
    private BlockPos table, stand;
    private Path path;
    private boolean placement, finished;
    private int nextCheck, nextPath, started, actionTicks, pathAttempts;

    public FishingRodCraftGoal(PlayerNpcEntity npc) {
        this.npc = npc;
        placing = new PlacingBlockAi(npc);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Loaded visible tables only; call on an admitted >=40 tick decision cadence. */
    public static FishingRodPrerequisites.Snapshot snapshot(PlayerNpcEntity npc, ServerLevel level) {
        var inventory = npc.getInventory();
        boolean needed = SurvivalFishingGoal.decision(npc) == FoodSupply.State.NEED_ROD;
        int string = inventory.countItem(Items.STRING);
        int sticks = PlayerNpcCraftingUtil.countSticks(inventory);
        int wood = PlayerNpcCraftingUtil.countPlankEquivalent(inventory);
        boolean table = InventoryUtils.hasItem(npc.getInventory(), Items.CRAFTING_TABLE);
        if (!table && needed && string >= 2 && (sticks >= 3 || wood >= 2)) {
            for (BlockPos pos : BlockPos.betweenClosed(npc.blockPosition().offset(-4, -2, -4),
                    npc.blockPosition().offset(4, 2, 4))) {
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                        && SurvivalTasks.visible(npc, pos)) { table = true; break; }
            }
        }
        return new FishingRodPrerequisites.Snapshot(needed, string, sticks, wood, table);
    }

    public static String describe(PlayerNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel)) return "food rod crafting paused: server only";
        // Diagnostics are inventory-only; local station discovery belongs to goal admission.
        var inventory = npc.getInventory();
        return switch (FishingRodPrerequisites.choose(new FishingRodPrerequisites.Snapshot(
                SurvivalFishingGoal.decision(npc) == FoodSupply.State.NEED_ROD,
                inventory.countItem(Items.STRING), PlayerNpcCraftingUtil.countSticks(inventory),
                PlayerNpcCraftingUtil.countPlankEquivalent(inventory), true))) {
            case NOT_NEEDED -> "food rod crafting not needed";
            case BLOCKED_STRING -> "food acquisition blocked: fishing rod needs 2 carried string";
            case BLOCKED_WOOD -> "food acquisition blocked: fishing rod/table needs carried sticks or wood";
            case NEED_TABLE -> "food rod crafting needs a safe reachable table placement";
            case READY -> "food rod materials ready: requires a reachable crafting table";
        };
    }

    private boolean safe() {
        return SurvivalFishingGoal.decision(npc) == FoodSupply.State.NEED_ROD;
    }

    @Override public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level) || !safe() || npc.tickCount < nextCheck) return false;
        nextCheck = npc.tickCount + 40 + npc.getRandom().nextInt(20);
        BlockPos feet = npc.blockPosition();
        // Never interrupt the native gathering episode or strand its temporary pillar.
        if (GatherLogsGoal.isLogGatheringEpisodeActive(npc)
                || npc.isGatherLogsTemporaryPillarSupport(feet)
                || npc.isGatherLogsTemporaryPillarSupport(feet.below())
                || npc.isGatherLogsTemporaryPillarSupport(feet.below(2))) return false;
        var inventory = npc.getInventory();
        var readiness = FishingRodPrerequisites.choose(new FishingRodPrerequisites.Snapshot(true,
                inventory.countItem(Items.STRING), PlayerNpcCraftingUtil.countSticks(inventory),
                PlayerNpcCraftingUtil.countPlankEquivalent(inventory), true));
        if (readiness != FishingRodPrerequisites.State.READY && readiness != FishingRodPrerequisites.State.NEED_TABLE) {
            npc.setIdleTraceDetail(describe(npc), 40);
            nextCheck = npc.tickCount + 100;
            return false;
        }
        table = null; stand = null; path = null; placement = false; pathAttempts = 0;
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 2, 4))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                    && SurvivalTasks.visible(npc, pos)) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> npc.distanceToSqr(Vec3.atCenterOf(pos))));
        int attempts = 0;
        for (BlockPos candidate : candidates) {
            if (attempts++ >= 2) break;
            if (planStand(level, candidate)) { table = candidate; return true; }
        }
        // A visible but unreachable table must not waive the materials for a new table.
        var placementReadiness = FishingRodPrerequisites.choose(new FishingRodPrerequisites.Snapshot(true,
                inventory.countItem(Items.STRING), PlayerNpcCraftingUtil.countSticks(inventory),
                PlayerNpcCraftingUtil.countPlankEquivalent(inventory),
                InventoryUtils.hasItem(inventory, Items.CRAFTING_TABLE)));
        if (placementReadiness != FishingRodPrerequisites.State.READY
                && placementReadiness != FishingRodPrerequisites.State.NEED_TABLE) {
            nextCheck = npc.tickCount + 100;
            npc.setIdleTraceDetail("food acquisition blocked: no reachable table or carried table materials", 40);
            return false;
        }
        int examined = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 4; distance++) {
                BlockPos candidate = feet.relative(direction, distance);
                if (++examined > 16 || pathAttempts >= 4) break;
                if (!safePlacement(level, candidate)) continue;
                attempts++;
                if (planStand(level, candidate)) { table = candidate; placement = true; return true; }
            }
        }
        npc.setIdleTraceDetail("food acquisition blocked: no safe reachable crafting table", 40);
        nextCheck = npc.tickCount + 100;
        return false;
    }

    private boolean planStand(ServerLevel level, BlockPos candidate) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos possible = candidate.relative(direction);
            if (level.hasChunkAt(possible) && PathNavigationAi.canStandAt(level, possible)
                    && Vec3.atBottomCenterOf(possible).distanceToSqr(Vec3.atCenterOf(candidate)) <= 2.25 * 2.25) {
                if (pathAttempts++ >= 4) return false;
                Path candidatePath = npc.getNavigation().createPath(possible, 0);
                if (candidatePath == null || !candidatePath.canReach()) continue;
                stand = possible; path = candidatePath;
                return true;
            }
        }
        return false;
    }

    private boolean safePlacement(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.below()) || !level.hasChunkAt(pos.above())) return false;
        return level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && !PlayerNpcHomeUtil.isInsideBuildFootprint(npc, pos)
                && !PlayerNpcHomeUtil.getHome(npc).map(home -> PlayerNpcHomeUtil.isInside(home, pos)).orElse(false)
                && !FarmAi.isProtectedFarmlandBlock(npc, pos)
                && placing.canPlaceWithoutClipping(level, pos, Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    @Override public boolean canContinueToUse() {
        return !finished && table != null && safe() && npc.tickCount - started < 200;
    }

    @Override public void start() {
        finished = false; started = npc.tickCount; nextPath = npc.tickCount + 40; actionTicks = 0;
        npc.setCurrentAiState(AI_STATE);
        npc.setCurrentAiDetail("walking to a crafting table for a food fishing rod");
        npc.getNavigation().moveTo(path, 1.0);
    }

    @Override public void tick() {
        if (!(npc.level() instanceof ServerLevel level) || !safe() || table == null || !level.hasChunkAt(table)) {
            finished = true; return;
        }
        Vec3 center = Vec3.atCenterOf(table);
        npc.getLookControl().setLookAt(center.x, center.y, center.z);
        if (npc.distanceToSqr(center) > 2.25 * 2.25 || !placement && !SurvivalTasks.visible(npc, table)) {
            actionTicks = 0;
            if (npc.tickCount >= nextPath) {
                nextPath = npc.tickCount + 40;
                if (!level.hasChunkAt(stand) || !PathNavigationAi.canStandAt(level, stand)) {
                    fail("food rod crafting stand is unavailable"); return;
                }
                path = npc.getNavigation().createPath(stand, 0);
                if (path == null || !path.canReach()) { fail("food rod crafting table has no safe walking route"); return; }
                npc.getNavigation().moveTo(path, 1.0);
            }
            return;
        }
        npc.getNavigation().stop();
        if (++actionTicks < 6) return;
        actionTicks = 0;
        if (placement) {
            if (!safePlacement(level, table) || !SurvivalTasks.visible(npc, table.below())) {
                fail("food rod crafting table placement became unavailable"); return;
            }
            if (!InventoryUtils.hasItem(npc.getInventory(), Items.CRAFTING_TABLE)
                    && !CookingCraftingExecutor.tryCraft(npc, CookingCraftingExecutor.Action.CRAFTING_TABLE, null)) {
                fail("food rod crafting table needs carried wood and inventory space"); return;
            }
            ItemStack item = npc.consumeInventoryItem(Items.CRAFTING_TABLE, 1).orElse(ItemStack.EMPTY);
            if (item.isEmpty()) { fail("food rod crafting table is no longer carried"); return; }
            if (!placing.placeBlock(level, table, Blocks.CRAFTING_TABLE.defaultBlockState())) {
                ItemStack remainder = InventoryUtils.addItemAndReturnRemainder(npc.getInventory(), item);
                if (!remainder.isEmpty()) npc.spawnAtLocation(remainder);
                fail("food rod crafting table placement failed"); return;
            }
            placement = false;
            npc.setCurrentAiDetail("placed a crafting table for a food fishing rod");
            return;
        }
        if (PlayerNpcFishingGoal.hasFishingRod(npc)) { finished = true; return; }
        if (!CookingCraftingExecutor.tryCraft(npc, CookingCraftingExecutor.Action.FISHING_ROD, table)) {
            fail("food rod crafting blocked: needs native recipe, carried materials and inventory space"); return;
        }
        placing.playMainHandAction();
        npc.setCurrentAiDetail("crafted a fishing rod from carried materials for food");
        finished = true;
    }

    private void fail(String detail) {
        npc.setCurrentAiDetail(detail); finished = true; nextCheck = npc.tickCount + 100;
    }

    @Override public void stop() {
        npc.getNavigation().stop(); table = null; stand = null; path = null;
        if (AI_STATE.equals(npc.getCurrentAiState())) npc.setCurrentAiState(PlayerNpcEntity.AI_IDLE);
    }
}

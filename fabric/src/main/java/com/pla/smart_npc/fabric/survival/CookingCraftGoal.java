package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.FarmAi;
import com.pla.smart_npc.entity.ai.FurnaceAi;
import com.pla.smart_npc.entity.ai.PathNavigationAi;
import com.pla.smart_npc.entity.ai.PlacingBlockAi;
import com.pla.smart_npc.entity.goal.GatherLogsGoal;
import com.pla.smart_npc.util.InventoryUtils;
import com.pla.smart_npc.util.PlayerNpcCraftingUtil;
import com.pla.smart_npc.util.PlayerNpcBuildLayoutLoader;
import com.pla.smart_npc.util.PlayerNpcBuildMaterialUtil;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;

/** Crafts only a committed cooking task's starter pickaxe or furnace at a real nearby table. */
public final class CookingCraftGoal extends Goal {
    private final PlayerNpcEntity npc;
    private final BooleanSupplier committedCooking;
    private final PlacingBlockAi placing;
    private BlockPos table, stand;
    private Path path;
    private CookingCraftingExecutor.Action action;
    private boolean placement, finished;
    private int nextCheck, nextPath, started, actionTicks;

    public CookingCraftGoal(PlayerNpcEntity npc, BooleanSupplier committedCooking) {
        this.npc = npc;
        this.committedCooking = committedCooking;
        this.placing = new PlacingBlockAi(npc);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Loaded visible station facts only. Call on an admitted >=20 tick decision cadence. */
    public static CookingPrerequisites.Snapshot snapshot(PlayerNpcEntity npc, ServerLevel level) {
        FurnaceBlockEntity tracked = SurvivalTasks.trackedFurnace(npc, level);
        boolean furnace = InventoryUtils.hasItem(npc, Items.FURNACE) || tracked != null;
        boolean stationFuel = hasStationFuel(tracked);
        boolean table = InventoryUtils.hasItem(npc, Items.CRAFTING_TABLE);
        for (BlockPos pos : BlockPos.betweenClosed(npc.blockPosition().offset(-4, -2, -4),
                npc.blockPosition().offset(4, 2, 4))) {
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            if ((!furnace && state.is(Blocks.FURNACE) && isOwnedHomeFurnace(npc, level, pos)
                    || !table && state.is(Blocks.CRAFTING_TABLE))
                    && SurvivalTasks.visible(npc, pos)) {
                furnace |= state.is(Blocks.FURNACE);
                table |= state.is(Blocks.CRAFTING_TABLE);
                if (state.is(Blocks.FURNACE) && level.getBlockEntity(pos) instanceof FurnaceBlockEntity homeFurnace) {
                    stationFuel |= hasStationFuel(homeFurnace);
                }
            }
            if (furnace && table) break;
        }
        var inventory = npc.getInventory();
        var furnaceAi = new FurnaceAi(npc);
        return new CookingPrerequisites.Snapshot(
                SurvivalTasks.cookingNeeded(npc, level),
                furnace, furnaceAi.hasFuel() || stationFuel, npc.hasCarriedTool(ItemTags.PICKAXES), table,
                PlayerNpcCraftingUtil.countFurnaceStone(inventory),
                PlayerNpcCraftingUtil.countPlankEquivalent(inventory), PlayerNpcCraftingUtil.countSticks(inventory));
    }

    private static boolean hasStationFuel(FurnaceBlockEntity furnace) {
        return furnace != null && (!furnace.getItem(1).isEmpty()
                && FurnaceAi.isFuel(furnace.getLevel(), furnace.getItem(1))
                || furnace.getBlockState().getValue(BlockStateProperties.LIT));
    }

    private static boolean isOwnedHomeFurnace(PlayerNpcEntity npc, ServerLevel level, BlockPos pos) {
        var home = PlayerNpcHomeUtil.getHome(npc).orElse(null);
        if (home == null || !PlayerNpcHomeUtil.isInside(home, pos)) return false;
        var layout = PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout).orElse(null);
        if (layout == null || layout.width() != home.width() || layout.depth() != home.depth()) return false;
        for (var block : layout.blocks()) {
            if (block.state().is(Blocks.FURNACE) && block.toWorld(home.origin()).equals(pos)
                    && PlayerNpcBuildMaterialUtil.matches(level.getBlockState(pos), block.state())) return true;
        }
        return false;
    }

    private boolean safe() {
        return committedCooking.getAsBoolean() && npc.isAlive() && !npc.isNoAi()
                && !npc.isPassenger() && !npc.isHealing() && !npc.isSleeping()
                && !npc.isOnFire() && npc.getTarget() == null && !npc.isTeamFollower()
                && SurvivalTasks.memory(npc).cookingDimension.equals(SurvivalTasks.dimension(npc))
                && npc.getHealth() > npc.getMaxHealth() * 0.5F;
    }

    @Override public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level) || !safe() || npc.tickCount < nextCheck) return false;
        nextCheck = npc.tickCount + 20 + npc.getRandom().nextInt(20);
        // Native gathering must finish its admitted route and recover owned pillar support
        // before higher-priority crafting can take MOVE/LOOK, including a paused episode.
        BlockPos feet = npc.blockPosition();
        if (GatherLogsGoal.isLogGatheringEpisodeActive(npc)
                || npc.isGatherLogsTemporaryPillarSupport(feet)
                || npc.isGatherLogsTemporaryPillarSupport(feet.below())
                || npc.isGatherLogsTemporaryPillarSupport(feet.below(2))) return false;
        action = switch (CookingPrerequisites.choose(snapshot(npc, level))) {
            case NEED_PICKAXE -> CookingCraftingExecutor.Action.WOODEN_PICKAXE;
            case NEED_FURNACE -> CookingCraftingExecutor.Action.FURNACE;
            default -> null;
        };
        if (action == null) return false;
        table = null; stand = null; path = null;
        placement = false;
        var candidates = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(npc.blockPosition().offset(-4, -2, -4),
                npc.blockPosition().offset(4, 2, 4))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                    && SurvivalTasks.visible(npc, pos)) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> npc.distanceToSqr(Vec3.atCenterOf(pos))));
        int attempts = 0;
        for (BlockPos candidate : candidates) {
            if (attempts++ >= 2) break;
            if (planStand(level, candidate)) { table = candidate; return true; }
        }
        if (!InventoryUtils.hasItem(npc.getInventory(), Items.CRAFTING_TABLE)
                && !PlayerNpcCraftingUtil.canCraftCraftingTable(npc.getInventory())) return false;
        // At most sixteen local placement candidates and four path attempts in total.
        int examined = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int distance = 1; distance <= 4; distance++) {
                BlockPos candidate = npc.blockPosition().relative(direction, distance);
                if (++examined > 16 || attempts >= 4) break;
                if (!safePlacement(level, candidate)) continue;
                attempts++;
                if (planStand(level, candidate)) { table = candidate; placement = true; return true; }
            }
        }
        detail("cooking crafting waits for a safe reachable table");
        nextCheck = npc.tickCount + 100;
        return false;
    }

    private boolean planStand(ServerLevel level, BlockPos candidate) {
        // Select geometry first; only one navigation attempt per station candidate.
        BlockPos selected = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos possible = candidate.relative(direction);
            if (PathNavigationAi.canStandAt(level, possible)
                    && Vec3.atBottomCenterOf(possible).distanceToSqr(Vec3.atCenterOf(candidate)) <= 2.25 * 2.25) {
                selected = possible;
                break;
            }
        }
        if (selected == null) return false;
        Path candidatePath = npc.getNavigation().createPath(selected, 0);
        if (candidatePath == null || !candidatePath.canReach()) return false;
        stand = selected; path = candidatePath;
        return true;
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
        npc.setCurrentAiState("ai.player_npc.crafting");
        detail("walking to a table for cooking supplies");
        npc.getNavigation().moveTo(path, 1.0);
    }

    @Override public void tick() {
        if (!(npc.level() instanceof ServerLevel level) || !safe() || table == null || !level.hasChunkAt(table)) {
            finished = true; return;
        }
        npc.getLookControl().setLookAt(Vec3.atCenterOf(table).x, Vec3.atCenterOf(table).y, Vec3.atCenterOf(table).z);
        boolean close = npc.distanceToSqr(Vec3.atCenterOf(table)) <= 2.25 * 2.25;
        if (!close || !placement && !SurvivalTasks.visible(npc, table)) {
            actionTicks = 0;
            if (npc.tickCount >= nextPath) {
                nextPath = npc.tickCount + 40;
                if (!PathNavigationAi.canStandAt(level, stand)) { fail("cooking table stand is unavailable"); return; }
                path = npc.getNavigation().createPath(stand, 0);
                if (path == null || !path.canReach()) { fail("cooking table has no safe walking route"); return; }
                npc.getNavigation().moveTo(path, 1.0);
            }
            return;
        }
        npc.getNavigation().stop();
        if (++actionTicks < 6) return;
        actionTicks = 0;
        if (placement) {
            if (!safePlacement(level, table) || !SurvivalTasks.visible(npc, table.below())) {
                fail("cooking table placement became unavailable"); return;
            }
            if (!InventoryUtils.hasItem(npc.getInventory(), Items.CRAFTING_TABLE)
                    && !CookingCraftingExecutor.tryCraft(npc, CookingCraftingExecutor.Action.CRAFTING_TABLE, null)) {
                fail("cooking table needs craftable wood and inventory space"); return;
            }
            ItemStack item = npc.consumeInventoryItem(Items.CRAFTING_TABLE, 1).orElse(ItemStack.EMPTY);
            if (item.isEmpty()) { fail("cooking table is no longer carried"); return; }
            if (!placing.placeBlock(level, table, Blocks.CRAFTING_TABLE.defaultBlockState())) {
                ItemStack remainder = InventoryUtils.addItemAndReturnRemainder(npc.getInventory(), item);
                if (!remainder.isEmpty()) npc.spawnAtLocation(remainder);
                fail("cooking table placement failed"); return;
            }
            placement = false;
            detail("placed a crafting table for cooking supplies");
            return;
        }
        // An interruption may have fulfilled this need or consumed some materials.
        if (action == CookingCraftingExecutor.Action.WOODEN_PICKAXE && npc.hasCarriedTool(ItemTags.PICKAXES)
                || action == CookingCraftingExecutor.Action.FURNACE && InventoryUtils.hasItem(npc, Items.FURNACE)) {
            finished = true; return;
        }
        if (!CookingCraftingExecutor.tryCraft(npc, action, table)) {
            fail("cooking supplies need a valid recipe, materials and inventory space"); return;
        }
        placing.playMainHandAction();
        detail(action == CookingCraftingExecutor.Action.FURNACE ? "crafted a furnace for cooking food" : "crafted a pickaxe for cooking resources");
        finished = true;
    }

    private void detail(String detail) { npc.setCurrentAiDetail(detail); }
    private void fail(String detail) { detail(detail); finished = true; nextCheck = npc.tickCount + 100; }

    @Override public void stop() {
        npc.getNavigation().stop(); table = null; stand = null; path = null;
        if ("ai.player_npc.crafting".equals(npc.getCurrentAiState())) npc.setCurrentAiState(PlayerNpcEntity.AI_IDLE);
    }
}

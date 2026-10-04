package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class SurvivalTasks {
    public static final String KEY = "SurvivalResources";
    private static final Map<PlayerNpcEntity, ResourceMemory> MEMORIES = new WeakHashMap<>();
    private SurvivalTasks() {}
    public static ResourceMemory memory(PlayerNpcEntity npc) {
        return MEMORIES.computeIfAbsent(npc, n -> ResourceMemory.load(PersistentData.get(n).getCompoundOrEmpty(KEY)));
    }
    public static void save(PlayerNpcEntity npc) { PersistentData.get(npc).put(KEY, memory(npc).save()); }
    public static String dimension(PlayerNpcEntity npc) { return npc.level().dimension().identifier().toString(); }
    public static boolean isCoal(BlockState state) { return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE); }
    public static boolean visible(PlayerNpcEntity npc, BlockPos pos) {
        var origin = npc.blockPosition();
        for (int x = Math.min(origin.getX(), pos.getX()) >> 4; x <= (Math.max(origin.getX(), pos.getX()) >> 4); x++)
            for (int z = Math.min(origin.getZ(), pos.getZ()) >> 4; z <= (Math.max(origin.getZ(), pos.getZ()) >> 4); z++)
                if (!npc.level().hasChunkAt(new BlockPos(x * 16, pos.getY(), z * 16))) return false;
        return npc.level().clip(new ClipContext(npc.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc)).getBlockPos().equals(pos);
    }
    public static void request(PlayerNpcEntity npc, int additional) {
        var m = memory(npc);
        m.cookingActive = false; m.cookingStatus = "paused for requested resource task";
        m.request(dimension(npc), npc.blockPosition().asLong(), npc.getInventory().countItem(Items.COAL), additional);
        save(npc);
    }

    public static boolean cookingActive(PlayerNpcEntity npc) {
        var m = memory(npc);
        return m.cookingActive && !(m.active && !m.automatic);
    }
    private static boolean canPursueCooking(PlayerNpcEntity npc) {
        return cookingActive(npc) && npc.isAlive() && !npc.isNoAi() && !npc.isPassenger()
            && npc.getTarget() == null && !npc.isOnFire() && !npc.isHealing() && !npc.isSleeping()
            && !npc.isTeamFollower() && npc.getHealth() > npc.getMaxHealth() * 0.5F
            && memory(npc).cookingDimension.equals(dimension(npc));
    }
    public static boolean needsCookingLogs(PlayerNpcEntity npc) {
        return canPursueCooking(npc) && memory(npc).cookingStep.equals("NEED_WOOD")
            && com.pla.smart_npc.entity.ai.ResourceAi.countLogs(npc) < memory(npc).cookingLogGoal;
    }
    public static boolean needsCookingStone(PlayerNpcEntity npc) {
        return canPursueCooking(npc) && memory(npc).cookingStep.equals("NEED_STONE")
            && com.pla.smart_npc.util.PlayerNpcCraftingUtil.countFurnaceStone(npc.getInventory()) < 8;
    }
    public static boolean needsCookingPickaxe(PlayerNpcEntity npc) {
        return canPursueCooking(npc) && memory(npc).cookingStep.equals("NEED_PICKAXE");
    }
    public static int cookingLogTarget(PlayerNpcEntity npc) { return memory(npc).cookingLogGoal; }
    public static int cookingStoneTarget(PlayerNpcEntity npc) { return 8; }

    private static boolean cookedFood(net.minecraft.world.item.ItemStack stack) {
        return stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) || stack.is(Items.COOKED_CHICKEN)
            || stack.is(Items.COOKED_MUTTON) || stack.is(Items.COOKED_RABBIT) || stack.is(Items.COOKED_COD)
            || stack.is(Items.COOKED_SALMON) || stack.is(Items.BAKED_POTATO);
    }
    public static int cookedFoodCount(PlayerNpcEntity npc) {
        int count = 0;
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++)
            if (cookedFood(npc.getInventory().getItem(i))) count += npc.getInventory().getItem(i).getCount();
        return count;
    }
    /** Called only after a native output transfer increased real carried cooked-food stock. */
    public static void cookedOutputCollected(PlayerNpcEntity npc, net.minecraft.world.item.ItemStack output, int previousCount) {
        if (cookingActive(npc) && cookedFood(output) && cookedFoodCount(npc) > previousCount) {
            memory(npc).cookingProduced = true; save(npc);
        }
    }
    public static net.minecraft.world.level.block.entity.FurnaceBlockEntity trackedFurnace(PlayerNpcEntity npc, ServerLevel level) {
        var data = PersistentData.get(npc);
        if (com.pla.smart_npc.entity.ai.FurnaceAi.TEMP_FURNACE_KIND_COOKING.equals(
                data.getStringOr(com.pla.smart_npc.entity.ai.FurnaceAi.TEMP_FURNACE_KIND, ""))
                && dimension(npc).equals(data.getStringOr(
                com.pla.smart_npc.entity.goal.CookFoodGoal.TEMP_FURNACE_DIMENSION, ""))) {
            var pos = new BlockPos(data.getIntOr(com.pla.smart_npc.entity.ai.FurnaceAi.TEMP_FURNACE_X, 0),
                data.getIntOr(com.pla.smart_npc.entity.ai.FurnaceAi.TEMP_FURNACE_Y, 0),
                data.getIntOr(com.pla.smart_npc.entity.ai.FurnaceAi.TEMP_FURNACE_Z, 0));
            if (npc.blockPosition().distSqr(pos) <= 8 * 8
                    && com.pla.smart_npc.entity.ai.FurnaceAi.hasValidTrackedTemporaryFurnace(level, npc))
                return (net.minecraft.world.level.block.entity.FurnaceBlockEntity)level.getBlockEntity(pos);
        }
        var home = com.pla.smart_npc.util.PlayerNpcHomeUtil.getHome(npc);
        var layout = com.pla.smart_npc.util.PlayerNpcHomeUtil.getHomeLayoutId(npc)
            .flatMap(com.pla.smart_npc.util.PlayerNpcBuildLayoutLoader::getLayout);
        if (home.isPresent() && layout.isPresent() && home.get().width() == layout.get().width()
                && home.get().depth() == layout.get().depth()
                && npc.blockPosition().distSqr(home.get().origin()) <= 8 * 8) {
            for (var block : layout.get().blocks()) {
                if (!block.state().is(Blocks.FURNACE)) continue;
                var pos = block.toWorld(home.get().origin());
                if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.FURNACE)
                    && level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace)
                    return furnace;
            }
        }
        return null;
    }
    public static boolean cookingNeeded(PlayerNpcEntity npc, ServerLevel level) {
        if (com.pla.smart_npc.entity.ai.FurnaceAi.isCookableFood(npc.getMainHandItem())
            || com.pla.smart_npc.entity.ai.FurnaceAi.isCookableFood(npc.getOffhandItem())) return true;
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++)
            if (com.pla.smart_npc.entity.ai.FurnaceAi.isCookableFood(npc.getInventory().getItem(i))) return true;
        var furnace = trackedFurnace(npc, level);
        return furnace != null && (com.pla.smart_npc.entity.ai.FurnaceAi.isCookableFood(furnace.getItem(0))
            || cookedFood(furnace.getItem(2)));
    }

    @Events.SubscribeEvent public static void observe(Events.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PlayerNpcEntity npc) || npc.isNoAi()
                || !(npc.level() instanceof ServerLevel level)
                || Math.floorMod(npc.tickCount + npc.getUUID().hashCode(), 20) != 0) return;
        observeNearby(npc, level);
        planFuel(npc, level);
    }

    public static void planFuel(PlayerNpcEntity npc, ServerLevel level) {
        var m = memory(npc); long now = level.getGameTime();
        if (m.active) {
            if (m.automatic && now - m.started >= 1200) {
                m.active = false; m.nextDecision = now + 1200;
                m.status = "cooking fuel paused: " + m.status;
                save(npc);
            }
            if (!m.automatic) return;
        }
        if (now < m.cookingNextDecision || npc.getTarget() != null || npc.isOnFire() || npc.isHealing()
                || npc.isSleeping() || npc.isTeamFollower() || npc.getHealth() <= npc.getMaxHealth() * 0.5F) return;
        m.cookingNextDecision = now + 40;
        boolean cooking = cookingNeeded(npc, level);
        if (!m.cookingActive) {
            if (!cooking) return;
            m.cookingActive = true; m.cookingDimension = dimension(npc); m.cookingStarted = now;
            m.cookingOrigin = npc.blockPosition().asLong(); m.cookingProduced = false;
            m.cookingBaselineCooked = cookedFoodCount(npc);
        }
        if (!m.cookingDimension.equals(dimension(npc))) {
            m.cookingStatus = "waiting in cooking dimension"; save(npc); return;
        }
        if (!cooking || now - m.cookingStarted >= 6000) {
            m.cookingActive = false; m.cookingNextDecision = now + 1200;
            if (m.active && m.automatic) { m.active = false; m.status = "cancelled: cooking intention ended"; }
            m.cookingStatus = !cooking && m.cookingProduced ? "complete: cooked food collected"
                : !cooking ? "paused: cooking inputs disappeared without collected food"
                : "paused: cooking prerequisite time limit reached";
            save(npc); return;
        }
        var snapshot = CookingCraftGoal.snapshot(npc, level);
        var knownFurnace = trackedFurnace(npc, level);
        boolean alternativeFuel = false;
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++) {
            var stack = npc.getInventory().getItem(i);
            alternativeFuel |= !stack.is(Items.COAL) && com.pla.smart_npc.entity.ai.FurnaceAi.isFuel(level, stack);
        }
        // Preserve the requested coal reserve/return unless another fuel source or a live
        // furnace has already fulfilled this child task's purpose. Manual tasks are never cancelled here.
        if (m.active && m.automatic && (alternativeFuel || knownFurnace != null
                && knownFurnace.getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT))) {
            m.active = false; m.status = "cancelled: cooking already has fuel";
        }
        boolean known = m.coal.stream().anyMatch(o -> o.dimension().equals(dimension(npc))
            && now - o.seen() < ResourceMemory.MAX_AGE
            && npc.blockPosition().distSqr(BlockPos.of(o.position())) <= 32 * 32);
        var decision = CookingPrerequisites.choose(snapshot);
        // Use locally obtainable wood fuel when there is no remembered coal; no pickaxe
        // or new mining system is necessary just to cook at an existing furnace.
        boolean woodFuel = snapshot.furnaceReady() && !snapshot.fuelReady() && !known;
        if (woodFuel) decision = CookingPrerequisites.State.NEED_WOOD;
        m.cookingStep = decision.name();
        if (decision == CookingPrerequisites.State.NEED_WOOD) {
            int requiredPlanks = !snapshot.pickaxeReady() && snapshot.furnaceStone() < 8
                ? (snapshot.craftingTableReady() ? 0 : 4) + 3 + (snapshot.sticks() >= 2 ? 0 : 2) : 4;
            int additionalLogs = woodFuel ? 1 : Math.max(1, (requiredPlanks - snapshot.plankEquivalent() + 3) / 4);
            m.cookingLogGoal = Math.min(64, com.pla.smart_npc.entity.ai.ResourceAi.countLogs(npc) + additionalLogs);
        }
        m.cookingStatus = switch (decision) {
            case NEED_WOOD -> woodFuel ? "collect nearby wood for cooking fuel" : "collect nearby wood for crafting prerequisites";
            case NEED_PICKAXE -> "craft a starter pickaxe at a real crafting table";
            case NEED_STONE -> "collect eight furnace stones with a suitable pickaxe";
            case NEED_FURNACE -> "craft a furnace at a real crafting table";
            case NEED_FUEL -> "gather fuel from a remembered coal source";
            case READY -> "cook and collect food using the existing furnace routine";
        };
        if (decision == CookingPrerequisites.State.NEED_FUEL && !m.active && now >= m.nextDecision) {
            m.request(dimension(npc), m.cookingOrigin, npc.getInventory().countItem(Items.COAL),
                Math.max(1, 2 - npc.getInventory().countItem(Items.COAL)));
            m.automatic = true; m.purpose = "fuel for cooking food"; m.started = now; m.nextDecision = now + 1200;
            m.status = "gathering fuel for cooking food";
        }
        save(npc);
    }

    public static String describe(PlayerNpcEntity npc) {
        var m = memory(npc);
        return m.cookingStatus + "; " + m.purpose + ": " + m.status;
    }

    /** Twenty-four loaded-block probes per second per actor; no chunk requests or hidden ore lookup. */
    public static void observeNearby(PlayerNpcEntity npc, ServerLevel level) {
        var m = memory(npc); long now = level.getGameTime();
        boolean changed = m.coal.removeIf(o -> now - o.seen() > ResourceMemory.MAX_AGE);
        for (int probe = 0; probe < 24; probe++) {
            int i = m.cursor++ % 405;
            var pos = npc.blockPosition().offset(i % 9 - 4, (i / 9) % 5 - 2, i / 45 - 4);
            if (level.hasChunkAt(pos) && isCoal(level.getBlockState(pos)) && visible(npc, pos)) {
                m.observe(dimension(npc), pos.asLong(), now); changed = true;
            }
        }
        m.cursor %= 405;
        if (changed) save(npc);
    }

    @Events.SubscribeEvent public static void unload(Events.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof PlayerNpcEntity npc) MEMORIES.remove(npc);
    }
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event) { MEMORIES.clear(); }

    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("smart_npc")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("survival").then(Commands.argument("npc", EntityArgument.entity())
                .then(Commands.literal("coal").then(Commands.argument("additional", IntegerArgumentType.integer(1, 64))
                    .executes(c -> {
                        if (!(EntityArgument.getEntity(c, "npc") instanceof PlayerNpcEntity npc)) {
                            c.getSource().sendFailure(Component.literal("Select a Smart NPC.")); return 0;
                        }
                        if (memory(npc).active) {
                            c.getSource().sendFailure(Component.literal("Task already active; cancel it before replacing it.")); return 0;
                        }
                        request(npc, IntegerArgumentType.getInteger(c, "additional"));
                        c.getSource().sendSuccess(() -> Component.literal("Coal task started: " + memory(npc).target + " total coal, then return here."), true);
                        return 1;
                    })))
                .then(Commands.literal("status").executes(c -> {
                    if (!(EntityArgument.getEntity(c, "npc") instanceof PlayerNpcEntity npc)) return 0;
                    var m = memory(npc);
                    c.getSource().sendSuccess(() -> Component.literal(describe(npc) + "; cooking=" + m.cookingActive + "/" + m.cookingStep + "; active=" + m.active + "; automatic=" + m.automatic + "; coal="
                        + npc.getInventory().countItem(Items.COAL) + "/" + m.target + "; observations=" + m.coal.size()), false);
                    return 1;
                }))
                .then(Commands.literal("shelter").executes(c -> {
                    if (!(EntityArgument.getEntity(c, "npc") instanceof PlayerNpcEntity npc)
                            || !(npc.level() instanceof ServerLevel level)) return 0;
                    c.getSource().sendSuccess(() -> Component.literal(ShelterReadiness.assess(npc, level).describe()), false);
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(c -> {
                    if (!(EntityArgument.getEntity(c, "npc") instanceof PlayerNpcEntity npc)) return 0;
                    var m = memory(npc); m.active = false; m.cookingActive = false;
                    m.cookingStatus = "cancelled"; m.status = "cancelled";
                    m.nextDecision = npc.level().getGameTime() + 1200; m.cookingNextDecision = m.nextDecision; save(npc);
                    c.getSource().sendSuccess(() -> Component.literal("Coal task cancelled."), true); return 1;
                })))));
    }
}

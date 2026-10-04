import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.FurnaceAi;
import com.pla.smart_npc.entity.goal.CookFoodGoal;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import com.pla.smart_npc.entity.goal.StartupWorkGatedGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.fabric.survival.CookingCraftingExecutor;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Real selector/admission-driven prerequisite chain in the disposable verification world. */
public final class SmartNpcCookingChecks {
    private static final int DEADLINE_TICKS = 3000;
    private static final Set<String> RETAINED = Set.of("SurvivalGatherGoal", "CookingCraftGoal",
            "GatherCoalGoal", "CookFoodGoal", "PickupNearbyItemGoal");
    private static PlayerNpcEntity npc;
    private static ServerLevel level;
    private static ServerPlayer fixturePlayer;
    private static BlockPos base;
    private static final List<BlockPos> stones = new ArrayList<>();
    private static int ticks;
    private static boolean committed, woodenPick, damagedPick, table, furnace;
    private static boolean daylight, checkpointPassed;
    private static int maxCobblestone;
    public static volatile boolean passed;

    private SmartNpcCookingChecks() {}

    private static void check(boolean condition, String message) {
        SmartNpcFunctional.check(condition, message);
    }

    private static Goal unwrap(Goal goal) {
        while (true) {
            if (goal instanceof StartupWorkGatedGoal startup) goal = startup.getDelegateGoal();
            else if (goal instanceof InterestGatedGoal interest) goal = interest.getDelegateGoal();
            else return goal;
        }
    }

    public static void setup(ServerLevel testLevel, ServerPlayer player) {
        level = testLevel;
        fixturePlayer = player;
        passed = false;
        ticks = 0;
        committed = woodenPick = damagedPick = table = furnace = false;
        daylight = checkpointPassed = false;
        maxCobblestone = 0;
        stones.clear();
        base = new BlockPos(player.getBlockX(), 290, player.getBlockZ());
        var fixtureBounds = new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(-8, -3, -8)), net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(9, 6, 9)));
        for (var item : level.getEntitiesOfClass(ItemEntity.class, fixtureBounds)) item.discard();
        for (var previousActor : level.getEntitiesOfClass(PlayerNpcEntity.class, fixtureBounds)) previousActor.discard();
        // Bedrock cannot be harvested as spare crafting stone. All useful materials are
        // accounted for in the carried four logs and the explicitly exposed stone wall.
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            level.getChunk(base.offset(x, 0, z));
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState());
            for (int y = 0; y <= 4; y++) {
                level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        for (int z = -2; z <= 3; z++) for (int y = 0; y <= 1; y++) {
            BlockPos pos = base.offset(4, y, z);
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            stones.add(pos);
        }
        // Fuel is available through real perception/mining if the parent requests coal.
        level.setBlockAndUpdate(base.offset(-4, 0, 2), Blocks.COAL_ORE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(-4, 0, 3), Blocks.COAL_ORE.defaultBlockState());

        checkFailedCraftConservation();
        checkOutputTransferConservation();

        npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        var personality = SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
                .map(SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
                .filter(name -> !name.interests().contains(PlayerNpcInterest.BUILDING)
                        && !name.interests().contains(PlayerNpcInterest.MINING))
                .sorted(java.util.Comparator.comparing(name -> !name.skinName().equals("Technoblade")))
                .findFirst().orElseThrow();
        npc.setUsername(personality.skinName());
        npc.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        npc.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.BEEF, 2));
        npc.getInventory().addItem(new ItemStack(Items.OAK_LOG, 4));
        npc.setHealth(npc.getMaxHealth());
        npc.setNoAi(false);
        npc.setNoGravity(false);
        npc.getRandom().setSeed(90241004L);
        level.addFreshEntity(npc);
        try {
            var goalsField = Mob.class.getDeclaredField("goalSelector");
            goalsField.setAccessible(true);
            GoalSelector goals = (GoalSelector) goalsField.get(npc);
            goals.removeAllGoals(goal -> !RETAINED.contains(unwrap(goal).getClass().getSimpleName()));
            var targetsField = Mob.class.getDeclaredField("targetSelector");
            targetsField.setAccessible(true);
            ((GoalSelector) targetsField.get(npc)).removeAllGoals(goal -> true);
            for (String required : Set.of("SurvivalGatherGoal", "CookingCraftGoal", "CookFoodGoal", "PickupNearbyItemGoal")) {
                check(goals.getAvailableGoals().stream().anyMatch(wrapped ->
                        wrapped.getGoal() instanceof StartupWorkGatedGoal
                                && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(required)),
                        "cooking fixture preserves actual registered admission wrapper for " + required);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
        check(!npc.hasInterest(PlayerNpcInterest.BUILDING) && !npc.hasInterest(PlayerNpcInterest.MINING),
                "cooking fixture personality has no building or mining interest");
        check(count(Items.OAK_LOG) == 4 && count(Items.BEEF) == 2 && count(Items.COBBLESTONE) == 0
                        && count(Items.WOODEN_PICKAXE) == 0 && count(Items.FURNACE) == 0
                        && count(Items.CRAFTING_TABLE) == 0 && count(Items.COAL) == 0,
                "cooking fixture starts with raw food and logs, without tools stone manufactured stations or coal");
        System.out.println("SMARTNPC_COOKING_FIXTURE " + personality.skinName() + " at " + base);
    }

    private static int count(Item item) {
        return stacks().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> stacks() {
        var result = new ArrayList<ItemStack>();
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            result.add(npc.getInventory().getItem(slot));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) result.add(npc.getItemBySlot(slot));
        return result;
    }

    private static long removedStone() {
        return stones.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.STONE)).count();
    }

    private static String diagnostic() {
        var memory = SurvivalTasks.memory(npc);
        return "ticks=" + ticks + " step=" + memory.cookingStep + " status=" + memory.status
                + " waitingForTurn=" + PlayerNpcAiWorkBudget.isWaitingForTurn(npc)
                + " budget=" + PlayerNpcAiWorkBudget.resourceSnapshot(level.getServer())
                + " ai=" + npc.getCurrentAiState() + " detail=" + npc.getCurrentAiDetail()
                + " pos=" + npc.blockPosition() + " committed=" + committed
                + " table=" + table + " furnace=" + furnace + " woodpick=" + woodenPick
                + " damagedPick=" + damagedPick + " removedStone=" + removedStone()
                + " peakCobble=" + maxCobblestone + " inventory=" + stacks();
    }

    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        if (!daylight && SmartNpcSurvivalChecks.sleepPassed) {
            server.getCommands().performPrefixedCommand(fixturePlayer.createCommandSourceStack(), "time set 6000");
            daylight = true;
            System.out.println("SMARTNPC_COOKING_DAYLIGHT after native sleep fixture completed");
        }
        ticks++;
        committed |= SurvivalTasks.cookingActive(npc);
        if (!checkpointPassed && SurvivalTasks.cookingActive(npc)
                && "NEED_STONE".equals(SurvivalTasks.memory(npc).cookingStep)) {
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
            npc.saveWithoutId(output);
            var clone = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.LOAD);
            clone.load(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
            var before = SurvivalTasks.memory(npc);
            var after = SurvivalTasks.memory(clone);
            check(after.cookingActive && after.cookingStep.equals(before.cookingStep)
                    && after.cookingDimension.equals(before.cookingDimension)
                    && after.cookingOrigin == before.cookingOrigin
                    && after.cookingStarted == before.cookingStarted
                    && clone.getInventory().countItem(Items.WOODEN_PICKAXE) == count(Items.WOODEN_PICKAXE),
                    "pending cooking prerequisite checkpoint and real tool survive entity serialization");
            clone.discard();
            checkpointPassed = true;
        }
        woodenPick |= count(Items.WOODEN_PICKAXE) > 0;
        damagedPick |= stacks().stream().anyMatch(stack -> stack.is(Items.WOODEN_PICKAXE) && stack.getDamageValue() > 0);
        maxCobblestone = Math.max(maxCobblestone, count(Items.COBBLESTONE));
        if (ticks % 10 == 0) {
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-7, 0, -7), base.offset(7, 2, 7))) {
                table |= level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
                furnace |= level.getBlockState(pos).is(Blocks.FURNACE);
            }
        }
        checkNoUnrelatedGear();
        if (ticks % 200 == 0) System.out.println("SMARTNPC_COOKING_PROGRESS " + diagnostic());
        if (count(Items.COOKED_BEEF) >= 2 && committed && !SurvivalTasks.cookingActive(npc)) {
            check(checkpointPassed, "cooking checkpoint test completed before stone gathering");
            check(woodenPick && damagedPick, "autonomous cooking crafts and wears a real wooden pickaxe");
            check(removedStone() >= 8 && maxCobblestone >= 8,
                    "furnace prerequisites come from real stone breaking and drop pickup");
            check(table && furnace, "autonomous cooking places real crafting table and furnace");
            check(count(Items.BEEF) == 0 && count(Items.COOKED_BEEF) == 2,
                    "native furnace converts the two supplied raw beef into two cooked beef");
            check(!SurvivalTasks.memory(npc).active,
                    "durable parent cooking intent completes after prerequisites fuel and cooking");
            System.out.println("SMARTNPC_COOKING_COMPLETE " + diagnostic());
            passed = true;
            npc.discard();
            npc = null;
            return;
        }
        if (ticks >= DEADLINE_TICKS) throw new AssertionError("Autonomous native cooking timed out: " + diagnostic());
    }

    private static void checkNoUnrelatedGear() {
        if (count(Items.WOODEN_AXE) > 0 || count(Items.WOODEN_SHOVEL) > 0 || count(Items.WOODEN_SWORD) > 0
                || count(Items.STONE_AXE) > 0 || count(Items.STONE_SHOVEL) > 0 || count(Items.STONE_SWORD) > 0) {
            throw new AssertionError("Cooking prerequisites crafted unrelated gear: " + diagnostic());
        }
    }

    private static void checkFailedCraftConservation() {
        var probe = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        probe.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        probe.setNoAi(false);
        probe.setHealth(probe.getMaxHealth());
        var inventory = probe.getInventory();
        inventory.clearContent();
        var namedLogs = new ItemStack(Items.OAK_LOG, 4);
        namedLogs.set(DataComponents.CUSTOM_NAME, Component.literal("conservation logs"));
        inventory.setItem(0, namedLogs);
        BlockPos nearby = base.offset(1, 0, 0);
        BlockPos distant = base.offset(6, 0, 0);
        try {
            assertCraftFailureUnchanged(probe, CookingCraftingExecutor.Action.WOODEN_PICKAXE, null,
                    "missing table rejects craft without changing stacks counts or components");
            assertCraftFailureUnchanged(probe, CookingCraftingExecutor.Action.WOODEN_PICKAXE, nearby,
                    "air instead of a real table rejects craft without consuming logs");
            level.setBlockAndUpdate(distant, Blocks.CRAFTING_TABLE.defaultBlockState());
            assertCraftFailureUnchanged(probe, CookingCraftingExecutor.Action.WOODEN_PICKAXE, distant,
                    "out-of-reach real table rejects craft without consuming logs");
            level.setBlockAndUpdate(nearby, Blocks.CRAFTING_TABLE.defaultBlockState());
            assertCraftFailureUnchanged(probe, CookingCraftingExecutor.Action.FURNACE, nearby,
                    "missing furnace ingredients rejects craft and preserves named logs");
            inventory.clearContent();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = new ItemStack(slot == 0 ? Items.COBBLESTONE : Items.DIRT, 64);
                stack.set(DataComponents.CUSTOM_NAME, Component.literal("capacity slot " + slot));
                inventory.setItem(slot, stack);
            }
            assertCraftFailureUnchanged(probe, CookingCraftingExecutor.Action.FURNACE, nearby,
                    "full inventory output-capacity failure preserves every ingredient and component");
        } finally {
            level.setBlockAndUpdate(nearby, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(distant, Blocks.AIR.defaultBlockState());
            probe.discard();
        }
    }

    private static void assertCraftFailureUnchanged(PlayerNpcEntity probe,
                                                    CookingCraftingExecutor.Action action,
                                                    BlockPos station, String message) {
        var inventory = probe.getInventory();
        var before = new ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) before.add(inventory.getItem(slot).copy());
        boolean crafted = CookingCraftingExecutor.tryCraft(probe, action, station);
        boolean unchanged = true;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack original = before.get(slot), current = inventory.getItem(slot);
            unchanged &= original.getCount() == current.getCount()
                    && (original.isEmpty() && current.isEmpty()
                    || ItemStack.isSameItemSameComponents(original, current));
        }
        check(!crafted && unchanged, message);
    }

    private static void checkOutputTransferConservation() {
        for (boolean goalTransfer : new boolean[] { false, true }) {
            for (int carried : new int[] { 63, 64 }) checkOutputTransfer(goalTransfer, carried);
        }
    }

    private static void checkOutputTransfer(boolean goalTransfer, int carried) {
        String label = (goalTransfer ? "CookFoodGoal" : "FurnaceAi") + " output with carried " + carried;
        var probe = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        probe.setPos(base.getX() + 0.5D, base.getY(), base.getZ() - 4.5D);
        probe.setNoAi(true);
        probe.setHealth(probe.getMaxHealth());
        level.addFreshEntity(probe);
        var inventory = probe.getInventory();
        inventory.clearContent();
        var output = new ItemStack(Items.COOKED_BEEF, 4);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("conservation " + label));
        var food = output.copy();
        food.setCount(carried);
        inventory.setItem(0, food);
        var unchangedSlots = new ArrayList<ItemStack>();
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            var dirt = new ItemStack(Items.DIRT, 64);
            dirt.set(DataComponents.CUSTOM_NAME, Component.literal("output blocked slot " + slot));
            inventory.setItem(slot, dirt);
            unchangedSlots.add(dirt.copy());
        }
        var memory = SurvivalTasks.memory(probe);
        memory.cookingActive = true;
        memory.cookingDimension = SurvivalTasks.dimension(probe);
        memory.cookingProduced = false;
        BlockPos furnacePos = base.offset(1, 0, -5);
        var dropBounds = probe.getBoundingBox().inflate(3.0D);
        var previousDrops = level.getEntitiesOfClass(ItemEntity.class, dropBounds).stream()
                .map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
        try {
            level.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
            var furnace = (FurnaceBlockEntity) level.getBlockEntity(furnacePos);
            check(furnace != null, label + " has an actual native furnace block entity");
            furnace.setItem(2, output.copy());
            boolean transferred;
            if (goalTransfer) {
                var goal = new CookFoodGoal(probe);
                var position = CookFoodGoal.class.getDeclaredField("furnacePos");
                position.setAccessible(true);
                position.set(goal, furnacePos);
                var transfer = CookFoodGoal.class.getDeclaredMethod("takeCookedOutput", ServerLevel.class, FurnaceBlockEntity.class);
                transfer.setAccessible(true);
                transferred = (boolean) transfer.invoke(goal, level, furnace);
            } else {
                transferred = new FurnaceAi(probe).takeOutput(level, furnacePos, furnace);
            }
            var drops = level.getEntitiesOfClass(ItemEntity.class, dropBounds,
                    item -> ItemStack.isSameItemSameComponents(item.getItem(), output));
            int dropped = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
            int expectedDrop = carried == 63 ? 3 : 4;
            check(transferred && furnace.getItem(2).isEmpty(), label + " empties furnace output once");
            check(inventory.getItem(0).getCount() == 64
                            && ItemStack.isSameItemSameComponents(inventory.getItem(0), output)
                            && dropped == expectedDrop
                            && inventory.getItem(0).getCount() + dropped == carried + 4,
                    label + " preserves exact carried-plus-dropped quantity and custom components");
            boolean slotsPreserved = true;
            for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
                slotsPreserved &= inventory.getItem(slot).getCount() == unchangedSlots.get(slot - 1).getCount()
                        && ItemStack.isSameItemSameComponents(inventory.getItem(slot), unchangedSlots.get(slot - 1));
            }
            check(slotsPreserved, label + " preserves every blocking inventory stack");
            check(memory.cookingProduced == (carried == 63),
                    label + " marks collected cooking output only when inventory really increased");
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Cannot invoke actual native cooking output transfer", exception);
        } finally {
            for (var item : level.getEntitiesOfClass(ItemEntity.class, dropBounds,
                    entity -> !previousDrops.contains(entity.getUUID()))) item.discard();
            level.setBlockAndUpdate(furnacePos, Blocks.AIR.defaultBlockState());
            probe.discard();
        }
    }
}

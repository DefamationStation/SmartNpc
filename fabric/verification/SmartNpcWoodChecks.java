import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import com.pla.smart_npc.entity.goal.StartupWorkGatedGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcCraftingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Isolated native observer -> cooking wood shortage -> real tree drops -> starter craft. */
public final class SmartNpcWoodChecks {
    private static final Set<String> RETAINED = Set.of("SurvivalGatherGoal", "CookingCraftGoal", "PickupNearbyItemGoal");
    private static PlayerNpcEntity npc;
    private static ServerLevel level;
    private static BlockPos base;
    private static final List<BlockPos> logs = new ArrayList<>();
    private static int ticks, requiredLogs, peakLogs;
    private static boolean committedWood, sawTable;
    public static volatile boolean passed;

    private SmartNpcWoodChecks() { }

    private static Goal unwrap(Goal goal) {
        while (true) {
            if (goal instanceof StartupWorkGatedGoal startup) goal = startup.getDelegateGoal();
            else if (goal instanceof InterestGatedGoal interest) goal = interest.getDelegateGoal();
            else return goal;
        }
    }

    public static void setup(ServerLevel testLevel, ServerPlayer player) {
        level = testLevel;
        base = new BlockPos(player.getBlockX() + 30, 285, player.getBlockZ());
        var fixtureBounds = new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(-9, -3, -9)), net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(10, 9, 10)));
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fixtureBounds)) item.discard();
        for (var previousActor : level.getEntitiesOfClass(PlayerNpcEntity.class, fixtureBounds)) previousActor.discard();
        ticks = requiredLogs = peakLogs = 0;
        passed = committedWood = sawTable = false;
        logs.clear();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.getChunk(base.offset(x, 0, z));
            level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.BEDROCK.defaultBlockState());
            for (int y = 0; y <= 7; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        BlockPos stump = base.offset(4, 0, 0);
        for (int y = 0; y < 4; y++) {
            BlockPos pos = stump.above(y);
            level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
            logs.add(pos);
        }
        // A connected canopy satisfies native tree classification, rather than loose-log recovery.
        for (int y = 2; y <= 4; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pos = stump.offset(x, y, z);
            if (!logs.contains(pos) && (y < 4 || Math.abs(x) + Math.abs(z) <= 2)) {
                level.setBlockAndUpdate(pos, Blocks.OAK_LEAVES.defaultBlockState());
            }
        }
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
        npc.setHealth(npc.getMaxHealth());
        npc.setNoAi(false); npc.setNoGravity(false);
        npc.getRandom().setSeed(90241005L);
        level.addFreshEntity(npc);
        try {
            var goalsField = Mob.class.getDeclaredField("goalSelector");
            goalsField.setAccessible(true);
            GoalSelector goals = (GoalSelector) goalsField.get(npc);
            goals.removeAllGoals(goal -> !RETAINED.contains(unwrap(goal).getClass().getSimpleName()));
            var targetsField = Mob.class.getDeclaredField("targetSelector");
            targetsField.setAccessible(true);
            ((GoalSelector) targetsField.get(npc)).removeAllGoals(goal -> true);
            for (String retained : RETAINED) {
                SmartNpcFunctional.check(goals.getAvailableGoals().stream().anyMatch(wrapped ->
                                wrapped.getGoal() instanceof StartupWorkGatedGoal
                                        && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(retained)),
                        "wood fixture preserves registered native startup wrapper for " + retained);
            }
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        SmartNpcFunctional.check(!npc.hasInterest(PlayerNpcInterest.BUILDING) && !npc.hasInterest(PlayerNpcInterest.MINING),
                "wood acquisition is independent of building and mining interests");
        SmartNpcFunctional.check(PlayerNpcCraftingUtil.countLogs(npc.getInventory()) == 0
                        && PlayerNpcCraftingUtil.countPlanks(npc.getInventory()) == 0
                        && PlayerNpcCraftingUtil.countSticks(npc.getInventory()) == 0
                        && npc.getInventory().countItem(Items.BEEF) == 2,
                "wood fixture starts only with raw food and no crafting materials");
        System.out.println("SMARTNPC_WOOD_FIXTURE " + personality.skinName() + " at " + base);
    }

    private static long removedLogs() {
        return logs.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.OAK_LOG)).count();
    }

    private static String diagnostic() {
        var memory = SurvivalTasks.memory(npc);
        var inventory = new ArrayList<ItemStack>();
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            if (!npc.getInventory().getItem(slot).isEmpty()) inventory.add(npc.getInventory().getItem(slot));
        }
        return "ticks=" + ticks + " step=" + memory.cookingStep + " status=" + memory.cookingStatus
                + " detail=" + npc.getCurrentAiDetail() + " ai=" + npc.getCurrentAiState()
                + " pos=" + npc.blockPosition() + " requiredLogs=" + requiredLogs + " peakLogs=" + peakLogs
                + " removedLogs=" + removedLogs() + " table=" + sawTable + " inventory=" + inventory;
    }

    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        ticks++;
        var memory = SurvivalTasks.memory(npc);
        if (SurvivalTasks.cookingActive(npc) && memory.cookingStep.equals("NEED_WOOD")) {
            if (!committedWood) requiredLogs = SurvivalTasks.cookingLogTarget(npc);
            committedWood = true;
        }
        peakLogs = Math.max(peakLogs, PlayerNpcCraftingUtil.countLogs(npc.getInventory()));
        if (ticks % 10 == 0) {
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, 0, -8), base.offset(8, 2, 8))) {
                sawTable |= level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
            }
        }
        if (npc.getInventory().countItem(Items.WOODEN_AXE) > 0
                || npc.getInventory().countItem(Items.WOODEN_SHOVEL) > 0
                || npc.getInventory().countItem(Items.WOODEN_SWORD) > 0) {
            throw new AssertionError("Wood prerequisite created unrelated starter gear: " + diagnostic());
        }
        if (npc.getInventory().countItem(Items.WOODEN_PICKAXE) > 0 || npc.getMainHandItem().is(Items.WOODEN_PICKAXE)) {
            SmartNpcFunctional.check(committedWood && requiredLogs >= 3 && peakLogs >= requiredLogs,
                    "native cooking intention collects its required wood target through real log pickup");
            SmartNpcFunctional.check(removedLogs() >= requiredLogs && removedLogs() <= 4,
                    "picked-up wood comes from the fixture's actual four-log tree");
            SmartNpcFunctional.check(sawTable, "wood-only prerequisite places a real crafting table before pickaxe craft");
            SmartNpcFunctional.check(PlayerNpcCraftingUtil.countPlankEquivalent(npc.getInventory())
                            <= removedLogs() * 4 - 9,
                    "table pickaxe and sticks consume real harvested wood");
            SmartNpcFunctional.check(npc.getInventory().countItem(Items.BEEF) == 2
                            && npc.getInventory().countItem(Items.COBBLESTONE) == 0
                            && npc.getInventory().countItem(Items.FURNACE) == 0,
                    "wood fixture manufactures no stone furnace or food without their prerequisites");
            System.out.println("SMARTNPC_WOOD_COMPLETE " + diagnostic());
            passed = true; npc.discard(); npc = null;
            return;
        }
        if (ticks % 200 == 0) System.out.println("SMARTNPC_WOOD_PROGRESS " + diagnostic());
        if (ticks >= 2000) throw new AssertionError("Native wood acquisition timed out: " + diagnostic());
    }
}

import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.PlayerNpcFishingBobberEntity;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import com.pla.smart_npc.entity.goal.StartupWorkGatedGoal;
import com.pla.smart_npc.fabric.survival.CookingCraftingExecutor;
import com.pla.smart_npc.fabric.survival.FoodSupply;
import com.pla.smart_npc.fabric.survival.SurvivalFishingGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One actor, actual registered selectors, native recipe/cast/loot/pickup/smelting.
 * This controlled fixture supplies string, logs, fuel and a carried furnace. The
 * actor must craft and place its real table before crafting its rod. It proves
 * that material-to-food chain, not an empty-inventory survival day.
 * No bite timer, loot table, delegate tick or work-admission override is injected.
 */
public final class SmartNpcFoodChainChecks {
    private static final Set<String> RETAINED = Set.of("FishingRodCraftGoal", "SurvivalFishingGoal",
            "CookingCraftGoal", "CookFoodGoal", "PickupNearbyItemGoal");
    private static PlayerNpcEntity npc;
    private static ServerLevel level;
    private static BlockPos base, table;
    private static AABB bounds;
    private static int ticks, peakRodDamage, retainSince;
    private static boolean tablePlaced, crafted, cast, rawPickup, handoff, furnaceInput, furnaceLit, furnaceOutput;
    private static final Map<UUID, Integer> codDrops = new HashMap<>(), salmonDrops = new HashMap<>();
    public static volatile boolean passed;
    private SmartNpcFoodChainChecks() { }

    private static void check(boolean value, String message) { SmartNpcFunctional.check(value, message); }
    private static Goal unwrap(Goal goal) {
        while (true) {
            if (goal instanceof StartupWorkGatedGoal startup) goal = startup.getDelegateGoal();
            else if (goal instanceof InterestGatedGoal interest) goal = interest.getDelegateGoal();
            else return goal;
        }
    }

    public static void setup(ServerLevel testLevel, ServerPlayer player) {
        level = testLevel;
        // Prior fixtures have completed sleep; give this isolated chain a daylight window.
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "time set 6000");
        passed = tablePlaced = crafted = cast = rawPickup = handoff = furnaceInput = furnaceLit = furnaceOutput = false;
        ticks = peakRodDamage = retainSince = 0;
        codDrops.clear(); salmonDrops.clear();
        base = new BlockPos(player.getBlockX() + 60, 285, player.getBlockZ() + 32);
        bounds = new AABB(Vec3.atLowerCornerOf(base.offset(-12, -4, -12)),
                Vec3.atLowerCornerOf(base.offset(13, 9, 13)));
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) item.discard();
        for (var previous : level.getEntitiesOfClass(PlayerNpcEntity.class, bounds)) previous.discard();
        for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds)) bobber.discard();
        // A named leftover-fuel marker proves that clearing real old containers is
        // covered even when the previous run already removed its own furnace.
        BlockPos resetProbePos = base.offset(-4, 0, -4);
        level.getChunk(resetProbePos);
        level.setBlockAndUpdate(resetProbePos, Blocks.FURNACE.defaultBlockState());
        var resetProbe = (FurnaceBlockEntity) level.getBlockEntity(resetProbePos);
        check(resetProbe != null, "fixture reset probe uses a real loaded furnace block entity");
        var resetMarker = new ItemStack(Items.STICK);
        resetMarker.set(DataComponents.CUSTOM_NAME, Component.literal("fixture reset probe"));
        resetProbe.setItem(1, resetMarker.copy());
        resetProbe.setChanged();
        // Two source layers and open sky: real loaded water, no harvestable substitute inputs.
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            level.getChunk(base.offset(x, 0, z));
            level.setBlockAndUpdate(base.offset(x, -3, z), Blocks.BEDROCK.defaultBlockState());
            boolean pond = x >= 2 && x <= 10 && z >= -7 && z <= 7;
            for (int y = -2; y <= 7; y++) {
                var block = y < 0 ? (pond ? Blocks.WATER : Blocks.BEDROCK) : Blocks.AIR;
                level.setBlockAndUpdate(base.offset(x, y, z), block.defaultBlockState());
            }
        }
        // Replacing an old furnace drops its remaining input/fuel/output AFTER the
        // first cleanup. Remove those real container drops before supplying the new
        // actor, so replay cannot pick up fuel left by the previous successful run.
        var displacedDrops = level.getEntitiesOfClass(ItemEntity.class, bounds);
        int resetMarkers = displacedDrops.stream()
                .filter(drop -> ItemStack.isSameItemSameComponents(drop.getItem(), resetMarker))
                .mapToInt(drop -> drop.getItem().getCount()).sum();
        check(resetMarkers == 1,
                "platform reset exposes exactly one named fuel marker from the old native furnace");
        if (!displacedDrops.isEmpty()) System.out.println("SMARTNPC_FOOD_CHAIN_REPLAY_CLEANUP "
                + displacedDrops.stream().map(ItemEntity::getItem).toList());
        for (var displaced : displacedDrops) displaced.discard();
        check(level.getEntitiesOfClass(ItemEntity.class, bounds,
                        drop -> ItemStack.isSameItemSameComponents(drop.getItem(), resetMarker)).isEmpty(),
                "second cleanup removes the old furnace marker before the food-chain actor exists");
        table = base.offset(-1, 0, -1);
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        // Conservation probes alone use this temporary station; the live chain gets none.
        try { checkFailedRodConservation(); }
        finally {
            level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
            table = null;
        }
        var personality = SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
                .map(SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
                .filter(name -> !name.interests().contains(PlayerNpcInterest.FISHING)
                        && !name.interests().contains(PlayerNpcInterest.CAUTIOUS)
                        && !name.interests().contains(PlayerNpcInterest.BUILDING))
                .sorted(java.util.Comparator.comparing(name -> !name.skinName().equals("Technoblade")))
                .findFirst().orElseThrow();
        npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        npc.setUsername(personality.skinName());
        npc.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        npc.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, ItemStack.EMPTY);
        npc.setHealth(npc.getMaxHealth()); npc.setNoAi(false); npc.setNoGravity(false);
        npc.getRandom().setSeed(90241005L);
        npc.getInventory().addItem(new ItemStack(Items.OAK_LOG, 2));
        npc.getInventory().addItem(new ItemStack(Items.STRING, 2));
        npc.getInventory().addItem(new ItemStack(Items.FURNACE));
        npc.getInventory().addItem(new ItemStack(Items.COAL));
        check(count(Items.OAK_LOG) == 2 && count(Items.STRING) == 2 && count(Items.FURNACE) == 1
                        && count(Items.COAL) == 1 && count(Items.STICK) == 0 && count(Items.OAK_PLANKS) == 0
                        && level.getEntitiesOfClass(ItemEntity.class, bounds).isEmpty(),
                "fresh and replay food-chain inputs contain only the supplied materials, with no old furnace drops");
        check(count(Items.FISHING_ROD) == 0 && SurvivalFishingGoal.foodCount(npc) == 0
                        && count(Items.CRAFTING_TABLE) == 0 && placedTables().isEmpty(),
                "same food-chain actor starts with no rod, no food and no carried or world crafting table");
        level.addFreshEntity(npc);
        try {
            var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true);
            GoalSelector goals = (GoalSelector) field.get(npc);
            // Keep normal swimming safety when native pickup/navigation enters the pond.
            goals.removeAllGoals(goal -> !RETAINED.contains(unwrap(goal).getClass().getSimpleName())
                    && !(goal instanceof net.minecraft.world.entity.ai.goal.FloatGoal));
            var targets = Mob.class.getDeclaredField("targetSelector"); targets.setAccessible(true);
            ((GoalSelector) targets.get(npc)).removeAllGoals(goal -> true);
            for (String name : RETAINED) check(goals.getAvailableGoals().stream().anyMatch(wrapped ->
                    wrapped.getGoal() instanceof StartupWorkGatedGoal
                            && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(name)),
                    "food-chain actor retains registered startup admission wrapper for " + name);
            check(goals.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getPriority() == 0
                            && wrapped.getGoal() instanceof net.minecraft.world.entity.ai.goal.FloatGoal),
                    "food-chain fixture preserves the actor's registered priority-zero native swimming safety");
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        check(!npc.hasInterest(PlayerNpcInterest.FISHING) && !npc.isDailyJobActive(PlayerNpcInterest.FISHING),
                "food chain requires neither fishing personality nor fishing daily job");
        System.out.println("SMARTNPC_FOOD_CHAIN_FIXTURE " + personality.skinName() + " at " + base
                + " supplied=2logs,2string,1furnace,1coal; noTable; scope=craft-place-table-rod-native-fish-cook");
    }

    private static ArrayList<ItemStack> stacks() {
        var stacks = new ArrayList<ItemStack>();
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) stacks.add(npc.getInventory().getItem(slot));
        for (EquipmentSlot slot : EquipmentSlot.values()) stacks.add(npc.getItemBySlot(slot));
        return stacks;
    }
    private static int count(Item item) {
        return stacks().stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static int caught(Map<UUID, Integer> drops) { return drops.values().stream().mapToInt(Integer::intValue).sum(); }
    private static ArrayList<BlockPos> placedTables() {
        var tables = new ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-11, 0, -11), base.offset(11, 2, 11)))
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) tables.add(pos.immutable());
        return tables;
    }
    private static int stationCount(Item item) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-11, 0, -11), base.offset(11, 2, 11)))
            if (level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace)
                for (int slot = 0; slot < 3; slot++) if (furnace.getItem(slot).is(item)) count += furnace.getItem(slot).getCount();
        return count;
    }
    private static int looseCount(Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, bounds, drop -> drop.getItem().is(item))
                .stream().mapToInt(drop -> drop.getItem().getCount()).sum();
    }
    private static String diagnostic() {
        return "ticks=" + ticks + " tablePlaced=" + tablePlaced + " table=" + table
                + " crafted=" + crafted + " cast=" + cast + " wear=" + peakRodDamage
                + " rawPickup=" + rawPickup + " handoff=" + handoff + " furnace=" + furnaceInput + "/" + furnaceLit + "/" + furnaceOutput
                + " drops=" + caught(codDrops) + "cod," + caught(salmonDrops) + "salmon"
                + " decision=" + SurvivalFishingGoal.decision(npc) + " cooking=" + SurvivalTasks.memory(npc).cookingStatus
                + " ai=" + npc.getCurrentAiState() + " detail=" + npc.getCurrentAiDetail() + " pos=" + npc.blockPosition()
                + " health=" + npc.getHealth() + "/" + npc.getMaxHealth() + " air=" + npc.getAirSupply()
                + " inWater=" + npc.isInWater() + " dark=" + level.isDarkOutside()
                + " waiting=" + PlayerNpcAiWorkBudget.isWaitingForTurn(npc)
                + " budget=" + PlayerNpcAiWorkBudget.resourceSnapshot(level.getServer()) + " inventory=" + stacks();
    }

    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        ticks++;
        if (!tablePlaced) {
            var tables = placedTables();
            if (!tables.isEmpty()) {
                check(tables.size() == 1 && count(Items.CRAFTING_TABLE) == 0,
                        "same actor crafts and places exactly one native crafting table from its carried logs");
                table = tables.getFirst();
                if (count(Items.FISHING_ROD) == 0) {
                    check(count(Items.OAK_LOG) == 1 && count(Items.OAK_PLANKS) == 0
                                    && count(Items.STICK) == 0 && count(Items.STRING) == 2,
                            "native table placement consumes exactly one log's four planks and preserves both string: " + diagnostic());
                }
                tablePlaced = true;
            }
        }
        if (!crafted && count(Items.FISHING_ROD) > 0) {
            check(tablePlaced && placedTables().size() == 1 && count(Items.CRAFTING_TABLE) == 0
                            && count(Items.FISHING_ROD) == 1 && count(Items.STRING) == 0 && count(Items.OAK_LOG) == 0
                            && count(Items.OAK_PLANKS) == 2 && count(Items.STICK) == 1,
                    "two native logs become exactly one placed table, one rod, two spare planks and one spare stick; both string consumed: " + diagnostic());
            check(level.getBlockState(table).is(Blocks.CRAFTING_TABLE)
                            && npc.distanceToSqr(Vec3.atCenterOf(table)) <= 2.25D * 2.25D,
                    "rod is crafted within interaction reach of the real table this same actor crafted and placed");
            crafted = true;
        }
        cast |= !level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds, bobber -> bobber.getAngler() == npc).isEmpty();
        peakRodDamage = Math.max(peakRodDamage, stacks().stream().filter(stack -> stack.is(Items.FISHING_ROD))
                .mapToInt(ItemStack::getDamageValue).max().orElse(0));
        for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (drop.getItem().is(Items.COD)) codDrops.merge(drop.getUUID(), drop.getItem().getCount(), Math::max);
            if (drop.getItem().is(Items.SALMON)) salmonDrops.merge(drop.getUUID(), drop.getItem().getCount(), Math::max);
        }
        int raw = count(Items.COD) + count(Items.SALMON);
        rawPickup |= raw > 0;
        handoff |= raw > 0 && SurvivalFishingGoal.decision(npc) == FoodSupply.State.COOK_FIRST;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-11, 0, -11), base.offset(11, 2, 11))) {
            if (!(level.getBlockEntity(pos) instanceof FurnaceBlockEntity furnace)) continue;
            furnaceInput |= furnace.getItem(0).is(Items.COD) || furnace.getItem(0).is(Items.SALMON);
            furnaceLit |= furnace.getBlockState().getValue(BlockStateProperties.LIT);
            furnaceOutput |= furnace.getItem(2).is(Items.COOKED_COD) || furnace.getItem(2).is(Items.COOKED_SALMON);
        }
        if (ticks % 200 == 0) System.out.println("SMARTNPC_FOOD_CHAIN_PROGRESS " + diagnostic());
        if (count(Items.COOKED_COD) + count(Items.COOKED_SALMON) > 0 && !SurvivalTasks.cookingActive(npc)) {
            if (retainSince == 0) retainSince = ticks;
            if (ticks - retainSince >= 80) {
                check(tablePlaced && crafted && cast && peakRodDamage > 0 && caught(codDrops) + caught(salmonDrops) > 0,
                        "same actor crafts and places its table, crafts its rod, then obtains observed native fishing drops with real rod wear");
                check(rawPickup && handoff && furnaceInput && furnaceLit && furnaceOutput,
                        "real drop pickup hands raw fish to native cooking with actual furnace input burn and output");
                check(count(Items.COAL) + stationCount(Items.COAL) + looseCount(Items.COAL) == 0,
                        "native cooking consumes the one supplied coal instead of manufacturing fuel");
                for (Item[] pair : new Item[][] {{Items.COD, Items.COOKED_COD}, {Items.SALMON, Items.COOKED_SALMON}}) {
                    int observed = pair[0] == Items.COD ? caught(codDrops) : caught(salmonDrops);
                    int actual = count(pair[0]) + count(pair[1]) + stationCount(pair[0]) + stationCount(pair[1])
                            + looseCount(pair[0]) + looseCount(pair[1]);
                    check(actual == observed, "native " + pair[0] + " catch/raw/cooked conservation: " + actual + "=" + observed);
                }
                check(count(Items.COOKED_COD) + count(Items.COOKED_SALMON) > 0
                                && !npc.hasInterest(PlayerNpcInterest.FISHING) && !npc.isDailyJobActive(PlayerNpcInterest.FISHING),
                        "same nonfisher retains actual cooked fish stock for eighty ticks after cooking completes");
                System.out.println("SMARTNPC_FOOD_CHAIN_COMPLETE " + diagnostic());
                passed = true;
                for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds, b -> b.getAngler() == npc)) bobber.discard();
                npc.discard(); npc = null;
            }
        } else retainSince = 0;
        if (!passed && ticks >= 8000) throw new AssertionError("Same-actor native food chain timed out: " + diagnostic());
    }

    private static void checkFailedRodConservation() {
        var probe = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        probe.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        probe.setNoAi(false); probe.setHealth(probe.getMaxHealth());
        probe.getInventory().clearContent();
        var logs = new ItemStack(Items.OAK_LOG, 2);
        logs.set(DataComponents.CUSTOM_NAME, Component.literal("failed rod recipe logs"));
        probe.getInventory().setItem(0, logs);
        try {
            var action = CookingCraftingExecutor.Action.valueOf("FISHING_ROD");
            for (int strings : new int[] {0, 1}) {
                probe.getInventory().setItem(1, strings == 0 ? ItemStack.EMPTY : new ItemStack(Items.STRING, strings));
                var before = new ArrayList<ItemStack>();
                for (int slot = 0; slot < probe.getInventory().getContainerSize(); slot++) before.add(probe.getInventory().getItem(slot).copy());
                boolean result = CookingCraftingExecutor.tryCraft(probe, action, table);
                boolean unchanged = true;
                for (int slot = 0; slot < before.size(); slot++) {
                    var old = before.get(slot); var current = probe.getInventory().getItem(slot);
                    unchanged &= old.getCount() == current.getCount() && (old.isEmpty() && current.isEmpty()
                            || ItemStack.isSameItemSameComponents(old, current));
                }
                check(!result && unchanged, "native rod craft with " + strings + " string fails without consuming or altering any carried material");
            }
        } finally { probe.discard(); }
    }
}

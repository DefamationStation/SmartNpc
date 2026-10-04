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
 * This controlled fixture supplies string, logs, fuel, a carried furnace and a real
 * table. It proves that material-to-food chain, not an empty-inventory survival day.
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
    private static boolean crafted, cast, rawPickup, handoff, furnaceInput, furnaceLit, furnaceOutput;
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
        passed = crafted = cast = rawPickup = handoff = furnaceInput = furnaceLit = furnaceOutput = false;
        ticks = peakRodDamage = retainSince = 0;
        codDrops.clear(); salmonDrops.clear();
        base = new BlockPos(player.getBlockX() + 60, 285, player.getBlockZ() + 32);
        bounds = new AABB(Vec3.atLowerCornerOf(base.offset(-12, -4, -12)),
                Vec3.atLowerCornerOf(base.offset(13, 9, 13)));
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) item.discard();
        for (var previous : level.getEntitiesOfClass(PlayerNpcEntity.class, bounds)) previous.discard();
        for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds)) bobber.discard();
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
        table = base.offset(-1, 0, -1);
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        checkFailedRodConservation();
        var personality = SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
                .map(SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
                .filter(name -> !name.interests().contains(PlayerNpcInterest.FISHING)
                        && !name.interests().contains(PlayerNpcInterest.CAUTIOUS)
                        && !name.interests().contains(PlayerNpcInterest.BUILDING))
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
        check(count(Items.FISHING_ROD) == 0 && SurvivalFishingGoal.foodCount(npc) == 0,
                "same food-chain actor starts with no rod and no raw or cooked food");
        level.addFreshEntity(npc);
        try {
            var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true);
            GoalSelector goals = (GoalSelector) field.get(npc);
            goals.removeAllGoals(goal -> !RETAINED.contains(unwrap(goal).getClass().getSimpleName()));
            var targets = Mob.class.getDeclaredField("targetSelector"); targets.setAccessible(true);
            ((GoalSelector) targets.get(npc)).removeAllGoals(goal -> true);
            for (String name : RETAINED) check(goals.getAvailableGoals().stream().anyMatch(wrapped ->
                    wrapped.getGoal() instanceof StartupWorkGatedGoal
                            && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(name)),
                    "food-chain actor retains registered startup admission wrapper for " + name);
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        check(!npc.hasInterest(PlayerNpcInterest.FISHING) && !npc.isDailyJobActive(PlayerNpcInterest.FISHING),
                "food chain requires neither fishing personality nor fishing daily job");
        System.out.println("SMARTNPC_FOOD_CHAIN_FIXTURE " + personality.skinName() + " at " + base
                + " supplied=2logs,2string,1furnace,1coal,realTable; scope=controlled-material-to-food-chain");
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
        return "ticks=" + ticks + " crafted=" + crafted + " cast=" + cast + " wear=" + peakRodDamage
                + " rawPickup=" + rawPickup + " handoff=" + handoff + " furnace=" + furnaceInput + "/" + furnaceLit + "/" + furnaceOutput
                + " drops=" + caught(codDrops) + "cod," + caught(salmonDrops) + "salmon"
                + " decision=" + SurvivalFishingGoal.decision(npc) + " cooking=" + SurvivalTasks.memory(npc).cookingStatus
                + " ai=" + npc.getCurrentAiState() + " detail=" + npc.getCurrentAiDetail() + " pos=" + npc.blockPosition()
                + " waiting=" + PlayerNpcAiWorkBudget.isWaitingForTurn(npc)
                + " budget=" + PlayerNpcAiWorkBudget.resourceSnapshot(level.getServer()) + " inventory=" + stacks();
    }

    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        ticks++;
        if (!crafted && count(Items.FISHING_ROD) > 0) {
            check(count(Items.FISHING_ROD) == 1 && count(Items.STRING) == 0 && count(Items.OAK_LOG) == 1
                            && count(Items.OAK_PLANKS) == 2 && count(Items.STICK) == 1,
                    "same actor's native rod recipe consumes exactly two string and three crafted sticks from one log");
            check(level.getBlockState(table).is(Blocks.CRAFTING_TABLE)
                            && npc.distanceToSqr(Vec3.atCenterOf(table)) <= 2.25D * 2.25D,
                    "rod is crafted within interaction reach of the supplied real crafting table");
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
                check(crafted && cast && peakRodDamage > 0 && caught(codDrops) + caught(salmonDrops) > 0,
                        "same actor crafts its rod then obtains observed native fishing drops with real rod wear");
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

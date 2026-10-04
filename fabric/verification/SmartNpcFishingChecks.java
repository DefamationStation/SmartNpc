import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.PlayerNpcFishingBobberEntity;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import com.pla.smart_npc.entity.goal.StartupWorkGatedGoal;
import com.pla.smart_npc.fabric.survival.FoodSupply;
import com.pla.smart_npc.fabric.survival.SurvivalFishingGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;

/** Real selector -> native cast/bite/loot -> carried food -> cooking intention. */
public final class SmartNpcFishingChecks {
    private static final Set<String> RETAINED = Set.of("SurvivalFishingGoal", "PickupNearbyItemGoal");
    private static PlayerNpcEntity npc, peacefulVisitor;
    private static ServerLevel level;
    private static AABB bounds;
    private static int ticks, peakRodDamage;
    private static boolean sawCast, checkedPeacefulHook;
    public static volatile boolean passed;
    private SmartNpcFishingChecks() { }

    private static Goal unwrap(Goal goal) {
        while (true) {
            if (goal instanceof StartupWorkGatedGoal startup) goal = startup.getDelegateGoal();
            else if (goal instanceof InterestGatedGoal interest) goal = interest.getDelegateGoal();
            else return goal;
        }
    }
    private static void check(boolean condition, String message) {
        SmartNpcFunctional.check(condition, message);
    }
    public static void setup(ServerLevel testLevel, ServerPlayer player) {
        level = testLevel;
        ticks = peakRodDamage = 0;
        passed = sawCast = checkedPeacefulHook = false;
        BlockPos base = new BlockPos(player.getBlockX() + 60, 285, player.getBlockZ());
        bounds = new AABB(Vec3.atLowerCornerOf(base.offset(-12, -4, -12)),
                Vec3.atLowerCornerOf(base.offset(13, 9, 13)));
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) item.discard();
        for (var previous : level.getEntitiesOfClass(PlayerNpcEntity.class, bounds)) previous.discard();
        for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds)) bobber.discard();
        // Open sky and two source-water layers allow native fish/open-water checks.
        // Bedrock supplies no harvestable station/fuel materials.
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            level.getChunk(base.offset(x, 0, z));
            level.setBlockAndUpdate(base.offset(x, -3, z), Blocks.BEDROCK.defaultBlockState());
            boolean pond = x >= 2 && x <= 10 && z >= -7 && z <= 7;
            for (int y = -2; y <= 7; y++) {
                var block = y < 0 ? (pond ? Blocks.WATER : Blocks.BEDROCK) : Blocks.AIR;
                level.setBlockAndUpdate(base.offset(x, y, z), block.defaultBlockState());
            }
        }
        var personality = SmartNpcNamesConfig.getPlayerNpcNameEntries().stream()
                .map(SmartNpcNamesConfig::parseNameEntry).flatMap(Optional::stream)
                .filter(name -> !name.interests().contains(PlayerNpcInterest.FISHING)
                        && !name.interests().contains(PlayerNpcInterest.CAUTIOUS))
                .findFirst().orElseThrow();
        npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        npc.setUsername(personality.skinName());
        npc.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        npc.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, ItemStack.EMPTY);
        npc.setHealth(npc.getMaxHealth());
        npc.setNoAi(false); npc.setNoGravity(false);
        check(!com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal.hasFishingRod(npc)
                        && npc.getInventory().isEmpty(),
                "food fixture begins without a manufactured rod or supplies");
        npc.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        level.addFreshEntity(npc);
        peacefulVisitor = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        peacefulVisitor.setPos(base.getX() - 4.5D, base.getY(), base.getZ() + 0.5D);
        peacefulVisitor.setNoAi(true);
        level.addFreshEntity(peacefulVisitor);
        try {
            var goalsField = Mob.class.getDeclaredField("goalSelector");
            goalsField.setAccessible(true);
            GoalSelector goals = (GoalSelector) goalsField.get(npc);
            goals.removeAllGoals(goal -> !RETAINED.contains(unwrap(goal).getClass().getSimpleName()));
            var targetsField = Mob.class.getDeclaredField("targetSelector");
            targetsField.setAccessible(true);
            ((GoalSelector) targetsField.get(npc)).removeAllGoals(goal -> true);
            for (String retained : RETAINED) check(goals.getAvailableGoals().stream().anyMatch(wrapped ->
                    wrapped.getGoal() instanceof StartupWorkGatedGoal
                        && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(retained)),
                    "fishing fixture retains registered native startup wrapper for " + retained);
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        check(!npc.hasInterest(PlayerNpcInterest.FISHING) && !npc.isDailyJobActive(PlayerNpcInterest.FISHING),
                "food fishing requires no fishing interest or selected fishing job");
        check(SurvivalFishingGoal.foodCount(npc) == 0
                        && SurvivalFishingGoal.decision(npc) == FoodSupply.State.FISH,
                "fixture starts with only one real rod and an unmet food need");
        System.out.println("SMARTNPC_FISHING_FIXTURE " + personality.skinName() + " at " + base);
    }
    private static int rodDamage() {
        int damage = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = npc.getItemBySlot(slot);
            if (stack.is(Items.FISHING_ROD)) damage = Math.max(damage, stack.getDamageValue());
        }
        for (int i = 0; i < npc.getInventory().getContainerSize(); i++) {
            var stack = npc.getInventory().getItem(i);
            if (stack.is(Items.FISHING_ROD)) damage = Math.max(damage, stack.getDamageValue());
        }
        return damage;
    }
    private static String diagnostic() {
        return "ticks=" + ticks + " food=" + SurvivalFishingGoal.foodCount(npc)
            + " decision=" + SurvivalFishingGoal.decision(npc) + " cast=" + sawCast
            + " rodDamage=" + peakRodDamage + " ai=" + npc.getCurrentAiState()
            + " detail=" + npc.getCurrentAiDetail() + " cooking=" + SurvivalTasks.memory(npc).cookingStatus
            + " pos=" + npc.blockPosition();
    }
    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        ticks++;
        for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds,
                entity -> entity.getAngler() == npc)) {
            sawCast = true;
            if (!checkedPeacefulHook) {
                try {
                    var predicate = PlayerNpcFishingBobberEntity.class.getDeclaredMethod("canHitEntity", Entity.class);
                    predicate.setAccessible(true);
                    check(!(boolean) predicate.invoke(bobber, peacefulVisitor),
                            "real survival cast refuses to hook a peaceful living visitor");
                    var looseItem = new ItemEntity(level, npc.getX(), npc.getY(), npc.getZ(), new ItemStack(Items.STICK));
                    check((boolean) predicate.invoke(bobber, looseItem),
                            "survival collision policy retains native item hooks");
                    checkedPeacefulHook = true;
                } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
            }
        }
        peakRodDamage = Math.max(peakRodDamage, rodDamage());
        if (npc.getTarget() != null || peacefulVisitor.getHealth() != peacefulVisitor.getMaxHealth())
            throw new AssertionError("Supply fishing attacked a peaceful neighbouring NPC: " + diagnostic());
        int fish = npc.getInventory().countItem(Items.COD) + npc.getInventory().countItem(Items.SALMON);
        if (fish > 0 && SurvivalTasks.cookingActive(npc)) {
            check(sawCast && checkedPeacefulHook && peakRodDamage > 0,
                    "carried edible fish follows a real native cast and rod wear");
            check(SurvivalFishingGoal.decision(npc) == FoodSupply.State.COOK_FIRST,
                    "real raw fish pickup hands off to the cooking prerequisite intention");
            check(npc.getTarget() == null && peacefulVisitor.getHealth() == peacefulVisitor.getMaxHealth(),
                    "supply fishing does not attack a peaceful neighbouring NPC");
            check(!npc.hasInterest(PlayerNpcInterest.FISHING) && !npc.isDailyJobActive(PlayerNpcInterest.FISHING),
                    "food acquisition preserves the actor's personality and daily job");
            check(npc.getInventory().countItem(Items.OAK_LOG) == 0
                            && npc.getInventory().countItem(Items.FURNACE) == 0
                            && npc.getInventory().countItem(Items.COAL) == 0,
                    "fishing does not gift missing cooking resources");
            System.out.println("SMARTNPC_FISHING_COMPLETE " + diagnostic());
            passed = true;
            for (var bobber : level.getEntitiesOfClass(PlayerNpcFishingBobberEntity.class, bounds,
                    entity -> entity.getAngler() == npc)) bobber.discard();
            npc.discard(); peacefulVisitor.discard(); npc = peacefulVisitor = null;
            return;
        }
        if (ticks % 200 == 0) System.out.println("SMARTNPC_FISHING_PROGRESS " + diagnostic());
        if (ticks >= 6000) throw new AssertionError("Native survival fishing timed out: " + diagnostic());
    }
}

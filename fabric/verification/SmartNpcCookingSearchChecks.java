import com.pla.smart_npc.clazz.FakePlayer;
import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.InterestGatedGoal;
import com.pla.smart_npc.entity.goal.StartupWorkGatedGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.PlayerNpcCraftingUtil;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import com.pla.smart_npc.util.PlayerNpcTeamUpManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Registered-selector search acceptance; no goal tick calls or post-start material gifts. */
public final class SmartNpcCookingSearchChecks {
    private static final int RADIUS = 28;
    private static final boolean CONFIGURED_IDENTITY = Boolean.getBoolean("smartnpcsmoke.replay");
    private static final List<PlayerNpcInterest> JOBS = List.of(PlayerNpcInterest.BUILDING,
            PlayerNpcInterest.MINING, PlayerNpcInterest.FARMING, PlayerNpcInterest.FISHING, PlayerNpcInterest.EXPLORING);
    private static final Set<String> SAFETY = Set.of("FloatGoal", "WaterFallGoal", "EscapeWallGoal",
            "EscapeHoleWithBlockGoal", "DescendHighColumnGoal");
    private static final Set<String> WORK = Set.of("SurvivalGatherGoal", "CookingCraftGoal",
            "PickupNearbyItemGoal", "ExploreAroundGoal", "DigDownForStoneGoal");
    private static final List<BlockPos> logs = new ArrayList<>();
    private static final List<BlockPos> stones = new ArrayList<>();
    private static ServerLevel level;
    private static PlayerNpcEntity npc;
    private static GoalSelector selector;
    private static BlockPos base;
    private static int phase, ticks, peakLogs, peakStone, requiredLogs, maxRequestedLogs, peakSticks;
    private static boolean committed, searched, dug, worn, sawTable;
    private static double moved;
    public static volatile boolean passed;

    private SmartNpcCookingSearchChecks() {}

    private static Goal unwrap(Goal goal) {
        while (true) {
            if (goal instanceof StartupWorkGatedGoal startup) goal = startup.getDelegateGoal();
            else if (goal instanceof InterestGatedGoal interest) goal = interest.getDelegateGoal();
            else return goal;
        }
    }

    private static void setSyntheticJoblessIdentity(PlayerNpcEntity actor) {
        actor.setUsername("Technoblade");
        // Roster validation requires a job and setUsername reconstructs traits from that
        // roster. Represent the unsupported jobless edge state only in this fixture:
        // initialize the normal synced-name/revision cache, then replace its personality.
        // Goal admission, work scheduling, perception and ticking remain native.
        actor.getUsername();
        try {
            var cachedName = FakePlayer.class.getDeclaredField("cachedUsername");
            cachedName.setAccessible(true);
            cachedName.set(actor, new FakePlayer.FakePlayerName("Technoblade", PlayerNpcInterest.LOOTING));
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
    }

    public static void setup(ServerLevel testLevel, ServerPlayer player) {
        level = testLevel;
        // Separate columns from other fixtures so native sky and heightmap tests see this surface.
        base = new BlockPos(player.getBlockX() + 96, 240, player.getBlockZ() + 96);
        passed = false;
        phase = 0;
        beginPhase();
    }

    private static void beginPhase() {
        ticks = peakLogs = peakStone = requiredLogs = maxRequestedLogs = peakSticks = 0;
        committed = searched = dug = worn = sawTable = false;
        moved = 0;
        logs.clear(); stones.clear();
        var bounds = new AABB(Vec3.atLowerCornerOf(base.offset(-RADIUS, -13, -RADIUS)),
                Vec3.atLowerCornerOf(base.offset(RADIUS + 1, 8, RADIUS + 1)));
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) item.discard();
        for (var actor : level.getEntitiesOfClass(PlayerNpcEntity.class, bounds)) actor.discard();
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            level.getChunk(base.offset(x, 0, z));
            for (int y = -12; y <= -3; y++) {
                BlockPos pos = base.offset(x, y, z);
                boolean enclosedStone = phase == 1 && y > -12 && Math.abs(x) < RADIUS && Math.abs(z) < RADIUS;
                level.setBlockAndUpdate(pos, (enclosedStone ? Blocks.STONE : Blocks.BEDROCK).defaultBlockState());
                if (enclosedStone) stones.add(pos);
            }
            level.setBlockAndUpdate(base.offset(x, -2, z), (phase == 1 ? Blocks.DIRT : Blocks.BEDROCK).defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, -1, z), (phase == 1 ? Blocks.GRASS_BLOCK : Blocks.BEDROCK).defaultBlockState());
            for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        if (phase == 0) {
            // Every tree starts outside the collector's 16-block horizontal target search.
            // Several directions keep random native exploration useful without steering it.
            for (int[] offset : new int[][] {{22, 0}, {-22, 0}, {0, 22}, {0, -22},
                    {22, 22}, {22, -22}, {-22, 22}, {-22, -22}}) {
                BlockPos stump = base.offset(offset[0], 0, offset[1]);
                for (int y = 0; y < 4; y++) {
                    BlockPos pos = stump.above(y);
                    level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState()); logs.add(pos);
                }
                for (int y = 2; y <= 4; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                    BlockPos pos = stump.offset(x, y, z);
                    if (!logs.contains(pos) && (y < 4 || Math.abs(x) + Math.abs(z) <= 2))
                        level.setBlockAndUpdate(pos, Blocks.OAK_LEAVES.defaultBlockState());
                }
            }
        }
        // Replacing an old furnace can release its contents during replay cleanup.
        // Remove those drops after block replacement, before the new actor exists.
        for (var item : level.getEntitiesOfClass(ItemEntity.class, bounds)) item.discard();
        SmartNpcFunctional.check(level.getEntitiesOfClass(ItemEntity.class, bounds).isEmpty(),
                "cooking search starts without preexisting material drops");
        npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        if (CONFIGURED_IDENTITY) npc.setUsername("Technoblade");
        else setSyntheticJoblessIdentity(npc);
        npc.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        npc.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) npc.setItemSlot(slot, ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.BEEF, 2));
        if (phase == 1) {
            // Isolate stone search from starter-tool acquisition, already covered by phase zero.
            npc.getInventory().addItem(new ItemStack(Items.OAK_LOG, 4));
            npc.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        }
        npc.setHealth(npc.getMaxHealth()); npc.setNoAi(false); npc.setNoGravity(false);
        npc.getRandom().setSeed(90241010L + phase);
        level.addFreshEntity(npc);
        try {
            var field = Mob.class.getDeclaredField("goalSelector"); field.setAccessible(true);
            selector = (GoalSelector) field.get(npc);
            selector.removeAllGoals(goal -> !SAFETY.contains(unwrap(goal).getClass().getSimpleName())
                    && !WORK.contains(unwrap(goal).getClass().getSimpleName()));
            var targets = Mob.class.getDeclaredField("targetSelector"); targets.setAccessible(true);
            ((GoalSelector) targets.get(npc)).removeAllGoals(goal -> true);
            for (String name : Set.of("SurvivalGatherGoal", "ExploreAroundGoal", "DigDownForStoneGoal"))
                SmartNpcFunctional.check(selector.getAvailableGoals().stream().anyMatch(wrapped ->
                        wrapped.getGoal() instanceof StartupWorkGatedGoal
                                && unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(name)),
                        "cooking search preserves registered startup admission for " + name);
            for (String name : SAFETY)
                SmartNpcFunctional.check(selector.getAvailableGoals().stream().anyMatch(wrapped ->
                        unwrap(wrapped.getGoal()).getClass().getSimpleName().equals(name)),
                        "cooking search retains native safety goal " + name);
            Integer logSearchSlice = null, pickupSlice = null, digSlice = null, gatherSlice = null;
            for (var wrapped : selector.getAvailableGoals()) {
                Goal outer = wrapped.getGoal(), nativeGoal = unwrap(outer);
                if (!(outer instanceof StartupWorkGatedGoal) || !WORK.contains(nativeGoal.getClass().getSimpleName())) continue;
                Goal interest = ((StartupWorkGatedGoal) outer).getDelegateGoal();
                int slice = (Integer) readField(outer, "predicateSlice");
                String name = nativeGoal.getClass().getSimpleName();
                if (name.equals("PickupNearbyItemGoal")) pickupSlice = slice;
                else if (name.equals("DigDownForStoneGoal")) digSlice = slice;
                else if (name.equals("SurvivalGatherGoal")) gatherSlice = slice;
                else if (name.equals("ExploreAroundGoal") && "exploring for logs".equals(readField(nativeGoal, "detail"))) logSearchSlice = slice;
                System.out.println("SMARTNPC_COOKING_SEARCH_SLICE native=" + nativeGoal.getClass().getSimpleName()
                        + " wrapper=" + interest.getClass().getSimpleName()
                        + (nativeGoal.getClass().getSimpleName().equals("ExploreAroundGoal") ? " detail=" + readField(nativeGoal, "detail") : "")
                        + " predicateSlice=" + readField(outer, "predicateSlice")
                        + " priority=" + wrapped.getPriority());
            }
            SmartNpcFunctional.check(logSearchSlice != null && pickupSlice != null && !logSearchSlice.equals(pickupSlice),
                    "registered cooking log search has a distinct admission opportunity from pickup");
            SmartNpcFunctional.check(digSlice != null && gatherSlice != null && !digSlice.equals(gatherSlice),
                    "registered cooking dig fallback has a distinct admission opportunity from the local collector");
        } catch (ReflectiveOperationException exception) { throw new RuntimeException(exception); }
        SmartNpcFunctional.check(CONFIGURED_IDENTITY
                        ? npc.hasInterest(PlayerNpcInterest.EXPLORING)
                        : npc.getInterests().stream().noneMatch(PlayerNpcInterest::isJob),
                "cooking search identity=" + (CONFIGURED_IDENTITY ? "configured exploring" : "synthetic jobless"));
        SmartNpcFunctional.check(npc.getInventory().countItem(Items.COBBLESTONE) == 0
                        && npc.getInventory().countItem(Items.FURNACE) == 0
                        && npc.getInventory().countItem(Items.CRAFTING_TABLE) == 0
                        && npc.getInventory().countItem(Items.COAL) == 0
                        && npc.getInventory().countItem(Items.BEEF) == 2
                        && PlayerNpcCraftingUtil.countLogs(npc.getInventory()) == (phase == 0 ? 0 : 4)
                        && PlayerNpcCraftingUtil.countPlanks(npc.getInventory()) == 0
                        && PlayerNpcCraftingUtil.countSticks(npc.getInventory()) == 0
                        && npc.getInventory().countItem(Items.WOODEN_PICKAXE) == phase,
                "cooking search starts with exactly its declared raw food and isolated tool inputs");
        boolean noStations = true;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-RADIUS, -2, -RADIUS), base.offset(RADIUS, 6, RADIUS)))
            noStations &= !level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) && !level.getBlockState(pos).is(Blocks.FURNACE);
        SmartNpcFunctional.check(noStations, "cooking search starts without manufactured stations in the arena");
        SmartNpcFunctional.check(level.canSeeSky(base.above())
                        && level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        base.getX(), base.getZ()) == base.getY(),
                "cooking search arena has an open sky and native surface height at its starting stand");
        if (phase == 1) {
            boolean enclosed = stones.stream().allMatch(pos -> {
                for (var direction : net.minecraft.core.Direction.values())
                    if (level.getBlockState(pos.relative(direction)).isAir()) return false;
                return true;
            });
            SmartNpcFunctional.check(enclosed, "dig search starts with no exposed fixture stone");
        }
        System.out.println("SMARTNPC_COOKING_SEARCH_FIXTURE phase=" + phase + " configuredIdentity=" + CONFIGURED_IDENTITY + " at " + base);
    }

    private static String diagnostic() {
        var memory = SurvivalTasks.memory(npc);
        var carried = new ArrayList<String>();
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            ItemStack stack = npc.getInventory().getItem(slot);
            if (!stack.isEmpty()) carried.add(slot + ":" + stack);
        }
        return "phase=" + phase + " configuredIdentity=" + CONFIGURED_IDENTITY + " ticks=" + ticks + " step=" + memory.cookingStep
                + " status=" + memory.cookingStatus + " ai=" + npc.getCurrentAiState()
                + " detail=" + npc.getCurrentAiDetail() + " pos=" + npc.blockPosition()
                + " searched=" + searched + " dug=" + dug + " moved=" + moved
                + " requiredLogs=" + requiredLogs + " maxRequestedLogs=" + maxRequestedLogs
                + " peakLogs=" + peakLogs + " peakSticks=" + peakSticks + " sawTable=" + sawTable + " peakStone=" + peakStone
                + " inventory=" + carried + " mainHand=" + npc.getMainHandItem() + " offHand=" + npc.getOffhandItem()
                + " mainWeaponCache=" + npc.getMainWeaponItem() + " offWeaponCache=" + npc.getOffWeaponItem()
                + " idleTrace=" + npc.getIdleTraceDetail();
    }

    private static Object readField(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    @SuppressWarnings("unchecked")
    private static String searchAdmissionDiagnostic() {
        var result = new StringBuilder();
        try {
            for (var wrapped : selector.getAvailableGoals()) {
                Goal nativeGoal = unwrap(wrapped.getGoal());
                if (nativeGoal.getClass().getSimpleName().equals("DigDownForStoneGoal")) {
                    result.append(" dig={running=").append(wrapped.isRunning());
                    for (String field : List.of("stoneBlocksMined", "stoneBlocksNeeded", "stopReason", "finished", "targetPos", "digOrigin"))
                        result.append(' ').append(field).append('=').append(readField(nativeGoal, field));
                    Object tool = npc.getToolSwapState();
                    for (String field : List.of("swappedMainHand", "previousMainHand", "currentMainHandSource"))
                        result.append(" swap.").append(field).append('=').append(readField(tool, field));
                    result.append(" carriedPick=").append(npc.hasCarriedTool(net.minecraft.tags.ItemTags.PICKAXES))
                            .append(" transactionPick=").append(new com.pla.smart_npc.entity.ai.ToolAi(npc).hasTool(net.minecraft.tags.ItemTags.PICKAXES));
                    result.append('}');
                }
                if (!nativeGoal.getClass().getSimpleName().equals("ExploreAroundGoal")
                        || !"exploring for logs".equals(readField(nativeGoal, "detail"))) continue;
                Goal outer = wrapped.getGoal();
                Goal interest = outer instanceof StartupWorkGatedGoal startup ? startup.getDelegateGoal() : outer;
                result.append(" logSearch={wrapper=").append(interest.getClass().getSimpleName())
                        .append(" gate=").append(interest instanceof InterestGatedGoal gate && gate.isInterestGateActive())
                        .append(" running=").append(wrapped.isRunning());
                // Only evaluate the cooking log predicates: these use the retained collector
                // authority and do not invoke admission, collectors or goal lifecycle methods.
                if (SurvivalTasks.needsCookingLogs(npc)) {
                    result.append(" shouldExplore=").append(((java.util.function.Predicate<ServerLevel>) readField(nativeGoal, "shouldExplore")).test(level))
                            .append(" shouldYield=").append(((java.util.function.Predicate<ServerLevel>) readField(nativeGoal, "shouldYieldToSubGoal")).test(level));
                }
                for (String field : List.of("nextSearchTick", "subGoalProbeReadyTick", "initialRoutePending", "waitingForRetry", "targetPos"))
                    result.append(' ').append(field).append('=').append(readField(nativeGoal, field));
                Object throttle = readField(nativeGoal, "canUseThrottle");
                result.append(" throttleInitialized=").append(readField(throttle, "initialized"))
                        .append(" throttleNextCheck=").append(readField(throttle, "nextCheckTick"));
                if (outer instanceof StartupWorkGatedGoal)
                    for (String field : List.of("releaseServerTick", "predicateSlice"))
                        result.append(' ').append(field).append('=').append(readField(outer, field));
                result.append('}');
            }
            result.append(" npcTick=").append(npc.tickCount)
                    .append(" serverTick=").append(level.getServer().getTickCount())
                    .append(" onGround=").append(npc.onGround())
                    .append(" noAi=").append(npc.isNoAi()).append(" healing=").append(npc.isHealing())
                    .append(" target=").append(npc.getTarget()).append(" passenger=").append(npc.isPassenger())
                    .append(" upward=").append(npc.getUpwardEscapeTarget())
                    .append(" holeCooldown=").append(npc.getHoleEscapeCooldown())
                    .append(" gatherCooldown=").append(npc.getGatherCooldown())
                    .append(" home=").append(PlayerNpcHomeUtil.getHome(npc))
                    .append(" weatherHome=").append(PlayerNpcHomeUtil.getHome(npc).isPresent()
                            && (level.isDarkOutside() || level.isThundering()))
                    .append(" raining=").append(level.isRainingAt(npc.blockPosition()))
                    .append(" teamSuspend=").append(PlayerNpcTeamUpManager.shouldSuspendRoutineWork(npc))
                    .append(" teamRequest=").append(npc.isTeamUpRequestPending())
                    .append(" waitingForTurn=").append(PlayerNpcAiWorkBudget.isWaitingForTurn(npc))
                    .append(" budget=").append(PlayerNpcAiWorkBudget.resourceSnapshot(level.getServer()));
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Cannot read native search admission diagnostics", exception);
        }
        return result.toString();
    }

    public static void tick(MinecraftServer server) {
        if (npc == null || passed) return;
        if (!CONFIGURED_IDENTITY && npc.getInterests().stream().anyMatch(PlayerNpcInterest::isJob))
            throw new AssertionError("Synthetic jobless fixture personality was invalidated: " + diagnostic());
        ticks++;
        var memory = SurvivalTasks.memory(npc);
        String shortage = phase == 0 ? "NEED_WOOD" : "NEED_STONE";
        if (SurvivalTasks.cookingActive(npc) && shortage.equals(memory.cookingStep)) {
            if (npc.isInterestGateActive(JOBS))
                throw new AssertionError("Ordinary job gate reopened during cooking shortage: " + diagnostic());
            committed = true;
            if (phase == 0) {
                // Native leaf clearing can supply sticks, legitimately reducing the next
                // wood decision. Acceptance follows that last committed material target.
                requiredLogs = SurvivalTasks.cookingLogTarget(npc);
                maxRequestedLogs = Math.max(maxRequestedLogs, requiredLogs);
            }
        }
        for (var wrapped : selector.getAvailableGoals()) if (wrapped.isRunning()) {
            String name = unwrap(wrapped.getGoal()).getClass().getSimpleName();
            searched |= name.equals("ExploreAroundGoal"); dug |= name.equals("DigDownForStoneGoal");
        }
        moved = Math.max(moved, npc.blockPosition().distSqr(base));
        peakLogs = Math.max(peakLogs, PlayerNpcCraftingUtil.countLogs(npc.getInventory()));
        peakSticks = Math.max(peakSticks, PlayerNpcCraftingUtil.countSticks(npc.getInventory()));
        peakStone = Math.max(peakStone, npc.getInventory().countItem(Items.COBBLESTONE));
        worn |= npc.getMainHandItem().is(Items.WOODEN_PICKAXE) && npc.getMainHandItem().getDamageValue() > 0;
        for (int slot = 0; slot < npc.getInventory().getContainerSize(); slot++) {
            var stack = npc.getInventory().getItem(slot);
            worn |= stack.is(Items.WOODEN_PICKAXE) && stack.getDamageValue() > 0;
        }
        if (phase == 0 && (npc.getInventory().countItem(Items.WOODEN_PICKAXE) > 0
                || npc.getMainHandItem().is(Items.WOODEN_PICKAXE))) {
            for (BlockPos pos : BlockPos.betweenClosed(npc.blockPosition().offset(-5, -2, -5), npc.blockPosition().offset(5, 2, 5)))
                sawTable |= level.getBlockState(pos).is(Blocks.CRAFTING_TABLE);
            long removed = logs.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.OAK_LOG)).count();
            SmartNpcFunctional.check(committed && searched && moved > 64 && requiredLogs >= 2
                            && peakLogs >= requiredLogs && removed >= peakLogs && sawTable,
                    "cooking searches beyond local reach and crafts at a real table from harvested tree materials while ordinary job gates are closed: " + diagnostic());
            if (!CONFIGURED_IDENTITY) SmartNpcFunctional.check(npc.getSelectedDailyJobInterest().isEmpty(), "wood search never assigns a daily job");
            System.out.println("SMARTNPC_COOKING_SEARCH_WOOD_COMPLETE " + diagnostic());
            npc.discard(); npc = null; phase = 1; beginPhase(); return;
        }
        if (phase == 1 && peakStone >= 8) {
            long removed = stones.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.STONE)).count();
            SmartNpcFunctional.check(committed && dug && worn && removed >= 8,
                    "cooking digs into enclosed stone with a real worn tool and picks up eight furnace stones while ordinary job gates are closed");
            if (!CONFIGURED_IDENTITY) SmartNpcFunctional.check(npc.getSelectedDailyJobInterest().isEmpty(), "stone search never assigns a daily job");
            System.out.println("SMARTNPC_COOKING_SEARCH_STONE_COMPLETE " + diagnostic());
            passed = true; npc.discard(); npc = null; return;
        }
        if (ticks % 200 == 0) System.out.println("SMARTNPC_COOKING_SEARCH_PROGRESS " + diagnostic() + searchAdmissionDiagnostic());
        if (ticks >= 3000) throw new AssertionError("Native cooking prerequisite search timed out: " + diagnostic());
    }
}

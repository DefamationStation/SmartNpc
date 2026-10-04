package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** All neutral targeting passes through this policy, including direct and remembered targets. */
public final class SocialSafety {
    private static final String KEY = "SurvivalSocial";
    private static final Map<PlayerNpcEntity, GrievanceMemory> MEMORIES = new WeakHashMap<>();
    private static final Map<ServerPlayer, BlockPos> OPENED = new WeakHashMap<>();
    private static final Map<PlayerNpcEntity, Map<UUID, Long>> WARNINGS = new WeakHashMap<>();
    public record ChestChange(Container container, BlockPos pos, List<ItemStack> before) {}
    private SocialSafety() {}
    public static GrievanceMemory memory(PlayerNpcEntity npc) {
        return MEMORIES.computeIfAbsent(npc, n -> GrievanceMemory.load(PersistentData.get(n).getCompoundOrEmpty(KEY)));
    }
    public static boolean neutral(PlayerNpcEntity npc, LivingEntity target) {
        return target instanceof Player || target instanceof PlayerNpcEntity || target instanceof Villager
            || target instanceof IronGolem || npc.isSmartNpcCompatPlayerLikeTarget(target)
            || npc.isSmartNpcCompatVillagerTarget(target);
    }
    public static boolean permits(PlayerNpcEntity npc, LivingEntity target) {
        return !neutral(npc, target) || hasCause(npc, target);
    }
    public static boolean hasCause(PlayerNpcEntity npc, LivingEntity target) {
        return npc.level() instanceof ServerLevel level
            && memory(npc).cause(target.getUUID(), level.getGameTime()).isPresent();
    }
    public static void record(PlayerNpcEntity npc, LivingEntity offender, GrievanceMemory.Cause cause) {
        if (!(npc.level() instanceof ServerLevel level) || npc == offender || !offender.isAlive()
                || npc.isTeamAlliedWith(offender) || npc.isAlliedTo(offender) || offender.isAlliedTo(npc)) return;
        memory(npc).record(offender.getUUID(), cause, level.getGameTime());
        PersistentData.get(npc).put(KEY, memory(npc).save());
    }
    public static void defend(PlayerNpcEntity npc, LivingEntity offender, GrievanceMemory.Cause cause) {
        record(npc, offender, cause);
        npc.setChestProtectionTarget(offender); npc.wakeUpIdleWork();
        npc.setCurrentAiState(npc.getTarget() == offender ? "ai.player_npc.retaliating" : PlayerNpcEntity.AI_IDLE);
        npc.setCurrentAiDetail("defending against " + cause.name().toLowerCase(Locale.ROOT) + " by " + offender.getDisplayName().getString());
    }
    public static boolean witnesses(PlayerNpcEntity npc, LivingEntity offender) {
        return npc.isAlive() && npc != offender && !npc.isTeamAlliedWith(offender)
            && npc.distanceToSqr(offender) <= 32 * 32 && npc.hasLineOfSight(offender);
    }
    public static boolean ownsContainer(PlayerNpcEntity npc, Container container) {
        var pos = npc.getOwnedChestPos();
        if (pos == null || !npc.level().hasChunkAt(pos)) return false;
        var block = npc.level().getBlockEntity(pos);
        return block instanceof Container owned && (owned == container
            || container instanceof CompoundContainer doubleChest && doubleChest.contains(owned));
    }
    public static void opened(ServerPlayer player, BlockPos pos) {
        OPENED.put(player, pos.immutable());
    }
    public static void warn(PlayerNpcEntity owner, LivingEntity offender) {
        if (!(offender instanceof ServerPlayer player)) return;
        long now = owner.level().getGameTime();
        var warnings = WARNINGS.computeIfAbsent(owner, n -> new HashMap<>());
        warnings.entrySet().removeIf(e -> e.getValue() <= now);
        if (warnings.size() >= GrievanceMemory.LIMIT || warnings.containsKey(player.getUUID())) return;
        warnings.put(player.getUUID(), now + 1200);
        player.sendSystemMessage(Component.literal("<" + owner.getDisplayName().getString() + "> That's my chest. Please leave my supplies there."));
    }
    public static ChestChange beforeChestClick(ServerPlayer player, int menuId) {
        if (!(player.containerMenu instanceof ChestMenu menu) || menu.containerId != menuId) return null;
        var pos = OPENED.get(player);
        if (pos == null || !player.level().hasChunkAt(pos)) return null;
        var block = player.level().getBlockEntity(pos);
        var container = menu.getContainer();
        if (!(block instanceof Container clicked) || !(container == clicked
                || container instanceof CompoundContainer doubleChest && doubleChest.contains(clicked))) return null;
        boolean owned = player.level().getEntitiesOfClass(PlayerNpcEntity.class, player.getBoundingBox().inflate(32),
            npc -> witnesses(npc, player) && ownsContainer(npc, container)).stream().findAny().isPresent();
        return owned ? new ChestChange(container, pos, snapshot(container)) : null;
    }
    public static List<ItemStack> snapshot(Container container) {
        var result = new ArrayList<ItemStack>();
        for (int i = 0; i < container.getContainerSize(); i++) result.add(container.getItem(i).copy());
        return result;
    }
    /** Per-item/component counts distinguish removal from rearrangement and deposits. */
    public static boolean removedAny(List<ItemStack> before, List<ItemStack> after) {
        for (var stack : before) {
            if (stack.isEmpty()) continue;
            int oldCount = before.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            int newCount = after.stream().filter(s -> ItemStack.isSameItemSameComponents(s, stack)).mapToInt(ItemStack::getCount).sum();
            if (newCount < oldCount) return true;
        }
        return false;
    }
    public static void afterChestClick(ServerPlayer player, ChestChange change) {
        if (change != null && removedAny(change.before, snapshot(change.container)))
            com.pla.smart_npc.event.PlayerNpcChestProtectEvent.reportOffense(player.level(), change.pos, player, "stole");
    }
    /** Only tracked, matching construction blocks count as a base: raw terrain in a plot does not. */
    public static void propertyBroken(ServerLevel level, BlockPos pos, BlockState previous, LivingEntity offender) {
        for (var owner : level.getEntitiesOfClass(PlayerNpcEntity.class, offender.getBoundingBox().inflate(32), n -> witnesses(n, offender))) {
            var home = PlayerNpcHomeUtil.getHome(owner);
            var layout = PlayerNpcHomeUtil.getHomeLayoutId(owner).flatMap(PlayerNpcBuildLayoutLoader::getLayout);
            if (home.isEmpty() || layout.isEmpty() || !PlayerNpcHomeUtil.isInside(home.get(), pos)) continue;
            boolean built = layout.get().blocks().stream().anyMatch(b -> !b.state().isAir()
                && b.toWorld(home.get().origin()).equals(pos) && PlayerNpcBuildMaterialUtil.matches(previous, b.state()));
            if (built) defend(owner, offender, GrievanceMemory.Cause.VANDALISM);
        }
    }
    @Events.SubscribeEvent public static void unload(Events.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof PlayerNpcEntity npc) { MEMORIES.remove(npc); WARNINGS.remove(npc); }
        if (event.getEntity() instanceof ServerPlayer player) OPENED.remove(player);
    }
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event) { MEMORIES.clear(); OPENED.clear(); WARNINGS.clear(); }
}

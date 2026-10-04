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
        memory(npc).request(dimension(npc), npc.blockPosition().asLong(), npc.getInventory().countItem(Items.COAL), additional);
        save(npc);
    }

    @Events.SubscribeEvent public static void observe(Events.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PlayerNpcEntity npc) || npc.isNoAi()
                || !(npc.level() instanceof ServerLevel level)
                || Math.floorMod(npc.tickCount + npc.getUUID().hashCode(), 20) != 0) return;
        observeNearby(npc, level);
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
                    c.getSource().sendSuccess(() -> Component.literal(m.status + "; active=" + m.active + "; coal="
                        + npc.getInventory().countItem(Items.COAL) + "/" + m.target + "; observations=" + m.coal.size()), false);
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(c -> {
                    if (!(EntityArgument.getEntity(c, "npc") instanceof PlayerNpcEntity npc)) return 0;
                    var m = memory(npc); m.active = false; m.status = "cancelled"; save(npc);
                    c.getSource().sendSuccess(() -> Component.literal("Coal task cancelled."), true); return 1;
                })))));
    }
}

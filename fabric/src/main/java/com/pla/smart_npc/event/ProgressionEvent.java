package com.pla.smart_npc.event;

import com.pla.smart_npc.SmartNpc;
import com.pla.smart_npc.clazz.Difficulty;
import com.pla.smart_npc.util.ProgressionUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.pla.smart_npc.fabric.Events.EntityTickEvent;
import com.pla.smart_npc.fabric.Events.ServerTickEvent;
import com.pla.smart_npc.fabric.Events.LivingDeathEvent;
import com.pla.smart_npc.fabric.Events.PlayerEvent;
import com.pla.smart_npc.fabric.Events.ServerStartedEvent;
import com.pla.smart_npc.fabric.Events.SubscribeEvent;

public final class ProgressionEvent {
    private ProgressionEvent() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ProgressionUtil.reconcileHistoricalProgression(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            ProgressionUtil.reconcileDragonFightProgression(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionUtil.reconcileHistoricalProgression(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressionUtil.increaseDifficulty(player.level().getServer(), Difficulty.MEDIUM);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EnderDragon && event.getEntity().level() instanceof ServerLevel level) {
            ProgressionUtil.increaseDifficulty(level.getServer(), Difficulty.HARD);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EnderDragon dragon
                && dragon.dragonDeathTime > 0
                && dragon.level() instanceof ServerLevel level) {
            ProgressionUtil.increaseDifficulty(level.getServer(), Difficulty.HARD);
        }
    }
}

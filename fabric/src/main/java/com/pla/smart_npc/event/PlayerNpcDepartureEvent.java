package com.pla.smart_npc.event;

import com.pla.smart_npc.SmartNpc;
import com.pla.smart_npc.config.SmartNpcConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.util.ChatUtil;
import com.pla.smart_npc.util.ExternalChunkActivity;
import com.pla.smart_npc.util.PlayerNpcForceTickManager;
import com.pla.smart_npc.util.RemoteNpcDeparture;
import net.minecraft.server.level.ServerLevel;
import com.pla.smart_npc.fabric.Events.EntityTickEvent;
import com.pla.smart_npc.fabric.Events.ServerStoppingEvent;
import com.pla.smart_npc.fabric.Events.SubscribeEvent;

public final class PlayerNpcDepartureEvent {
    private PlayerNpcDepartureEvent() {}

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof PlayerNpcEntity npc)
                || !(npc.level() instanceof ServerLevel) || !npc.isAlive() || npc.isRemoved()
                || Math.floorMod(npc.tickCount, 20) != Math.floorMod(npc.getUUID().hashCode(), 20)) return;
        // A resident/traveller must not be discarded merely because the player
        // stayed at home. Ticket/worker budgets still decide when it is simulated.
        if (npc.hasInterest(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING)
                || com.pla.smart_npc.util.PlayerNpcHomeUtil.getHome(npc).isPresent()
                || com.pla.smart_npc.fabric.survival.Journeys.active(npc)) return;
        if (RemoteNpcDeparture.tick(npc,
                PlayerNpcForceTickManager.isEnabled() && PlayerNpcForceTickManager.hasForceTicket(npc.getUUID()),
                SmartNpcConfig.REMOTE_NPC_DEPARTURE_ENABLED.get(),
                SmartNpcConfig.REMOTE_NPC_DEPARTURE_MIN_MINUTES.get(),
                SmartNpcConfig.REMOTE_NPC_DEPARTURE_MAX_MINUTES.get())) {
            ChatUtil.leaveGame(npc);
            // Existing leave handlers release tickets, tab entry and the persisted natural spawn cap.
            npc.discard();
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) { ExternalChunkActivity.clear(); }
}

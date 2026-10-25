package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;

/** An authoritative completed action, never an intention, chat claim or inventory grant. */
public record VerifiedWorkOutcome(PlayerNpcEntity npc, Kind kind, BlockPos position, String resourceId, int units) {
    public enum Kind { HARVEST, PLACE, CRAFT, HOME_COMPLETE, JOURNEY_RETURN, COOK, FISH, SAFE_REST }
    public VerifiedWorkOutcome {
        Objects.requireNonNull(npc);Objects.requireNonNull(kind);Objects.requireNonNull(position);
        Objects.requireNonNull(resourceId);position=position.immutable();
        if(units<1||units>64||resourceId.isBlank()||resourceId.length()>160)throw new IllegalArgumentException("Invalid work outcome");
    }
    public static void emit(PlayerNpcEntity npc, Kind kind, BlockPos position, String resourceId, int units) {
        if(!(npc.level() instanceof ServerLevel)||units<=0)return;
        Events.post(new VerifiedWorkOutcome(npc,kind,position,resourceId,Math.min(64,units)));
    }
}

package com.pla.smart_npc.fabric.mixin;

import com.pla.smart_npc.fabric.survival.SocialSafety;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ChestClickMixin {
    @Shadow public ServerPlayer player;
    @Unique private SocialSafety.ChestChange smartNpc$chestChange;
    @Inject(method="handleContainerClick", at=@At("HEAD"))
    private void smartNpc$beforeClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        // The network-thread invocation is rescheduled by vanilla; inspect only its server-thread replay.
        if (player.level().getServer().isSameThread())
            smartNpc$chestChange = SocialSafety.beforeChestClick(player, packet.containerId());
    }
    @Inject(method="handleContainerClick", at=@At("TAIL"))
    private void smartNpc$afterClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        if (player.level().getServer().isSameThread()) {
            var change = smartNpc$chestChange; smartNpc$chestChange = null;
            SocialSafety.afterChestClick(player, change);
        }
    }
}

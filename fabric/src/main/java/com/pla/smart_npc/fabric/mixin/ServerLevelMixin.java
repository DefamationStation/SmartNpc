package com.pla.smart_npc.fabric.mixin;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"))
    private void smartNpc$beforeTick(Entity entity,CallbackInfo ci){if(entity instanceof PlayerNpcEntity || entity instanceof EnderDragon)Events.post(new Events.EntityTickEvent.Pre(entity));}
    @Inject(method="tickNonPassenger",at=@At("TAIL"))
    private void smartNpc$afterTick(Entity entity,CallbackInfo ci){if(entity instanceof PlayerNpcEntity || entity instanceof EnderDragon)Events.post(new Events.EntityTickEvent.Post(entity));}
}

package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBoat.class)
public abstract class BoatMixin {
    @Inject(method="getDismountLocationForPassenger",at=@At("HEAD"),cancellable=true)
    private void smartNpcSafeExit(net.minecraft.world.entity.LivingEntity passenger,CallbackInfoReturnable<net.minecraft.world.phys.Vec3> cir){
        if(passenger instanceof PlayerNpcEntity npc&&com.pla.smart_npc.fabric.survival.Journeys.ownsBoat(npc,(Entity)(Object)this)){
            var exit=com.pla.smart_npc.fabric.survival.Journeys.state(npc).dismountPosition;
            if(exit!=null)cir.setReturnValue(exit);
        }
    }
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void blockSpecificMobsFromBoat(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        if (passenger instanceof PlayerNpcEntity npc
                && !(com.pla.smart_npc.fabric.survival.Journeys.active(npc)
                    && com.pla.smart_npc.fabric.survival.Journeys.ownsBoat(npc,(Entity)(Object)this))) {
            cir.setReturnValue(false);
        }
    }

    @org.spongepowered.asm.mixin.Shadow private void controlBoat() { throw new AssertionError(); }

    // Vanilla applies paddle acceleration only for a client-controlled player.
    // Our explicitly owned NPC journey is server-controlled, using the same physics.
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;floatBoat()V",shift=At.Shift.AFTER))
    private void smartNpcPaddles(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        var boat=(AbstractBoat)(Object)this;
        if(!boat.level().isClientSide()&&boat.getFirstPassenger() instanceof PlayerNpcEntity npc
                &&com.pla.smart_npc.fabric.survival.Journeys.active(npc)
                &&com.pla.smart_npc.fabric.survival.Journeys.ownsBoat(npc,boat))controlBoat();
    }
}

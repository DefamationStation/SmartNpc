package com.pla.smart_npc.fabric.mixin;
import com.pla.smart_npc.client.gui.SmartNpcInspectorOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderDispatcher.class)
public class HideInspectatorMixin {
    @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
    private void smartNpc$hide(Entity entity,Frustum frustum,double x,double y,double z,float partialTick,CallbackInfoReturnable<Boolean> ci){
        if(entity instanceof AbstractClientPlayer player && SmartNpcInspectorOverlay.shouldHideInspectatorLocalPlayer(player))ci.setReturnValue(false);
    }
}

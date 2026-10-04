package com.pla.smart_npc.fabric.mixin;
import com.pla.smart_npc.fabric.Events;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public class ScreenMixin {
    @Inject(method="setScreen",at=@At("HEAD"),cancellable=true)
    private void smartNpc$opening(Screen screen,CallbackInfo ci){
        if(Events.post(new Events.ScreenEvent.Opening(screen)).isCanceled())ci.cancel();
    }
}

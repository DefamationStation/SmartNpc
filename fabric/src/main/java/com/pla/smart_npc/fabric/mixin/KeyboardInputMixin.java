package com.pla.smart_npc.fabric.mixin;
import com.pla.smart_npc.fabric.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void smartNpc$input(CallbackInfo ci){
        var player=Minecraft.getInstance().player;
        if(player!=null && player.input==(Object)this)
            Events.post(new Events.MovementInputUpdateEvent(player,(ClientInput)(Object)this));
    }
}

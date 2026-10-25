package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.memory.NpcMemories;
import com.pla.smart_npc.fabric.survival.progression.NpcProgression;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Flush dirty personal attachments before the native entity save serializes them. */
@Mixin(value=PlayerNpcEntity.class,remap=false)
public abstract class PersonalStateSaveMixin {
    @Inject(method="addAdditionalSaveData",at=@At("HEAD"))
    private void player_npc$savePersonalState(ValueOutput output,CallbackInfo callback){
        var npc=(PlayerNpcEntity)(Object)this;
        if(!npc.level().isClientSide()){NpcMemories.save(npc);NpcProgression.save(npc);}
    }
}

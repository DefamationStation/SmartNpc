package com.pla.smart_npc.fabric.mixin;

import com.pla.smart_npc.fabric.PersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityDataMixin implements PersistentData {
    @Unique private CompoundTag smartNpc$data = new CompoundTag();
    public CompoundTag smartNpc$data() { return smartNpc$data; }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void smartNpc$save(ValueOutput output, CallbackInfo ci) {
        if (!smartNpc$data.isEmpty()) output.store("SmartNpcPersistentData", CompoundTag.CODEC, smartNpc$data);
    }

    @Inject(method = "loadAdditional", at = @At("HEAD"))
    private void smartNpc$load(ValueInput input, CallbackInfo ci) {
        smartNpc$data = input.read("SmartNpcPersistentData", CompoundTag.CODEC).orElseGet(CompoundTag::new);
    }
}

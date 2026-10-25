package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.PlacingBlockAi;
import com.pla.smart_npc.fabric.survival.VerifiedWorkOutcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PlacingBlockAi.class,remap=false)
public abstract class PlacingWorkOutcomeMixin {
    @Shadow @Final private PlayerNpcEntity playerNpc;
    @Inject(method="placeBlock(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Z",at=@At("RETURN"))
    private void player_npc$completedPlacement(ServerLevel level,BlockPos pos,BlockState requested,boolean effects,CallbackInfoReturnable<Boolean> result){
        if(result.getReturnValue())VerifiedWorkOutcome.emit(playerNpc,VerifiedWorkOutcome.Kind.PLACE,pos,
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString(),1);
    }
}

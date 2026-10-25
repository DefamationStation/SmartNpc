package com.pla.smart_npc.mixin;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.PathNavigationAi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A close destination can still require a longer door or terrain detour. */
@Mixin(value = PathNavigationAi.class, remap = false)
public abstract class LocalPathDetourMixin {
    @Inject(method = "createBoundedPath", at = @At("HEAD"), cancellable = true)
    private static void player_npc$loadedDetourRegion(PlayerNpcEntity npc, BlockPos target, float nodes,
                                                     CallbackInfoReturnable<Path> result) {
        if(npc==null||target==null||!(npc.level() instanceof ServerLevel level))return;
        double dx=target.getX()-npc.getBlockX(),dz=target.getZ()-npc.getBlockZ();
        int distance=(int)Math.ceil(Math.hypot(dx,dz));if(distance>48)return;
        int radius=Math.max(24,Math.min(48,distance+6))+8;
        for(int x=(npc.getBlockX()-radius)>>4;x<=((npc.getBlockX()+radius)>>4);x++)
            for(int z=(npc.getBlockZ()-radius)>>4;z<=((npc.getBlockZ()+radius)>>4);z++)
                if(!level.hasChunkAt(new BlockPos(x*16,npc.getBlockY(),z*16))){result.setReturnValue(null);return;}
    }

    @ModifyArg(method = "createBoundedPath", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;II)Lnet/minecraft/world/level/pathfinder/Path;"), index = 2)
    private static int player_npc$allowLocalDetour(int range) {
        // Preserve the caller's visited-node ceiling and existing 48-block cap.
        return Math.max(24,range);
    }
}

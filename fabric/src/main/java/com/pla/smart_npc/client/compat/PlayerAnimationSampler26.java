package com.pla.smart_npc.client.compat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
/** The snapshot build deliberately does not load the optional animation library. */
final class PlayerAnimationSampler26 {
    static Object animation(Identifier id){return null;}
    static float endTick(Object animation){throw unavailable();}
    static float stopTick(Object animation){throw unavailable();}
    static float loopedTime(Object animation,float time){throw unavailable();}
    static Object sample(Object animation,float time,boolean mirrored){throw unavailable();}
    static void applyPart(Object animation,String name,ModelPart part){throw unavailable();}
    static void applyHeldItemTransform(PoseStack stack,Object animation,String name){throw unavailable();}
    private static UnsupportedOperationException unavailable(){return new UnsupportedOperationException("Player Animation Library integration is not enabled in the Fabric snapshot port");}
}

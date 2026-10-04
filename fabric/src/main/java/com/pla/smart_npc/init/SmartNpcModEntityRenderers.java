package com.pla.smart_npc.init;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import com.pla.smart_npc.client.renderer.*;
public final class SmartNpcModEntityRenderers {
    public static void register(){
        EntityRendererRegistry.register(SmartNpcModEntities.PLAYER_NPC.get(),FakePlayerRenderer::new);
        EntityRendererRegistry.register(SmartNpcModEntities.PLAYER_NPC_FISHING_BOBBER.get(),PlayerNpcFishingBobberRenderer::new);
    }
}

package com.pla.smart_npc.init;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.core.registries.BuiltInClientRegistries;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.pla.smart_npc.client.renderer.*;
public final class SmartNpcModEntityRenderers {
    public static void register(){
        Registry.register(BuiltInClientRegistries.ENTITY_RENDERER,BuiltInRegistries.ENTITY_TYPE.getKey(SmartNpcModEntities.PLAYER_NPC.get()),(EntityRendererProvider<com.pla.smart_npc.entity.PlayerNpcEntity>)FakePlayerRenderer::new);
        Registry.register(BuiltInClientRegistries.ENTITY_RENDERER,BuiltInRegistries.ENTITY_TYPE.getKey(SmartNpcModEntities.PLAYER_NPC_FISHING_BOBBER.get()),(EntityRendererProvider<com.pla.smart_npc.entity.PlayerNpcFishingBobberEntity>)PlayerNpcFishingBobberRenderer::new);
    }
}

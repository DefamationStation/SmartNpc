package com.pla.smart_npc.fabric;
import com.pla.smart_npc.client.SmartNpcClientItemProperties;
import com.pla.smart_npc.client.gui.*;
import com.pla.smart_npc.init.*;
import com.pla.smart_npc.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
public final class SmartNpcFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        SmartNpcModEntityRenderers.register();
        SmartNpcClientItemProperties.register();
        MenuScreens.register(SmartNpcModMenus.INVENTORY_VIEWER.get(),InventoryViewerScreen::new);
        Events.register(SmartNpcInspectorOverlay.class);
        ClientTickEvents.END_CLIENT_TICK.register(mc->Events.post(new Events.ClientTickEvent.Post()));
        HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.INFO_BAR, original -> (graphics, delta) -> {
            if (!SmartNpcInspectorOverlay.isInspectatorActive()) original.extractRenderState(graphics, delta);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("smart_npc","inspector"),(graphics,delta)->Events.post(new Events.RenderGuiEvent.Post(graphics)));
        ClientPlayNetworking.registerGlobalReceiver(PlayerNpcInspectorPacket.TYPE,(p,c)->PlayerNpcInspectorPacket.handle(p,new NetworkContext(c.player(),c.client())));
        ClientPlayNetworking.registerGlobalReceiver(PlayerNpcInspectatorCycleResultPacket.TYPE,(p,c)->PlayerNpcInspectatorCycleResultPacket.handle(p,new NetworkContext(c.player(),c.client())));
    }
}

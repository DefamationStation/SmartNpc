package com.pla.smart_npc.network;
import com.pla.smart_npc.fabric.NetworkContext;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
public final class SmartNpcNetwork {
    public static void register(){
        PayloadTypeRegistry.clientboundPlay().register(PlayerNpcInspectorPacket.TYPE,PlayerNpcInspectorPacket.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerNpcInspectatorCycleResultPacket.TYPE,PlayerNpcInspectatorCycleResultPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerNpcInspectorRequestPacket.TYPE,PlayerNpcInspectorRequestPacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerNpcInspectatorModePacket.TYPE,PlayerNpcInspectatorModePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerNpcInspectatorCyclePacket.TYPE,PlayerNpcInspectatorCyclePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PlayerNpcGoalTracePacket.TYPE,PlayerNpcGoalTracePacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayerNpcInspectorRequestPacket.TYPE,(p,c)->PlayerNpcInspectorRequestPacket.handle(p,new NetworkContext(c.player(),c.server())));
        ServerPlayNetworking.registerGlobalReceiver(PlayerNpcInspectatorModePacket.TYPE,(p,c)->PlayerNpcInspectatorModePacket.handle(p,new NetworkContext(c.player(),c.server())));
        ServerPlayNetworking.registerGlobalReceiver(PlayerNpcInspectatorCyclePacket.TYPE,(p,c)->PlayerNpcInspectatorCyclePacket.handle(p,new NetworkContext(c.player(),c.server())));
        ServerPlayNetworking.registerGlobalReceiver(PlayerNpcGoalTracePacket.TYPE,(p,c)->PlayerNpcGoalTracePacket.handle(p,new NetworkContext(c.player(),c.server())));
    }
}

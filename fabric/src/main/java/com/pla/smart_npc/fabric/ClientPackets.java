package com.pla.smart_npc.fabric;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public final class ClientPackets {
    public static void sendToServer(CustomPacketPayload payload) { ClientPlayNetworking.send(payload); }
}

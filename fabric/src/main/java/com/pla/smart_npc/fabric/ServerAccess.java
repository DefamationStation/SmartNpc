package com.pla.smart_npc.fabric;
import net.minecraft.server.MinecraftServer;
public final class ServerAccess {
    private static volatile MinecraftServer current;
    public static MinecraftServer getCurrentServer() { return current; }
    static void set(MinecraftServer server) { current=server; }
}

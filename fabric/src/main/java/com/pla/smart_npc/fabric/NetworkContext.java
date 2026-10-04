package com.pla.smart_npc.fabric;
import net.minecraft.world.entity.player.Player;
import java.util.concurrent.Executor;
public record NetworkContext(Player player, Executor executor) {
    public void enqueueWork(Runnable work) { executor.execute(work); }
}

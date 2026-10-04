package com.pla.smart_npc.fabric;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
/** Unregistered identity used only when constructing vanilla tab-list packets. */
public final class TabPlayer extends ServerPlayer {
    @Override public net.minecraft.network.chat.Component getTabListDisplayName() {
        return Events.post(new Events.PlayerEvent.TabListNameFormat(this)).getDisplayName();
    }
    public TabPlayer(ServerLevel level,GameProfile profile){
        super(level.getServer(),level,profile,ClientInformation.createDefault());
        // Initialize packet metadata without broadcasting a game-mode change for an
        // identity that has not yet been added to the real players' tab lists.
        gameMode.setGameModeForPlayer(net.minecraft.world.level.GameType.SPECTATOR, null);
        connection=new ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) {}
        },this,CommonListenerCookie.createInitial(profile,false));
    }
}

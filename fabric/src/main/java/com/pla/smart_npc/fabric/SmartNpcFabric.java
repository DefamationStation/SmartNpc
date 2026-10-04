package com.pla.smart_npc.fabric;

import com.pla.smart_npc.config.*;
import com.pla.smart_npc.event.*;
import com.pla.smart_npc.init.*;
import com.pla.smart_npc.network.SmartNpcNetwork;
import com.pla.smart_npc.util.*;
import com.pla.smart_npc.world.PlayerNpcWorldSpawns;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.world.InteractionResult;

public final class SmartNpcFabric implements ModInitializer {
    @Override public void onInitialize(){
        var dir=FabricLoader.getInstance().getConfigDir();
        SmartNpcConfig.SPEC.load(dir.resolve("smart_npc-server.toml"));
        SmartNpcNamesConfig.SPEC.load(dir.resolve("smart_npc-names.toml"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        // Force registration in dependency order while vanilla registries are mutable.
        SmartNpcModEntities.PLAYER_NPC.get();
        PlayerNpcForceTickManager.PLAYER_NPC_TICKET.id();
        SmartNpcModItems.INVENTORY_VIEWER.get();
        SmartNpcModMenus.INVENTORY_VIEWER.get();
        SmartNpcModEntities.registerAttributes(new Events.EntityAttributeCreationEvent());
        SmartNpcModEntities.registerSpawnPlacements(new Events.RegisterSpawnPlacementsEvent());
        SmartNpcModCreativeTabs.register();
        PlayerNpcWorldSpawns.register();
        SmartNpcNetwork.register();
        Events.register(com.pla.smart_npc.fabric.survival.SurvivalTasks.class);
        for(Class<?> type:new Class<?>[]{PlayerNpcChestProtectEvent.class,PlayerNpcCommandEvent.class,PlayerNpcDepartureEvent.class,PlayerNpcHomeEvent.class,ProgressionEvent.class,PlayerNpcTeamUpEvent.class,PlayerNpcInspectatorEvent.class,PlayerNpcForceTickManager.class,PlayerNpcGoalTraceLogger.class,PlayerNpcNaturalSpawnCap.class,PlayerNpcPerformanceMonitor.class}) Events.register(type);
        new NpcGearLoadEvent().onAddReloadListeners(new Events.AddServerReloadListenersEvent());
        ServerLifecycleEvents.SERVER_STARTING.register(ServerAccess::set);
        ServerLifecycleEvents.SERVER_STARTED.register(s->Events.post(new Events.ServerStartedEvent(s)));
        ServerLifecycleEvents.SERVER_STOPPING.register(s->Events.post(new Events.ServerStoppingEvent(s)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{Events.post(new Events.ServerStoppedEvent(s));PlayerNpcForceTickManager.PLAYER_NPC_TICKET.clear();ServerAccess.set(null);});
        ServerTickEvents.START_SERVER_TICK.register(s->Events.post(new Events.ServerTickEvent.Pre(s)));
        ServerTickEvents.END_SERVER_TICK.register(s->{Events.post(new Events.ServerTickEvent.Post(s));for(var p:s.getPlayerList().getPlayers())Events.post(new Events.PlayerTickEvent.Post(p));});
        ServerEntityEvents.ENTITY_LOAD.register((e,l)->Events.post(new Events.EntityJoinLevelEvent(e,l)));
        ServerEntityEvents.ENTITY_UNLOAD.register((e,l)->Events.post(new Events.EntityLeaveLevelEvent(e,l)));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e,source,amount)->!Events.post(new Events.LivingIncomingDamageEvent(e,source)).isCanceled());
        ServerLivingEntityEvents.AFTER_DEATH.register((e,source)->Events.post(new Events.LivingDeathEvent(e,source)));
        ServerPlayConnectionEvents.JOIN.register((h,sender,s)->Events.post(new Events.PlayerEvent.PlayerLoggedInEvent(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->Events.post(new Events.PlayerEvent.PlayerLoggedOutEvent(h.player)));
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->Events.post(new Events.PlayerEvent.PlayerRespawnEvent(p)));
        ServerPlayerEvents.COPY_FROM.register((old,p,alive)->Events.post(new Events.PlayerEvent.Clone(old,p)));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p,origin,destination)->Events.post(new Events.PlayerEvent.PlayerChangedDimensionEvent(p)));
        ServerMessageEvents.CHAT_MESSAGE.register((message,p,bound)->Events.post(new Events.ServerChatEvent(p,message.signedContent())));
        CommandRegistrationCallback.EVENT.register((dispatcher,context,env)->Events.post(new Events.RegisterCommandsEvent(dispatcher,context)));
        PlayerBlockBreakEvents.AFTER.register((l,p,pos,state,be)->Events.post(new Events.BreakBlockEvent(l,pos,state,p)));
        UseBlockCallback.EVENT.register((p,l,hand,hit)->{if(!l.isClientSide())Events.post(new Events.PlayerInteractEvent.RightClickBlock(l,hit.getBlockPos(),p));return InteractionResult.PASS;});
    }
}

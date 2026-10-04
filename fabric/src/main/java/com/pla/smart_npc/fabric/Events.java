package com.pla.smart_npc.fabric;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import com.mojang.brigadier.CommandDispatcher;

/** Small typed boundary between Fabric callbacks and the shared gameplay handlers.
 * These are Smart NPC events, not a replacement implementation of NeoForge. */
public final class Events {
    public enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
    public @interface SubscribeEvent { EventPriority priority() default EventPriority.NORMAL; boolean receiveCanceled() default false; }
    private record Handler(Object receiver, Method method, Class<?> type, SubscribeEvent annotation) {}
    private static volatile List<Handler> handlers = List.of();
    public static synchronized void register(Object receiver) {
        var next = new ArrayList<>(handlers);
        Class<?> type=receiver instanceof Class<?> c?c:receiver.getClass();
        for(Method method:type.getDeclaredMethods()) {
            var annotation=method.getAnnotation(SubscribeEvent.class);
            if(annotation==null) continue;
            if(method.getParameterCount()!=1) throw new IllegalArgumentException(method.toString());
            method.setAccessible(true);
            next.add(new Handler(Modifier.isStatic(method.getModifiers())?null:receiver,method,method.getParameterTypes()[0],annotation));
        }
        next.sort(Comparator.comparing(h->h.annotation.priority()));
        handlers = List.copyOf(next);
    }
    public static <T> T post(T event) {
        for(var handler:handlers) if(handler.type.isInstance(event)) {
            if(event instanceof Cancelable c && c.isCanceled() && !handler.annotation.receiveCanceled()) continue;
            try { handler.method.invoke(handler.receiver,event); }
            catch(ReflectiveOperationException e) { throw new IllegalStateException("Smart NPC event failed: "+handler.method,e instanceof InvocationTargetException i?i.getCause():e); }
        }
        return event;
    }
    public static class Cancelable {
        private boolean canceled;
        public boolean isCanceled(){return canceled;}
        public void setCanceled(boolean value){canceled=value;}
    }
    public record ServerStartedEvent(MinecraftServer getServer) {}
    public record ServerStoppingEvent(MinecraftServer getServer) {}
    public record ServerStoppedEvent(MinecraftServer getServer) {}
    public static final class ServerTickEvent {
        public record Pre(MinecraftServer getServer) {}
        public record Post(MinecraftServer getServer) {}
    }
    public static final class EntityTickEvent {
        public record Pre(Entity getEntity) {}
        public record Post(Entity getEntity) {}
    }
    public static final class PlayerTickEvent {
        public record Post(Player getEntity) {}
    }
    public static class EntityJoinLevelEvent extends Cancelable {
        private final Entity entity; private final Level level;
        public EntityJoinLevelEvent(Entity entity,Level level){this.entity=entity;this.level=level;}
        public Entity getEntity(){return entity;} public Level getLevel(){return level;}
    }
    public record EntityLeaveLevelEvent(Entity getEntity,Level getLevel) {}
    public record LivingDeathEvent(LivingEntity getEntity,DamageSource getSource) {}
    public static class LivingIncomingDamageEvent extends Cancelable {
        private final LivingEntity entity;private final DamageSource source;
        public LivingIncomingDamageEvent(LivingEntity entity,DamageSource source){this.entity=entity;this.source=source;}
        public LivingEntity getEntity(){return entity;} public DamageSource getSource(){return source;}
    }
    public static final class PlayerEvent {
        public record PlayerLoggedInEvent(Player getEntity) {}
        public record PlayerLoggedOutEvent(Player getEntity) {}
        public record PlayerRespawnEvent(Player getEntity) {}
        public record PlayerChangedDimensionEvent(Player getEntity) {}
        public record Clone(Player getOriginal,Player getEntity) {}
        public static final class TabListNameFormat {
            private final Player player;private net.minecraft.network.chat.Component name;
            public TabListNameFormat(Player player){this.player=player;}
            public Player getEntity(){return player;}
            public void setDisplayName(net.minecraft.network.chat.Component name){this.name=name;}
            public net.minecraft.network.chat.Component getDisplayName(){return name;}
        }
    }
    public record ServerChatEvent(ServerPlayer getPlayer,String getRawText) {}
    public record RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> getDispatcher,CommandBuildContext getBuildContext) {}
    public static final class AddServerReloadListenersEvent {
        public void addListener(net.minecraft.resources.Identifier id,net.minecraft.server.packs.resources.PreparableReloadListener listener){net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.SERVER_DATA).registerReloadListener(id,listener);}
    }
    public static final class ClientTickEvent { public record Post() {} }
    public static final class ScreenEvent {
        public static final class Opening extends Cancelable {
            private final net.minecraft.client.gui.screens.Screen screen;
            public Opening(net.minecraft.client.gui.screens.Screen screen){this.screen=screen;}
            public net.minecraft.client.gui.screens.Screen getNewScreen(){return screen;}
        }
    }
    public record MovementInputUpdateEvent(net.minecraft.client.player.LocalPlayer getEntity,net.minecraft.client.player.ClientInput getInput) {}
    public static final class RenderGuiEvent {public record Post(net.minecraft.client.gui.GuiGraphicsExtractor getGuiGraphics) {} }
    public static final class RenderGuiLayerEvent {
        public static final class Pre extends Cancelable {
            private final net.minecraft.resources.Identifier name;
            public Pre(net.minecraft.resources.Identifier name){this.name=name;}
            public net.minecraft.resources.Identifier getName(){return name;}
        }
    }
    public static class BlockEvent extends Cancelable {
        final Level level;final BlockPos pos;final BlockState state;final Player player;
        public BlockEvent(Level level,BlockPos pos,BlockState state,Player player){this.level=level;this.pos=pos;this.state=state;this.player=player;}
        public Level getLevel(){return level;} public BlockPos getPos(){return pos;} public BlockState getState(){return state;} public Player getPlayer(){return player;}
    }
    public static final class BreakBlockEvent extends BlockEvent { public BreakBlockEvent(Level l,BlockPos p,BlockState s,Player player){super(l,p,s,player);} }
    public static final class PlayerInteractEvent {
        public static final class RightClickBlock extends BlockEvent {
            public RightClickBlock(Level l,BlockPos p,Player player){super(l,p,l.getBlockState(p),player);}
            public Player getEntity(){return player;}
        }
    }
    public record ModConfig(ConfigSpec getSpec) {
        public Loaded getLoadedConfig(){return new Loaded(getSpec.file());}
        public record Loaded(com.electronwill.nightconfig.core.file.CommentedFileConfig config) {}
    }
    public static final class ModConfigEvent {
        public record Loading(ModConfig getConfig) {}
        public record Reloading(ModConfig getConfig) {}
    }
    public static final class EntityAttributeCreationEvent {
        public <T extends LivingEntity> void put(net.minecraft.world.entity.EntityType<T> type,net.minecraft.world.entity.ai.attributes.AttributeSupplier attrs){net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(type,attrs);}
    }
    public static final class RegisterSpawnPlacementsEvent {
        public enum Operation { REPLACE }
        public <T extends net.minecraft.world.entity.Mob> void register(net.minecraft.world.entity.EntityType<T> type,net.minecraft.world.entity.SpawnPlacementType placement,net.minecraft.world.level.levelgen.Heightmap.Types height,net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate,Operation operation){net.minecraft.world.entity.SpawnPlacements.register(type,placement,height,predicate);}
    }
}

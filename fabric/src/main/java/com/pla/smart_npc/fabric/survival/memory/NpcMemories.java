package com.pla.smart_npc.fabric.survival.memory;

import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.ai.StoneAi;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.ResourceMemory;
import com.pla.smart_npc.fabric.survival.SocialSafety;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.fabric.survival.VerifiedWorkOutcome;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Personal observed evidence. Queries never load chunks or read remote inventories. */
public final class NpcMemories {
    public static final String KEY="SmartNpcPersonalMemory";
    private static final Map<PlayerNpcEntity,State> STATES=new WeakHashMap<>();
    private static boolean registered;
    private static final class State {
        final MemoryCodec.Loaded loaded;boolean dirty;int cursor,revalidateCursor;
        State(MemoryCodec.Loaded loaded){this.loaded=loaded;}
    }
    public enum Validation { CONFIRMED,EXHAUSTED,UNKNOWN }
    private NpcMemories(){ }

    public static synchronized void register(){
        if(registered)return;registered=true;
        MemorySettings.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("smart_npc-memory.toml"));
        Events.register(NpcMemories.class);
    }
    private static String scope(ServerLevel level){
        String identity="smart-npc-memory:"+level.getSeed()+":"+level.getServer().getWorldData().getLevelName();
        return UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    public static String dimension(PlayerNpcEntity npc){return SurvivalTasks.dimension(npc);}
    public static ObservedMemory.Point point(BlockPos p){return new ObservedMemory.Point(p.getX(),p.getY(),p.getZ());}
    public static BlockPos block(ObservedMemory.Point p){return new BlockPos(p.x(),p.y(),p.z());}
    private static State state(PlayerNpcEntity npc){
        if(!(npc.level() instanceof ServerLevel level))throw new IllegalArgumentException("Personal memory is server-only");
        return STATES.computeIfAbsent(npc,n->{
            var attachment=PersistentData.get(n);var saved=attachment.getCompoundOrEmpty(KEY);
            var state=new State(MemoryCodec.load(saved,n.getUUID(),scope(level)));state.dirty=state.loaded.writable()&&!attachment.contains(KEY);
            // Old coal coordinates are historical observations, not proof that ore remains.
            if(saved.getIntOr("version",0)==0&&state.loaded.writable()){
                var legacy=ResourceMemory.load(attachment.getCompoundOrEmpty("SurvivalResources"));
                for(var o:legacy.coal){
                    if(o.seen()<0||o.dimension().isBlank()||o.dimension().length()>128)continue;
                    var entry=new ObservedMemory.Entry(ObservedMemory.Kind.WORK_SITE,o.dimension(),point(BlockPos.of(o.position())),null,null,
                            "legacy:coal_observation",ObservedMemory.Source.LEGACY_OBSERVATION,o.seen(),40);
                    if(entry.current(level.getGameTime()))state.dirty|=state.loaded.observations().observe(entry);
                }
            }
            return state;
        });
    }
    public static NpcPersonality personality(PlayerNpcEntity npc){
        var interests=new NpcPersonality.Interests(npc.hasInterest(PlayerNpcInterest.TEAMUP),npc.hasInterest(PlayerNpcInterest.CAUTIOUS),
                npc.hasInterest(PlayerNpcInterest.COWARD),npc.hasInterest(PlayerNpcInterest.EXPLORING),npc.hasInterest(PlayerNpcInterest.BUILDING));
        return state(npc).loaded.personality().effective(interests,MemorySettings.overrides());
    }
    public static int departureFoodReserve(PlayerNpcEntity npc){
        return MemorySettings.PERSONALITY_ENABLED.get()?personality(npc).departureFoodReserve(MemorySettings.EXTRA_DEPARTURE_FOOD.get()):4;
    }
    public static List<ObservedMemory.Entry> observations(PlayerNpcEntity npc){
        return state(npc).loaded.observations().entries();
    }
    public static void save(PlayerNpcEntity npc){
        var state=STATES.get(npc);
        if(state!=null&&state.dirty&&state.loaded.writable()){
            PersistentData.get(npc).put(KEY,MemoryCodec.save(state.loaded));state.dirty=false;
        }
    }
    private static void remember(PlayerNpcEntity npc,ObservedMemory.Kind kind,BlockPos pos,BlockPos destination,
                                 UUID subject,String detail,ObservedMemory.Source source,int confidence){
        if(!MemorySettings.MEMORY_ENABLED.get()||!(npc.level() instanceof ServerLevel level))return;
        var state=state(npc);if(!state.loaded.writable())return;
        try{
            state.dirty|=state.loaded.observations().observe(new ObservedMemory.Entry(kind,dimension(npc),point(pos),
                    destination==null?null:point(destination),subject,detail,source,level.getGameTime(),confidence));
        }catch(IllegalArgumentException ignored){ /* Unsupported coordinates/identifiers grant no knowledge. */ }
    }
    private static String bounded(String text){
        if(text==null)return "";var result=text.replaceAll("[\\p{Cntrl}]"," ");return result.length()>128?result.substring(0,128):result;
    }
    private static boolean resource(BlockState state){
        return state.is(BlockTags.LOGS)||StoneAi.isStone(state)||BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore");
    }
    /** Records exactly a currently visible block, never nearby hidden ores. */
    public static boolean observeResource(PlayerNpcEntity npc,BlockPos pos){
        if(!MemorySettings.MEMORY_ENABLED.get()||!(npc.level() instanceof ServerLevel level)||npc.blockPosition().distSqr(pos)>12*12
                ||!level.hasChunkAt(pos)||!SurvivalTasks.visible(npc,pos))return false;
        if(!state(npc).loaded.writable())return false;
        var block=level.getBlockState(pos);if(!resource(block))return false;
        String id=BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString();if(id.length()>128)return false;
        remember(npc,ObservedMemory.Kind.RESOURCE,pos,null,null,id,ObservedMemory.Source.DIRECT_SIGHT,100);
        return true;
    }
    public static List<ObservedMemory.Entry> knownResources(PlayerNpcEntity npc,Predicate<String> desired){
        if(!MemorySettings.MEMORY_ENABLED.get())return List.of();
        long now=npc.level().getGameTime();var origin=point(npc.blockPosition());
        return state(npc).loaded.observations().current(ObservedMemory.Kind.RESOURCE,dimension(npc),now,20).stream()
                .filter(e->desired.test(e.detail())).sorted(Comparator.comparingDouble(e->e.position().distanceSquared(origin))).toList();
    }
    /** An unloaded or unseen location remains uncertain; it is never force-loaded. */
    public static Validation revalidateResource(PlayerNpcEntity npc,ObservedMemory.Entry entry){
        if(!MemorySettings.MEMORY_ENABLED.get()||entry.kind()!=ObservedMemory.Kind.RESOURCE
                ||!dimension(npc).equals(entry.dimension())||!(npc.level() instanceof ServerLevel level))return Validation.UNKNOWN;
        var pos=block(entry.position());
        if(npc.blockPosition().distSqr(pos)>12*12||!level.hasChunkAt(pos)||!SurvivalTasks.visible(npc,pos))return Validation.UNKNOWN;
        var current=level.getBlockState(pos);
        if(resource(current)&&BuiltInRegistries.BLOCK.getKey(current.getBlock()).toString().equals(entry.detail())){
            observeResource(npc,pos);return Validation.CONFIRMED;
        }
        var state=state(npc);if(state.loaded.writable())state.dirty|=state.loaded.observations().forget(ObservedMemory.Kind.RESOURCE,entry.dimension(),entry.position());
        return Validation.EXHAUSTED;
    }
    public static void routeFailed(PlayerNpcEntity npc,BlockPos destination,String reason){
        if(destination!=null)remember(npc,ObservedMemory.Kind.FAILED_ROUTE,npc.blockPosition(),destination,null,bounded(reason),ObservedMemory.Source.TERMINAL_ROUTE_FAILURE,100);
    }
    public static boolean failedRouteRecently(PlayerNpcEntity npc,BlockPos destination){
        return destination!=null&&MemorySettings.MEMORY_ENABLED.get()&&state(npc).loaded.observations().failedRecently(
                dimension(npc),point(npc.blockPosition()),point(destination),npc.level().getGameTime());
    }
    public static void routeSucceeded(PlayerNpcEntity npc,BlockPos origin,BlockPos destination){
        if(origin==null||destination==null||!MemorySettings.MEMORY_ENABLED.get())return;
        var state=state(npc);if(state.loaded.writable())state.dirty|=state.loaded.observations().clearRouteFailure(dimension(npc),point(destination));
        remember(npc,ObservedMemory.Kind.SUCCESSFUL_ROUTE,origin,destination,null,"arrived",ObservedMemory.Source.SELF_ACTION,100);
    }
    /** Only an authoritative completed handoff/heal may call this; proximity is not attribution. */
    public static void helpReceived(PlayerNpcEntity npc,UUID actor,String actualHelp){
        if(actor!=null&&!actor.equals(npc.getUUID()))remember(npc,ObservedMemory.Kind.HELP,npc.blockPosition(),null,actor,bounded(actualHelp),ObservedMemory.Source.ACTUAL_HANDOFF,100);
    }
    @Events.SubscribeEvent public static void work(VerifiedWorkOutcome event){
        if(event.units()<1||!(event.npc().level() instanceof ServerLevel))return;
        // A harvested position is a site of past success, not still-present ore.
        remember(event.npc(),ObservedMemory.Kind.WORK_SITE,event.position(),null,null,
                bounded(event.kind().name().toLowerCase(java.util.Locale.ROOT)+":"+event.resourceId()),ObservedMemory.Source.SELF_ACTION,100);
    }
    @Events.SubscribeEvent public static void encounter(Events.LivingIncomingDamageEvent event){
        if(!(event.getEntity() instanceof PlayerNpcEntity npc)||!(event.getSource().getEntity() instanceof LivingEntity actor)
                ||!SocialSafety.hasCause(npc,actor))return;
        var cause=SocialSafety.memory(npc).cause(actor.getUUID(),npc.level().getGameTime());
        if(cause.isPresent())remember(npc,ObservedMemory.Kind.HOSTILE,npc.blockPosition(),null,actor.getUUID(),
                cause.get().name(),ObservedMemory.Source.CANONICAL_DEFENCE,100);
    }
    @Events.SubscribeEvent public static void tick(Events.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof PlayerNpcEntity npc)||!(npc.level() instanceof ServerLevel level)
                ||!MemorySettings.MEMORY_ENABLED.get()||!npc.isAlive()||npc.isNoAi()
                ||npc.tickCount%20!=Math.floorMod(npc.getUUID().hashCode(),20)
                ||!PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc))return;
        var state=state(npc);if(!state.loaded.writable())return;
        state.dirty|=state.loaded.observations().expire(level.getGameTime());
        PlayerNpcHomeUtil.getHome(npc).ifPresent(home->{
            boolean recent=state.loaded.observations().current(ObservedMemory.Kind.HOME,dimension(npc),level.getGameTime(),1).stream()
                    .anyMatch(e->e.position().equals(point(home.origin()))&&level.getGameTime()-e.seen()<1200);
            if(!recent)remember(npc,ObservedMemory.Kind.HOME,home.origin(),null,null,"owned home",ObservedMemory.Source.OWN_HOME,100);
        });
        if(npc.isInLava()||npc.isOnFire()||npc.isInWater()&&npc.getAirSupply()<120)
            remember(npc,ObservedMemory.Kind.HAZARD,npc.blockPosition(),null,null,npc.isInLava()?"minecraft:lava":npc.isOnFire()?"minecraft:fire":"minecraft:water",ObservedMemory.Source.EXPERIENCED_HAZARD,100);
        if(MemorySettings.OBSERVE_RESOURCES.get()&&PlayerNpcAiWorkBudget.tryAcquire(level,npc)){
            for(int count=0;count<4;count++){
                int cursor=state.cursor++%405;observeResource(npc,npc.blockPosition().offset(cursor%9-4,cursor/81-2,cursor/9%9-4));
            }
            var remembered=state.loaded.observations().current(ObservedMemory.Kind.RESOURCE,dimension(npc),level.getGameTime(),1);
            if(!remembered.isEmpty())revalidateResource(npc,remembered.get(Math.floorMod(state.revalidateCursor++,remembered.size())));
        }
        save(npc);
    }
    @Events.SubscribeEvent public static void unload(Events.EntityLeaveLevelEvent event){if(event.getEntity() instanceof PlayerNpcEntity npc){save(npc);STATES.remove(npc);}}
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event){STATES.clear();}
    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("smart_npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("memory").then(Commands.argument("npc",EntityArgument.entity()).then(Commands.literal("status").executes(c->{
                    if(!(EntityArgument.getEntity(c,"npc") instanceof PlayerNpcEntity npc))return 0;
                    c.getSource().sendSuccess(()->Component.literal("Personal tendencies "+personality(npc)+"; departure food="+departureFoodReserve(npc)
                            +"; remembered facts="+observations(npc).size()+"/"+ObservedMemory.LIMIT),false);return 1;
                })))));
    }
}

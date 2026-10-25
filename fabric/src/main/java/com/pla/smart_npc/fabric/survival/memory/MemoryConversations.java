package com.pla.smart_npc.fabric.survival.memory;

import com.pla.smart_npc.config.SmartNpcConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.util.PlayerNpcAiWorkBudget;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Explicit local conversation only. No broadcast, generated story, reward or movement interruption. */
public final class MemoryConversations {
    private static final Map<PlayerNpcEntity,DialogueLedger> LEDGERS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> PLAYER_REPLY=new WeakHashMap<>();
    private static boolean registered;
    private MemoryConversations(){ }
    public static synchronized void register(){
        if(registered)return;registered=true;Events.register(MemoryConversations.class);
        UseEntityCallback.EVENT.register((player,level,hand,target,hit)->{
            if(!(target instanceof PlayerNpcEntity npc)||hand!=InteractionHand.MAIN_HAND
                    ||!player.isShiftKeyDown()||!player.getItemInHand(hand).isEmpty()
                    ||!MemorySettings.MEMORY_ENABLED.get()||!MemorySettings.CONTEXTUAL_DIALOGUE.get()||!SmartNpcConfig.TURN_ON_NPC_CHAT.get())return InteractionResult.PASS;
            if(level.isClientSide())return InteractionResult.SUCCESS;
            return player instanceof ServerPlayer serverPlayer&&tryTalk(npc,serverPlayer)?InteractionResult.SUCCESS_SERVER:InteractionResult.PASS;
        });
    }
    public static boolean tryTalk(PlayerNpcEntity npc,ServerPlayer player){
        if(!SmartNpcConfig.TURN_ON_NPC_CHAT.get()||!MemorySettings.CONTEXTUAL_DIALOGUE.get()||!MemorySettings.MEMORY_ENABLED.get()
                ||npc.level()!=player.level()||!npc.isAlive()||npc.isNoAi()||npc.isSleeping()||npc.isHealing()
                ||npc.isOnFire()||npc.getTarget()!=null||!player.isAlive()||player.isSpectator()
                ||npc.distanceToSqr(player)>6*6||!npc.hasLineOfSight(player)||!player.hasLineOfSight(npc))return false;
        long now=npc.level().getGameTime();var previous=PLAYER_REPLY.get(player);
        if(previous!=null&&now>=previous&&now-previous<100)return false;
        var personality=NpcMemories.personality(npc);
        if(!LEDGERS.computeIfAbsent(npc,n->new DialogueLedger()).allow(player.getUUID(),now,800-4*personality.sociability()))return false;
        var selected=selectionFor(npc,player.getUUID());
        var line=Component.literal(SmartNpcConfig.SHOW_NPC_CHAT_PREFIX.get()?"[NPC] <":"<")
                .append(npc.getDisplayName()).append("> ").append(text(selected));
        player.sendSystemMessage(line);PLAYER_REPLY.put(player,now);NpcMemories.save(npc);return true;
    }
    /** Selection is testable separately; only tryTalk enforces interaction safety/throttles and sends. */
    public static DialogueSelection.Reply selectionFor(PlayerNpcEntity npc,UUID listener){
        var facts=NpcMemories.observations(npc);var eligible=new ArrayList<ObservedMemory.Entry>();
        int rechecks=PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc)&&PlayerNpcAiWorkBudget.tryAcquire((net.minecraft.server.level.ServerLevel)npc.level(),npc)?2:0;
        for(var fact:facts){
            if(fact.kind()!=ObservedMemory.Kind.RESOURCE){eligible.add(fact);continue;}
            if(rechecks--<=0||!fact.current(npc.level().getGameTime()))continue;
            if(NpcMemories.revalidateResource(npc,fact)==NpcMemories.Validation.CONFIRMED)
                NpcMemories.knownResources(npc,id->id.equals(fact.detail())).stream().filter(e->e.position().equals(fact.position())).findFirst().ifPresent(eligible::add);
        }
        return DialogueSelection.choose(listener,NpcMemories.dimension(npc),npc.level().getGameTime(),NpcMemories.personality(npc),
                npc.isGatheringJobRunning()||npc.isMainHandReservedForAi(),eligible);
    }
    public static Component text(DialogueSelection.Reply reply){
        var fact=reply.evidence();
        return switch(reply.type()){
            case BUSY -> Component.literal("I'm concentrating on my work right now.");
            case UNKNOWN -> Component.literal("I haven't learned much about this place yet.");
            case THANKS -> Component.literal("Thanks for the help earlier.");
            case HOSTILE_HISTORY -> Component.literal(switch(fact.detail()){
                case "ASSAULT" -> "I remember you attacking me earlier.";
                case "THEFT" -> "I remember supplies being taken from my chest.";
                case "VANDALISM" -> "I remember you damaging my home earlier.";
                default -> "I remember trouble between us earlier.";
            });
            case FAILED_ROUTE -> Component.literal("Last time, I couldn't finish my route to "+coordinates(fact.destination())+".");
            case HAZARD -> Component.literal("I ran into trouble near "+coordinates(fact.position())+".");
            case RESOURCE -> Component.literal("I last saw ").append(resourceName(fact.detail())).append(" near "+coordinates(fact.position())+".");
            case WORK_SITE -> Component.literal(DialogueSelection.historicalActivity(fact)+coordinates(fact.position())+".");
            case HOME -> Component.literal("My home site is near "+coordinates(fact.position())+".");
            case SUCCESSFUL_ROUTE -> Component.literal("I finished a trip to "+coordinates(fact.destination())+".");
        };
    }
    private static Component resourceName(String id){
        var key=Identifier.tryParse(id);var block=key==null?null:BuiltInRegistries.BLOCK.getValue(key);
        return block==null?Component.literal("resources"):block.getName();
    }
    private static String coordinates(ObservedMemory.Point point){return point.x()+", "+point.y()+", "+point.z();}
    @Events.SubscribeEvent public static void unload(Events.EntityLeaveLevelEvent event){if(event.getEntity() instanceof PlayerNpcEntity npc)LEDGERS.remove(npc);}
    @Events.SubscribeEvent public static void logout(Events.PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer player)PLAYER_REPLY.remove(player);}
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event){LEDGERS.clear();PLAYER_REPLY.clear();}
}

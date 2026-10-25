package com.pla.smart_npc.fabric.survival.relationships;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.SocialSafety;
import com.pla.smart_npc.fabric.survival.memory.MemoryWorldIdentity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** Observer/advisory facade; positive history never overrides canonical defense, ownership or consent. */
public final class NpcRelationships {
    public static final String KEY="SmartNpcRelationshipEvidence";
    private static boolean registered;
    private NpcRelationships(){}
    public static synchronized void register(){
        if(registered)return;registered=true;
        RelationshipSettings.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("smart_npc-relationships.toml"));Events.register(NpcRelationships.class);
    }
    public static RelationshipBook.Advice advice(PlayerNpcEntity npc,UUID exactActor){
        if(!(npc.level() instanceof ServerLevel)||!RelationshipSettings.ENABLED.get()||exactActor==null)return new RelationshipBook.Advice(RelationshipBook.Stance.UNKNOWN,0,0,0);
        return RelationshipCodec.load(PersistentData.get(npc).getCompoundOrEmpty(KEY)).book().advice(exactActor,npc.level().getGameTime());
    }
    private static boolean record(PlayerNpcEntity npc,UUID actor,RelationshipBook.Evidence event){
        if(!RelationshipSettings.ENABLED.get()||actor==null||actor.equals(npc.getUUID())||!(npc.level() instanceof ServerLevel level)||!MemoryWorldIdentity.supported(level.getServer()))return false;
        var tag=PersistentData.get(npc);var state=RelationshipCodec.load(tag.getCompoundOrEmpty(KEY));if(!state.writable())return false;
        if(!state.book().record(actor,event))return false;tag.put(KEY,RelationshipCodec.save(state));return true;
    }
    /** Successful conserved delivery producers only; units do not multiply the advisory influence. */
    public static boolean acceptedFood(PlayerNpcEntity npc,UUID actualGiver,String registryItem,int actualUnits){
        if(!(npc.level() instanceof ServerLevel)||actualUnits<1||actualUnits>64)return false;
        try{return record(npc,actualGiver,new RelationshipBook.Evidence(RelationshipBook.Kind.ASSISTANCE,RelationshipBook.Source.CONSERVED_HANDOFF,registryItem,npc.level().getGameTime(),actualUnits));}catch(IllegalArgumentException ignored){return false;}
    }
    /** Root calls only after exact canonical SocialSafety.record has committed successfully. */
    public static boolean canonicalEncounter(PlayerNpcEntity npc,LivingEntity actualOffender){
        if(actualOffender==null||!SocialSafety.hasCause(npc,actualOffender))return false;
        var cause=SocialSafety.memory(npc).cause(actualOffender.getUUID(),npc.level().getGameTime());if(cause.isEmpty())return false;
        RelationshipBook.Kind kind=switch(cause.get()){case ASSAULT->RelationshipBook.Kind.ASSAULT;case THEFT->RelationshipBook.Kind.THEFT;case VANDALISM->RelationshipBook.Kind.VANDALISM;case ALLY_DEFENCE->RelationshipBook.Kind.ALLY_DEFENCE;};
        return record(npc,actualOffender.getUUID(),new RelationshipBook.Evidence(kind,RelationshipBook.Source.CANONICAL_DEFENCE,"smart_npc:"+cause.get().name().toLowerCase(java.util.Locale.ROOT),npc.level().getGameTime(),1));
    }
    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("smart_npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("relationship").then(Commands.argument("npc",EntityArgument.entity()).then(Commands.literal("status").executes(context->{
                    if(!(EntityArgument.getEntity(context,"npc") instanceof PlayerNpcEntity npc))return 0;
                    var actor=context.getSource().getPlayerOrException();var opinion=advice(npc,actor.getUUID());
                    context.getSource().sendSuccess(()->Component.literal("Recent evidence for your UUID: "+opinion.stance()+"; familiarity="+opinion.familiarity()+"; advisory trust="+opinion.trust()+"; facts="+opinion.currentEvidence()+". No team or property rights implied."),false);return 1;
                })))));
    }
}
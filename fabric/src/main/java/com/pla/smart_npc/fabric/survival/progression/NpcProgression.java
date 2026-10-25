package com.pla.smart_npc.fabric.survival.progression;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.BuildHouseGoal;
import com.pla.smart_npc.fabric.*;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import java.util.*;

/** Observed individual capability. No gear, items, vanilla XP or world difficulty is granted. */
public final class NpcProgression {
    private static final String KEY="SurvivalPersonalProgress";
    private static final ConfigSpec.Builder BUILDER=new ConfigSpec.Builder();
    public static final ConfigSpec.BooleanValue ENABLED=BUILDER.comment("Record verified personal work; disabling preserves saved history and native behaviour.").define("enabled",true);
    public static final ConfigSpec SPEC=BUILDER.build();
    private static final Map<PlayerNpcEntity,State> STATES=new WeakHashMap<>();
    private static boolean registered;
    private static final Set<String> COOKED=Set.of("minecraft:cooked_beef","minecraft:cooked_porkchop","minecraft:cooked_mutton",
            "minecraft:cooked_chicken","minecraft:cooked_rabbit","minecraft:cooked_cod","minecraft:cooked_salmon","minecraft:baked_potato");
    private static final class State {
        PersonalProgress progress;boolean supported,dirty,sleepNight;long sleepSince=-1,homeCheckAt=-1;BlockPos sleepPos;
    }
    private NpcProgression() { }
    public static synchronized void register(){if(registered)return;registered=true;SPEC.load(FabricLoader.getInstance().getConfigDir().resolve("smart_npc-progress.toml"));Events.register(NpcProgression.class);}
    private static State state(PlayerNpcEntity npc){return STATES.computeIfAbsent(npc,n->{
        var tag=PersistentData.get(n).getCompoundOrEmpty(KEY);var s=new State();s.supported=ProgressCodec.supported(tag);s.progress=ProgressCodec.load(tag);
        s.sleepSince=tag.getLongOr("sleepSince",-1);s.sleepNight=tag.getBooleanOr("sleepNight",false);
        s.homeCheckAt=Math.max(-1,Math.min(tag.getLongOr("homeCheckAt",-1),n.level().getGameTime()+20));
        if(tag.contains("sleepPos"))s.sleepPos=BlockPos.of(tag.getLongOr("sleepPos",0));return s;
    });}
    public static PersonalProgress.Snapshot snapshot(PlayerNpcEntity npc){return state(npc).progress.snapshot();}
    public static void save(PlayerNpcEntity npc){
        var s=STATES.get(npc);if(s==null||!s.supported||!s.dirty)return;
        var tag=ProgressCodec.save(s.progress);tag.putLong("sleepSince",s.sleepSince);tag.putBoolean("sleepNight",s.sleepNight);tag.putLong("homeCheckAt",s.homeCheckAt);
        if(s.sleepPos!=null)tag.putLong("sleepPos",s.sleepPos.asLong());PersistentData.get(npc).put(KEY,tag);s.dirty=false;
    }
    private static boolean ownBlueprintPosition(PlayerNpcEntity npc,ServerLevel level,BlockPos pos){
        var home=PlayerNpcHomeUtil.getHome(npc);var layout=PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout);
        return level.hasChunkAt(pos)&&home.isPresent()&&layout.isPresent()&&PlayerNpcHomeUtil.isInside(home.get(),pos)
                &&layout.get().blocks().stream().anyMatch(b->!b.state().isAir()&&b.toWorld(home.get().origin()).equals(pos)
                &&PlayerNpcBuildMaterialUtil.matches(level.getBlockState(pos),b.state()));
    }
    @Events.SubscribeEvent public static void work(VerifiedWorkOutcome event){
        if(!ENABLED.get()||!(event.npc().level() instanceof ServerLevel level))return;
        var npc=event.npc();var s=state(npc);if(!s.supported)return;
        String id=event.resourceId();String proof=level.dimension().identifier()+"@"+event.position().asLong()+":"+id;
        PersonalProgress.Skill skill=null;int units=event.units();
        switch(event.kind()){
            case HARVEST -> {
                if(id.contains("ore")||id.contains("stone")||id.contains("granite")||id.contains("diorite")||id.contains("andesite"))skill=PersonalProgress.Skill.MINING;
                else if(id.endsWith("_log")||id.endsWith("_stem"))skill=PersonalProgress.Skill.FORAGING;
                units=1;
            }
            case PLACE -> {
                if(ownBlueprintPosition(npc,level,event.position())){
                    skill=PersonalProgress.Skill.BUILDING;units=1;s.homeCheckAt=level.getGameTime()+20;
                }
            }
            case CRAFT -> {skill=PersonalProgress.Skill.CRAFTING;s.progress.rememberCraft(id);s.dirty=true;}
            case COOK -> {skill=PersonalProgress.Skill.COOKING;if(COOKED.contains(id))s.progress.mark(PersonalProgress.Milestone.FOOD_PREPARED);}
            case FISH -> skill=PersonalProgress.Skill.FISHING;
            case HOME_COMPLETE -> s.progress.mark(PersonalProgress.Milestone.HOME_COMPLETE);
            case SAFE_REST -> s.progress.mark(PersonalProgress.Milestone.SAFE_REST);
            case JOURNEY_RETURN -> {skill=PersonalProgress.Skill.EXPLORATION;units=1;s.progress.mark(PersonalProgress.Milestone.FIRST_JOURNEY_RETURN);}
        }
        if(skill!=null)s.progress.record(skill,proof,units,level.getGameTime());
        s.dirty=true;
    }
    @Events.SubscribeEvent public static void tick(Events.EntityTickEvent.Post event){
        if(!ENABLED.get()||!(event.getEntity() instanceof PlayerNpcEntity npc)||npc.tickCount%20!=Math.floorMod(npc.getUUID().hashCode(),20)
                ||npc.isNoAi()||!(npc.level() instanceof ServerLevel level))return;
        var s=state(npc);if(!s.supported)return;long now=level.getGameTime();
        if(npc.isSleeping()&&npc.getSleepingPos().isPresent()){
            var bed=npc.getSleepingPos().get();
            if(s.sleepSince<0||!bed.equals(s.sleepPos)){
                s.sleepSince=now;s.sleepPos=bed.immutable();s.sleepNight=level.isDarkOutside();s.dirty=true;
            }
        }else if(s.sleepSince>=0){
            boolean rested=s.sleepNight&&now-s.sleepSince>=100&&!level.isDarkOutside()&&s.sleepPos!=null
                    &&PlayerNpcHomeUtil.getHome(npc).map(h->PlayerNpcHomeUtil.isInside(h,s.sleepPos)).orElse(false)
                    &&npc.isAlive()&&npc.getTarget()==null&&!npc.isOnFire();
            if(rested)VerifiedWorkOutcome.emit(npc,VerifiedWorkOutcome.Kind.SAFE_REST,s.sleepPos,"native:owned_bed",1);
            s.sleepSince=-1;s.sleepPos=null;s.sleepNight=false;s.dirty=true;
        }
        if(s.homeCheckAt>=0&&now>=s.homeCheckAt&&PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc)){
            s.homeCheckAt=-1;s.dirty=true;
            if(BuildHouseGoal.isHomeLayoutFinished(npc,level))PlayerNpcHomeUtil.getHome(npc).ifPresent(h->
                    VerifiedWorkOutcome.emit(npc,VerifiedWorkOutcome.Kind.HOME_COMPLETE,h.origin(),"native:owned_blueprint",1));
        }
        save(npc);
    }
    @Events.SubscribeEvent public static void unload(Events.EntityLeaveLevelEvent event){
        if(event.getEntity() instanceof PlayerNpcEntity npc){save(npc);STATES.remove(npc);}
    }
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent event){STATES.clear();}
    public static String describe(PlayerNpcEntity npc){
        var s=state(npc);if(!s.supported)return "Personal progress uses an unsupported saved version; preserved read-only";
        return "earnedStage="+s.progress.stage()+" workWindows="+s.progress.workWindows()+" skills="+s.progress.snapshot().totals()
                +"; current readiness: food="+SurvivalTasks.cookedFoodCount(npc)+"/"+com.pla.smart_npc.fabric.survival.memory.NpcMemories.departureFoodReserve(npc)+" pickaxe="+npc.hasCarriedTool(ItemTags.PICKAXES)
                +" knownHome="+PlayerNpcHomeUtil.getHome(npc).isPresent()+" expeditionMissing="
                +com.pla.smart_npc.fabric.survival.readiness.NpcReadiness.expedition(npc,Journeys.state(npc).needsBoat).missing()
                +"; route feasibility is separate; world difficulty and vanilla XP unchanged";
    }
    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("smart_npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("progress").then(Commands.argument("npc",EntityArgument.entity()).executes(context->{
                    if(!(EntityArgument.getEntity(context,"npc") instanceof PlayerNpcEntity npc))return 0;
                    context.getSource().sendSuccess(()->Component.literal(describe(npc)),false);return 1;
                }))));
    }
}

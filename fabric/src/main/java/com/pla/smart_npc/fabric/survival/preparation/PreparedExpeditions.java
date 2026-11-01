package com.pla.smart_npc.fabric.survival.preparation;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.progression.*;
import com.pla.smart_npc.fabric.survival.readiness.NpcReadiness;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Opt-in prepared commitments reuse native saved Journeys; no second controller,
 * inventory mutation, route scan, permission unlock, or survival-goal interception. */
public final class PreparedExpeditions {
    private PreparedExpeditions() { }
    private static final String VISIT_REASON="prepared visit";
    public record Result(boolean started,String message){ }
    /** Explicit round-trip mode; ordinary go/resource trips retain their own lifecycle. */
    public static Result requestVisit(PlayerNpcEntity npc,BlockPos destination,boolean water){
        var result=request(npc,destination,water);
        if(result.started()){
            Journeys.state(npc).reason=VISIT_REASON;Journeys.save(npc);
            return new Result(true,"Prepared visit started; return home follows physical arrival");
        }
        return result;
    }
    static boolean shouldReturn(String reason,String phase,String dimension,String actualDimension,BlockPos destination,BlockPos lastArrival,BlockPos feet){
        return VISIT_REASON.equals(reason)&&"at_destination".equals(phase)&&dimension.equals(actualDimension)
                &&destination!=null&&destination.equals(lastArrival)&&feet!=null&&feet.distSqr(destination)<=9;
    }
    @Events.SubscribeEvent(priority=Events.EventPriority.HIGH) public static void finishVisit(Events.EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof PlayerNpcEntity npc)||!(npc.level() instanceof ServerLevel)||npc.isNoAi()||!npc.isAlive())return;
        var trip=Journeys.state(npc);
        if(shouldReturn(trip.reason,trip.phase,trip.dimension,SurvivalTasks.dimension(npc),trip.destination,trip.lastArrival,npc.blockPosition()))
            Journeys.returnHome(npc,"completed prepared visit");
    }
    public static Result request(PlayerNpcEntity npc,BlockPos destination,boolean declaredWaterCrossing){
        if(!(npc.level() instanceof ServerLevel level)||npc.isRemoved()||level.getEntity(npc.getUUID())!=npc)return new Result(false,"Declined: resident is not available in a server world");
        var journey=Journeys.state(npc);var resource=SurvivalTasks.memory(npc);
        boolean safe=npc.isAlive()&&!npc.isNoAi()&&!npc.isSleeping()&&!npc.isHealing()&&npc.getTarget()==null
                &&!npc.isOnFire()&&npc.getHealth()>npc.getMaxHealth()*.5F&&!npc.isTeamFollower()
                &&npc.getUpwardEscapeTarget()==null&&!npc.hasExplorationReturnHomeRequest();
        // at_destination is also a commitment despite State.active() being false.
        // A requested return/cancel must resolve it before another departure.
        boolean idle=journey.phase.equals("idle")&&!SurvivalTasks.cookingActive(npc)&&!(resource.active&&!resource.automatic);
        var history=NpcProgression.snapshot(npc).milestones();
        var decision=PreparedExpeditionPolicy.assess(new PreparedExpeditionPolicy.Evidence(safe,idle,
                history.contains(PersonalProgress.Milestone.FOOD_PREPARED),history.contains(PersonalProgress.Milestone.SAFE_REST),
                NpcReadiness.expedition(npc,declaredWaterCrossing)));
        if(!decision.accepted())return new Result(false,describe(decision));
        // Final native validation includes same-dimension owned home, proximity,
        // actual food reserve and supported ground/owned mounted boat. Decline
        // does not replace an existing trip or spend any carried materials.
        String result=Journeys.start(npc,destination);
        return new Result(result.equals("Journey started"),result);
    }
    private static String describe(PreparedExpeditionPolicy.Decision decision){
        var needs=new ArrayList<String>();
        for(var reason:decision.reasons())needs.add(switch(reason){
            case UNSAFE_OR_UNAVAILABLE -> "be alive, awake, unpaused, healthy and free of combat, following or escape/return work";
            case EXISTING_COMMITMENT -> "finish the current cooking/resource task, or return/cancel the existing journey";
            case EARN_COOKED_FOOD -> "first collect food from the resident's own native cooking task";
            case EARN_SAFE_REST -> "first complete a safe night of rest in the resident's own home";
        });
        for(var missing:decision.materials())needs.add(switch(missing.code()){
            case NO_OWNED_HOME -> "establish an owned home";
            case HOME_IN_OTHER_DIMENSION -> "return to the dimension containing the owned home";
            case FOOD_RESERVE -> "carry "+missing.need()+" cooked food items (currently "+missing.have()+")";
            case BOAT_MISSING -> "carry a boat or board the resident's owned boat for the declared water crossing";
            case TOOL_MISSING,TOOL_TIER -> "carry a "+missing.requiredTier().name().toLowerCase(Locale.ROOT)+" or better "+missing.tool().name().toLowerCase(Locale.ROOT);
            case TOOL_DURABILITY -> "carry a usable "+missing.tool().name().toLowerCase(Locale.ROOT)+" with at least "+missing.need()+" uses remaining (best "+missing.have()+")";
        });
        return "Declined prepared expedition: "+String.join("; ",needs);
    }
    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent event){
        var go=Commands.literal("go").then(Commands.argument("destination",BlockPosArgument.blockPos())
                .executes(c->run(c,false,false)).then(Commands.literal("water").executes(c->run(c,true,false))));
        var visit=Commands.literal("visit").then(Commands.argument("destination",BlockPosArgument.blockPos())
                .executes(c->run(c,false,true)).then(Commands.literal("water").executes(c->run(c,true,true))));
        event.getDispatcher().register(Commands.literal("smart_npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("expedition").then(Commands.argument("npc",EntityArgument.entity()).then(go).then(visit))));
    }
    private static int run(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,boolean water,boolean visit)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!(EntityArgument.getEntity(context,"npc") instanceof PlayerNpcEntity npc))return 0;
        var result=visit?requestVisit(npc,BlockPosArgument.getBlockPos(context,"destination"),water):request(npc,BlockPosArgument.getBlockPos(context,"destination"),water);
        context.getSource().sendSuccess(()->Component.literal(result.message()),false);return result.started()?1:0;
    }
}



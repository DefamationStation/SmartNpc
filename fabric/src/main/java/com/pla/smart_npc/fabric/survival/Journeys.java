package com.pla.smart_npc.fabric.survival;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Persistent trip ownership; live paths belong to JourneyGoal and are rebuilt after load. */
public final class Journeys {
    private static final String KEY="SmartNpcJourney";
    private static final Map<PlayerNpcEntity,State> STATES=new WeakHashMap<>();
    public static final class State {
        public String dimension="", phase="idle", reason="", status="idle";
        public BlockPos home, destination, lastArrival, blockedPosition;
        public int cursor=-1;
        public long progressAt;
        public int activeNoProgressTicks;
        public double bestDistance=Double.MAX_VALUE;
        public BlockPos progressGoal;
        public final LinkedHashMap<Long,Integer> visits=new LinkedHashMap<>();
        public UUID boatId;
        public net.minecraft.world.item.ItemStack boatItem=net.minecraft.world.item.ItemStack.EMPTY;
        public BlockPos landingTarget;
        public long landingStarted;
        public com.pla.smart_npc.entity.ai.WaterEscapeAi landingRecovery;
        public boolean needsBoat;
        public long nextBoatCheck;
        public BlockPos boatWaypoint;
        public net.minecraft.world.phys.Vec3 boatSteering;
        public BlockPos boatLanding;
        public long nextBoatPlan;
        public net.minecraft.world.phys.Vec3 dismountPosition;
        public final JourneyTrail trail=new JourneyTrail();
        public boolean active(){return phase.equals("outbound")||phase.equals("returning");}
    }
    private Journeys(){}
    public static State state(PlayerNpcEntity npc){return STATES.computeIfAbsent(npc,n->{
        var d=PersistentData.get(n).getCompoundOrEmpty(KEY);var s=new State();
        s.dimension=d.getStringOr("dimension","");s.phase=d.getStringOr("phase","idle");
        s.reason=d.getStringOr("reason","");s.status=d.getStringOr("status","idle");
        if(d.contains("home"))s.home=BlockPos.of(d.getLongOr("home",0));
        if(d.contains("destination"))s.destination=BlockPos.of(d.getLongOr("destination",0));
        if(d.contains("arrival"))s.lastArrival=BlockPos.of(d.getLongOr("arrival",0));
        if(d.contains("blockedPosition"))s.blockedPosition=BlockPos.of(d.getLongOr("blockedPosition",0));
        s.cursor=d.getIntOr("cursor",-1);
        s.needsBoat=d.getBooleanOr("needsBoat",false);
        if(d.contains("landing"))s.landingTarget=BlockPos.of(d.getLongOr("landing",0));
        s.landingStarted=d.getLongOr("landingStarted",0);
        if(d.contains("boat"))try{s.boatId=UUID.fromString(d.getStringOr("boat",""));}catch(IllegalArgumentException ignored){}
        if(d.contains("boatItem"))s.boatItem=net.minecraft.world.item.ItemStack.CODEC.parse(n.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),d.getCompoundOrEmpty("boatItem")).result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
        s.progressAt=d.getLongOr("progressAt",0);s.bestDistance=d.getDoubleOr("bestDistance",Double.MAX_VALUE);
        s.activeNoProgressTicks=d.getIntOr("activeNoProgressTicks",0);
        if(d.contains("progressGoal"))s.progressGoal=BlockPos.of(d.getLongOr("progressGoal",0));
        for(long key:d.getLongArray("visits").orElse(new long[0])){s.visits.merge(key,1,Integer::sum);if(s.visits.size()>=512)break;}
        var list=new ArrayList<JourneyTrail.Point>();
        for(long p:d.getLongArray("trail").orElse(new long[0])){var b=BlockPos.of(p);list.add(point(b));if(list.size()==JourneyTrail.LIMIT)break;}
        s.trail.restore(list);s.cursor=Math.max(-1,Math.min(s.cursor,list.size()-1));
        if(s.active()&&(s.home==null||s.destination==null)) {s.phase="blocked";s.status="invalid saved journey";}
        return s;
    });}
    public static JourneyTrail.Point point(BlockPos p){return new JourneyTrail.Point(p.getX(),p.getY(),p.getZ());}
    public static BlockPos block(JourneyTrail.Point p){return new BlockPos(p.x(),p.y(),p.z());}
    public static void save(PlayerNpcEntity npc){var s=state(npc);var d=new CompoundTag();d.putInt("version",1);
        d.putString("dimension",s.dimension);d.putString("phase",s.phase);d.putString("reason",s.reason);d.putString("status",s.status);
        if(s.home!=null)d.putLong("home",s.home.asLong());if(s.destination!=null)d.putLong("destination",s.destination.asLong());
        if(s.lastArrival!=null)d.putLong("arrival",s.lastArrival.asLong());if(s.blockedPosition!=null)d.putLong("blockedPosition",s.blockedPosition.asLong());
        d.putInt("cursor",s.cursor);d.putLongArray("trail",s.trail.points().stream().mapToLong(p->block(p).asLong()).toArray());
        d.putBoolean("needsBoat",s.needsBoat);
        d.putLong("progressAt",s.progressAt);d.putDouble("bestDistance",s.bestDistance);if(s.progressGoal!=null)d.putLong("progressGoal",s.progressGoal.asLong());
        d.putInt("activeNoProgressTicks",s.activeNoProgressTicks);
        d.putLongArray("visits",s.visits.entrySet().stream().flatMapToLong(e->java.util.stream.LongStream.range(0,Math.min(8,e.getValue())).map(i->e.getKey())).toArray());
        if(s.boatId!=null)d.putString("boat",s.boatId.toString());
        if(s.landingTarget!=null)d.putLong("landing",s.landingTarget.asLong());d.putLong("landingStarted",s.landingStarted);
        if(!s.boatItem.isEmpty())net.minecraft.world.item.ItemStack.CODEC.encodeStart(npc.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),s.boatItem).result().ifPresent(t->d.put("boatItem",t));
        PersistentData.get(npc).put(KEY,d);
    }
    public static boolean active(PlayerNpcEntity n){return state(n).active();}
    public static String start(PlayerNpcEntity n,BlockPos destination){
        var s=state(n);if(s.active())return "Journey already active; return or cancel first";
        var h=PlayerNpcHomeUtil.getHome(n);if(h.isEmpty())return "No home in this dimension";
        if(SurvivalTasks.cookedFoodCount(n)<4)return "Need four cooked food items before departure";
        if(n.blockPosition().distSqr(h.get().origin())>32*32)return "Start an outbound trip near home to establish a return trail";
        s.dimension=SurvivalTasks.dimension(n);s.home=n.blockPosition().immutable();s.destination=destination.immutable();
        s.lastArrival=null;s.blockedPosition=null;s.activeNoProgressTicks=0;
        s.phase="outbound";s.reason="requested travel";s.status="departing";s.cursor=-1;s.trail.clear();s.visits.clear();s.progressGoal=null;s.bestDistance=Double.MAX_VALUE;s.trail.observe(point(n.blockPosition()));save(n);return "Journey started";
    }
    public static void returnHome(PlayerNpcEntity n,String reason){
        var s=state(n);var h=PlayerNpcHomeUtil.getHome(n);
        if(s.phase.equals("blocked")&&s.blockedPosition!=null&&s.blockedPosition.distSqr(n.blockPosition())<16&&!reason.equals("requested return"))return;
        if(!s.dimension.isEmpty()&&!s.dimension.equals(SurvivalTasks.dimension(n))){block(n,"home is in another dimension; no portal route");return;}
        if(h.isPresent()){s.home=h.get().origin().offset(h.get().width()/2,1,h.get().depth()/2);s.dimension=SurvivalTasks.dimension(n);}
        if(s.home==null)return;
        if(!s.dimension.isEmpty()&&!s.dimension.equals(SurvivalTasks.dimension(n))){s.phase="blocked";s.status="home is in another dimension; no portal route";save(n);return;}
        s.dimension=SurvivalTasks.dimension(n);s.phase="returning";s.destination=s.home;s.reason=reason;
        s.visits.clear();s.progressGoal=null;s.bestDistance=Double.MAX_VALUE;
        s.cursor=s.trail.points().size()-1;
        boolean sailing=ownsBoat(n,n.getVehicle());
        double nearest=sailing?128*128:24*24;
        for(int i=0;i<s.trail.points().size();i++){
            BlockPos point=block(s.trail.points().get(i));double distance=point.distSqr(n.blockPosition());
            if(distance>=nearest)continue;
            if(sailing&&(!(n.level() instanceof ServerLevel level)||!level.hasChunkAt(point)
                    ||!level.getFluidState(point).is(net.minecraft.tags.FluidTags.WATER)
                    &&!level.getFluidState(point.below()).is(net.minecraft.tags.FluidTags.WATER)))continue;
            nearest=distance;s.cursor=i;
        }
        s.status="returning along observed trail";save(n);
    }
    public static void block(PlayerNpcEntity n,String why){var s=state(n);s.phase="blocked";s.status=why;s.blockedPosition=n.blockPosition().immutable();JourneyBoats.stop(n);n.getNavigation().stop();save(n);}
    @Events.SubscribeEvent public static void observe(Events.EntityTickEvent.Post e){
        if(!(e.getEntity() instanceof PlayerNpcEntity n)||!(n.level() instanceof ServerLevel l)||n.tickCount%10!=0||n.isNoAi())return;
        var s=state(n);var home=PlayerNpcHomeUtil.getHome(n);
        if(s.active()&&!s.dimension.equals(SurvivalTasks.dimension(n))){block(n,"dimension changed; no portal route");return;}
        if(home.isEmpty()){
            if(s.phase.equals("outbound")&&(n.onGround()||ownsBoat(n,n.getVehicle()))&&s.trail.observe(point(n.blockPosition())))save(n);
            if(s.phase.equals("outbound")&&(s.trail.full()||SurvivalTasks.cookedFoodCount(n)<2))returnHome(n,"return to camp for supplies");
            return;
        }
        BlockPos anchor=home.get().origin().offset(home.get().width()/2,1,home.get().depth()/2);
        if(!s.active()&&!s.phase.equals("blocked")){
            if(n.blockPosition().distSqr(anchor)<12*12){s.home=anchor;s.dimension=SurvivalTasks.dimension(n);if(!s.trail.points().isEmpty()){s.trail.clear();save(n);}}
            if(s.home!=null&&s.dimension.equals(SurvivalTasks.dimension(n))&&n.onGround()&&s.trail.observe(point(n.blockPosition())))save(n);
            if(n.blockPosition().distSqr(anchor)>96*96&&(n.hasExplorationReturnHomeRequest()||l.isDarkOutside()||SurvivalTasks.cookedFoodCount(n)<2||s.trail.full()))returnHome(n,"survival return");
        }else if(s.phase.equals("outbound")&&(n.onGround()||ownsBoat(n,n.getVehicle()))){
            if(s.trail.observe(point(n.blockPosition())))save(n);
            if(s.trail.full()||SurvivalTasks.cookedFoodCount(n)<2)returnHome(n,"return reserve or trail capacity reached");
        }
    }
    public static boolean ownsBoat(PlayerNpcEntity n,net.minecraft.world.entity.Entity boat){return boat!=null&&boat.getUUID().equals(state(n).boatId);}
    public static boolean shouldPrepareBoat(PlayerNpcEntity n){
        var s=state(n);if(!s.active()||!s.needsBoat||s.boatId!=null)return false;
        for(var stack:n.getInventory().getItems())if(stack.getItem() instanceof net.minecraft.world.item.BoatItem)return false;
        // Reserve enough for both a table and the native boat recipe. No failed
        // prerequisite should monopolize movement while materials are missing.
        return com.pla.smart_npc.util.PlayerNpcCraftingUtil.countPlankEquivalent(n.getInventory())>=9;
    }
    @Events.SubscribeEvent public static void stopped(Events.ServerStoppedEvent e){STATES.clear();}
    @Events.SubscribeEvent public static void unloaded(Events.EntityLeaveLevelEvent e){if(e.getEntity() instanceof PlayerNpcEntity n){var s=STATES.remove(n);if(s!=null&&s.landingRecovery!=null)s.landingRecovery.stop();}}
    @Events.SubscribeEvent public static void commands(Events.RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("smart_npc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("journey").then(Commands.argument("npc",EntityArgument.entity())
                .then(Commands.literal("go").then(Commands.argument("destination",BlockPosArgument.blockPos()).executes(c->{
                    if(!(EntityArgument.getEntity(c,"npc") instanceof PlayerNpcEntity n))return 0;
                    String message=start(n,BlockPosArgument.getBlockPos(c,"destination"));c.getSource().sendSuccess(()->Component.literal(message),true);return active(n)?1:0;})))
                .then(Commands.literal("home").executes(c->{if(!(EntityArgument.getEntity(c,"npc") instanceof PlayerNpcEntity n))return 0;returnHome(n,"requested return");return 1;}))
                .then(Commands.literal("cancel").executes(c->{if(!(EntityArgument.getEntity(c,"npc") instanceof PlayerNpcEntity n))return 0;var s=state(n);s.phase="idle";s.status="cancelled";save(n);return 1;}))
                .then(Commands.literal("status").executes(c->{if(!(EntityArgument.getEntity(c,"npc") instanceof PlayerNpcEntity n))return 0;var s=state(n);c.getSource().sendSuccess(()->Component.literal(s.phase+": "+s.status+"; "+s.reason+"; trail="+s.trail.points().size()+"; destination="+s.destination),false);return 1;})))));
    }
}

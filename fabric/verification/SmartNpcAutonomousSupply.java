import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

/** Declared fresh flat yard and almost-built owned cabin; every subsequent action
 * must come from ordinary production AI. No trip request, work event, worker-slot
 * claim, goal invocation, resource-memory injection or later item/movement grant. */
public final class SmartNpcAutonomousSupply implements DedicatedServerModInitializer {
    static final UUID ID=new UUID(0x737570706c797472L,0x69702d6163746f72L);
    static final BlockPos ORIGIN=new BlockPos(-3,279,-2),SOURCE=new BlockPos(18,280,-4);
    static final BlockPos MISSING=ORIGIN;
    static final AABB YARD=new AABB(-49,278,-49,49,288,49);
    final boolean replay=Boolean.getBoolean("journey.replay");
    PlayerNpcEntity npc;PlayerNpcBuildLayout cabin;
    boolean done,outboundSaved,returning,returnSaved,sawNativeOutbound,sawHarvest,sawPickup;
    int ticks,placementSettleAt=-1;double startX=-1.5,maxX=-1.5,returnStartX;
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("SupplyResident|BUILDING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread owner=Thread.currentThread();new Thread(()->{try{owner.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable error){error.printStackTrace();finish(s,"failure.txt",error.toString());}});
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(Integer.getInteger("journey.tickRate",100));var level=server.overworld();
        if(npc==null){
            cabin=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();
            if(!replay){setup(server,level);return;}
            npc=(PlayerNpcEntity)level.getEntity(ID);if(npc==null){check(ticks<2000,"original saved actor missing");return;}
            loadLedger();var trip=Journeys.state(npc);var supply=AutonomousSupplyTrip.snapshot(npc);
            check(returning?trip.phase.equals("returning"):trip.phase.equals("outbound")||trip.phase.equals("idle"),"saved native supply phase lost");
            check(supply.active()&&(returning?supply.stage().equals("returning"):supply.stage().equals("outward")||supply.stage().equals("collecting")),"saved autonomous supply ownership lost");
            check(SOURCE.equals(supply.resource()),"saved actual supply target changed");
            check(PlayerNpcHomeUtil.getHomeLayoutId(npc).orElse("").equals(cabin.id()),"owned cabin plan lost on reload");
            System.out.println("SUPPLY_RESUMED phase="+trip.phase+" snapshot="+supply+" pos="+npc.position());
        }
        check(npc.isAlive()&&!npc.isNoAi()&&goals()==81,"ordinary enabled eighty-one-goal actor changed");
        check(npc.hasInterest(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING),"resident lost BUILDING interest");
        var trip=Journeys.state(npc);var supply=AutonomousSupplyTrip.snapshot(npc);maxX=Math.max(maxX,npc.getX());
        check(!trip.phase.equals("blocked"),"native supply trip blocked: "+trip.status);
        boolean nativeCollecting=supply.active()&&supply.stage().equals("collecting")&&com.pla.smart_npc.entity.goal.GatherLogsGoal.isLogGatheringEpisodeActive(npc);
        if(nativeCollecting){check(SOURCE.equals(supply.resource()),"native collector selected unrelated resource: "+supply);sawNativeOutbound=true;}
        check(sawNativeOutbound||trip.phase.equals("outbound")||!com.pla.smart_npc.entity.goal.GatherLogsGoal.isLogGatheringEpisodeActive(npc),
                "native collector began before ordinary outbound admission; fixture cannot certify this supply-trip path");
        if(trip.phase.equals("outbound")){
            check(trip.reason.equals("visible wood for shelter supplies"),"outbound was not selected by native ShelterSupplyTravel");
            check(trip.destination!=null&&trip.destination.distSqr(SOURCE)<=3,"outbound selected another resource");
            check(supply.active()&&!supply.stone()&&SOURCE.equals(supply.resource()),"autonomous observer not bound to actual native departure");
            sawNativeOutbound=true;
        }
        if(!level.getBlockState(SOURCE).is(Blocks.OAK_LOG)){
            check(sawNativeOutbound,"collector harvested before ordinary travel admission; fixture cannot certify autonomous outbound");
            check(level.getBlockState(SOURCE).isAir(),"source replaced without native harvest");
            sawHarvest=true;
        }
        if(supply.collected()){
            check(sawHarvest&&supply.harvested(),"pickup observer lacks real source harvest");sawPickup=true;
        }
        if(!outboundSaved&&sawNativeOutbound&&npc.getX()>startX+2){
            check(!sawHarvest,"outbound checkpoint missed pre-harvest actual walk");
            outboundSaved=true;saveLedger();finish(server,"checkpoint.txt","PASS ordinary AI selected visible wood through native collection or supply travel and physically departed; saved original outbound actor, native trip and supply observer with all81goals");return;
        }
        if(trip.phase.equals("returning")&&!returning){
            check(sawNativeOutbound&&sawHarvest&&sawPickup&&supply.harvested()&&supply.collected(),"return lacks real native harvest and actual pickup");
            check(trip.reason.equals("autonomous supply return"),"return was not issued by production supply observer");
            check(supply.stage().equals("returning"),"return observer stage missing");
            returning=true;returnStartX=npc.getX();
            Files.writeString(Path.of("harvested.txt"),"PASS native source block harvested and actual carried pickup observed before automatic return; "+supply);
        }
        if(returning&&!returnSaved&&trip.phase.equals("returning")&&npc.getX()<returnStartX-.5){
            check(npc.getX()>6,"return checkpoint too close to home to establish saved walking leg");
            returnSaved=true;saveLedger();finish(server,"return-checkpoint.txt","PASS actual native harvest/pickup followed by automatic supply return; original returning actor physically moved homeward before second process checkpoint");return;
        }
        if(returning&&returnSaved&&trip.phase.equals("idle")&&!supply.active()){
            if(!Files.exists(Path.of("home-reached.txt"))){check(npc.blockPosition().distSqr(ORIGIN.offset(cabin.width()/2,1,cabin.depth()/2))<=16,"idle trip did not physically return to owned home");Files.writeString(Path.of("home-reached.txt"),"PASS collected material returned to physical owned home");}
            check(!supply.active()&&supply.status().startsWith("complete"),"supply observer did not finish at physical home");
            check(sawHarvest&&sawPickup&&maxX>startX+8,"missing native outward/harvest/pickup evidence");
            check(level.getBlockState(SOURCE).isAir(),"harvested source reappeared");
            int placed=PlayerNpcBuildMaterialUtil.matches(level.getBlockState(MISSING),Blocks.OAK_PLANKS.defaultBlockState())?1:0;
            int units=carriedWoodUnits()+droppedWoodUnits(level)+storedWoodUnits(level)+2*placed;
            if(units!=8&&npc.getMainHandItem().is(Items.OAK_PLANKS)){if(placementSettleAt<0)placementSettleAt=ticks;check(ticks-placementSettleAt<400,"native placement preview did not settle conserved inventory after400ticks: units="+units+" hand="+npc.getMainHandItem());return;}
            check(units==8,"one actual log was lost or duplicated across inventory/drop/storage/build conversion: half-plank units="+units);
            check(carriedWoodUnits()+storedWoodUnits(level)+2*placed>0,"actual collected material neither retained nor used");
            check(npc.getInventory().countItem(Items.COOKED_BEEF)==16,"food reserve changed in controlled full-health supply trip");
            check(toolDamage(Items.STONE_AXE)>0,"native log harvest did not use real carried axe");
            check(toolCount(Items.STONE_AXE)==1&&toolCount(Items.STONE_PICKAXE)==1,"native tools lost or duplicated");
            check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,ORIGIN)==1-placed,"supply trip damaged another owned cabin block");
            if(placed==0){if(ticks%200==0)System.out.println("SUPPLY_HOME_WAIT tick="+ticks+" ai="+npc.getCurrentAiState()+" inventory="+npc.getInventory().getItems());check(ticks<Integer.getInteger("journey.maxTicks",12000),"collected supplies returned but native builder never repaired its missing plank");return;}
            finish(server,"complete.txt","PASS ordinary BUILDING resident independently selected shelter wood through native collection or supply travel, physically walked outbound, saved/reloaded, harvested and picked up the actual log, automatically returned, saved/reloaded returning, and reached its owned cabin with all81goals; one-log wood conservation="+units+" half-plank units, missing plank placed="+placed+"; geometry and initial materials explicitly declared");return;
        }
        if(ticks%200==0)System.out.println("SUPPLY_PROGRESS tick="+ticks+" phase="+trip.phase+" reason="+trip.reason+" pos="+npc.position()+" supply="+supply+" ai="+npc.getCurrentAiState()+" worker="+PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc)+" inventory="+npc.getInventory().getItems());
        check(ticks<Integer.getInteger("journey.maxTicks",12000),"ordinary autonomous supply did not finish; phase="+trip.phase+" supply="+supply);
    }
    void setup(MinecraftServer server,ServerLevel level)throws Exception{
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
        for(int x=-48;x<=48;x++)for(int z=-48;z<=48;z++){
            level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,279,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=280;y<=286;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),(Math.abs(x)==48||Math.abs(z)==48)&&y<=282?Blocks.BEDROCK.defaultBlockState():Blocks.AIR.defaultBlockState());
        }
        for(var block:cabin.blocks())level.setBlockAndUpdate(block.toWorld(ORIGIN),block.state());
        level.setBlockAndUpdate(MISSING,Blocks.AIR.defaultBlockState());
        check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,ORIGIN)==1,"declared cabin must have exactly one real missing plank");
        level.setBlockAndUpdate(SOURCE,Blocks.OAK_LOG.defaultBlockState());
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("SupplyResident");npc.setUUID(ID);npc.setPersistenceRequired();npc.setPos(startX,280,-3.5);npc.getRandom().setSeed(0);
        npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(ORIGIN,cabin.width(),cabin.depth()),cabin.id());
        level.addFreshEntity(npc);
        check(goals()==81&&!npc.isNoAi()&&SurvivalTasks.visible(npc,SOURCE),"initial ordinary actor or visible supply geometry invalid");
        check(carriedWoodUnits()==0&&storedWoodUnits(level)==0,"initial carried/stored wood must be absent");
        check(Journeys.state(npc).phase.equals("idle")&&!AutonomousSupplyTrip.snapshot(npc).active(),"fixture actor already has a trip");
        Files.writeString(Path.of("setup.txt"),"Declared fresh isolated supported flat yard, real almost-complete owned cabin missing one plank, source oak log twenty blocks away, real food16/stone tools; production AI has not ticked; no trip request or dispatch");
    }
    int woodUnits(ItemStack s){return s.is(Items.OAK_LOG)?s.getCount()*8:s.is(Items.OAK_PLANKS)?s.getCount()*2:s.is(Items.STICK)?s.getCount():0;}
    Set<ItemStack> carried(){Set<ItemStack> items=Collections.newSetFromMap(new IdentityHashMap<>());items.addAll(npc.getInventory().getItems());for(var slot:EquipmentSlot.values())items.add(npc.getItemBySlot(slot));return items;}
    int carriedWoodUnits(){return carried().stream().mapToInt(this::woodUnits).sum();}
    int droppedWoodUnits(ServerLevel level){return level.getEntitiesOfClass(ItemEntity.class,YARD).stream().mapToInt(e->woodUnits(e.getItem())).sum();}
    int storedWoodUnits(ServerLevel level){int units=0;for(var b:cabin.blocks())if(level.getBlockEntity(b.toWorld(ORIGIN)) instanceof Container c)for(int i=0;i<c.getContainerSize();i++)units+=woodUnits(c.getItem(i));return units;}
    int toolDamage(Item item){return carried().stream().filter(s->s.is(item)).mapToInt(ItemStack::getDamageValue).sum();}
    int toolCount(Item item){return carried().stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    int goals()throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((net.minecraft.world.entity.ai.goal.GoalSelector)f.get(npc)).getAvailableGoals().size();}
    void saveLedger()throws Exception{var p=new Properties();var supply=AutonomousSupplyTrip.snapshot(npc);p.setProperty("supplyHome",Long.toString(supply.home().asLong()));p.setProperty("supplyDestination",Long.toString(supply.destination().asLong()));p.setProperty("uuid",ID.toString());p.setProperty("returning",Boolean.toString(returning));p.setProperty("sawOutbound",Boolean.toString(sawNativeOutbound));p.setProperty("sawHarvest",Boolean.toString(sawHarvest));p.setProperty("sawPickup",Boolean.toString(sawPickup));p.setProperty("maxX",Double.toString(maxX));p.setProperty("returnStartX",Double.toString(returnStartX));try(var out=Files.newOutputStream(Path.of("actor.properties"))){p.store(out,"observed original autonomous supply actor; no route or inventory mutations");}}
    void loadLedger()throws Exception{var p=new Properties();try(var in=Files.newInputStream(Path.of("actor.properties"))){p.load(in);}check(ID.toString().equals(p.getProperty("uuid")),"checkpoint actor identity changed");var supply=AutonomousSupplyTrip.snapshot(npc);check(supply.home()!=null&&supply.home().asLong()==Long.parseLong(p.getProperty("supplyHome"))&&supply.destination()!=null&&supply.destination().asLong()==Long.parseLong(p.getProperty("supplyDestination")),"original supply destination/home identity lost on replay");check(!Journeys.state(npc).trail.points().isEmpty(),"native observed return trail lost on replay");returning=Boolean.parseBoolean(p.getProperty("returning"));outboundSaved=true;returnSaved=returning;sawNativeOutbound=Boolean.parseBoolean(p.getProperty("sawOutbound"));sawHarvest=Boolean.parseBoolean(p.getProperty("sawHarvest"));sawPickup=Boolean.parseBoolean(p.getProperty("sawPickup"));maxX=Double.parseDouble(p.getProperty("maxX"));returnStartX=Double.parseDouble(p.getProperty("returnStartX"));}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception e){throw new RuntimeException(e);}server.halt(false);}
}

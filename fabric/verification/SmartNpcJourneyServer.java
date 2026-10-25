import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import java.nio.file.*;
import java.util.*;

/** Real copied terrain, complete normal selectors, no movement or inventory injection after setup. */
public final class SmartNpcJourneyServer implements DedicatedServerModInitializer {
    final boolean replay=Boolean.getBoolean("journey.replay");
    final int distance=Integer.getInteger("journey.distance",256);
    final boolean survival=System.getProperty("journey.mode","journey").equals("survival");
    final boolean craftBoat=Boolean.getBoolean("journey.craftBoat"),requireBoat=Boolean.getBoolean("journey.requireBoat");
    final boolean pause=Boolean.getBoolean("journey.pause"),aboard=Boolean.getBoolean("journey.aboard"),guards=System.getProperty("journey.mode","").equals("guards");
    final boolean returnAfterPause=Boolean.getBoolean("journey.returnAfterPause");
    int pauseUntil,pauseCounter;
    int ticks,stage,originalGoals;long maxTickNs,totalTickNs,begin;UUID id;boolean slept;
    PlayerNpcEntity npc;BlockPos start;Properties ledger=new Properties();boolean finishing;
    Path file(String n){return Path.of(n);}
    public void onInitializeServer(){
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(finishing){
            Thread serverThread=Thread.currentThread();
            // The vanilla JVM shutdown hook joins the server thread. Exiting on
            // that same thread would deadlock after an otherwise successful save.
            new Thread(()->{try{serverThread.join();System.exit(0);}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Journey test shutdown").start();
        }});
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("JourneyResident|"+(survival?"BUILDING":"EXPLORING")));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerTickEvents.START_SERVER_TICK.register(s->begin=System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(s->{totalTickNs+=System.nanoTime()-begin;maxTickNs=Math.max(maxTickNs,System.nanoTime()-begin);if(finishing)return;try{tick(s);}catch(Throwable t){t.printStackTrace();finish(s,"failure.txt",t.toString());}});
    }
    void finish(MinecraftServer s,String name,String text){finishing=true;try{Files.writeString(file(name),text+"\nticks="+ticks+" meanTickMs="+(totalTickNs/1e6/Math.max(1,ticks))+" maxTickMs="+maxTickNs/1e6);}catch(Exception e){throw new RuntimeException(e);}s.halt(false);}
    void tick(MinecraftServer s)throws Exception{
        ticks++;var l=s.overworld();s.tickRateManager().setTickRate(Integer.getInteger("journey.tickRate",200));
        if(stage==0){
            if(replay){try(var in=Files.newInputStream(file("actor.properties"))){ledger.load(in);}id=UUID.fromString(ledger.getProperty("uuid"));start=BlockPos.of(Long.parseLong(ledger.getProperty("start")));originalGoals=Integer.parseInt(ledger.getProperty("goals"));stage=1;}
            else{
                if(Boolean.getBoolean("journey.fixedConditions")){
                    s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"time set 1000");
                    s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),"weather clear");
                    System.out.println("SCENARIO_CONDITIONS dawn=1000 weather=clear; copied terrain unchanged");
                }
                // Real copied-world surface at the requested test site; no artificial road/bridge.
                int sx=Integer.getInteger("journey.x",0),sz=Integer.getInteger("journey.z",0);
                l.getChunk(sx>>4,sz>>4);int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sx,sz);start=new BlockPos(sx,y,sz);
                npc=SmartNpcModEntities.PLAYER_NPC.get().create(l,EntitySpawnReason.COMMAND);npc.setUsername("JourneyResident");npc.setPersistenceRequired();npc.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);
                long seed=Long.getLong("journey.seed",0L);npc.setUUID(UUID.nameUUIDFromBytes(("smartnpc-journey-test:"+seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)));npc.getRandom().setSeed(seed);
                check(npc.hasInterest(survival?com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING:com.pla.smart_npc.clazz.PlayerNpcInterest.EXPLORING),"fixture identity did not receive its declared profession");
                npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
                npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.STONE_AXE));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
                if(!survival){if(craftBoat){npc.getInventory().addItem(new ItemStack(Items.OAK_LOG,3));npc.getInventory().addItem(new ItemStack(Items.CRAFTING_TABLE));}else npc.getInventory().addItem(new ItemStack(Items.OAK_BOAT));}
                if(!survival)PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(start.offset(-2,-1,-2),5,5));
                else{npc.getInventory().addItem(new ItemStack(Items.OAK_LOG,8));npc.getInventory().addItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed"))));npc.getInventory().addItem(new ItemStack(Items.STRING,2));npc.getInventory().addItem(new ItemStack(Items.BEEF,2));}
                l.addFreshEntity(npc);id=npc.getUUID();originalGoals=goals(npc);
                if(!survival){double angle=Math.toRadians(Integer.getInteger("journey.heading",0));int x=start.getX()+(int)Math.round(distance*Math.cos(angle)),z=start.getZ()+(int)Math.round(distance*Math.sin(angle));l.getChunk(x>>4,z>>4);BlockPos dest=workSite(l,x,z);String result=Journeys.start(npc,dest);check(Journeys.active(npc),result);}
                ledger.setProperty("uuid",id.toString());ledger.setProperty("start",Long.toString(start.asLong()));ledger.setProperty("goals",Integer.toString(originalGoals));
                try(var out=Files.newOutputStream(file("actor.properties"))){ledger.store(out,"test observations only");}
                System.out.println("JOURNEY_SETUP uuid="+id+" start="+start+" distance="+distance+" goals="+originalGoals);stage=2;
            }
        }
        if(stage==1){npc=(PlayerNpcEntity)l.getEntity(id);if(npc==null){if(ticks>Math.max(1000,Integer.getInteger("journey.tickRate",200)*30))throw new AssertionError("Original saved actor not restored");return;}if(!survival&&Boolean.getBoolean("journey.retryBlocked")&&Journeys.state(npc).phase.equals("blocked")){Journeys.returnHome(npc,"requested return");if(!ledger.containsKey("destinationReached"))ledger.setProperty("returnOnly","true");}if(!survival)check(Journeys.state(npc).active(),"saved journey not active");check(goals(npc)==originalGoals,"selector changed on reload");System.out.println("JOURNEY_RESUMED uuid="+id+" phase="+Journeys.state(npc).phase+" trail="+Journeys.state(npc).trail.points().size());stage=2;}
        if(stage!=2)return;
        if(ticks%10==0&&npc.isInWall()){
            var head=BlockPos.containing(npc.getX(),npc.getEyeY(),npc.getZ());
            System.out.println("ACTOR_IN_WALL pos="+npc.position()+" eye="+npc.getEyeY()+" pose="+npc.getPose()+" box="+npc.getBoundingBox()+" head="+l.getBlockState(head)+" above="+l.getBlockState(head.above())+" vehicle="+npc.getVehicle()+" health="+npc.getHealth());
        }
        if(guards){
            var state=Journeys.state(npc);check(state.active(),"valid departure rejected");
            Journeys.block(npc,"fixture: inaccessible route");Journeys.returnHome(npc,"automatic retry");check(state.phase.equals("blocked"),"same-position automatic retry loop");
            state.dimension="minecraft:the_nether";com.pla.smart_npc.fabric.PersistentData.get(npc).putString("PlayerNpcHomeDimension","minecraft:the_nether");
            check(PlayerNpcHomeUtil.getHome(npc).isEmpty(),"foreign-dimension home exposed");Journeys.returnHome(npc,"requested return");check(state.phase.equals("blocked"),"unsupported portal route accepted");
            state.dimension=SurvivalTasks.dimension(npc);com.pla.smart_npc.fabric.PersistentData.get(npc).putString("PlayerNpcHomeDimension",state.dimension);state.phase="idle";
            npc.getInventory().removeAllItems();String result=Journeys.start(npc,start.offset(200,0,0));check(!state.active()&&result.contains("food"),"unprepared departure accepted");
            finish(s,"complete.txt","PASS same-position blocked retry, foreign-dimension home and prepared-food departure guards");return;
        }
        if(replay&&pause&&ledger.containsKey("pauseActive")&&!ledger.containsKey("pausePassed")){
            if(pauseUntil==0){check(npc.isNoAi(),"saved pause flag lost");pauseUntil=ticks+2000;pauseCounter=Journeys.state(npc).activeNoProgressTicks;}
            check(Journeys.state(npc).activeNoProgressTicks==pauseCounter,"suspended work consumed journey no-progress allowance");
            if(ticks<pauseUntil)return;
            npc.setNoAi(false);ledger.setProperty("pausePassed","true");System.out.println("JOURNEY_PAUSE_RESUMED saved original actor after 2000 suspended world ticks");
            if(returnAfterPause){ledger.setProperty("returnOnly","true");Journeys.returnHome(npc,"test: return after suspended trip");}
        }
        if(survival){
            check(npc.isAlive(),"survivor stopped: health="+npc.getHealth()+" removed="+npc.isRemoved()+" air="+npc.getAirSupply()+" damage="+npc.getLastDamageSource());
            var h=PlayerNpcHomeUtil.getHome(npc);var layout=PlayerNpcHomeUtil.getHomeLayoutId(npc).flatMap(PlayerNpcBuildLayoutLoader::getLayout);
            int missing=h.isPresent()&&layout.isPresent()?PlayerNpcBuildLayoutLoader.countMissingRequired(l,layout.get(),h.get().origin()):-1;
            if(ticks%1000==0&&h.isPresent()&&layout.isPresent()){
                var unmet=layout.get().blocks().stream().filter(b->!b.optional()&&!b.state().isAir()&&!PlayerNpcBuildMaterialUtil.matches(l.getBlockState(b.toWorld(h.get().origin())),b.state())).map(b->b.toWorld(h.get().origin())+" "+b.state()).toList();
                System.out.println("SURVIVAL_UNMET "+unmet+" next="+PlayerNpcBuildMaterialUtil.findMissingBuildMaterialNeed(l,npc)+" escape="+npc.getUpwardEscapeTarget()+" cooldown="+npc.getHoleEscapeCooldown());
                System.out.println("SURVIVAL_EXECUTORS "+running());
            }
            if(ticks%500==0)System.out.println("SURVIVAL_PROGRESS ticks="+ticks+" pos="+npc.position()+" ai="+npc.getCurrentAiState()+" detail="+npc.getCurrentAiDetail()+" home="+h+" missing="+missing+" food="+SurvivalTasks.cookedFoodCount(npc)+" cooking="+SurvivalTasks.describe(npc)+" idle="+npc.getIdleTraceDetail()+" buildJob="+npc.isDailyJobActive(com.pla.smart_npc.clazz.PlayerNpcInterest.BUILDING)+" logNeed="+npc.shouldPrioritizeLogGathering()+" reserve="+npc.getRawLogReserveTarget()+" snapshot="+CookingCraftGoal.snapshot(npc,l)+" placeFurnace="+new com.pla.smart_npc.entity.ai.FurnaceAi(npc).shouldPlaceFurnaceForWork(l)+" water="+npc.isInWater()+" inventory="+npc.getInventory().getItems());
            if(missing==0&&!replay){ledger.setProperty("shelterBuilt","true");try(var out=Files.newOutputStream(file("actor.properties"))){ledger.store(out,"test observations only");}finish(s,"checkpoint.txt","Limited-start resident built all 73 cabin blocks from real resources on copied terrain");return;}
            if(npc.isSleeping())slept=true;
            if(replay&&missing==0&&slept&&!npc.isSleeping()&&!l.isDarkOutside()){check(SurvivalTasks.cookedFoodCount(npc)>=4,"safe food reserve after rest");finish(s,"complete.txt","PASS limited-start real-terrain cabin, saved original actor, native sleep/wake and food reserve="+SurvivalTasks.cookedFoodCount(npc));}
            if(ticks>Math.min(96000,Integer.getInteger("journey.maxTicks",96000)))throw new AssertionError("Survival deadline; missing="+missing+" "+npc.getCurrentAiDetail());return;
        }
        check(npc.isAlive(),"traveller stopped: health="+npc.getHealth()+" removed="+npc.isRemoved()+" air="+npc.getAirSupply()+" damage="+npc.getLastDamageSource());var journey=Journeys.state(npc);
        if(journey.landingTarget!=null&&ticks%20==0)System.out.println("LANDING_DIAGNOSTIC target="+journey.landingTarget+" pos="+npc.position()+" ground="+npc.onGround()+" water="+npc.isInWater()+" feet="+l.getBlockState(npc.blockPosition())+" below="+l.getBlockState(npc.blockPosition().below()));
        if(journey.boatId!=null)ledger.setProperty("boatUsed","true");
        check(goals(npc)==originalGoals,"normal behaviour removed");
        if(ticks%200==0)System.out.println("JOURNEY_PROGRESS ticks="+ticks+" pos="+npc.position()+" phase="+journey.phase+" status="+journey.status+" reason="+journey.reason+" trail="+journey.trail.points().size()+" cursor="+journey.cursor+" worker="+PlayerNpcAiWorkBudget.hasActiveWorkerSlot(npc)+" passenger="+npc.isPassenger()+" owns="+Journeys.ownsBoat(npc,npc.getVehicle())+" healing="+npc.isHealing()+" target="+npc.getTarget()+" noai="+npc.isNoAi()+" actorTicks="+npc.tickCount+" vehicle="+npc.getVehicle());
        if(!replay&&!ledger.containsKey("checkpoint")&&npc.blockPosition().distSqr(start)>64*64&&(!aboard||npc.isPassenger())){ledger.setProperty("checkpoint",aboard?"outbound aboard owned boat":"outbound");if(pause){npc.setNoAi(true);ledger.setProperty("pauseActive","true");}try(var out=Files.newOutputStream(file("actor.properties"))){ledger.store(out,"test observations only");}finish(s,"checkpoint.txt","Saved original actor on outbound real-terrain journey; aboard="+npc.isPassenger()+" paused="+pause);return;}
        if(journey.lastArrival!=null)ledger.setProperty("destinationReached","true");
        if(journey.phase.equals("at_destination")){Journeys.returnHome(npc,"test return from reached work site");}
        if(replay&&(ledger.containsKey("destinationReached")||ledger.containsKey("returnOnly"))&&!ledger.containsKey("returnCheckpoint")&&journey.phase.equals("returning")&&journey.cursor<journey.trail.points().size()-16&&(npc.onGround()||npc.isPassenger())){
            ledger.setProperty("returnCheckpoint","true");try(var out=Files.newOutputStream(file("actor.properties"))){ledger.store(out,"test observations only");}finish(s,"return-checkpoint.txt","Saved original actor on returning real-terrain journey");return;
        }
        if(journey.phase.equals("blocked"))throw new AssertionError("Journey blocked: "+journey.status+" pos="+npc.position());
        if(journey.phase.equals("idle")&&!ledger.containsKey("destinationReached")&&!ledger.containsKey("returnOnly"))throw new AssertionError("Returned without reaching destination: "+journey.reason);
        if((ledger.containsKey("destinationReached")||ledger.containsKey("returnOnly"))&&!journey.active()&&npc.blockPosition().distSqr(start)<=16){int food=npc.getInventory().countItem(Items.COOKED_BEEF);check(food>=4&&food<=16,"safe reserve and no duplicated food; native healing may consume supplies");int boats=npc.getInventory().getItems().stream().filter(i->i.getItem() instanceof BoatItem).mapToInt(ItemStack::getCount).sum()+(journey.boatId!=null&&l.getEntity(journey.boatId)!=null?1:0);check(boats==1,"one boat conserved across carried/physical state");if(requireBoat)check(ledger.containsKey("boatUsed"),"physical boat crossing not observed");finish(s,"complete.txt","PASS real copied-world outward/return travel; original UUID and complete selectors preserved; distance="+distance+" reachedWorkSite="+ledger.containsKey("destinationReached")+" returnOnly="+ledger.containsKey("returnOnly")+" food="+food+" boats="+boats+" boatUsed="+ledger.containsKey("boatUsed")+" crafted="+craftBoat+" loadedChunks="+l.getChunkSource().getLoadedChunksCount()+" tickets="+PlayerNpcForceTickManager.forceTickSnapshot(s).workerTicketCount());}
        if(ticks>Math.min(12000+distance*40,Integer.getInteger("journey.maxTicks",12000+distance*40)))throw new AssertionError("Journey deadline: "+journey.status);
    }
    BlockPos workSite(ServerLevel level,int x,int z){
        // A test waypoint is a standing work site, not the top of an arbitrary
        // tree trunk caught by MOTION_BLOCKING_NO_LEAVES. Never alter terrain.
        for(int r=0;r<=8;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            int px=x+dx,pz=z+dz;level.getChunk(px>>4,pz>>4);
            var p=new BlockPos(px,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,px,pz),pz);var floor=level.getBlockState(p.below());
            if(floor.is(net.minecraft.tags.BlockTags.LOGS)||floor.is(net.minecraft.tags.BlockTags.LEAVES))continue;
            if(JourneyBoats.dry(level,p)){System.out.println("JOURNEY_WORK_SITE nominal="+x+","+z+" actual="+p+" support="+floor);return p;}
        }
        var p=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z);
        System.out.println("JOURNEY_WORK_SITE open-water fallback="+p);return p;
    }
    int goals(PlayerNpcEntity n)throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((GoalSelector)f.get(n)).getAvailableGoals().size();}
    String running(){try{var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);return ((GoalSelector)field.get(npc)).getAvailableGoals().stream().filter(w->w.isRunning()).map(w->{var g=w.getGoal();while(g instanceof com.pla.smart_npc.entity.goal.StartupWorkGatedGoal q)g=q.getDelegateGoal();while(g instanceof com.pla.smart_npc.entity.goal.InterestGatedGoal q)g=q.getDelegateGoal();String out=g.getClass().getSimpleName();for(String name:new String[]{"samePlacementTicks","placementAttempts","waitingForPlacementClearance","completedPlacements","goalTicks","activeBuildBlock","craftRouteNoProgressTicks"})try{var f=g.getClass().getDeclaredField(name);f.setAccessible(true);out+=" "+name+"="+f.get(g);}catch(Exception ignored){}try{var f=g.getClass().getDeclaredField("placingBlockAi");f.setAccessible(true);var helper=f.get(g);var d=helper.getClass().getDeclaredField("activeDelayTicks");d.setAccessible(true);out+=" placementDelay="+d.get(helper);}catch(Exception ignored){}return out;}).toList().toString();}catch(Exception e){return e.toString();}}
    void check(boolean b,String message){if(!b)throw new AssertionError(message);}
}

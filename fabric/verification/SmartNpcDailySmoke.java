import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import java.nio.file.*;
import java.util.*;

/** A real day/night/morning with the complete registered goal and target selectors. */
public final class SmartNpcDailySmoke implements ClientModInitializer {
    final boolean churnOnly=Boolean.getBoolean("smartnpc.daily.churnOnly");
    final boolean blockedStorageOnly=Boolean.getBoolean("smartnpc.daily.blockedStorageOnly");
    boolean storageObservedSleep;int storageSettledAt;
    final boolean entryOnly=Boolean.getBoolean("smartnpc.daily.entryOnly");
    final boolean storageOnly=Boolean.getBoolean("smartnpc.daily.storageOnly");
    final boolean pitOnly=Boolean.getBoolean("smartnpc.daily.pitOnly");
    final boolean repairOnly=Boolean.getBoolean("smartnpc.daily.repairOnly");
    final boolean replay=Boolean.getBoolean("smartnpc.daily.replay");
    final boolean nightCheckpoint=Boolean.getBoolean("smartnpc.daily.night");
    final boolean continuous=Boolean.getBoolean("smartnpc.daily.continuous");
    int stage,ticks,goals,targets,initialLogs=48,logsBroken,firstCookTick,firstSleepTick,morningWork,daytimeSleepingTicks;
    boolean withdrew,deposited,cooked,slept,checkpointed,morningAction,morningDamageApplied,morningRepaired; String morningBaseline; volatile boolean done; volatile Throwable failure;
    long readyAt; PlayerNpcEntity npc; BlockPos base,chestPos,foreignPos; ServerLevel level;
    final Set<String> states=new TreeSet<>(); final List<BlockPos> treeLogs=new ArrayList<>();
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public void onInitializeClient(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("DailyResident|BUILDING"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        if(replay)net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server->server.tickRateManager().setFrozen(true));
        ClientTickEvents.END_CLIENT_TICK.register(this::client);
        ServerTickEvents.END_SERVER_TICK.register(server->{if(stage!=3||done||failure!=null||npc==null)return;try{tick();}catch(Throwable t){failure=t;}});
    }
    GoalSelector selector(String name)throws Exception{var f=Mob.class.getDeclaredField(name);f.setAccessible(true);return (GoalSelector)f.get(npc);}
    int count(Item item){int n=npc.getInventory().countItem(item);for(var slot:EquipmentSlot.values())if(npc.getItemBySlot(slot).is(item))n+=npc.getItemBySlot(slot).getCount();return n;}
    String trace(){return "ticks="+ticks+" time="+level.getOverworldClockTime()+" pos="+npc.position()+" ai="+npc.getCurrentAiState()+" detail="+npc.getCurrentAiDetail()+" idle="+npc.getIdleTraceDetail()+" cooking="+SurvivalTasks.describe(npc)+" food="+SurvivalFishingGoal.describe(npc)+" harvested="+logsBroken+" withdrew="+withdrew+" deposited="+deposited+" cooked="+cooked+" slept="+slept+" morningWork="+morningWork+" morningAction="+morningAction+" morningRepair="+morningRepaired+" hand="+npc.getMainHandItem()+" escape="+npc.getUpwardEscapeTarget()+" holeCooldown="+npc.getHoleEscapeCooldown()+" running="+running()+" inventory="+npc.getInventory().getItems();}
    String running(){try{return selector("goalSelector").getAvailableGoals().stream().filter(w->w.isRunning()).map(w->{net.minecraft.world.entity.ai.goal.Goal g=w.getGoal();while(g instanceof com.pla.smart_npc.entity.goal.StartupWorkGatedGoal q)g=q.getDelegateGoal();while(g instanceof com.pla.smart_npc.entity.goal.InterestGatedGoal q)g=q.getDelegateGoal();String detail=g.getClass().getSimpleName();for(String name:new String[]{"mode","goalTicks","pillarBasePos","furnacePos","furnaceStandPos","bedPos"})try{var f=g.getClass().getDeclaredField(name);f.setAccessible(true);detail+=" "+name+"="+f.get(g);}catch(Exception ignored){}return detail;}).toList().toString();}catch(Exception e){return e.toString();}}
    void setup(ServerLevel world)throws Exception{
        var swapProbe=SmartNpcModEntities.PLAYER_NPC.get().create(world,EntitySpawnReason.COMMAND);
        swapProbe.getInventory().clearContent();for(var slot:EquipmentSlot.values())swapProbe.setItemSlot(slot,ItemStack.EMPTY);
        swapProbe.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.FISHING_ROD));swapProbe.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        var swapAi=new com.pla.smart_npc.entity.ai.ToolAi(swapProbe);
        check(swapAi.equipItem(Items.IRON_AXE),"ordinary tool swap starts");
        check(com.pla.smart_npc.entity.goal.PlayerNpcFishingGoal.hasFishingRod(swapProbe),"temporarily retained rod remains available to fishing admission");
        check(swapAi.equipItem(Items.FISHING_ROD),"fishing retrieves the original retained rod");swapAi.restoreMainHand();
        check(swapProbe.getMainHandItem().is(Items.FISHING_ROD)&&swapProbe.getInventory().countItem(Items.IRON_AXE)==1,"rod and axe conserved across handoff");
        System.out.println("DAILY_EQUIPMENT retained rod admitted and recovered; real rod and axe conserved");
        var furnace=Blocks.FURNACE.defaultBlockState();
        check(PlayerNpcBuildMaterialUtil.matches(furnace.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true),furnace),"burning furnace remains structurally correct");
        check(!PlayerNpcBuildMaterialUtil.matches(furnace.setValue(AbstractFurnaceBlock.FACING,net.minecraft.core.Direction.EAST),furnace),"wrong furnace orientation still rejected");
        var door=Blocks.OAK_DOOR.defaultBlockState();
        check(PlayerNpcBuildMaterialUtil.matches(door.setValue(DoorBlock.OPEN,true).setValue(DoorBlock.POWERED,true),door),"open/powered door remains structurally correct");
        check(!PlayerNpcBuildMaterialUtil.matches(door.setValue(DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),door),"wrong door half still rejected");
        var bed=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed")).defaultBlockState();
        check(PlayerNpcBuildMaterialUtil.matches(bed.setValue(BedBlock.OCCUPIED,true),bed),"occupied bed remains structurally correct");
        check(!PlayerNpcBuildMaterialUtil.matches(bed.setValue(BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD),bed),"wrong bed half still rejected");
        check(!PlayerNpcBuildMaterialUtil.matches(Blocks.AIR.defaultBlockState(),Blocks.OAK_PLANKS.defaultBlockState()),"missing roof still requires repair");
        System.out.println("DAILY_GEOMETRY operating states accepted; wrong orientations, halves and absent roof rejected");
        level=world;var server=level.getServer();var p=server.getPlayerList().getPlayers().getFirst();base=new BlockPos(0,260,0);
        // Prepared bounded resident yard, deep mineable ground and a real source pond.
        for(int x=-28;x<=28;x++)for(int z=-24;z<=24;z++){
            level.getChunk(base.offset(x,0,z));level.setBlockAndUpdate(base.offset(x,-13,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=-12;y<-1;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
            for(int y=0;y<8;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());
            if(Math.abs(x)==28||Math.abs(z)==24)for(int y=-12;y<3;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.GLASS.defaultBlockState());
        }
        var layout=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();
        for(var block:layout.blocks())level.setBlockAndUpdate(block.toWorld(base),block.state());
        check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,layout,base)==0,"prepared home must match its plan");
        chestPos=layout.blocks().stream().filter(b->b.state().is(Blocks.CHEST)).map(b->b.toWorld(base)).findFirst().orElseThrow();
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);chest.clearContent();chest.setItem(0,new ItemStack(Items.COAL,8));chest.setItem(1,new ItemStack(Items.STRING,2));chest.setChanged();
        foreignPos=base.offset(-8,0,-8);level.setBlockAndUpdate(foreignPos,Blocks.CHEST.defaultBlockState());((ChestBlockEntity)level.getBlockEntity(foreignPos)).setItem(0,new ItemStack(Items.DIAMOND,7));
        for(int x=11;x<=22;x++)for(int z=-5;z<=7;z++){level.setBlockAndUpdate(base.offset(x,-3,z),Blocks.DIRT.defaultBlockState());for(int y=-2;y<0;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.WATER.defaultBlockState());}
        for(int x:new int[]{-15,-9,9})for(int z:new int[]{-15,14}){
            for(int y=0;y<8;y++){var pos=base.offset(x,y,z);treeLogs.add(pos);level.setBlockAndUpdate(pos,Blocks.OAK_LOG.defaultBlockState());}
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx!=0||dz!=0)level.setBlockAndUpdate(base.offset(x+dx,6,z+dz),Blocks.OAK_LEAVES.defaultBlockState());
        }
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("DailyResident");npc.setPersistenceRequired();npc.setPos(2.5,260,-3.5);
        npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.BEEF,2));npc.getInventory().addItem(new ItemStack(Items.IRON_AXE));npc.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));
        // Real surplus that exercises the unchanged >half-full nightly deposit gate.
        for(int i=3;i<19;i++){var stack=new ItemStack(Items.ANDESITE,4);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Daily surplus "+i));npc.getInventory().setItem(i,stack);}
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(base,layout.width(),layout.depth()));PlayerNpcHomeUtil.setHomeLayoutId(npc,layout.id());npc.setOwnedChestPos(chestPos);level.addFreshEntity(npc);
        if(repairOnly){npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.OAK_PLANKS));npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,8));npc.getInventory().addItem(new ItemStack(Items.IRON_AXE));npc.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));npc.setPos(1.5,261,1.5);if(churnOnly){var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_NAME,Component.literal("Churn 0"));npc.getInventory().addItem(paper);}level.setBlockAndUpdate(base.offset(1,3,1),Blocks.AIR.defaultBlockState());}
        if(storageOnly){npc.getInventory().setItem(0,new ItemStack(Items.COOKED_BEEF,8));npc.setPos(entryOnly?-2.7623383404:1.5,entryOnly?260:261,entryOnly?1.4994962981:2.5);for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){var stand=chestPos.relative(direction);if(com.pla.smart_npc.entity.ai.ChestAi.canStandAt(level,stand)){var standard=npc.getNavigation().createPath(stand,0);var local=HomeWorkNavigation.createPath(npc,stand);System.out.println("STORAGE_PATH stand="+stand+" standard="+(standard==null?"null":standard.canReach()+" end="+standard.getEndNode().asBlockPos())+" home="+(local==null?"null":local.canReach()+" end="+local.getEndNode().asBlockPos()));}}if(blockedStorageOnly)level.setBlockAndUpdate(base.offset(1,1,1),Blocks.COBBLESTONE.defaultBlockState());var selected=com.pla.smart_npc.entity.ai.ChestAi.findAdjacentStand(npc,level,chestPos);check(blockedStorageOnly||entryOnly||selected!=null&&com.pla.smart_npc.entity.ai.ChestAi.isAtStand(npc,selected),"already in reach must select a chest stand even before path setup");}
        if(pitOnly){npc.getInventory().clearContent();npc.getInventory().addItem(new ItemStack(Items.DIRT,8));npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,8));npc.getInventory().addItem(new ItemStack(Items.IRON_AXE));npc.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));for(int x=-11;x<=-8;x++)for(int z=-20;z<=-17;z++)for(int y=257;y<260;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());npc.setPos(-9.5,257,-18.5);}
        goals=selector("goalSelector").getAvailableGoals().size();targets=selector("targetSelector").getAvailableGoals().size();check(goals>30&&targets>0,"complete native selectors required");
        server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"difficulty peaceful");server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),(pitOnly||storageOnly?"time set 18000":"time set 1000"));server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"weather clear");p.teleportTo(-20,260,20);server.tickRateManager().setTickRate(200);
        System.out.println("DAILY_SETUP goals="+goals+" targets="+targets+" npc="+npc.getUUID()+" "+trace());
    }
    void tick()throws Exception{
        if(storageOnly){ticks++;storageObservedSleep|=npc.isSleeping();slept|=npc.isSleeping();check(com.pla.smart_npc.fabric.PersistentData.get(npc).getIntOr("SmartNpcHomeDepositAttempts",0)<=3,"storage retry cap");if(blockedStorageOnly&&ticks==500){level.setBlockAndUpdate(base.offset(1,1,1),Blocks.AIR.defaultBlockState());System.out.println("STORAGE_REOPENED real chest stand; actor is not moved");}deposited|=((ChestBlockEntity)level.getBlockEntity(chestPos)).countItem(Items.ANDESITE)>0;check(npc.isAlive(),"storage resident alive");if(ticks%200==0)System.out.println("STORAGE_PROGRESS "+trace());boolean storageQualified=deposited&&(blockedStorageOnly||entryOnly?storageObservedSleep:npc.isSleeping());if(blockedStorageOnly&&storageQualified&&storageSettledAt==0)storageSettledAt=ticks+200;if(storageQualified&&(!blockedStorageOnly||ticks>=storageSettledAt)){if(blockedStorageOnly){check(com.pla.smart_npc.fabric.PersistentData.get(npc).getBooleanOr("SmartNpcHomeDepositSucceeded",false),"successful storage pass recorded");check(com.pla.smart_npc.fabric.PersistentData.get(npc).getIntOr("SmartNpcHomeDepositAttempts",0)>=2,"blocked approach was retried rather than skipped");check(count(Items.ANDESITE)+((ChestBlockEntity)level.getBlockEntity(chestPos)).countItem(Items.ANDESITE)==64,"storage retry conserves all surplus");}done=true;level.getServer().tickRateManager().setFrozen(true);System.out.println("STORAGE_COMPLETE "+trace());return;}if(ticks>=(blockedStorageOnly?6000:3000))throw new AssertionError("Ordinary home storage before rest stalled: "+trace());return;}
        if(pitOnly){ticks++;check(npc.isAlive(),"pit resident died");check(selector("goalSelector").getAvailableGoals().size()==goals,"pit selector changed");if(ticks%200==0)System.out.println("PIT_PROGRESS "+trace());if(npc.isSleeping()&&npc.getY()>260){System.out.println("PIT_COMPLETE ticks="+ticks+" "+trace());done=true;level.getServer().tickRateManager().setFrozen(true);return;}if(ticks>=6000)throw new AssertionError("Home return out of enclosed pit stalled: "+trace());return;}
        if(repairOnly&&churnOnly){for(var item:npc.getInventory().getItems())if(item.is(Items.PAPER)){item.set(DataComponents.CUSTOM_NAME,Component.literal("Churn "+(ticks%2)));break;}}
        if(repairOnly){ticks++;check(npc.isAlive(),"repair resident died");check(selector("goalSelector").getAvailableGoals().size()==goals,"repair selector changed");if(ticks%200==0)System.out.println("REPAIR_PROGRESS "+trace());if(level.getBlockState(base.offset(1,3,1)).is(Blocks.OAK_PLANKS)){check(count(Items.OAK_PLANKS)==0,"repair consumes the real plank");if(churnOnly)check(count(Items.PAPER)==1,"irrelevant item preserved; only its name changes");check(treeLogs.stream().allMatch(pos->level.getBlockState(pos).is(Blocks.OAK_LOG)),"funded repair must not wait for unrelated wood stockpiles");System.out.println("REPAIR_COMPLETE ticks="+ticks+" "+trace());done=true;level.getServer().tickRateManager().setFrozen(true);return;}if(ticks>=3000)throw new AssertionError("Funded home repair stalled: "+trace());return;}
        check(com.pla.smart_npc.fabric.PersistentData.get(npc).getIntOr("SmartNpcHomeDepositAttempts",0)<=3,"nightly storage retry cap exceeded");
        ticks++;check(npc.isAlive(),"resident died: "+trace());check(!npc.isNoAi(),"resident AI disabled");
        check(selector("goalSelector").getAvailableGoals().size()==goals&&selector("targetSelector").getAvailableGoals().size()==targets,"normal selectors changed");
        check(!(npc.getTarget() instanceof net.minecraft.world.entity.player.Player),"unprovoked observer target");
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos);check(chest!=null,"home chest lost");
        check(((ChestBlockEntity)level.getBlockEntity(foreignPos)).getItem(0).is(Items.DIAMOND)&&((ChestBlockEntity)level.getBlockEntity(foreignPos)).getItem(0).getCount()==7,"foreign chest changed");
        withdrew|=chest.countItem(Items.COAL)<8||chest.countItem(Items.STRING)<2;
        deposited|=chest.countItem(Items.ANDESITE)>0;
        logsBroken=(int)treeLogs.stream().filter(pos->!level.getBlockState(pos).is(Blocks.OAK_LOG)).count();
        if(count(Items.COOKED_BEEF)>=2){cooked=true;if(firstCookTick==0)firstCookTick=ticks;}
        if(npc.isSleeping()){slept=true;if(firstSleepTick==0)firstSleepTick=ticks;}
        states.add(npc.getCurrentAiState());
        long time=level.getOverworldClockTime();
        // Declared environmental interruption: remove one roof plank at dawn.
        // Only the NPC may repair it, using its real materials and normal build goal.
        if(time>=24000&&!morningDamageApplied){morningDamageApplied=true;check(level.getBlockState(base.offset(1,3,1)).is(Blocks.OAK_PLANKS),"morning repair fixture needs original roof");level.setBlockAndUpdate(base.offset(1,3,1),Blocks.AIR.defaultBlockState());System.out.println("DAILY_DAWN roof plank removed by fixture; NPC must repair it");}
        if(morningDamageApplied&&level.getBlockState(base.offset(1,3,1)).is(Blocks.OAK_PLANKS)){morningRepaired=true;morningAction=true;}
        if(time>=24000&&!level.isDarkOutside()&&!npc.isSleeping()&&(npc.getCurrentAiState().contains("gather")||npc.getCurrentAiState().contains("fishing")||npc.getCurrentAiState().contains("craft")||npc.getCurrentAiState().contains("suppl")||npc.getCurrentAiState().contains("build")))morningWork++;
        if(time>=24000&&!level.isDarkOutside()&&npc.isSleeping())daytimeSleepingTicks++;else daytimeSleepingTicks=0;
        check(daytimeSleepingTicks<=600,"saved sleep did not wake during daytime: "+trace());
        if(time>=24000&&!level.isDarkOutside()) {
            String evidence=logsBroken+":"+count(Items.COBBLESTONE)+":"+count(Items.FISHING_ROD)+":"+chest.countItem(Items.ANDESITE)+":"+count(Items.COOKED_COD)+":"+count(Items.COOKED_SALMON);
            if(morningBaseline==null)morningBaseline=evidence;else if(!morningBaseline.equals(evidence))morningAction=true;
        }
        if(ticks%400==0)System.out.println("DAILY_PROGRESS "+trace());
        if(!replay&&!continuous&&!checkpointed&&(nightCheckpoint?npc.isSleeping():ticks>=200&&SurvivalTasks.cookingActive(npc)&&!cooked)){checkpointed=true;save();level.getServer().tickRateManager().setFrozen(true);done=true;System.out.println("DAILY_CHECKPOINT "+trace());return;}
        if(time>=25000&&cooked&&withdrew&&deposited&&logsBroken>0&&slept&&(morningWork>=100||morningRepaired)&&morningAction&&morningRepaired&&SurvivalFishingGoal.foodCount(npc)>=4&&!SurvivalTasks.cookingActive(npc)&&!SurvivalTasks.cookingNeeded(npc,level)){int andesite=count(Items.ANDESITE)+chest.countItem(Items.ANDESITE);
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(-30,-15,-26)),net.minecraft.world.phys.Vec3.atLowerCornerOf(base.offset(31,10,27)))))if(item.getItem().is(Items.ANDESITE))andesite+=item.getItem().getCount();
            for(var pos:BlockPos.betweenClosed(base.offset(-28,-12,-24),base.offset(28,8,24)))if(level.getBlockState(pos).is(Blocks.ANDESITE))andesite++;
            check(andesite==64,"surplus item/block conservation: "+andesite+" instead of 64");
            done=true;save();level.getServer().tickRateManager().setFrozen(true);System.out.println("DAILY_COMPLETE "+trace()+" states="+states);return;}
        if(ticks>=40000)throw new AssertionError("Daily routine unfinished: "+trace()+" states="+states);
    }
    void save()throws Exception{
        var s=new Properties();s.setProperty("npc",npc.getUUID().toString());s.setProperty("ticks",""+ticks);s.setProperty("goals",""+goals);s.setProperty("targets",""+targets);
        s.setProperty("withdrew",""+withdrew);s.setProperty("deposited",""+deposited);s.setProperty("cooked",""+cooked);s.setProperty("slept",""+slept);s.setProperty("firstCookTick",""+firstCookTick);s.setProperty("firstSleepTick",""+firstSleepTick);s.setProperty("morningWork",""+morningWork);s.setProperty("states",String.join(",",states));
        s.setProperty("chest",""+chestPos.asLong());s.setProperty("foreign",""+foreignPos.asLong());s.setProperty("trees",String.join(",",treeLogs.stream().map(p->Long.toString(p.asLong())).toList()));SurvivalTasks.save(npc);
        try(var out=Files.newOutputStream(Minecraft.getInstance().gameDirectory.toPath().resolve("daily-state.properties"))){s.store(out,"Observation ledger; Minecraft persists the original actor and world");}
    }
    void resume(ServerLevel world)throws Exception{
        level=world;base=new BlockPos(0,260,0);level.getChunk(base);var s=new Properties();try(var in=Files.newInputStream(Minecraft.getInstance().gameDirectory.toPath().resolve("daily-state.properties"))){s.load(in);}
        npc=(PlayerNpcEntity)level.getEntity(UUID.fromString(s.getProperty("npc")));check(npc!=null,"original daily NPC not loaded");
        ticks=Integer.parseInt(s.getProperty("ticks"));goals=Integer.parseInt(s.getProperty("goals"));targets=Integer.parseInt(s.getProperty("targets"));withdrew=Boolean.parseBoolean(s.getProperty("withdrew"));deposited=Boolean.parseBoolean(s.getProperty("deposited"));cooked=Boolean.parseBoolean(s.getProperty("cooked"));slept=Boolean.parseBoolean(s.getProperty("slept"));firstCookTick=Integer.parseInt(s.getProperty("firstCookTick"));firstSleepTick=Integer.parseInt(s.getProperty("firstSleepTick"));morningWork=Integer.parseInt(s.getProperty("morningWork"));states.addAll(List.of(s.getProperty("states").split(",")));
        chestPos=BlockPos.of(Long.parseLong(s.getProperty("chest")));foreignPos=BlockPos.of(Long.parseLong(s.getProperty("foreign")));for(var p:s.getProperty("trees").split(","))treeLogs.add(BlockPos.of(Long.parseLong(p)));
        check(PlayerNpcHomeUtil.getHome(npc).isPresent(),"saved home absent");if(!nightCheckpoint)check(SurvivalTasks.cookingActive(npc),"pending cooking did not survive restart");level.getServer().tickRateManager().setFrozen(false);level.getServer().tickRateManager().setTickRate(200);System.out.println("DAILY_RESUMED npc="+npc.getUUID()+" "+trace());
    }
    void client(Minecraft mc){try{
        if(failure!=null)throw new RuntimeException(failure);
        if(!replay&&stage==0&&mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
        if(stage==1&&mc.gui.screen() instanceof CreateWorldScreen screen){stage=2;var ui=screen.getUiState();ui.setName("SmartNpcDaily");ui.setSeed("20261024005");ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);ui.setAllowCommands(true);var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}
        if(replay&&stage==0&&mc.player!=null)stage=2;
        if(stage==2&&mc.player!=null&&mc.getSingleplayerServer()!=null){if(readyAt==0)readyAt=System.nanoTime();if(!replay&&System.nanoTime()-readyAt<5_000_000_000L)return;stage=3;mc.getSingleplayerServer().execute(()->{try{if(replay)resume(mc.getSingleplayerServer().overworld());else setup(mc.getSingleplayerServer().overworld());}catch(Throwable t){failure=t;}});}
        if(done){stage=4;Files.writeString(mc.gameDirectory.toPath().resolve(replay?"replay-complete.txt":"complete.txt"),storageOnly?"PASS ordinary storage then rest":pitOnly?"PASS ordinary pit escape and native return to home bed":repairOnly?"PASS ordinary funded home repair consumes its plank without harvesting unrelated reserves":replay||continuous?"PASS complete ordinary daily routine":(nightCheckpoint?"Saved ordinary daily routine while asleep":"Saved ordinary daily routine with cooking pending"));mc.stop();}
    }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}}
}

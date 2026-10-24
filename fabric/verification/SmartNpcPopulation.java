import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import java.nio.file.*;
import java.util.*;

/** Ordinary selectors are left intact. Supplies and prepared terrain are fixture inputs. */
public final class SmartNpcPopulation implements ClientModInitializer {
 int stage,ticks,uiFrames,completeTicks; boolean cabinReplay=Boolean.getBoolean("smartnpc.population.cabinReplay"); long replayReadyAt; volatile boolean uiSeen; long began; volatile Throwable failure; volatile boolean done;
 final List<PlayerNpcEntity> actors=new ArrayList<>(); final Set<String> states=new TreeSet<>();
 PlayerNpcBuildLayout cabin; BlockPos origin; int initialMissing,minMissing;
 public void onInitializeClient(){
  SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
  SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("PortBuilder|BUILDING","PortMiner|MINING","PortFarmer|FARMING","PortFisher|FISHING"));
  SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
  ClientTickEvents.END_CLIENT_TICK.register(this::client);
  ServerTickEvents.END_SERVER_TICK.register(server->{if(stage!=3 || done || cabin==null)return;try{
   var level=server.overworld(); ++ticks;
   if(ticks==400 && (!Boolean.getBoolean("smartnpc.population.cabin") || Boolean.getBoolean("smartnpc.population.blockingObserver"))){var actor=actors.getFirst();var player=server.getPlayerList().getPlayers().getFirst();player.teleportTo(actor.getX()+1,actor.getY(),actor.getZ());
    player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer>() {
     public Integer getScreenOpeningData(net.minecraft.server.level.ServerPlayer p){return actor.getId();}
     public net.minecraft.network.chat.Component getDisplayName(){return net.minecraft.network.chat.Component.literal("NPC inventory port check");}
     public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id,net.minecraft.world.entity.player.Inventory inventory,net.minecraft.world.entity.player.Player p){return new com.pla.smart_npc.inventory.InventoryViewerMenu(id,inventory,actor.getId());}
    });
   }
   for(var npc:actors){if(!npc.isAlive())throw new AssertionError("Actor died: "+npc.getUsername());
    if(npc.getTarget() instanceof net.minecraft.world.entity.player.Player || npc.getTarget() instanceof PlayerNpcEntity)throw new AssertionError("Unprovoked player/NPC targeting");
    states.add(npc.getUsername()+":"+npc.getCurrentAiState());
   }
   if(ticks%200==0){minMissing=Math.min(minMissing,PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,origin));
    if(Boolean.getBoolean("smartnpc.population.cabin")) {
      var builder=actors.getFirst();var player=server.getPlayerList().getPlayers().getFirst();
      System.out.println("CABIN positions npc="+builder.position()+" box="+builder.getBoundingBox()+" observer="+player.position()+" box="+player.getBoundingBox()+" nav="+builder.getNavigation().isDone());
      for(var block:cabin.blocks())if(!block.optional() && !PlayerNpcBuildMaterialUtil.matches(level.getBlockState(block.toWorld(origin)),block.state()))System.out.println("CABIN missing "+block.toWorld(origin)+" desired="+block.state()+" actual="+level.getBlockState(block.toWorld(origin))+" selfCollision="+new com.pla.smart_npc.entity.ai.PlacingBlockAi(builder).hasSelfPlacementCollision(level,block.toWorld(origin),block.state())+" blockers="+new com.pla.smart_npc.entity.ai.PlacingBlockAi(builder).findBlockingPlacementEntities(level,block.toWorld(origin),block.state()));
    }
    String line="POPULATION ticks="+ticks+" cabinMissing="+minMissing+" "+actors.stream().map(n->n.getUsername()+"="+n.getCurrentAiState()+"/"+n.getCurrentAiDetail()+" "+n.getInventory().getItems()).toList();
    System.out.println(line);
   }
   if(Boolean.getBoolean("smartnpc.population.uiOnly") && ticks>=1000){if(!uiSeen)throw new AssertionError("NPC menu did not render");done=true;server.tickRateManager().setTickRate(20);return;}
   if(Boolean.getBoolean("smartnpc.population.cabin")){
    int missing=PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,origin);
    completeTicks=missing==0?completeTicks+1:0;
    if(cabinReplay && missing!=0)throw new AssertionError("Completed cabin changed across restart: "+missing);
    if(completeTicks>=200){
     String result="PASS all "+cabin.requiredBlocks()+" required cabin blocks match for 200 ticks at tick "+ticks+"; replay="+cabinReplay;
     if(!cabinReplay){
      var state=new java.util.Properties();state.setProperty("origin",origin.getX()+","+origin.getY()+","+origin.getZ());state.setProperty("layout",cabin.id());state.setProperty("actors",String.join(",",actors.stream().map(n->n.getUUID().toString()).toList()));
      try(var writer=Files.newBufferedWriter(Minecraft.getInstance().gameDirectory.toPath().resolve("cabin-state.properties"))){state.store(writer,"Isolated completed cabin fixture");}
      Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("cabin-complete.txt"),result);
     }
     System.out.println("CABIN_COMPLETE "+result);done=true;server.tickRateManager().setTickRate(20);return;
    }
   }
   if(Boolean.getBoolean("smartnpc.population.cabin") && ticks>=16000)throw new AssertionError("Cabin unfinished: "+PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,origin));
   if(ticks>=72000){
    if(!uiSeen)throw new AssertionError("NPC inventory screen did not render");
    if(minMissing>=initialMissing)throw new AssertionError("Ordinary builder made no material-funded construction progress");
    if(states.stream().noneMatch(s->(s.contains("mining") || s.contains("prospecting_ore"))) || states.stream().noneMatch(s->s.contains("fishing")))throw new AssertionError("Expected profession work absent: "+states);
    var out=Minecraft.getInstance().gameDirectory.toPath();
    Files.writeString(out.resolve("population.txt"),"ticks="+ticks+" actors="+actors.size()+" initialMissing="+initialMissing+" minMissing="+minMissing+" states="+states);
    server.tickRateManager().setTickRate(20);done=true;
   }
  }catch(Throwable t){failure=t;done=true;}});
 }
 void client(Minecraft mc){try{
  if(failure!=null)throw new RuntimeException(failure);
  if(!cabinReplay && stage==0 && mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
  if(stage==1 && mc.gui.screen() instanceof CreateWorldScreen screen){stage=2;var ui=screen.getUiState();ui.setName("SmartNpcPopulation");ui.setSeed("20261024005");ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);ui.setAllowCommands(true);var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}
  if(mc.player==null || mc.getSingleplayerServer()==null)return;
  if(cabinReplay && stage==0){if(replayReadyAt==0)replayReadyAt=System.nanoTime();if(System.nanoTime()-replayReadyAt<5_000_000_000L)return;stage=2;}
  mc.getFramerateLimitTracker().onInputReceived();
  if(mc.gui.screen() instanceof com.pla.smart_npc.client.gui.InventoryViewerScreen && ++uiFrames==20){net.minecraft.client.Screenshot.grab(mc,false);uiSeen=true;mc.player.closeContainer();
   if(!Boolean.getBoolean("smartnpc.population.blockingObserver"))mc.getSingleplayerServer().execute(()->{var observer=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();observer.teleportTo(origin.getX()+cabin.width()+8,origin.getY(),origin.getZ()+cabin.depth()+8);});}
  if(stage==2){stage=3;began=System.nanoTime();mc.getSingleplayerServer().execute(()->{try{if(cabinReplay)loadReplay(mc.getSingleplayerServer().overworld());else setup(mc.getSingleplayerServer().overworld());}catch(Throwable t){failure=t;}});}
  if(done){net.minecraft.client.Screenshot.grab(mc,false);Files.writeString(mc.gameDirectory.toPath().resolve(cabinReplay?"replay-complete.txt":"complete.txt"),Boolean.getBoolean("smartnpc.population.cabin") ? "PASS complete cabin: all 73 required blocks match for 200 ticks; ordinary builder; replay="+cabinReplay : Boolean.getBoolean("smartnpc.population.uiOnly") ? "PASS NPC inventory screen over native menu networking" : "PASS three simulated days, four ordinary selectors, peaceful interactions and real building progress; see population receipt for observed scope");stage=4;mc.stop();}
  if(began!=0 && System.nanoTime()-began>1_800_000_000_000L)throw new AssertionError("Population test exceeded 30 minutes");
 }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}}
 void loadReplay(ServerLevel level) throws Exception {
  var state=new java.util.Properties();try(var reader=Files.newBufferedReader(Minecraft.getInstance().gameDirectory.toPath().resolve("cabin-state.properties"))){state.load(reader);}
  String[] xyz=state.getProperty("origin").split(",");origin=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));level.getChunkAt(origin);
  cabin=PlayerNpcBuildLayoutLoader.getLayout(state.getProperty("layout")).orElseThrow();
  for(String id:state.getProperty("actors").split(",")){var npc=(PlayerNpcEntity)level.getEntity(java.util.UUID.fromString(id));if(npc==null)throw new AssertionError("Saved NPC absent: "+id);actors.add(npc);}
  if(!PlayerNpcHomeUtil.getHomeLayoutId(actors.getFirst()).orElse("").equals(cabin.id()))throw new AssertionError("Builder home plan did not survive restart");
  initialMissing=minMissing=PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,origin);if(initialMissing!=0)throw new AssertionError("Saved cabin incomplete: "+initialMissing);
  level.getServer().tickRateManager().setTickRate(60);
 }
 void setup(ServerLevel level){
  var server=level.getServer();var p=server.getPlayerList().getPlayers().getFirst();
  server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"difficulty peaceful");
  p.teleportTo(p.getX(),280,p.getZ());BlockPos base=p.blockPosition();
  for(int x=-42;x<=42;x++)for(int z=-28;z<=28;z++){
   level.setBlockAndUpdate(base.offset(x,-26,z),Blocks.BEDROCK.defaultBlockState());
   for(int y=-25;y<-1;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.STONE.defaultBlockState());
   level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.GRASS_BLOCK.defaultBlockState());
  }
  // A bounded prepared yard prevents actors leaving the prepared yard; deep ground supports native mining.
  for(int y=-25;y<2;y++){
   for(int x=-42;x<=42;x++)for(int z:new int[]{-28,28})level.setBlockAndUpdate(base.offset(x,y,z),Blocks.GLASS.defaultBlockState());
   for(int z=-28;z<=28;z++)for(int x:new int[]{-42,42})level.setBlockAndUpdate(base.offset(x,y,z),Blocks.GLASS.defaultBlockState());
  }
  cabin=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();origin=base.offset(-22,0,-12);
  String[] names=Boolean.getBoolean("smartnpc.population.cabin") ? new String[]{"PortBuilder"} : new String[]{"PortBuilder","PortMiner","PortFarmer","PortFisher"};
  for(int i=0;i<names.length;i++){
   var npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername(names[i]);npc.setPersistenceRequired();npc.setPos(base.getX()-26+i*16,280,base.getZ());
   npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,64));npc.getInventory().addItem(new ItemStack(Items.IRON_PICKAXE));npc.getInventory().addItem(new ItemStack(Items.IRON_AXE));npc.getInventory().addItem(new ItemStack(Items.IRON_HOE));npc.getInventory().addItem(new ItemStack(Items.FISHING_ROD));npc.getInventory().addItem(new ItemStack(Items.OAK_LOG,64));npc.getInventory().addItem(new ItemStack(Items.COBBLESTONE,64));npc.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS,32));npc.getInventory().addItem(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("white_bed"))));npc.getInventory().addItem(new ItemStack(Items.COAL,32));
   if(i==0){for(var item:List.of(Items.OAK_PLANKS,Items.GLASS_PANE,Items.OAK_STAIRS,Items.OAK_SLAB))npc.getInventory().addItem(new ItemStack(item,64));for(var item:List.of(Items.CHEST,Items.FURNACE,Items.CRAFTING_TABLE,Items.OAK_DOOR,Items.TORCH))npc.getInventory().addItem(new ItemStack(item,8));PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(origin,cabin.width(),cabin.depth()));PlayerNpcHomeUtil.setHomeLayoutId(npc,cabin.id());}
   level.addFreshEntity(npc);actors.add(npc);
  }
  for(int x=10;x<=22;x++)for(int z=8;z<=20;z++)level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.STONE.defaultBlockState());
  for(int x=10;x<22;x+=2)level.setBlockAndUpdate(base.offset(x,0,12),Blocks.IRON_ORE.defaultBlockState());
  for(int x=25;x<=39;x++)for(int z=3;z<=22;z++){level.setBlockAndUpdate(base.offset(x,-2,z),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(base.offset(x,-1,z),Blocks.WATER.defaultBlockState());}
  for(int x=-8;x<=8;x+=8)for(int y=0;y<5;y++)level.setBlockAndUpdate(base.offset(x,y,-18),Blocks.OAK_LOG.defaultBlockState());
  if(Boolean.getBoolean("smartnpc.population.cabin")){
   try {var method=com.pla.smart_npc.entity.goal.BuildHouseGoal.class.getDeclaredMethod("canUsePlacementClearanceStand",ServerLevel.class,BlockPos.class,BlockPos.class,net.minecraft.world.level.block.state.BlockState.class);method.setAccessible(true);
    boolean safe=(boolean)method.invoke(new com.pla.smart_npc.entity.goal.BuildHouseGoal(actors.getFirst()),level,base.offset(-23,0,0),base.offset(-24,1,0),Blocks.OAK_PLANKS.defaultBlockState());
    if(!safe)throw new AssertionError("Standing clearance incorrectly rejects an unobstructed solid floor");
    // A real neighbouring solid wall must still reject the same body volume.
    BlockPos blockedStand=base.offset(-23,0,2);level.setBlockAndUpdate(blockedStand,Blocks.STONE.defaultBlockState());
    boolean wall=(boolean)method.invoke(new com.pla.smart_npc.entity.goal.BuildHouseGoal(actors.getFirst()),level,blockedStand,base.offset(-24,1,2),Blocks.OAK_PLANKS.defaultBlockState());
    level.setBlockAndUpdate(blockedStand,Blocks.AIR.defaultBlockState());
    if(wall)throw new AssertionError("Standing clearance accepted a solid wall");
    System.out.println("CABIN clearance floor accepted; solid wall rejected");
   }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
  }
  initialMissing=minMissing=PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,origin);
  server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"time set day");server.tickRateManager().setTickRate(200);
 }
}

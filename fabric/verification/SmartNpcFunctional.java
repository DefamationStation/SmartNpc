import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.util.ProblemReporter;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.network.*;
import com.pla.smart_npc.client.gui.SmartNpcInspectorOverlay;
import java.nio.file.*;
import java.util.UUID;
public class SmartNpcFunctional implements ClientModInitializer {
 com.pla.smart_npc.fabric.NpcBowAttackGoal bowGoal; PlayerNpcEntity bowNpc; net.minecraft.world.entity.Mob bowTarget; int bowTicks; volatile boolean bowPassed;
 int stage,step;long start,readyAt;volatile int target=-1;volatile Throwable failure;boolean replay=Boolean.getBoolean("smartnpcsmoke.replay");
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);
 net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{if(bowGoal==null)return;try{
  bowGoal.tick();
  if(++bowTicks==120){
   check(bowNpc.getInventory().countItem(net.minecraft.world.item.Items.ARROW)<32,"bow controller fired ammunition");
   check(bowNpc.getMainHandItem().getDamageValue()>0,"bow durability consumed");
   bowGoal.stop();bowGoal=null;bowNpc.discard();bowTarget.discard();bowPassed=true;
  }
 }catch(Throwable t){failure=t;bowGoal=null;}});
 }
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("SMARTNPC_PASS "+name);}
 void server(Minecraft mc,Runnable action){mc.getSingleplayerServer().execute(()->{try{action.run();}catch(Throwable t){failure=t;}});}
 void tick(Minecraft mc){try{
  if(failure!=null)throw new RuntimeException(failure);
  if(!replay && stage==0 && mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
  if(stage==1 && mc.gui.screen() instanceof CreateWorldScreen screen){
   stage=2;var s=screen.getUiState();s.setName("SmartNpcFunctional");s.setSeed("60020261004");s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);s.setAllowCommands(true);s.setBonusChest(false);
   var method=CreateWorldScreen.class.getDeclaredMethod("onCreate");method.setAccessible(true);method.invoke(screen);
  }
  if(mc.player==null || mc.getSingleplayerServer()==null)return;
  if(readyAt==0)readyAt=System.nanoTime();
  if(System.nanoTime()-readyAt<5_000_000_000L)return;
  if(replay && stage==0)stage=2;
  var s=mc.getSingleplayerServer();var file=mc.gameDirectory.toPath().resolve("npc-uuid.txt");
  if(stage==2){stage=3;start=System.nanoTime();server(mc,()->{try{
   var p=s.getPlayerList().getPlayers().getFirst();var level=p.level();PlayerNpcEntity npc;
   if(replay){npc=(PlayerNpcEntity)level.getEntity(UUID.fromString(Files.readString(file)));check(npc!=null,"saved NPC reloaded");check(PersistentData.get(npc).getStringOr("PortTest","").equals("retained"),"NPC attachment survived world restart");check(npc.getStoredExperience()==37,"NPC XP survived world restart");}
   else {
    s.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"summon smart_npc:player_npc ~ ~ ~4 {NoAI:1b}");
    npc=level.getEntitiesOfClass(PlayerNpcEntity.class,p.getBoundingBox().inflate(8)).stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElseThrow();
    npc.setNoAi(true);npc.setPersistenceRequired();PersistentData.get(npc).putString("PortTest","retained");npc.awardStoredExperience(37);
    Files.writeString(file,npc.getUUID().toString());
    var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());npc.saveWithoutId(output);
    var clone=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.LOAD);
    clone.load(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),output.buildResult()));
    check(PersistentData.get(clone).getStringOr("PortTest","").equals("retained"),"NPC attachment round trip");check(clone.getStoredExperience()==37,"NPC XP round trip");
    var pos=p.blockPosition().offset(2,0,0);level.setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());var furnace=(FurnaceBlockEntity)level.getBlockEntity(pos);PersistentData.get(furnace).putString("PortOwner","retained");furnace.setChanged();
    var fresh=new FurnaceBlockEntity(pos,Blocks.FURNACE.defaultBlockState());fresh.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),furnace.saveWithFullMetadata(level.registryAccess())));
    check(PersistentData.get(fresh).getStringOr("PortOwner","").equals("retained"),"furnace ownership round trip");
   }
   var tickets=com.pla.smart_npc.util.PlayerNpcForceTickManager.PLAYER_NPC_TICKET;
   var chunk=new net.minecraft.world.level.ChunkPos(p.chunkPosition().x()+20,p.chunkPosition().z()+20);
   var a=UUID.randomUUID();var b=UUID.randomUUID();
   var manager=((com.pla.smart_npc.mixin.ServerChunkCacheAccessor)level.getChunkSource()).smartNpc$getDistanceManager();
   var storage=((com.pla.smart_npc.mixin.DistanceManagerAccessor)manager).smartNpc$getTicketStorage();
   var entries=((com.pla.smart_npc.mixin.TicketStorageAccessor)storage).smartNpc$getTickets();
   java.util.function.BooleanSupplier hasTicket=()->entries.getOrDefault(chunk.pack(),java.util.List.of()).stream().anyMatch(t->t.getType()==com.pla.smart_npc.fabric.NpcTickets.TYPE);
   tickets.forceChunk(level,a,chunk.x(),chunk.z(),true,false);tickets.forceChunk(level,b,chunk.x(),chunk.z(),true,false);
   check(hasTicket.getAsBoolean(),"NPC chunk ticket installed");
   tickets.forceChunk(level,a,chunk.x(),chunk.z(),false,false);check(hasTicket.getAsBoolean(),"shared ticket survives first owner release");
   tickets.forceChunk(level,b,chunk.x(),chunk.z(),false,false);check(!hasTicket.getAsBoolean(),"ticket removed after last owner release");
   bowNpc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
   var fighter=com.pla.smart_npc.config.SmartNpcNamesConfig.getPlayerNpcNameEntries().stream().map(com.pla.smart_npc.config.SmartNpcNamesConfig::parseNameEntry).flatMap(java.util.Optional::stream).filter(n->!n.interests().contains(com.pla.smart_npc.clazz.PlayerNpcInterest.CAUTIOUS)).findFirst().orElseThrow();
   bowNpc.setUsername(fighter.skinName());
   bowNpc.setPos(p.getX(),300,p.getZ());bowNpc.setNoAi(true);bowNpc.setNoGravity(true);
   bowNpc.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
   bowNpc.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW,32));
   level.addFreshEntity(bowNpc);
   bowTarget=net.minecraft.world.entity.EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
   bowTarget.setPos(p.getX()+8,300,p.getZ());bowTarget.setNoAi(true);bowTarget.setNoGravity(true);level.addFreshEntity(bowTarget);
   bowNpc.setTarget(bowTarget);bowGoal=new com.pla.smart_npc.fabric.NpcBowAttackGoal(bowNpc,1.0,20,16);bowGoal.start();
   target=npc.getId();s.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"give @s smart_npc:player_npc_inspector");
   s.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"tp @s ~ ~ ~ 0 0");
   check(s.getCommands().getDispatcher().getRoot().getChildren().stream().anyMatch(n->n.getName().contains("npc")),"NPC commands registered");
  }catch(Throwable t){failure=t;}});}
  if(stage!=3 || target<0)return;
  double elapsed=(System.nanoTime()-start)/1e9;
  if(step==0 && elapsed>5){step++;ClientPlayNetworking.send(new PlayerNpcInspectorRequestPacket(target,true));}
  if(step==1 && elapsed>8){step++;var f=SmartNpcInspectorOverlay.class.getDeclaredField("inspectedEntityId");f.setAccessible(true);check(f.getInt(null)==target,"inspector request and response over Fabric networking");net.minecraft.client.Screenshot.grab(mc,false);var method=SmartNpcInspectorOverlay.class.getDeclaredMethod("startInspectator",Minecraft.class,PlayerNpcEntity.class);method.setAccessible(true);method.invoke(null,mc,(PlayerNpcEntity)mc.level.getEntity(target));}
  if(step==2 && elapsed>12){step++;check(SmartNpcInspectorOverlay.isInspectatorActive(),"spectator client active");server(mc,()->check(PlayerNpcInspectatorModePacket.isInspectatorActive(s.getPlayerList().getPlayers().getFirst()),"spectator server active"));net.minecraft.client.Screenshot.grab(mc,false);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);}
  if(step==3 && elapsed>16){step++;net.minecraft.client.Screenshot.grab(mc,false);var method=SmartNpcInspectorOverlay.class.getDeclaredMethod("stopInspectator",Minecraft.class,boolean.class);method.setAccessible(true);method.invoke(null,mc,true);}
  if(step==4 && elapsed>20){step++;check(!SmartNpcInspectorOverlay.isInspectatorActive(),"spectator client restored");server(mc,()->check(!PlayerNpcInspectatorModePacket.isInspectatorActive(s.getPlayerList().getPlayers().getFirst()),"spectator server restored"));}
  if(elapsed>25){check(bowPassed,"bow controller completed");Files.writeString(mc.gameDirectory.toPath().resolve(replay?"replay-complete.txt":"complete.txt"),"All functional assertions passed");stage=4;mc.stop();}
 }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}}
}

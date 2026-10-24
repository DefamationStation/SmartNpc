import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import java.nio.file.*;
public class SmartNpcCookingRecovery implements ClientModInitializer {
 int stage,ticks;volatile Throwable failure;volatile boolean done;PlayerNpcEntity npc;CookFoodGoal cook;
 public void onInitializeClient(){com.pla.smart_npc.config.SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
  ClientTickEvents.END_CLIENT_TICK.register(this::client);
  ServerTickEvents.END_SERVER_TICK.register(s->{if(stage!=3||npc==null||done)return;try{
   ticks++;
   if(ticks%100==0)System.out.println("RECOVERY ticks="+ticks+" pos="+npc.position()+" ai="+npc.getCurrentAiState()+" detail="+npc.getCurrentAiDetail()+" cooking="+SurvivalTasks.memory(npc).cookingStatus+" cooldown="+npc.getCookFoodCooldown()+" inventory="+npc.getInventory().getItems()+" mode="+field(cook,"mode")+" furnace="+field(cook,"furnacePos")+" stand="+field(cook,"furnaceStandPos"));
   if(npc.getInventory().countItem(Items.COOKED_COD)>0){if(npc.getInventory().countItem(Items.COD)!=0)throw new AssertionError("Raw fish duplicated");done=true;s.tickRateManager().setTickRate(20);}
   if(ticks>=1600&&!done){
    var level=npc.level();var defer=CookFoodGoal.class.getDeclaredMethod("shouldDeferForPrimarySupply",ServerLevel.class);defer.setAccessible(true);System.out.println("RECOVERY defer="+defer.invoke(cook,level)+" cooldown="+npc.getCookFoodCooldown());var method=CookFoodGoal.class.getDeclaredMethod("findTemporaryFurnacePlacement",ServerLevel.class);method.setAccessible(true);var pos=(BlockPos)method.invoke(cook,level);
    var standMethod=CookFoodGoal.class.getDeclaredMethod("findFurnaceStand",ServerLevel.class,BlockPos.class);standMethod.setAccessible(true);var stand=pos==null?null:(BlockPos)standMethod.invoke(cook,level,pos);
    System.out.println("RECOVERY final placement="+pos+" stand="+stand);
    if(stand!=null)for(float budget:new float[]{.01F,.05F,.1F}){var path=com.pla.smart_npc.entity.ai.PathNavigationAi.createBoundedPath(npc,stand,budget);System.out.println("RECOVERY path budget="+budget+" result="+(path==null?"null":("reaches="+path.canReach()+" end="+path.getEndNode()+" nodes="+path.getNodeCount())));}
    throw new AssertionError("Table-edge cooking did not finish");
   }
  }catch(Throwable t){failure=t;}});
 }
 static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
 void client(Minecraft mc){try{
  if(failure!=null)throw new RuntimeException(failure);
  if(stage==0&&mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
  if(stage==1&&mc.gui.screen() instanceof CreateWorldScreen screen){stage=2;var ui=screen.getUiState();ui.setName("SmartNpcCookingRecovery");ui.setSeed("20261024005");ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);ui.setAllowCommands(true);var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}
  if(stage==2&&mc.player!=null&&mc.getSingleplayerServer()!=null){stage=3;mc.getSingleplayerServer().execute(()->{try{setup(mc.getSingleplayerServer().overworld());}catch(Throwable t){failure=t;}});}
  if(done){Files.writeString(mc.gameDirectory.toPath().resolve("complete.txt"),"PASS NPC recovers from table edge and cooks its real fish with native furnace work");stage=4;mc.stop();}
 }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception e){}stage=4;mc.stop();}}
 void setup(ServerLevel level)throws Exception{
  var player=level.getServer().getPlayerList().getPlayers().getFirst();var base=new BlockPos(39,285,57);
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){level.getChunk(base.offset(x,0,z));level.setBlockAndUpdate(base.offset(x,-1,z),(x>=2&&x<=8&&z>=-7&&z<=7?Blocks.WATER:Blocks.BEDROCK).defaultBlockState());for(int y=0;y<4;y++)level.setBlockAndUpdate(base.offset(x,y,z),Blocks.AIR.defaultBlockState());}
  level.setBlockAndUpdate(base,Blocks.CRAFTING_TABLE.defaultBlockState());
  npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("Technoblade");npc.setPersistenceRequired();npc.setPos(base.getX()+1.03,base.getY()+1,base.getZ()+.35);npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
  npc.getRandom().setSeed(90241005L);npc.getInventory().addItem(new ItemStack(Items.OAK_PLANKS,2));npc.getInventory().addItem(new ItemStack(Items.FISHING_ROD));npc.getInventory().addItem(new ItemStack(Items.STICK));npc.getInventory().addItem(new ItemStack(Items.COD));npc.getInventory().addItem(new ItemStack(Items.FURNACE));npc.getInventory().addItem(new ItemStack(Items.COAL));level.addFreshEntity(npc);
  var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);var goals=(GoalSelector)f.get(npc);
  goals.removeAllGoals(g->{Goal n=g;while(n instanceof StartupWorkGatedGoal w)n=w.getDelegateGoal();return !(n instanceof CookFoodGoal)&&!(n instanceof com.pla.smart_npc.fabric.survival.CookingCraftGoal)&&!(n instanceof PickupNearbyItemGoal)&&!(n instanceof FloatGoal);});
  cook=(CookFoodGoal)goals.getAvailableGoals().stream().map(w->{Goal g=w.getGoal();while(g instanceof StartupWorkGatedGoal q)g=q.getDelegateGoal();return g;}).filter(g->g instanceof CookFoodGoal).findFirst().orElseThrow();
  f=Mob.class.getDeclaredField("targetSelector");f.setAccessible(true);((GoalSelector)f.get(npc)).removeAllGoals(g->true);
  level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(),"time set 6000");level.getServer().tickRateManager().setTickRate(60);
 }
}

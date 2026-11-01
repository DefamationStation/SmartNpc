import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.Events;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.fabric.survival.progression.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import java.nio.file.*;
import java.util.*;

/** Fresh declared furnished cabin and raw iron; production AI alone must discover
 * real coal, plan fuel, mine/pick it up, use the empty owned furnace and collect
 * actual iron. No task commands, observer calls, events, goal calls or later grants.
 * Uses pre-existing public APIs so the old artifact can expose the missing intent. */
public final class SmartNpcAutonomousSmelting implements DedicatedServerModInitializer {
    static final UUID ID=new UUID(0x736d656c74696e67L,0x2d7265736964656eL);
    static final BlockPos ORIGIN=new BlockPos(-3,279,-2);
    static final List<BlockPos> COAL=List.of(new BlockPos(2,280,-4),new BlockPos(2,280,-5));
    static final AABB YARD=new AABB(-13,278,-13,14,287,14);
    final boolean replay=Boolean.getBoolean("journey.replay");
    PlayerNpcEntity npc;PlayerNpcBuildLayout cabin;BlockPos furnacePos;
    boolean done,sawAutomatic,sawDiscovery,sawCoalBreak,sawCoalPickup,sawFuel,sawRawLoaded,sawOutput,sawProduced;
    boolean fuelCheckpoint,loadedCheckpoint;int ticks;String checkpointStage="";
    public void onInitializeServer(){
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("SmeltResident|BUILDING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new Events.ModConfigEvent.Loading(new Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{if(done){Thread owner=Thread.currentThread();new Thread(()->{try{owner.join();System.exit(0);}catch(Exception ignored){}}).start();}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(done)return;try{tick(s);}catch(Throwable error){error.printStackTrace();finish(s,"failure.txt",error.toString());}});
    }
    void tick(MinecraftServer server)throws Exception{
        ticks++;server.tickRateManager().setTickRate(Integer.getInteger("journey.tickRate",100));var level=server.overworld();
        if(npc==null){
            cabin=PlayerNpcBuildLayoutLoader.getLayouts().stream().filter(l->l.id().contains("starter_survival_cabin")).findFirst().orElseThrow();
            furnacePos=cabin.blocks().stream().filter(b->b.state().is(Blocks.FURNACE)).findFirst().orElseThrow().toWorld(ORIGIN);
            if(!replay){setup(server,level);return;}
            npc=(PlayerNpcEntity)level.getEntity(ID);if(npc==null){check(ticks<2000,"saved original smelting actor missing");return;}
            loadLedger();var m=SurvivalTasks.memory(npc);var f=furnace(level);
            check(PlayerNpcHomeUtil.getHomeLayoutId(npc).orElse("").equals(cabin.id()),"owned furnace blueprint lost on reload");
            if(checkpointStage.equals("fuel"))check(m.active&&m.automatic&&m.cookingActive,"automatic pending fuel intent lost on native replay");
            else check(checkpointStage.equals("loaded")&&f.getItem(0).is(Items.RAW_IRON)&&f.getItem(2).isEmpty(),"native loaded-ore checkpoint lost or output invented on replay");
            System.out.println("SMELT_RESUMED stage="+checkpointStage+" uuid="+npc.getUUID()+" pos="+npc.position()+" task="+SurvivalTasks.describe(npc));
        }
        check(npc.isAlive()&&!npc.isNoAi()&&goals()==81,"ordinary enabled 81-goal resident changed");
        check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,ORIGIN)==0,"smelting task damaged furnished owned cabin");
        check(npc.getInventory().countItem(Items.COOKED_BEEF)==16,"declared food reserve was changed by raw-iron task");
        check(!NpcProgression.snapshot(npc).milestones().contains(PersonalProgress.Milestone.FOOD_PREPARED),"iron collection incorrectly earned FOOD_PREPARED");
        check(ironUnits(level)==18,"two declared raw irons lost or duplicated; ninth-ingot units="+ironUnits(level));
        var m=SurvivalTasks.memory(npc);var f=furnace(level);
        if(m.coal.stream().anyMatch(o->COAL.stream().anyMatch(p->p.asLong()==o.position())&&o.dimension().equals(SurvivalTasks.dimension(npc))))sawDiscovery=true;
        if(m.active&&m.automatic){check(m.cookingActive,"automatic fuel lacks native processing intention");sawAutomatic=true;}
        for(var p:COAL)if(!level.getBlockState(p).is(Blocks.COAL_ORE)){check(level.getBlockState(p).isAir(),"declared coal replaced instead of harvested");sawCoalBreak=true;}
        if(countCarried(Items.COAL)>0){check(sawCoalBreak,"carried coal appeared before actual ore break");sawCoalPickup=true;}
        boolean lit=level.getBlockState(furnacePos).getValue(BlockStateProperties.LIT);
        if(f.getItem(1).is(Items.COAL)||lit){check(sawCoalBreak&&sawCoalPickup,"furnace fuel appeared without native coal harvest and pickup");sawFuel=true;}
        if(f.getItem(0).is(Items.RAW_IRON)){check(sawAutomatic,"raw ore loaded without observed automatic processing/fuel intent");sawRawLoaded=true;}
        // Native furnace and entity ticks may create and collect an output between
        // fixture samples. Actual carried/stored ingot growth plus the real transfer
        // callback is also valid output evidence; no synthetic event is dispatched.
        if(f.getItem(2).is(Items.IRON_INGOT)||collectedIronUnits(level)>0&&m.cookingProduced){check(sawFuel&&sawRawLoaded,"iron output lacks actual loaded ore/fuel");sawOutput=true;}
        sawProduced|=m.cookingProduced;
        if(!fuelCheckpoint&&sawAutomatic){
            check(sawDiscovery,"automatic fuel intent preceded real production coal observation");
            check(!sawCoalBreak&&f.getItem(0).isEmpty()&&f.getItem(1).isEmpty()&&f.getItem(2).isEmpty(),"pending-fuel checkpoint missed the real empty-furnace pre-harvest state");
            fuelCheckpoint=true;checkpointStage="fuel";saveLedger();finish(server,"checkpoint.txt","PASS actual raw iron plus empty usable owned furnace caused autonomous fuel intent after production discovery of visible coal; native original actor and pending intention saved with81goals; no request or memory injection");return;
        }
        if(fuelCheckpoint&&!loadedCheckpoint&&sawRawLoaded&&sawFuel&&f.getItem(0).is(Items.RAW_IRON)&&f.getItem(2).isEmpty()){
            check(sawCoalBreak&&sawCoalPickup,"loaded checkpoint lacks native coal harvest/pickup");
            loadedCheckpoint=true;checkpointStage="loaded";saveLedger();finish(server,"return-checkpoint.txt","PASS native coal mining/pickup and actual fuel/raw-iron insertion into owned furnace; native processing saved before any output, no inventory/furnace injection after setup");return;
        }
        if(loadedCheckpoint&&sawOutput&&f.getItem(0).isEmpty()&&f.getItem(2).isEmpty()&&rawIronUnits(level)==0&&collectedIronUnits(level)==18){
            check(sawAutomatic&&sawDiscovery&&sawCoalBreak&&sawCoalPickup&&sawFuel&&sawRawLoaded,"processing chain missing independent native observations");
            check(sawProduced&&m.cookingProduced,"actual iron output transfer did not mark native intention produced");
            check(countCarried(Items.STONE_PICKAXE)==1&&pickDamage()>0,"coal mining did not retain/use the real stone pickaxe");
            finish(server,"complete.txt","PASS raw iron2 autonomously caused real visible-coal discovery, automatic fuel intention, native coal break/pickup, native owned-furnace loading/burning/smelting and actual output collection; pending-fuel and loaded-ore process replays retain original UUID/81goals and exactly2Fe; food16 unchanged, cookingProduced=true, FOOD_PREPARED absent; initial furnished geometry/materials declared");return;
        }
        if(ticks%100==0)System.out.println("SMELT_PROGRESS tick="+ticks+" pos="+npc.position()+" ai="+npc.getCurrentAiState()+" detail="+npc.getCurrentAiDetail()+" active="+m.active+" auto="+m.automatic+" cooking="+m.cookingActive+"/"+m.cookingStep+" produced="+m.cookingProduced+" status="+m.cookingStatus+" coalObservations="+m.coal+" furnace="+f.getItem(0)+"/"+f.getItem(1)+"/"+f.getItem(2)+" lit="+lit+" inventory="+npc.getInventory().getItems());
        check(sawAutomatic||ticks<500,"raw iron with real usable furnace and visible discoverable coal never formed an automatic fuel intent within500ticks; cooking="+m.cookingActive+"/"+m.cookingStatus+" coal="+m.coal);
        check(ticks<Integer.getInteger("journey.maxTicks",8000),"native automatic smelting did not finish; "+SurvivalTasks.describe(npc));
    }
    void setup(MinecraftServer server,ServerLevel level)throws Exception{
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,279,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=280;y<=286;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            if(Math.abs(x)==12||Math.abs(z)==12)for(int y=280;y<=284;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.GLASS.defaultBlockState());
        }
        for(var block:cabin.blocks())level.setBlockAndUpdate(block.toWorld(ORIGIN),block.state());
        check(PlayerNpcBuildLayoutLoader.countMissingRequired(level,cabin,ORIGIN)==0,"declared furnished cabin incomplete");
        for(var block:cabin.blocks())if(level.getBlockEntity(block.toWorld(ORIGIN)) instanceof Container c)c.clearContent();
        for(var p:COAL)level.setBlockAndUpdate(p,Blocks.COAL_ORE.defaultBlockState());
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("SmeltResident");npc.setUUID(ID);npc.setPersistenceRequired();npc.setPos(-1.5,280,-3.5);npc.getRandom().setSeed(0);
        npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,16));npc.getInventory().addItem(new ItemStack(Items.RAW_IRON,2));npc.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        PlayerNpcHomeUtil.setHome(npc,new PlayerNpcHomeUtil.HomeArea(ORIGIN,cabin.width(),cabin.depth()),cabin.id());
        cabin.blocks().stream().filter(b->b.state().is(Blocks.CHEST)).findFirst().ifPresent(b->npc.setOwnedChestPos(b.toWorld(ORIGIN)));
        level.addFreshEntity(npc);
        check(goals()==81&&npc.tickCount==0&&!npc.isNoAi(),"initial ordinary AI fixture changed");
        check(SurvivalTasks.trackedFurnace(npc,level)==furnace(level),"declared usable blueprint furnace not naturally discoverable");
        check(SurvivalTasks.memory(npc).coal.isEmpty()&&!SurvivalTasks.memory(npc).active&&!SurvivalTasks.memory(npc).cookingActive,"initial resource task/memory must be empty");
        for(var p:COAL)check(SurvivalTasks.visible(npc,p),"declared initial coal is not actually visible");
        check(ironUnits(level)==18&&countCarried(Items.COAL)==0&&furnace(level).isEmpty(),"declared initial iron/fuel/furnace contents wrong");
        check(!NpcProgression.snapshot(npc).milestones().contains(PersonalProgress.Milestone.FOOD_PREPARED),"fixture spawned with unearned food milestone");
        Files.writeString(Path.of("setup.txt"),"Declared fresh supported y280 arena and fully furnished owned cabin with empty usable blueprint furnace, RAW_IRON2/cookedbeef16/stonepick only, real visible coal ore2; no wood/fuel, injected resource observations, synthetic work, task requests or later supplies");
    }
    FurnaceBlockEntity furnace(ServerLevel level){check(level.getBlockEntity(furnacePos) instanceof FurnaceBlockEntity,"owned native furnace missing");return (FurnaceBlockEntity)level.getBlockEntity(furnacePos);}
    Set<ItemStack> carried(){Set<ItemStack> result=Collections.newSetFromMap(new IdentityHashMap<>());result.addAll(npc.getInventory().getItems());for(var slot:EquipmentSlot.values())result.add(npc.getItemBySlot(slot));return result;}
    int countCarried(Item item){return carried().stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    int pickDamage(){return carried().stream().filter(s->s.is(Items.STONE_PICKAXE)).mapToInt(ItemStack::getDamageValue).sum();}
    int fe(ItemStack s){return (s.is(Items.RAW_IRON)||s.is(Items.IRON_INGOT))?s.getCount()*9:s.is(Items.SHEARS)?s.getCount()*18:s.is(Items.IRON_NUGGET)?s.getCount():s.is(Items.IRON_BLOCK)?s.getCount()*81:0;}
    int raw(ItemStack s){return s.is(Items.RAW_IRON)?s.getCount()*9:0;}
    List<ItemStack> stored(ServerLevel level,boolean includeFurnace){var result=new ArrayList<ItemStack>();for(var block:cabin.blocks()){var be=level.getBlockEntity(block.toWorld(ORIGIN));if(be instanceof Container c&&(includeFurnace||!(be instanceof FurnaceBlockEntity)))for(int i=0;i<c.getContainerSize();i++)result.add(c.getItem(i));}return result;}
    int ironUnits(ServerLevel level){return carried().stream().mapToInt(this::fe).sum()+stored(level,true).stream().mapToInt(this::fe).sum()+level.getEntitiesOfClass(ItemEntity.class,YARD).stream().mapToInt(e->fe(e.getItem())).sum();}
    int rawIronUnits(ServerLevel level){return carried().stream().mapToInt(this::raw).sum()+stored(level,true).stream().mapToInt(this::raw).sum()+level.getEntitiesOfClass(ItemEntity.class,YARD).stream().mapToInt(e->raw(e.getItem())).sum();}
    int collectedIronUnits(ServerLevel level){return carried().stream().filter(s->!s.is(Items.RAW_IRON)).mapToInt(this::fe).sum()+stored(level,false).stream().filter(s->!s.is(Items.RAW_IRON)).mapToInt(this::fe).sum();}
    int goals()throws Exception{var field=Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);return ((GoalSelector)field.get(npc)).getAvailableGoals().size();}
    void saveLedger()throws Exception{var p=new Properties();p.setProperty("uuid",ID.toString());p.setProperty("stage",checkpointStage);p.setProperty("automatic",Boolean.toString(sawAutomatic));p.setProperty("discovery",Boolean.toString(sawDiscovery));p.setProperty("coalBreak",Boolean.toString(sawCoalBreak));p.setProperty("coalPickup",Boolean.toString(sawCoalPickup));p.setProperty("fuel",Boolean.toString(sawFuel));p.setProperty("rawLoaded",Boolean.toString(sawRawLoaded));p.setProperty("output",Boolean.toString(sawOutput));p.setProperty("produced",Boolean.toString(sawProduced));try(var out=Files.newOutputStream(Path.of("actor.properties"))){p.store(out,"read-only observations of original native autonomous smelting actor");}}
    void loadLedger()throws Exception{var p=new Properties();try(var in=Files.newInputStream(Path.of("actor.properties"))){p.load(in);}check(ID.toString().equals(p.getProperty("uuid")),"saved smelting identity changed");checkpointStage=p.getProperty("stage");fuelCheckpoint=true;loadedCheckpoint=checkpointStage.equals("loaded");sawAutomatic=Boolean.parseBoolean(p.getProperty("automatic"));sawDiscovery=Boolean.parseBoolean(p.getProperty("discovery"));sawCoalBreak=Boolean.parseBoolean(p.getProperty("coalBreak"));sawCoalPickup=Boolean.parseBoolean(p.getProperty("coalPickup"));sawFuel=Boolean.parseBoolean(p.getProperty("fuel"));sawRawLoaded=Boolean.parseBoolean(p.getProperty("rawLoaded"));sawOutput=Boolean.parseBoolean(p.getProperty("output"));sawProduced=Boolean.parseBoolean(p.getProperty("produced"));}
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    void finish(MinecraftServer server,String marker,String message){done=true;try{Files.writeString(Path.of(marker),message);}catch(Exception error){throw new RuntimeException(error);}server.halt(false);}
}

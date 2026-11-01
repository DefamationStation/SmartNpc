import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import com.pla.smart_npc.config.*;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.memory.*;
import com.pla.smart_npc.fabric.survival.relationships.*;
import com.pla.smart_npc.init.SmartNpcModEntities;
import java.nio.file.*;
import java.util.*;

/** Actual local client interaction packet, real ServerPlayer inventory, and saved-world replay. */
public class SmartNpcFoodOfferClient implements ClientModInitializer {
    final boolean replay=Boolean.getBoolean("smartnpc.foodoffer.replay");
    final UUID npcId=new UUID(0x666f6f6467696674L,1);
    volatile int stage,target=-1,serverTicks;volatile boolean done;volatile Throwable failure;
    long readyAt;int inputTicks;boolean clicked;PlayerNpcEntity npc;
    public void onInitializeClient(){
        NpcMemories.register();NpcFoodOffers.register();FoodOfferSettings.ENABLED.set(true);
        MemorySettings.MEMORY_ENABLED.set(true);MemorySettings.EXTRA_DEPARTURE_FOOD.set(2);MemorySettings.CAUTION.set(100);
        SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);SmartNpcConfig.FORCE_TICK_MANAGE.set(1);
        SmartNpcNamesConfig.PLAYER_NPC_NAMES.set(List.of("FoodReceiver|EXPLORING|CAUTIOUS"));
        SmartNpcNamesConfig.onConfigLoading(new com.pla.smart_npc.fabric.Events.ModConfigEvent.Loading(new com.pla.smart_npc.fabric.Events.ModConfig(SmartNpcNamesConfig.SPEC)));
        ClientTickEvents.END_CLIENT_TICK.register(this::client);
        ServerTickEvents.END_SERVER_TICK.register(s->{if(stage!=3||done||failure!=null)return;try{server(s);}catch(Throwable t){failure=t;}});
    }
    void setup(MinecraftServer server)throws Exception{
        var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SURVIVAL);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"difficulty peaceful");
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
            level.getChunk(x>>4,z>>4);level.setBlockAndUpdate(new BlockPos(x,259,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=260;y<=263;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        player.teleportTo(.5,260,2.5);player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(Items.COOKED_BEEF,3));player.containerMenu.broadcastChanges();
        npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);npc.setUsername("FoodReceiver");npc.setUUID(npcId);npc.setPersistenceRequired();npc.setPos(.5,260,.5);
        npc.getInventory().clearContent();for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
        npc.getInventory().addItem(new ItemStack(Items.COOKED_BEEF,2));level.addFreshEntity(npc);target=npc.getId();
        check(NpcMemories.observations(npc).stream().noneMatch(e->e.kind()==ObservedMemory.Kind.HELP),"proximity manufactured help");
    }
    void server(MinecraftServer server)throws Exception{
        if(++serverTicks>400)throw new AssertionError("native client gift deadline");
        var level=server.overworld();if(server.getPlayerList().getPlayers().isEmpty())return;var player=server.getPlayerList().getPlayers().getFirst();
        if(npc==null){
            if(replay){npc=(PlayerNpcEntity)level.getEntity(npcId);if(npc==null)return;}
            else{setup(server);return;}
        }
        if(replay){
            var ledger=new Properties();try(var in=Files.newInputStream(Path.of("food-offer-actor.properties"))){ledger.load(in);}
            check(player.getUUID().toString().equals(ledger.getProperty("actor")),"real saved player identity changed");
            check(player.getInventory().countItem(Items.COOKED_BEEF)==2&&npc.getInventory().countItem(Items.COOKED_BEEF)==3,"real player/NPC inventories did not persist");
            verify(player.getUUID());check(!FoodOfferLedger.load(PersistentData.get(npc).getCompoundOrEmpty(NpcFoodOffers.KEY)).ready(player.getUUID(),level.getGameTime(),200,20),"accepted pair cooldown lost on reload");
            check(goals()==80,"reloaded ordinary goals changed");done=true;return;
        }
        var help=NpcMemories.observations(npc).stream().filter(e->e.kind()==ObservedMemory.Kind.HELP).toList();if(help.isEmpty())return;
        verify(player.getUUID());check(player.getInventory().countItem(Items.COOKED_BEEF)==2&&npc.getInventory().countItem(Items.COOKED_BEEF)==3,"native packet transfer did not conserve exactly one food");
        check(goals()==80,"normal goals changed");var ledger=new Properties();ledger.setProperty("actor",player.getUUID().toString());
        try(var out=Files.newOutputStream(Path.of("food-offer-actor.properties"))){ledger.store(out,"actual connected player source");}done=true;
    }
    void verify(UUID actor){
        check(NpcMemories.observations(npc).stream().anyMatch(e->e.kind()==ObservedMemory.Kind.HELP&&actor.equals(e.subject())&&e.source()==ObservedMemory.Source.ACTUAL_HANDOFF),"native offer lacks exact connected actor memory");
        var state=RelationshipCodec.load(PersistentData.get(npc).getCompoundOrEmpty(NpcRelationships.KEY));
        var contact=state.book().contacts().stream().filter(c->c.actor().equals(actor)).findFirst().orElseThrow(()->new AssertionError("native offer lacks exact relationship actor"));
        check(contact.evidence().size()==1,"one food offer multiplied relationship evidence");
        var fact=contact.evidence().getFirst();
        check(fact.kind()==RelationshipBook.Kind.ASSISTANCE&&fact.source()==RelationshipBook.Source.CONSERVED_HANDOFF
                &&fact.resource().equals("minecraft:cooked_beef")&&fact.units()==1,"native relationship handoff facts changed");
        var advice=NpcRelationships.advice(npc,actor);check(advice.trust()==2&&advice.currentEvidence()==1,"native handoff influence changed");
        check(NpcRelationships.advice(npc,new UUID(0x6e6f746769766572L,2)).stance()==RelationshipBook.Stance.UNKNOWN,"unrelated UUID acquired help");
        var receipt=Path.of("relationship-food-seen.txt");
        try{if(replay)check(Long.parseLong(Files.readString(receipt))==fact.seen(),"relationship timestamp changed after process reload");else Files.writeString(receipt,Long.toString(fact.seen()));}catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    int goals()throws Exception{var f=Mob.class.getDeclaredField("goalSelector");f.setAccessible(true);return ((net.minecraft.world.entity.ai.goal.GoalSelector)f.get(npc)).getAvailableGoals().size();}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    void client(Minecraft mc){try{
        if(failure!=null)throw new RuntimeException(failure);
        if(!replay&&stage==0&&mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
        if(stage==1&&mc.gui.screen() instanceof CreateWorldScreen screen){stage=2;var ui=screen.getUiState();ui.setName("SmartNpcFoodOffers");ui.setSeed("20261025077");ui.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);ui.setAllowCommands(true);var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);}
        if(replay&&stage==0&&mc.player!=null)stage=2;
        if(stage==2&&mc.player!=null&&mc.getSingleplayerServer()!=null){if(readyAt==0)readyAt=System.nanoTime();if(System.nanoTime()-readyAt<3_000_000_000L)return;stage=3;}
        if(!replay&&stage==3&&target>=0&&mc.player!=null&&mc.level!=null){
            var entity=mc.level.getEntity(target);if(entity!=null&&mc.player.getMainHandItem().is(Items.COOKED_BEEF)){
                mc.options.keyShift.setDown(true);
                if(++inputTicks>=5&&!clicked){clicked=true;mc.gameMode.interact(mc.player,entity,new net.minecraft.world.phys.EntityHitResult(entity),InteractionHand.MAIN_HAND);}
            }
        }
        if(done){stage=4;mc.options.keyShift.setDown(false);Files.writeString(mc.gameDirectory.toPath().resolve(replay?"replay-complete.txt":"complete.txt"),replay?"PASS saved real connected player and NPC inventories, exact help actor and accepted cooldown after native client process restart":"PASS actual client interact packet conserves one real held food and attributes help only to connected sender; all80ordinary goals retained");mc.stop();}
    }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}}
}

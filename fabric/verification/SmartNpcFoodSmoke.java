import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.*;
import java.nio.file.*;

/** Real native food chain, optionally interrupted between fish pickup and furnace placement. */
public final class SmartNpcFoodSmoke implements ClientModInitializer {
    int stage; long readyAt; volatile Throwable failure; volatile boolean done;
    final boolean replay=Boolean.getBoolean("smartnpc.food.replay");
    final boolean checkpoint=Boolean.getBoolean("smartnpc.food.checkpoint");
    public void onInitializeClient() {
        com.pla.smart_npc.config.SmartNpcConfig.MAX_NATURAL_PLAYER_NPCS.set(0);
        ClientTickEvents.END_CLIENT_TICK.register(this::client);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(stage!=3 || done || failure!=null)return;
            try {
                SmartNpcFoodChainChecks.tick(server);
                if(checkpoint && !replay && SmartNpcFoodChainChecks.readyToSave()) {
                    SmartNpcFoodChainChecks.checkpoint(Minecraft.getInstance().gameDirectory.toPath().resolve("food-ledger.properties"));
                    done=true;
                } else if(SmartNpcFoodChainChecks.passed) done=true;
            } catch(Throwable t){failure=t;}
        });
    }
    void client(Minecraft mc) {
        try {
            if(failure!=null)throw new RuntimeException(failure);
            if(!replay && stage==0 && mc.gui.screen() instanceof TitleScreen){stage=1;CreateWorldScreen.openFresh(mc,()->{});}
            if(stage==1 && mc.gui.screen() instanceof CreateWorldScreen screen){
                stage=2;var ui=screen.getUiState();ui.setName("SmartNpcFood");ui.setSeed("20261024005");ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);ui.setAllowCommands(true);
                var m=CreateWorldScreen.class.getDeclaredMethod("onCreate");m.setAccessible(true);m.invoke(screen);
            }
            if(replay && stage==0 && mc.player!=null)stage=2;
            if(stage==2 && mc.player!=null && mc.getSingleplayerServer()!=null){
                if(readyAt==0)readyAt=System.nanoTime();
                if(!replay && System.nanoTime()-readyAt<5_000_000_000L)return;
                stage=3;mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().getFirst();
                    if(replay)SmartNpcFoodChainChecks.resume(server.overworld(),mc.gameDirectory.toPath().resolve("food-ledger.properties"));
                    else SmartNpcFoodChainChecks.setup(server.overworld(),player);
                    server.tickRateManager().setTickRate(60);
                }catch(Throwable t){failure=t;}});
            }
            if(done){stage=4;Files.writeString(mc.gameDirectory.toPath().resolve(replay?"replay-complete.txt":"complete.txt"),checkpoint&&!replay?"Saved pending native food chain":"PASS native food chain completed with conservation checks");mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().tickRateManager().setTickRate(20));mc.stop();}
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}stage=4;mc.stop();}
    }
}

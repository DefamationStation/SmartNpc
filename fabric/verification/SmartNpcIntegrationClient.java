import baritone.buddy.BuddyRelay;
import com.pla.smart_npc.client.gui.InventoryViewerScreen;
import com.pla.smart_npc.inventory.InventoryViewerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.nio.file.*;

/** Full installed mod set, vanilla diagnostic assets: real food packet first,
 * actual inventory-screen rendering, then a second client using the same candidate.
 * The screen is opened by the fixture; this does not claim a native menu gesture. */
public final class SmartNpcIntegrationClient extends SmartNpcFoodOfferClient {
    int integrationStage,integrationTicks;long started=System.nanoTime();
    @Override void client(Minecraft mc){try{
        if(System.nanoTime()-started>300_000_000_000L)throw new AssertionError("Native NPC/Buddy integration deadline");
        if(!done){super.client(mc);return;}
        if(integrationStage==0){
            mc.options.keyShift.setDown(false);
            check(mc.player!=null&&mc.level.getEntity(target)!=null,"Native NPC absent before inventory render");
            mc.setScreenAndShow(new InventoryViewerScreen(new InventoryViewerMenu(0,mc.player.getInventory(),Integer.valueOf(target)),mc.player.getInventory(),Component.literal("Native Snapshot 6 NPC inventory")));
            integrationStage=1;integrationTicks=0;
        }else if(integrationStage==1&&++integrationTicks==40){
            check(mc.gui.screen() instanceof InventoryViewerScreen,"Native inventory screen did not remain open");
            net.minecraft.client.Screenshot.grab(mc,false);mc.setScreenAndShow(null);
            BuddyRelay.intercept("@Buddy spawn");integrationStage=2;integrationTicks=0;
        }else if(integrationStage==2&&BuddyRelay.assistantStatus().get("joined").getAsBoolean()){
            check(mc.getSingleplayerServer().getPlayerList().getPlayerCount()==2,"Real second client absent");
            integrationStage=3;integrationTicks=0;
        }else if(integrationStage==3&&++integrationTicks==120){
            check(BuddyRelay.assistantStatus().get("joined").getAsBoolean(),"Buddy registry/login unstable");
            check(npc.isAlive()&&goals()==81,"Native actor or original selectors lost during integration");
            verify(mc.player.getUUID());BuddyRelay.intercept("@Buddy quit");
            Files.writeString(mc.gameDirectory.toPath().resolve("complete.txt"),"PASS full28-mod host and17-mod real Buddy using identical native NPC candidate; real food packet attribution/conservation; native inventory GUI rendered40frames; second-client login stable120ticks; original NPC/all81goals and exact relationship facts retained. Vanilla diagnostic assets; production graphics unchanged.");
            integrationStage=4;mc.stop();
        }
    }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("failure.txt"),t.toString());}catch(Exception ignored){}BuddyRelay.intercept("@Buddy quit");integrationStage=4;mc.stop();}}
}

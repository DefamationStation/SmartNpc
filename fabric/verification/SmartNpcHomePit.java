import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcHomePit implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.pitOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcHomeStorage implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.storageOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

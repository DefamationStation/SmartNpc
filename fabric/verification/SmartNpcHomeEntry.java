import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcHomeEntry implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.storageOnly","true");System.setProperty("smartnpc.daily.entryOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcStorageRecovery implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.storageOnly","true");System.setProperty("smartnpc.daily.blockedStorageOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

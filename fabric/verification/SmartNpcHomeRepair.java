import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcHomeRepair implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.repairOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

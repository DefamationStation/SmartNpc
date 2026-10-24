import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcHomeRepairChurn implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.repairOnly","true");System.setProperty("smartnpc.daily.churnOnly","true");new SmartNpcDailySmoke().onInitializeClient();}
}

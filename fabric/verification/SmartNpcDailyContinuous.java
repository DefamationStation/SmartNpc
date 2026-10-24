import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcDailyContinuous implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.continuous","true");new SmartNpcDailySmoke().onInitializeClient();}
}

import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcDailyNight implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.daily.night","true");new SmartNpcDailySmoke().onInitializeClient();}
}

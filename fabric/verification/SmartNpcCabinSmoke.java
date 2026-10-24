import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcCabinSmoke implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.population.cabin","true");new SmartNpcPopulation().onInitializeClient();}
}

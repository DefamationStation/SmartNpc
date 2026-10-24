import net.fabricmc.api.ClientModInitializer;
/** Short native menu-networking/rendering check, separate from the three-day soak. */
public final class SmartNpcInventorySmoke implements ClientModInitializer {
    @Override public void onInitializeClient() {
        System.setProperty("smartnpc.population.uiOnly", "true");
        new SmartNpcPopulation().onInitializeClient();
    }
}

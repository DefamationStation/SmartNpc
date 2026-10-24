import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcFoodCheckpoint implements ClientModInitializer {
    public void onInitializeClient() {
        System.setProperty("smartnpc.food.checkpoint", "true");
        new SmartNpcFoodSmoke().onInitializeClient();
    }
}

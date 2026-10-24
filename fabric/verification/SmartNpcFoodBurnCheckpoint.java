import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcFoodBurnCheckpoint implements ClientModInitializer {
    public void onInitializeClient() {
        System.setProperty("smartnpc.food.checkpoint", "true");
        System.setProperty("smartnpc.food.duringBurn", "true");
        new SmartNpcFoodSmoke().onInitializeClient();
    }
}

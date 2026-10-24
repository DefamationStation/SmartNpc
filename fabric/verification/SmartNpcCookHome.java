import net.fabricmc.api.ClientModInitializer;
public final class SmartNpcCookHome implements ClientModInitializer {
 public void onInitializeClient(){System.setProperty("smartnpc.recovery.home","true");new SmartNpcCookingRecovery().onInitializeClient();}
}

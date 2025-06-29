package fish.crafting.fimfabric.listener;

import fish.crafting.fimfabric.connection.ConnectionManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

public class ConnectionShutdownListener implements ClientLifecycleEvents.ClientStopping {
    @Override
    public void onClientStopping(MinecraftClient client) {
        ConnectionManager.get().shutdown();
    }
}

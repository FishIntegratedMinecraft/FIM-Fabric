package fish.crafting.fimfabric.util;

import net.minecraft.client.MinecraftClient;

public class WindowUtil {

    public static int width(){
        return MinecraftClient.getInstance().getWindow().getFramebufferWidth();
    }

    public static int height(){
        return MinecraftClient.getInstance().getWindow().getFramebufferHeight();
    }

    public static int scaledWidth(){
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    public static int scaledHeight(){
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }

    public static int guiScale(){
        Integer value = MinecraftClient.getInstance().options.getGuiScale().getValue();
        return MinecraftClient.getInstance().getWindow().calculateScaleFactor(value, false);
    }

}

package vorga.phazeclient.base;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.Window;
import net.minecraft.client.render.RenderTickCounter;
import vorga.phazeclient.api.system.draw.DrawEngineImpl;
import vorga.phazeclient.api.system.shape.implement.*;
import vorga.phazeclient.implement.menu.components.implement.window.WindowManager;

public interface QuickImports {
    DrawEngineImpl drawEngine = new DrawEngineImpl();
    Rectangle rectangle = new Rectangle();
    Arc arc = new Arc();
    InvertedArc invertedArc = new InvertedArc();
    InvertedRectangle invertedRectangle = new InvertedRectangle();
    Image image = new Image();
    WindowManager windowManager = WindowManager.INSTANCE;

    default MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    default Window getWindow() {
        MinecraftClient client = client();
        return client != null ? client.getWindow() : null;
    }

    default Window window() {
        return getWindow();
    }

    default RenderTickCounter tickCounter() {
        MinecraftClient client = client();
        return client != null ? client.getRenderTickCounter() : null;
    }

    default Tessellator tessellator() {
        return Tessellator.getInstance();
    }
}

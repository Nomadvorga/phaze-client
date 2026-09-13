package vorga.phazeclient.mixins;

import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

@Mixin(targets = {
        "net.minecraft.client.gui.hud.ChatHud$Hud",
        "net.minecraft.client.gui.hud.ChatHud$Interactable"
})
public interface ChatHudBackendPoseAccess {
    @Invoker("updatePose")
    void phaze$updatePose(Consumer<Matrix3x2f> transformer);
}

package vorga.phazeclient.mixins;

import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

/** Accesses the pose retained by the 1.21.11 ChatHud backend. */
@Mixin(targets = {
        "net.minecraft.client.gui.hud.ChatHud$Hud",
        "net.minecraft.client.gui.hud.ChatHud$Interactable"
})
public interface ChatHudBackendPoseAccess {
    @Invoker("updatePose")
    void phaze$updatePose(Consumer<Matrix3x2f> transformer);
}

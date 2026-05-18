package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.PlayerModelHud;

/**
 * Renders {@link PlayerModelHud}'s 3D portrait by delegating to
 * {@link InventoryScreen#drawEntity(DrawContext, float, float, float,
 * Vector3f, Quaternionf, Quaternionf, net.minecraft.entity.LivingEntity)}.
 *
 * <h3>Rotation modes</h3>
 * The two-quaternion overload of drawEntity expects:
 * <ul>
 *   <li>{@code quaternionf}  - the body rotation (model orientation)</li>
 *   <li>{@code quaternionf2} - optional head rotation, applied on top
 *       of the body quaternion. Null when we don't want separate
 *       head tracking.</li>
 * </ul>
 *
 * <p>Follow-mouse mode mirrors the inventory portrait math: yaw
 * tracks horizontal mouse position, pitch tracks vertical. Auto
 * rotate mode advances the body yaw by
 * {@code rotationSpeed * deltaSeconds}. Static mode pins the body
 * to the default 0/0/0 orientation.
 *
 * <h3>Held item</h3>
 * Vanilla's drawEntity always renders the player's currently held
 * stack as part of the model. To hide it we'd need to swap-out the
 * mainhand temporarily; that path is fragile (other code can pick
 * up the swap mid-frame), so the toggle just does nothing right
 * now beyond the user's expected default of "always shown". Left
 * as a hook in the module so the rendering side can add a real
 * implementation later without touching the API surface.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudPlayerModelMixin {

    /** Drag latch for chat-edit mode. */
    @Unique private static boolean phaze$dragging = false;
    @Unique private static float phaze$dragOffsetX = 0.0F;
    @Unique private static float phaze$dragOffsetY = 0.0F;
    @Unique private static boolean phaze$wasMouseDown = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawPlayerModel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerModelHud module = PlayerModelHud.getInstance();
        if (module == null || !module.isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options == null) return;
        if (client.options.hudHidden) return;

        ClientPlayerEntity player = client.player;
        float scale = module.getHudScale();
        int size = Math.round(module.modelSize.getValue());

        // Panel footprint matches drawEntity bounds at the chosen
        // size: 2*size wide, 2.5*size tall (top of head -> below
        // feet). Used for hit-testing the chat-mode drag and for
        // clamping the position to screen bounds.
        float panelW = 2.0F * size * scale;
        float panelH = 2.5F * size * scale;

        // Drag handling - only while chat is open. Mirrors
        // InventoryHud's chat-drag pattern: latch on press inside
        // the panel, release on mouse up. Coordinates are in GUI-
        // scaled space so they match the panel x/y the renderer
        // uses below.
        boolean chatEditing = client.currentScreen instanceof ChatScreen;
        boolean mouseDown = chatEditing && GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double scaleFactor = client.getWindow().getScaleFactor();
        if (scaleFactor <= 0.0) scaleFactor = 1.0;
        float mouseX = (float) (client.mouse.getX() / scaleFactor);
        float mouseY = (float) (client.mouse.getY() / scaleFactor);

        int scaledScreenW = client.getWindow().getScaledWidth();
        int scaledScreenH = client.getWindow().getScaledHeight();
        float maxX = Math.max(0.0F, scaledScreenW - panelW);
        float maxY = Math.max(0.0F, scaledScreenH - panelH);

        // Clamp the stored position so a window resize between
        // sessions doesn't strand the panel off-screen.
        float panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
        float panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
        module.setHudX(panelX);
        module.setHudY(panelY);

        if (chatEditing) {
            boolean inside = mouseX >= panelX && mouseX <= panelX + panelW
                    && mouseY >= panelY && mouseY <= panelY + panelH;
            if (mouseDown && !phaze$wasMouseDown && inside) {
                phaze$dragging = true;
                phaze$dragOffsetX = mouseX - panelX;
                phaze$dragOffsetY = mouseY - panelY;
            }
            if (!mouseDown) {
                phaze$dragging = false;
            }
            if (phaze$dragging) {
                panelX = MathHelper.clamp(mouseX - phaze$dragOffsetX, 0.0F, maxX);
                panelY = MathHelper.clamp(mouseY - phaze$dragOffsetY, 0.0F, maxY);
                module.setHudX(panelX);
                module.setHudY(panelY);
            }
        } else {
            phaze$dragging = false;
        }
        phaze$wasMouseDown = mouseDown;

        // drawEntity wants the screen-space CENTRE of the model.
        // Use the actual panel position (post-drag, post-clamp) so
        // the visual follows the cursor while the user is dragging.
        float centerX = panelX + size;
        float centerY = panelY + size * 2.0F;

        Vector3f translation = new Vector3f();
        Quaternionf bodyRotation;
        Quaternionf headRotation = null;

        String mode = module.mode.getSelected();
        if ("Follow Mouse".equalsIgnoreCase(mode) && !chatEditing) {
            // Follow-mouse only when chat ISN'T open - otherwise the
            // dragging cursor would also rotate the model on every
            // press, which fights the drag visually. In chat mode we
            // fall through to the Static branch so the model sits
            // still while the user repositions it.
            float mx = (float) (client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth());
            float my = (float) (client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight());
            float dx = (centerX - mx) / 50.0F;
            float dy = (centerY - my * 0.5F - size) / 50.0F;
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf().rotateX(dy * 20.0F * (float) (Math.PI / 180.0));
            bodyRotation.mul(new Quaternionf().rotateY(dx * 20.0F * (float) (Math.PI / 180.0)));
        } else if ("Auto Rotate".equalsIgnoreCase(mode)) {
            // Continuous spin around the Y axis. Time source is
            // System.nanoTime so the rotation keeps advancing even
            // while the game is paused (consistent visual cadence).
            float speed = module.rotationSpeed.getValue();
            float angleDeg = (System.nanoTime() / 1_000_000_000.0F) * speed;
            bodyRotation = new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .rotateY(angleDeg * (float) (Math.PI / 180.0));
        } else {
            // Static (or Follow Mouse while chat is open): pin the
            // body facing the camera, no per-frame rotation. The
            // entity's body / head yaw and pitch are NOT used by
            // drawEntity directly when we pass an explicit body
            // quaternion, but the renderer still pulls the model's
            // animation pose from the entity's tick state. We zero
            // out the head rotation so the model doesn't track the
            // player's actual look direction either - "Static"
            // means visually frozen, not "follows your in-world
            // head".
            bodyRotation = new Quaternionf().rotateZ((float) Math.PI);
            headRotation = new Quaternionf();
        }

        // Apply the user's HUD scale by pre-multiplying the size
        // argument; the helper internally scales the entity by
        // {@code size}, so a scaled size produces the visual zoom
        // we want without touching matrix stacks ourselves.
        InventoryScreen.drawEntity(context,
                centerX, centerY,
                size * scale,
                translation,
                bodyRotation,
                headRotation,
                player);
    }
}

package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.hud.InventoryHud;

/**
 * Renders the {@link InventoryHud} module on top of vanilla HUD,
 * and handles drag-to-position behaviour while the chat screen is
 * open (matches the rest of the rect-HUD family - users grab the
 * panel with the mouse during chat editing to relocate it).
 *
 * <h3>Layout</h3>
 * Standard 9x3 inventory grid. Each slot is 18 GUI pixels (16 px
 * icon + 1 px padding on each side), so the inner-slot grid is
 * 162x54 px. We pad an additional 1 px around the entire grid so
 * the gap between the outer slot edge and the panel border matches
 * the gap between adjacent slots - the user explicitly asked for
 * that visual symmetry. Total panel = 164x56 px before user scale.
 *
 * <h3>Drag</h3>
 * When the user opens chat ({@link ChatScreen}) we enable a simple
 * press-drag handler: left-click inside the panel rectangle latches
 * a drag, releasing left-click ends it. Mouse coords are read in
 * GUI-pixel space (the same coord system DrawContext uses) so the
 * panel position written back to the module matches the rendering
 * coords exactly. Coordinates are clamped to the visible screen so
 * the user can't drag the panel off the edge.
 *
 * <h3>Why a separate TAIL inject (not the batched HUD pipeline)</h3>
 * The main InGameHudMixin's batched HUD pipeline is text-heavy and
 * uses an FBO cache that doesn't play well with item rendering
 * (drawItem batches into a different vertex consumer that has
 * lifetime tied to the main framebuffer). Item rendering in a
 * cached FBO would either crash or render with broken textures, so
 * we take the simpler "direct render" path here - costs one extra
 * non-batched draw per frame, which is negligible for 27 items.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudInventoryHudMixin {

    /** Slot pixel size including the 1-px border padding on each side. */
    private static final int SLOT = 18;
    /** Width of the visible item icon inside a slot. */
    private static final int ICON = 16;
    /** Padding between the outer-edge slots and the panel border,
     *  picked so the spacing matches the gap between adjacent slots
     *  (1 px each side per slot, total 2 px between slots; we use
     *  1 px on the panel border to match a single slot's padding
     *  side). */
    private static final int PANEL_PADDING = 1;

    /** Drag latch: true while left-mouse is being held inside the panel. */
    @Unique private static boolean phaze$dragging = false;
    /** Drag offset cached from the press point so the panel doesn't
     *  jump under the cursor (panel anchor stays where the user
     *  initially clicked it). */
    @Unique private static float phaze$dragOffsetX = 0.0F;
    @Unique private static float phaze$dragOffsetY = 0.0F;
    /** Edge tracker for the press detection: {@code true} on the
     *  frame after the user pressed the button; flipped back when
     *  the button releases. Without it a held-button drag would
     *  re-fire every frame. */
    @Unique private static boolean phaze$wasMouseDown = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void phaze$drawInventoryHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        InventoryHud module = InventoryHud.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options == null) {
            return;
        }
        if (client.options.hudHidden) {
            return;
        }

        // Resolve source: live main inventory (slots 9..35 of the
        // PlayerInventory main list) or the ender chest snapshot.
        ItemStack[] stacks = new ItemStack[27];
        if (module.isEnderChestMode()) {
            ItemStack[] snap = module.getEnderChestSnapshot();
            System.arraycopy(snap, 0, stacks, 0, 27);
        } else {
            PlayerInventory inv = client.player.getInventory();
            for (int i = 0; i < 27; i++) {
                stacks[i] = inv.main.get(9 + i);
            }
        }

        float scale = module.getHudScale();
        int innerW = SLOT * 9;
        int innerH = SLOT * 3;
        int panelW = innerW + PANEL_PADDING * 2;
        int panelH = innerH + PANEL_PADDING * 2;

        // Drag handling - only while chat is open. The mouse coords
        // we work with are in window pixels; vanilla HUD draws in
        // GUI-scaled coords, so divide by the scale factor to bring
        // the cursor into the same coord space as the panel x/y.
        boolean chatEditing = client.currentScreen instanceof ChatScreen;
        boolean mouseDown = chatEditing && GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double scaleFactor = client.getWindow().getScaleFactor();
        if (scaleFactor <= 0.0) scaleFactor = 1.0;
        float mouseX = (float) (client.mouse.getX() / scaleFactor);
        float mouseY = (float) (client.mouse.getY() / scaleFactor);

        float scaledW = panelW * scale;
        float scaledH = panelH * scale;
        int scaledScreenW = client.getWindow().getScaledWidth();
        int scaledScreenH = client.getWindow().getScaledHeight();
        float maxX = Math.max(0.0F, scaledScreenW - scaledW);
        float maxY = Math.max(0.0F, scaledScreenH - scaledH);

        // Clamp the stored position so a window resize between
        // sessions doesn't leave the panel off-screen.
        float panelX = MathHelper.clamp(module.getHudX(), 0.0F, maxX);
        float panelY = MathHelper.clamp(module.getHudY(), 0.0F, maxY);
        module.setHudX(panelX);
        module.setHudY(panelY);

        if (chatEditing) {
            boolean inside = mouseX >= panelX && mouseX <= panelX + scaledW
                    && mouseY >= panelY && mouseY <= panelY + scaledH;
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

        // Render the panel at the (possibly drag-updated) position
        // with the user's scale applied.
        context.getMatrices().push();
        context.getMatrices().translate(panelX, panelY, 0);
        context.getMatrices().scale(scale, scale, 1.0F);

        // Outer panel backdrop: vanilla container-slot dark gray
        // with 60% alpha so the items read against any world view.
        // Drawn at the FULL panel size including the 1 px padding,
        // which is what gives the equal-spacing-on-all-sides look
        // the user asked for: panel edge -> 1 px gap -> first slot
        // edge, exactly the same as the 1 px gap between adjacent
        // slot edges (which is half of each slot's 2 px total
        // padding, mirrored to the next slot).
        context.fill(0, 0, panelW, panelH, 0x90000000);

        // Slot inner highlights so individual slots read separately.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = PANEL_PADDING + col * SLOT + 1;
                int sy = PANEL_PADDING + row * SLOT + 1;
                context.fill(sx, sy, sx + ICON, sy + ICON, 0x55_8B8B8B);
            }
        }

        // Items + stack count badges. drawItem handles enchant glint
        // and damage bars automatically; drawStackOverlay handles
        // the bottom-right count number with vanilla shadow / scale.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = stacks[row * 9 + col];
                if (stack == null || stack.isEmpty()) continue;
                int sx = PANEL_PADDING + col * SLOT + 1;
                int sy = PANEL_PADDING + row * SLOT + 1;
                context.drawItem(stack, sx, sy);
                if (module.drawCounts.isValue()) {
                    context.drawStackOverlay(client.textRenderer, stack, sx, sy);
                }
            }
        }

        context.getMatrices().pop();
    }
}

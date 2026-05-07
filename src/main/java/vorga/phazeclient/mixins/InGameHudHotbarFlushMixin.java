package vorga.phazeclient.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces a {@link DrawContext#draw} flush at the very end of
 * {@link InGameHud#renderHotbar} so the hotbar background and selection
 * sprite always commit with the GL state that vanilla intends, instead of
 * piggybacking on whatever happens to flush them later.
 *
 * <p>Why this is needed:
 * <ul>
 *   <li>{@code drawGuiTexture} queues into a deferred {@code VertexConsumer}
 *       per render layer. The queue is only flushed when something explicitly
 *       calls {@code context.draw()} or when the next item-rendering call
 *       (which internally flushes) fires.</li>
 *   <li>{@code renderHotbarItem} for an EMPTY slot does not call
 *       {@code drawItem}. If every slot is empty (e.g. a fresh spawn before
 *       picking anything up) nothing inside {@code renderHotbar} ever
 *       triggers a flush.</li>
 *   <li>The queued hotbar batch then sits there until a LATER HUD layer
 *       happens to flush it - by which point that layer may have called
 *       {@code RenderSystem.setShaderColor(1,1,1,alpha&lt;1)} (e.g. tab list
 *       fade) or otherwise altered global GL state. The hotbar inherits
 *       whatever uniform is live at flush time and renders darker / partly
 *       transparent. As soon as the player picks something up, the slot's
 *       {@code drawItem} flushes early with clean state and the symptom
 *       disappears - which exactly matches the user-observed pattern
 *       ("hotbar is darker until I have an item, then it stays fine").</li>
 * </ul>
 *
 * <p>The fix is a single {@code context.draw()} at TAIL plus a defensive
 * {@code setShaderColor(1,1,1,1)} reset so any leaked shader-color from a
 * future frame's layer can't haunt this one. Cheap (~one extra batch flush
 * per frame) and runs unconditionally - it isn't gated on any module
 * because the issue is unrelated to Animations.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudHotbarFlushMixin {

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phaze$flushHotbarBatch(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        // Reset shader color first so the impending flush commits the
        // hotbar with full opacity, even if some prior code path forgot
        // to reset its own setShaderColor call.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        context.draw();
    }
}

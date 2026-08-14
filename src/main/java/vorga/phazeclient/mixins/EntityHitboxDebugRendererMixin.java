/**
 * Based on HitboxPlus by PingIsFun (https://github.com/PingIsFun/hitboxplus)
 * Licensed under MIT License
 * Original Copyright (c) 2022 PingIsFun
 * Modified for Phaze Client.
 */
package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.HitboxCustomizer;

/**
 * Recolours vanilla entity hitboxes from the Hitbox Customizer module.
 *
 * <h3>1.21.11 port</h3>
 *
 * <p>Through 1.21.4 this hooked {@code WorldRenderer.renderHitbox} (targeted by
 * its intermediary name {@code method_3956}) and replaced the whole draw with
 * Phaze's own {@code HitboxRenderUtil.drawBox}, because vanilla handed the
 * method a {@link net.minecraft.client.util.math.MatrixStack} and a
 * {@link net.minecraft.client.render.VertexConsumer} to draw into directly.
 *
 * <p>1.21.11 removed that method outright. Debug shapes are now declarative
 * "gizmos": {@link net.minecraft.client.render.debug.EntityHitboxDebugRenderer}
 * describes each hitbox with
 * {@code GizmoDrawing.box(Box, DrawStyle)} and the renderer draws the collected
 * gizmos later, so there is no VertexConsumer to take over any more.
 *
 * <p>That turns out to suit this module better than the old approach did.
 * Everything it customises is carried by {@link DrawStyle}, a record of
 * {@code (stroke, strokeWidth, fill)} - so rather than reimplementing the box
 * geometry, we let vanilla build its boxes and rewrite only the style. Vanilla
 * keeps ownership of which entities get a hitbox, where the parts of a
 * multi-part entity are, and the eye/velocity arrows.
 *
 * <p>Note this only applies while the debug hitbox renderer is actually
 * running, i.e. F3+B - same as before.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    /**
     * Every hitbox in {@code drawHitbox} goes through
     * {@code GizmoDrawing.box(Box, DrawStyle)} - the entity box, the eye-line
     * marker box and each Ender Dragon part - so one {@code @ModifyArg} on the
     * style argument covers them all.
     *
     * <p>{@code require = 0}: if a future version renames the gizmo call the
     * module quietly falls back to vanilla colours instead of crashing the
     * client, matching the convention used by the other render hooks here.
     */
    @ModifyArg(
            method = "drawHitbox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/debug/gizmo/GizmoDrawing;box(Lnet/minecraft/util/math/Box;Lnet/minecraft/client/render/DrawStyle;)Lnet/minecraft/world/debug/gizmo/VisibilityConfigurable;"
            ),
            index = 1,
            require = 0
    )
    private DrawStyle phaze$styleHitbox(DrawStyle original) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }

        int stroke = module.getHitboxColor();
        if (module.redInReach.isValue() && phaze$isLookingAtEntityInReach()) {
            stroke = module.reachColor.getColor();
        }

        float width = module.outlineThickness.getValue();

        if (!module.isFillEnabled()) {
            return DrawStyle.stroked(stroke, width);
        }

        // The fill reuses the stroke's RGB with its own opacity, which is what
        // the 1.21.4 renderer did when Fill was on.
        int fillAlpha = Math.round(Math.max(0.0F, Math.min(1.0F, module.getFillOpacity())) * 255.0F);
        int fill = (fillAlpha << 24) | (stroke & 0x00FFFFFF);
        return DrawStyle.filledAndStroked(stroke, width, fill);
    }

    private static boolean phaze$isLookingAtEntityInReach() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return false;
        }
        HitResult target = client.crosshairTarget;
        return target != null
                && target.getType() == HitResult.Type.ENTITY
                && target instanceof EntityHitResult hit
                && hit.getEntity() instanceof Entity;
    }
}

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.VisibilityConfigurable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import vorga.phazeclient.implement.features.modules.other.HitboxCustomizer;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {

    @ModifyArg(
            method = "drawHitbox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/debug/gizmo/GizmoDrawing;box(Lnet/minecraft/util/math/Box;Lnet/minecraft/client/render/DrawStyle;)Lnet/minecraft/world/debug/gizmo/VisibilityConfigurable;",
                    ordinal = 0
            ),
            index = 1,
            require = 0
    )
    private DrawStyle phaze$styleHitbox(Entity entity, float tickDelta, boolean highlighted, DrawStyle original) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (module == null || !module.isEnabled()) {
            return original;
        }

        int stroke = module.getHitboxColor();
        if (module.redInReach.isValue() && phaze$isCrosshairEntity(entity)) {
            stroke = module.reachColor.getColor();
        }

        float width = module.outlineThickness.getValue();

        if (!module.isFillEnabled()) {
            return DrawStyle.stroked(stroke, width);
        }

        int fillAlpha = Math.round(Math.max(0.0F, Math.min(1.0F, module.getFillOpacity())) * 255.0F);
        int fill = (fillAlpha << 24) | (stroke & 0x00FFFFFF);
        return DrawStyle.filledAndStroked(stroke, width, fill);
    }

    @Redirect(
            method = "drawHitbox",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/debug/gizmo/GizmoDrawing;arrow(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;I)Lnet/minecraft/world/debug/gizmo/VisibilityConfigurable;",
                    ordinal = 0
            ),
            require = 0
    )
    private VisibilityConfigurable phaze$hideVanillaLookLine(Vec3d from, Vec3d to, int color) {
        HitboxCustomizer module = HitboxCustomizer.getInstance();
        if (module != null && module.isEnabled() && !module.showLookLine.isValue()) {
            return null;
        }
        return GizmoDrawing.arrow(from, to, color);
    }

    private static boolean phaze$isCrosshairEntity(Entity entity) {
        if (entity == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.crosshairTarget == null) {
            return false;
        }
        HitResult target = client.crosshairTarget;
        return target.getType() == HitResult.Type.ENTITY
                && target instanceof EntityHitResult hit
                && hit.getEntity() != null
                && hit.getEntity().getId() == entity.getId();
    }
}

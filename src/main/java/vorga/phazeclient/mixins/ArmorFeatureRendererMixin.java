package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.HitColorArmorContext;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin implements OverlayRendered {
    @Shadow
    @Final
    private EquipmentRenderer equipmentRenderer;

    /**
     * Equipment rendering is deferred in 1.21.11. Keep the hurt overlay tied
     * to this armor feature pass instead of relying only on mutable state in
     * the shared EquipmentRenderer instance.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD")
    )
    private void phaze$beginArmorHitColor(MatrixStack matrices,
                                           OrderedRenderCommandQueue queue,
                                           int light,
                                           BipedEntityRenderState state,
                                           float limbAngle,
                                           float limbDistance,
                                           CallbackInfo ci) {
        HitColor module = HitColor.getInstance();
        if (module.isEnabled() && module.showDamageInArmor.isValue()) {
            HitColorArmorContext.begin(OverlayTexture.getUv(0.0F, state.hurt));
        } else {
            HitColorArmorContext.end();
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("RETURN")
    )
    private void phaze$endArmorHitColor(MatrixStack matrices,
                                         OrderedRenderCommandQueue queue,
                                         int light,
                                         BipedEntityRenderState state,
                                         float limbAngle,
                                         float limbDistance,
                                         CallbackInfo ci) {
        HitColorArmorContext.end();
    }

    @Override
    public void setOverlay(int overlay) {
        if (equipmentRenderer instanceof OverlayRendered rendered) {
            rendered.setOverlay(overlay);
        }
    }
}

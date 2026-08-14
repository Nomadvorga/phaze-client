package vorga.phazeclient.mixins;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.HitColorArmorContext;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin implements OverlayRendered {
    @Unique
    private int phaze$overlay = OverlayTexture.DEFAULT_UV;

    // 1.21.11: EquipmentRenderer no longer draws the armour itself.
    //
    // Old shape (<=1.21.4) - two direct immediate-mode draws, and the mixin
    // hooked the overlay argument (index 3) of each:
    //   Model#render(MatrixStack, VertexConsumer, light, overlay, color)  <- tinted armour + glint
    //   Model#render(MatrixStack, VertexConsumer, light, overlay)         <- armour trim
    //
    // New shape - render() only enqueues work onto the deferred entity render
    // command queue; both Model#render overloads are gone from this method:
    //   queue.getBatchingQueue(order)
    //        .submitModel(model, state, matrices, layer, light, overlay,
    //                     color, sprite, outlineColor, crumblingOverlay)
    // so the overlay is argument index 5 of RenderCommandQueue#submitModel.
    //
    // All three former draws (armour layer, glint layer, trim layer) now funnel
    // through that single submitModel call site, so the two old @ModifyArg
    // handlers collapse into this one. Vanilla passes OverlayTexture.DEFAULT_UV
    // at every one of them, which is exactly what the old code replaced.
    //
    // The owner in the @At target is the RenderCommandQueue *interface* - the
    // call is invokeinterface, not invokevirtual on OrderedRenderCommandQueue.
    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
            ),
            index = 5,
            require = 1
    )
    private int phaze$applyOverlayToEquipment(int overlay) {
        if (HitColorArmorContext.isActive()) {
            return HitColorArmorContext.getOverlay();
        }
        return phaze$shouldRenderHurtOverlay() ? phaze$overlay : overlay;
    }

    @Unique
    private boolean phaze$shouldRenderHurtOverlay() {
        HitColor module = HitColor.getInstance();
        return module.isEnabled()
                && module.showDamageInArmor.isValue();
    }

    @Override
    public void setOverlay(int overlay) {
        phaze$overlay = overlay;
    }
}

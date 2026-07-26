package vorga.phazeclient.mixins;

import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin implements OverlayRendered {
    @Unique
    private int phaze$overlay = OverlayTexture.DEFAULT_UV;

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            ),
            index = 3,
            require = 1
    )
    private int phaze$applyOverlayToTintedArmor(int overlay) {
        return phaze$shouldRenderHurtOverlay() ? phaze$overlay : overlay;
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
            ),
            index = 3,
            require = 1
    )
    private int phaze$applyOverlayToArmorTrim(int overlay) {
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

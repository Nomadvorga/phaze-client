package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vorga.phazeclient.implement.features.modules.other.HitColor;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin implements OverlayRendered {
    @Unique
    private int overlayCoords;

    @WrapOperation(
        method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
        ),
        require = 1
    )
    private RenderLayer phaze$useEntityLayerForArmorOverlay(Identifier texture, Operation<RenderLayer> original) {
        if (this.phaze$shouldUseArmorHitColorLayer()) {
            return RenderLayer.getEntityCutoutNoCull(texture);
        }

        return original.call(texture);
    }

    @ModifyArg(
        method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
        ),
        index = 3,
        require = 1
    )
    private int phaze$modifyOverlayNoTint(int overlay) {
        return this.phaze$isArmorHitColorEnabled() ? this.overlayCoords : overlay;
    }

    @ModifyArg(
        method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
        ),
        index = 3,
        require = 1
    )
    private int phaze$modifyOverlayWithTint(int overlay) {
        return this.phaze$isArmorHitColorEnabled() ? this.overlayCoords : overlay;
    }

    @Unique
    private boolean phaze$isArmorHitColorEnabled() {
        HitColor module = HitColor.getInstance();
        return module.isEnabled() && module.showDamageInArmor.isValue();
    }

    @Unique
    private boolean phaze$shouldUseArmorHitColorLayer() {
        return this.phaze$isArmorHitColorEnabled() && this.overlayCoords != OverlayTexture.DEFAULT_UV;
    }

    @Override
    public void setOverlay(int overlay) {
        this.overlayCoords = overlay;
    }
}

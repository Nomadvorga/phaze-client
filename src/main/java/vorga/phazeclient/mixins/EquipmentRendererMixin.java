package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
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
    private int phaze$overlay = OverlayTexture.DEFAULT_UV;

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
        return phaze$shouldRenderHurtOverlay() ? phaze$overlay : overlay;
    }

    @WrapOperation(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/RenderLayers;armorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
            ),
            require = 1
    )
    private RenderLayer phaze$useEntityOverlayForHurtArmor(
            Identifier texture, Operation<RenderLayer> original
    ) {
        return phaze$hasActiveArmorHit()
                ? RenderLayers.entityCutoutNoCull(texture)
                : original.call(texture);
    }

    @Unique
    private boolean phaze$shouldRenderHurtOverlay() {
        HitColor module = HitColor.getInstance();
        return module.isEnabled()
                && module.showDamageInArmor.isValue();
    }

    @Unique
    private boolean phaze$hasActiveArmorHit() {
        return phaze$shouldRenderHurtOverlay()
                && phaze$overlay != OverlayTexture.DEFAULT_UV;
    }

    @Override
    public void setOverlay(int overlay) {
        phaze$overlay = overlay;
    }
}

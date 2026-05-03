package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.item.ItemStack;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
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

    @ModifyArg(
        method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
        ),
        index = 3
    )
    private int modifyOverlay1(int overlay) {
        return isHitColor() ? this.overlayCoords : overlay;
    }

    @ModifyArg(
        method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
        ),
        index = 3
    )
    private int modifyOverlay2(int overlay) {
        return isHitColor() ? this.overlayCoords : overlay;
    }

    @Unique
    private boolean isHitColor() {
        return HitColor.getInstance().isEnabled() && HitColor.getInstance().showDamageInArmor.isValue();
    }

    public void setOverlay(int overlay) {
        this.overlayCoords = overlay;
    }
}

package me.zyouime.hitcolor.mixin;

import me.zyouime.hitcolor.client.HitColorClient;
import me.zyouime.hitcolor.util.overlay.OverlayRendered;
import net.minecraft.class_10186;
import net.minecraft.class_10197;
import net.minecraft.class_10394;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_3879;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_5321;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin({class_10197.class})
public abstract class EquipmentRendererMixin implements OverlayRendered {
   @Unique
   private int overlayCoords;

   @Shadow
   public abstract void method_64078(class_10186.class_10190 var1, class_5321<class_10394> var2, class_3879 var3, class_1799 var4, class_4587 var5, class_4597 var6, int var7, @Nullable class_2960 var8);

   @ModifyArg(
      method = {"render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V"},
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/model/Model;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V"
),
      index = 3
   )
   private int modifyOverlay1(int overlay) {
      return this.isHitColor() ? this.overlayCoords : overlay;
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
      return this.isHitColor() ? this.overlayCoords : overlay;
   }

   @Unique
   private boolean isHitColor() {
      return (Boolean)HitColorClient.getInstance().settings.armorOverlay.getValue();
   }

   public void setOverlay(int coords) {
      this.overlayCoords = coords;
   }
}

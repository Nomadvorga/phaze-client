package me.zyouime.hitcolor.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import me.zyouime.hitcolor.util.overlay.OverlayRendered;
import net.minecraft.class_10042;
import net.minecraft.class_1309;
import net.minecraft.class_3883;
import net.minecraft.class_3887;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_5617;
import net.minecraft.class_583;
import net.minecraft.class_897;
import net.minecraft.class_922;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_922.class})
public abstract class LivingEntityRendererMixin<T extends class_1309, S extends class_10042, M extends class_583<? super S>> extends class_897<T, S> implements class_3883<S, M> {
   @Shadow
   public static int method_23622(class_10042 state, float whiteOverlayProgress) {
      return 0;
   }

   @Shadow
   protected abstract float method_23185(S var1);

   protected LivingEntityRendererMixin(class_5617.class_5618 context) {
      super(context);
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V",
   ordinal = 0
)}
   )
   private void render(S livingEntityRenderState, class_4587 matrixStack, class_4597 vertexConsumerProvider, int i, CallbackInfo ci, @Local class_3887<S, M> featureRenderer) {
      if (featureRenderer instanceof OverlayRendered rendered) {
         int overlay = method_23622(livingEntityRenderState, this.method_23185(livingEntityRenderState));
         rendered.setOverlay(overlay);
      }

   }
}

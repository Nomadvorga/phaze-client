package vorga.phazeclient.implement.cosmetics.bridge.pulserender;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoBone;
import vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo.GeoCube;

public class GeckoRenderHelper {
   private static final Vector3f POSITIVE_X = new Vector3f(1.0F, 0.0F, 0.0F);
   private static final Vector3f POSITIVE_Y = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Vector3f POSITIVE_Z = new Vector3f(0.0F, 0.0F, 1.0F);

   public static void translate(GeoBone var0, MatrixStack var1) {
      var1.translate(-var0.getPositionX() / 16.0F, var0.getPositionY() / 16.0F, var0.getPositionZ() / 16.0F);
   }

   public static void moveToPivot(GeoBone var0, MatrixStack var1) {
      var1.translate(var0.getPivotX() / 16.0F, var0.getPivotY() / 16.0F, var0.getPivotZ() / 16.0F);
   }

   public static void moveBackFromPivot(GeoBone var0, MatrixStack var1) {
      var1.translate(-var0.getPivotX() / 16.0F, -var0.getPivotY() / 16.0F, -var0.getPivotZ() / 16.0F);
   }

   public static void rotate(GeoBone var0, MatrixStack var1) {
      if (var0.getRotationZ() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_Z, var0.getRotationZ()));
      }

      if (var0.getRotationY() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_Y, var0.getRotationY()));
      }

      if (var0.getRotationX() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_X, var0.getRotationX()));
      }
   }

   public static void scale(GeoBone var0, MatrixStack var1) {
      var1.scale(var0.getScaleX(), var0.getScaleY(), var0.getScaleZ());
   }

   public static void moveToPivot(GeoCube var0, MatrixStack var1) {
      var1.translate(var0.pivot.getX() / 16.0F, var0.pivot.getY() / 16.0F, var0.pivot.getZ() / 16.0F);
   }

   public static void moveBackFromPivot(GeoCube var0, MatrixStack var1) {
      var1.translate(-var0.pivot.getX() / 16.0F, -var0.pivot.getY() / 16.0F, -var0.pivot.getZ() / 16.0F);
   }

   public static void rotate(GeoCube var0, MatrixStack var1) {
      if (var0.rotation.getZ() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_Z, var0.rotation.getZ()));
      }

      if (var0.rotation.getY() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_Y, var0.rotation.getY()));
      }

      if (var0.rotation.getX() != 0.0F) {
         var1.multiply(getRadialQuaternion(POSITIVE_X, var0.rotation.getX()));
      }
   }

   public static Quaternionf getRadialQuaternion(Vector3f var0, float var1) {
      float var2 = (float)Math.sin((double)(var1 / 2.0F));
      float var3 = var0.x() * var2;
      float var4 = var0.y() * var2;
      float var5 = var0.z() * var2;
      float var6 = (float)Math.cos((double)(var1 / 2.0F));
      return new Quaternionf(var3, var4, var5, var6);
   }
}

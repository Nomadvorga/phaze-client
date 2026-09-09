package vorga.phazeclient.implement.cosmetics.bridge.phazerender.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeoModel {
   public List<GeoBone> topLevelBones = new ArrayList<>();
   public int textureWidth = 64;
   public int textureHeight = 64;

   public Optional<GeoBone> getBone(String var1) {
      for (GeoBone var3 : this.topLevelBones) {
         GeoBone var4 = this.getBoneRecursively(var1, var3);
         if (var4 != null) {
            return Optional.of(var4);
         }
      }

      return Optional.empty();
   }

   private GeoBone getBoneRecursively(String var1, GeoBone var2) {
      if (var2.name.equals(var1)) {
         return var2;
      } else {
         for (GeoBone var4 : var2.childBones) {
            GeoBone var5 = this.getBoneRecursively(var1, var4);
            if (var5 != null) {
               return var5;
            }
         }

         return null;
      }
   }
}

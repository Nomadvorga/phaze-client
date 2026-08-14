package vorga.phazeclient.implement.cosmetics.bridge.pulserender.geo;

public class GeoVertex {
   public Vec3F position;
   public float textureU;
   public float textureV;

   public GeoVertex(float var1, float var2, float var3, float var4, float var5) {
      this.position = new Vec3F(var1, var2, var3);
      this.textureU = var4;
      this.textureV = var5;
   }

   public GeoVertex(Vec3F var1, float var2, float var3) {
      this.position = var1;
      this.textureU = var2;
      this.textureV = var3;
   }
}

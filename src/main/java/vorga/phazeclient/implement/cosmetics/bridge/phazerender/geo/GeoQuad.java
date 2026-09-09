package vorga.phazeclient.implement.cosmetics.bridge.phazerender.geo;

public class GeoQuad {
   public GeoVertex[] vertices;
   public Vec3F normal;

   public GeoQuad(GeoVertex[] var1, Vec3F var2) {
      this.vertices = var1;
      this.normal = var2;
   }

   public GeoQuad(GeoVertex[] var1, float var2, float var3, float var4) {
      this.vertices = var1;
      this.normal = new Vec3F(var2, var3, var4);
   }
}

package vorga.phazeclient.implement.cosmetics.bridge.phazerender.geo;

public class GeoCube {
   public GeoQuad[] quads = new GeoQuad[6];
   public Vec3F size;
   public Vec3F pivot;
   public Vec3F rotation;
   public float inflate;
   public boolean mirror;

   public GeoCube(float var1, float var2, float var3) {
      this.size = new Vec3F(var1, var2, var3);
      this.pivot = new Vec3F(0.0F, 0.0F, 0.0F);
      this.rotation = new Vec3F(0.0F, 0.0F, 0.0F);
      this.inflate = 0.0F;
      this.mirror = false;
   }
}

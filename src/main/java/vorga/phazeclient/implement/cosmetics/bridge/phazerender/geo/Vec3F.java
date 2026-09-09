package vorga.phazeclient.implement.cosmetics.bridge.phazerender.geo;

public class Vec3F {
   public static final Vec3F NULL_VECTOR = new Vec3F(0.0F, 0.0F, 0.0F);
   public float x;
   public float y;
   public float z;

   public Vec3F(float var1, float var2, float var3) {
      this.x = var1;
      this.y = var2;
      this.z = var3;
   }

   public float getX() {
      return this.x;
   }

   public float getY() {
      return this.y;
   }

   public float getZ() {
      return this.z;
   }

   public void setX(float var1) {
      this.x = var1;
   }

   public void setY(float var1) {
      this.y = var1;
   }

   public void setZ(float var1) {
      this.z = var1;
   }

   public void set(float var1, float var2, float var3) {
      this.x = var1;
      this.y = var2;
      this.z = var3;
   }

   public Vec3F crossProduct(Vec3F var1) {
      return new Vec3F(this.y * var1.z - this.z * var1.y, this.z * var1.x - this.x * var1.z, this.x * var1.y - this.y * var1.x);
   }

   @Override
   public String toString() {
      return this.x + "," + this.y + "," + this.z;
   }
}

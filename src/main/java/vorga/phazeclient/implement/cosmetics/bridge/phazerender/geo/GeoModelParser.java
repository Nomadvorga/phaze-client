package vorga.phazeclient.implement.cosmetics.bridge.phazerender.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;

public class GeoModelParser {
   public static GeoModel parse(String var0) {
      try {
         JsonObject var1 = JsonParser.parseString(var0).getAsJsonObject();
         return parseModel(var1);
      } catch (Exception var2) {
         System.err.println("[PhazeCosmetics] Failed to parse Bedrock model: " + var2);
         return null;
      }
   }

   private static GeoModel parseModel(JsonObject var0) {
      JsonArray var1 = var0.getAsJsonArray("minecraft:geometry");
      if (var1 != null && !var1.isEmpty()) {
         JsonObject var2 = var1.get(0).getAsJsonObject();
         JsonObject var3 = var2.getAsJsonObject("description");
         int var4 = var3.has("texture_width") ? var3.get("texture_width").getAsInt() : 64;
         int var5 = var3.has("texture_height") ? var3.get("texture_height").getAsInt() : 64;
         GeoModel var6 = new GeoModel();
         var6.textureWidth = var4;
         var6.textureHeight = var5;
         JsonArray var7 = var2.getAsJsonArray("bones");
         if (var7 != null) {
            HashMap var8 = new HashMap();

            for (JsonElement var10 : var7) {
               JsonObject var11 = var10.getAsJsonObject();
               GeoBone var12 = parseBone(var11, var4, var5);
               var8.put(var12.name, var12);
            }

            for (JsonElement var17 : var7) {
               JsonObject var18 = var17.getAsJsonObject();
               String var19 = var18.get("name").getAsString();
               GeoBone var13 = (GeoBone)var8.get(var19);
               if (var18.has("parent")) {
                  String var14 = var18.get("parent").getAsString();
                  GeoBone var15 = (GeoBone)var8.get(var14);
                  if (var15 != null) {
                     var15.childBones.add(var13);
                     var13.parent = var15;
                  }
               } else {
                  var6.topLevelBones.add(var13);
               }
            }
         }
         // Parsed using the coordinate conversion from the supplied Phaze renderer.
         return var6;
      } else {
         System.err.println("[PhazeCosmetics] No minecraft:geometry found in model");
         return null;
      }
   }

   private static GeoBone parseBone(JsonObject var0, int var1, int var2) {
      String var3 = var0.get("name").getAsString();
      GeoBone var4 = new GeoBone(var3);
      if (var0.has("pivot")) {
         JsonArray var5 = var0.getAsJsonArray("pivot");
         var4.rotationPointX = -var5.get(0).getAsFloat();
         var4.rotationPointY = var5.get(1).getAsFloat();
         var4.rotationPointZ = var5.get(2).getAsFloat();
      }

      if (var0.has("rotation")) {
         JsonArray var10 = var0.getAsJsonArray("rotation");
         var4.setRotationX((float)Math.toRadians((double)(-var10.get(0).getAsFloat())));
         var4.setRotationY((float)Math.toRadians((double)(-var10.get(1).getAsFloat())));
         var4.setRotationZ((float)Math.toRadians((double)var10.get(2).getAsFloat()));
      }

      if (var0.has("cubes")) {
         for (JsonElement var7 : var0.getAsJsonArray("cubes")) {
            JsonObject var8 = var7.getAsJsonObject();
            GeoCube var9 = parseCube(var8, var1, var2);
            var4.childCubes.add(var9);
         }
      }

      return var4;
   }

   private static GeoCube parseCube(JsonObject var0, int var1, int var2) {
      float[] var3 = parseFloatArray(var0, "origin", new float[]{0.0F, 0.0F, 0.0F});
      float[] var4 = parseFloatArray(var0, "size", new float[]{1.0F, 1.0F, 1.0F});
      float[] var5 = parseFloatArray(var0, "pivot", (float[])var3.clone());
      float[] var6 = parseFloatArray(var0, "rotation", new float[]{0.0F, 0.0F, 0.0F});
      float var7 = var0.has("inflate") ? var0.get("inflate").getAsFloat() : 0.0F;
      boolean var8 = var0.has("mirror") && var0.get("mirror").getAsBoolean();
      GeoCube var9 = new GeoCube(var4[0], var4[1], var4[2]);
      var9.pivot = new Vec3F(-var5[0], var5[1], var5[2]);
      var9.rotation = new Vec3F((float)Math.toRadians((double)(-var6[0])), (float)Math.toRadians((double)(-var6[1])), (float)Math.toRadians((double)var6[2]));
      var9.inflate = var7;
      var9.mirror = var8;
      buildCubeQuads(var9, var3, var4, var7, var8, var0, var1, var2);
      return var9;
   }

   private static void buildCubeQuads(GeoCube var0, float[] var1, float[] var2, float var3, boolean var4, JsonObject var5, int var6, int var7) {
      float var8 = var1[0];
      float var9 = var1[1];
      float var10 = var1[2];
      float var11 = var2[0];
      float var12 = var2[1];
      float var13 = var2[2];
      var8 -= var3;
      var9 -= var3;
      var10 -= var3;
      var11 += var3 * 2.0F;
      var12 += var3 * 2.0F;
      var13 += var3 * 2.0F;
      float var14 = 0.0F;
      float var15 = 0.0F;
      boolean var16 = false;
      JsonObject var17 = null;
      if (var5.has("uv")) {
         JsonElement var18 = var5.get("uv");
         if (var18.isJsonArray()) {
            JsonArray var19 = var18.getAsJsonArray();
            var14 = var19.get(0).getAsFloat();
            var15 = var19.get(1).getAsFloat();
         } else if (var18.isJsonObject()) {
            var16 = true;
            var17 = var18.getAsJsonObject();
         }
      }

      float var32 = -(var8 + var11) / 16.0F;
      float var33 = var9 / 16.0F;
      float var20 = var10 / 16.0F;
      float var21 = -var8 / 16.0F;
      float var22 = (var9 + var12) / 16.0F;
      float var23 = (var10 + var13) / 16.0F;
      if (var16 && var17 != null) {
         var0.quads[0] = buildQuadPerFace(var17, "west", var32, var33, var20, var32, var22, var23, -1.0F, 0.0F, 0.0F, var6, var7);
         var0.quads[1] = buildQuadPerFace(var17, "east", var21, var33, var20, var21, var22, var23, 1.0F, 0.0F, 0.0F, var6, var7);
         var0.quads[2] = buildQuadPerFace(var17, "down", var32, var33, var20, var21, var33, var23, 0.0F, -1.0F, 0.0F, var6, var7);
         var0.quads[3] = buildQuadPerFace(var17, "up", var32, var22, var20, var21, var22, var23, 0.0F, 1.0F, 0.0F, var6, var7);
         var0.quads[4] = buildQuadPerFace(var17, "north", var32, var33, var20, var21, var22, var20, 0.0F, 0.0F, -1.0F, var6, var7);
         var0.quads[5] = buildQuadPerFace(var17, "south", var32, var33, var23, var21, var22, var23, 0.0F, 0.0F, 1.0F, var6, var7);
      } else {
         float var24 = (float)var6;
         float var25 = (float)var7;
         var0.quads[0] = buildQuadBox(var32, var33, var20, var32, var22, var23, -1.0F, 0.0F, 0.0F, var14, var15, var13, var12, var11, var24, var25, "west");
         var0.quads[1] = buildQuadBox(var21, var33, var20, var21, var22, var23, 1.0F, 0.0F, 0.0F, var14, var15, var13, var12, var11, var24, var25, "east");
         var0.quads[2] = buildQuadBox(var32, var33, var20, var21, var33, var23, 0.0F, -1.0F, 0.0F, var14, var15, var13, var12, var11, var24, var25, "down");
         var0.quads[3] = buildQuadBox(var32, var22, var20, var21, var22, var23, 0.0F, 1.0F, 0.0F, var14, var15, var13, var12, var11, var24, var25, "up");
         var0.quads[4] = buildQuadBox(var32, var33, var20, var21, var22, var20, 0.0F, 0.0F, -1.0F, var14, var15, var13, var12, var11, var24, var25, "north");
         var0.quads[5] = buildQuadBox(var32, var33, var23, var21, var22, var23, 0.0F, 0.0F, 1.0F, var14, var15, var13, var12, var11, var24, var25, "south");
      }
   }

   private static GeoQuad buildQuadPerFace(
      JsonObject var0,
      String var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      int var11,
      int var12
   ) {
      if (!var0.has(var1)) {
         return null;
      } else {
         JsonObject var13 = var0.getAsJsonObject(var1);
         JsonArray var14 = var13.getAsJsonArray("uv");
         JsonArray var15 = var13.getAsJsonArray("uv_size");
         float var16 = var14.get(0).getAsFloat() / (float)var11;
         float var17 = var14.get(1).getAsFloat() / (float)var12;
         float var18 = var15.get(0).getAsFloat() / (float)var11;
         float var19 = var15.get(1).getAsFloat() / (float)var12;
         GeoVertex[] var20 = buildFaceVertices(var1, var2, var3, var4, var5, var6, var7, var16, var17, var18, var19);
         return new GeoQuad(var20, var8, var9, var10);
      }
   }

   private static GeoQuad buildQuadBox(
      float var0,
      float var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      float var14,
      float var15,
      String var16
   ) {
      float var17;
      float var18;
      float var19;
      float var20;
      switch (var16) {
         case "north":
            var17 = (var9 + var11 + var13) / var14;
            var18 = (var10 + var11) / var15;
            var19 = var13 / var14;
            var20 = var12 / var15;
            break;
         case "south":
            var17 = (var9 + var11 + var13 + var11) / var14;
            var18 = (var10 + var11) / var15;
            var19 = var13 / var14;
            var20 = var12 / var15;
            break;
         case "east":
            var17 = var9 / var14;
            var18 = (var10 + var11) / var15;
            var19 = var11 / var14;
            var20 = var12 / var15;
            break;
         case "west":
            var17 = (var9 + var11 + var13) / var14;
            var18 = (var10 + var11) / var15;
            var19 = var11 / var14;
            var20 = var12 / var15;
            break;
         case "up":
            var17 = (var9 + var11) / var14;
            var18 = var10 / var15;
            var19 = var13 / var14;
            var20 = var11 / var15;
            break;
         case "down":
            var17 = (var9 + var11 + var13) / var14;
            var18 = var10 / var15;
            var19 = var13 / var14;
            var20 = var11 / var15;
            break;
         default:
            var17 = 0.0F;
            var18 = 0.0F;
            var19 = 0.0F;
            var20 = 0.0F;
      }

      GeoVertex[] var21 = buildFaceVertices(var16, var0, var1, var2, var3, var4, var5, var17, var18, var19, var20);
      return new GeoQuad(var21, var6, var7, var8);
   }

   private static GeoVertex[] buildFaceVertices(
      String var0, float var1, float var2, float var3, float var4, float var5, float var6, float var7, float var8, float var9, float var10
   ) {
      GeoVertex[] var11 = new GeoVertex[4];
      float var12 = var7 + var9;
      float var13 = var8 + var10;
      switch (var0) {
         case "north":
            var11[0] = new GeoVertex(var4, var5, var3, var7, var8);
            var11[1] = new GeoVertex(var1, var5, var3, var12, var8);
            var11[2] = new GeoVertex(var1, var2, var3, var12, var13);
            var11[3] = new GeoVertex(var4, var2, var3, var7, var13);
            break;
         case "south":
            var11[0] = new GeoVertex(var1, var5, var6, var7, var8);
            var11[1] = new GeoVertex(var4, var5, var6, var12, var8);
            var11[2] = new GeoVertex(var4, var2, var6, var12, var13);
            var11[3] = new GeoVertex(var1, var2, var6, var7, var13);
            break;
         case "east":
            var11[0] = new GeoVertex(var4, var5, var6, var7, var8);
            var11[1] = new GeoVertex(var4, var5, var3, var12, var8);
            var11[2] = new GeoVertex(var4, var2, var3, var12, var13);
            var11[3] = new GeoVertex(var4, var2, var6, var7, var13);
            break;
         case "west":
            var11[0] = new GeoVertex(var1, var5, var3, var7, var8);
            var11[1] = new GeoVertex(var1, var5, var6, var12, var8);
            var11[2] = new GeoVertex(var1, var2, var6, var12, var13);
            var11[3] = new GeoVertex(var1, var2, var3, var7, var13);
            break;
         case "up":
            var11[0] = new GeoVertex(var1, var5, var3, var7, var8);
            var11[1] = new GeoVertex(var1, var5, var6, var7, var13);
            var11[2] = new GeoVertex(var4, var5, var6, var12, var13);
            var11[3] = new GeoVertex(var4, var5, var3, var12, var8);
            break;
         case "down":
            var11[0] = new GeoVertex(var4, var2, var3, var7, var8);
            var11[1] = new GeoVertex(var4, var2, var6, var7, var13);
            var11[2] = new GeoVertex(var1, var2, var6, var12, var13);
            var11[3] = new GeoVertex(var1, var2, var3, var12, var8);
      }

      return var11;
   }

   private static float[] parseFloatArray(JsonObject var0, String var1, float[] var2) {
      if (!var0.has(var1)) {
         return var2;
      } else {
         JsonArray var3 = var0.getAsJsonArray(var1);
         float[] var4 = new float[var3.size()];

         for (int var5 = 0; var5 < var3.size(); var5++) {
            var4[var5] = var3.get(var5).getAsFloat();
         }

         return var4;
      }
   }
}

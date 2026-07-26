# Phaze 1.21.11 — Second-Wave Migration Plan

All counts below are `grep` hit counts I re-ran against `D:/projects/Phaze Client 1.21.11/src/main/java` today. All signatures re-verified with `javap` against the merged jar. **Corrections to the four probes are marked ⚠.**

---

## 0. Probe corrections (verify these before trusting the probe text)

| Probe claim | Reality |
|---|---|
| "`com.mojang.blaze3d.vertex.VertexConsumer/BufferBuilder` import move — do it first" | ⚠ **Already done.** Port has 22 `import net.minecraft.client.render.BufferBuilder`, 10 `…VertexConsumer`, 15 `…Tessellator`, 0 blaze3d.vertex imports of those. Zero work. |
| "`GlStateManager` does not exist in the jar at all" (renderstate probe) | ⚠ **Wrong.** `com.mojang.blaze3d.opengl.GlStateManager` exists with `_glBindFramebuffer`, `_glBlitFrameBuffer`, `_glFramebufferTexture2D`, `glGenFramebuffers`. Port already imports it in 4 files. The *framebuffer* probe was right. |
| "`RenderSystem.lineWidth` has no equivalent at all" | ⚠ **Wrong.** `VertexConsumer.lineWidth(float)` is an abstract method on the interface, and `VertexFormatElement.LINE_WIDTH` is a real per-vertex attribute. It is a per-vertex value now, not global state — but it exists. |
| "`new Matrix3x2f(this.matrices)` at 9 sites in DrawContext" | 7 sites. Conclusion unchanged. |
| "Z translation: `AbstractWindow.java:75`" | ⚠ **Massively understated.** **28 of 41** `getMatrices().translate(...)` calls pass a nonzero Z. 24 are in `InGameHudMixin` using `HUD_RENDER_Z = 400.0f` / `HANDLE_RENDER_Z = 450.0f` (lines 140-141). This is the single largest judgement item in the port, not a footnote. |
| accesswidener has 3 stale entries | ⚠ **4.** `MatrixStack.stack` is `List`, not `Deque` — that entry's descriptor is also wrong. |

Confirmed-correct, load-bearing facts I re-verified myself:

```
DrawContext:  private final org.joml.Matrix3x2fStack matrices;
              public org.joml.Matrix3x2fStack getMatrices();
              public DrawContext(MinecraftClient, GuiRenderState, int, int)  →  new Matrix3x2fStack(16)   // bipush 16
              public void enableScissor(int,int,int,int)  →  new ScreenRect(x1, y1, x2-x1, y2-y1).transform(this.matrices)   // LEFT,TOP,RIGHT,BOTTOM + transformed by the pose
VertexConsumer (net.minecraft.client.render): vertex(Matrix4fc,float,float,float) AND vertex(Matrix3x2fc,float,float) both present
org.joml.Matrix4f: public Matrix4f mul(Matrix3x2fc);  public Vector3f getScale(Vector3f);
org.joml.Matrix3x2f: translate(f,f) scale(f,f) scale(f) scaleAround(f,f,f) scaleAround(f,f,f,f) rotate(f) rotateAbout(f,f,f) — NO getScale
org.joml.Matrix3x2fStack: only pushMatrix()/popMatrix()/clear() — no push/pop/peek
RenderPipelines.GUI_SNIPPET: withDepthTestFunction(NO_DEPTH_TEST)
```

---

## 1. THE DECISION: rewrite to `Matrix3x2fStack`. Do **not** keep a Phaze-owned `MatrixStack`.

### Option B (Phaze-owned `MatrixStack`, convert to `Matrix4f` at draw) — **disqualified**

The port makes **132 calls into vanilla `DrawContext` draw methods** that consume the pose implicitly:

```
context.fill(              37
context.drawText           17
context.drawItem            9
context.drawGuiTexture      9
context.enableScissor       5
context.drawStackOverlay    5
context.drawTexture         4
context.drawTextWithShadow  2   … 132 total
```

Every one of those reads `DrawContext.matrices` — the **private** `Matrix3x2fStack`. A Phaze-owned shadow stack cannot be seen by them. Worse: `DrawContext.enableScissor` **transforms its rect by `this.matrices`** (bytecode above), so a shadow stack desyncs clipping from geometry — the classic "scissor is in the wrong place and nobody knows why" bug.

And it isn't less work. The ~230 type-changing call sites still change (you'd write `phazeStack.push()` instead of `stack.pushMatrix()`), **plus** a seed/sync layer at every render entry point, **plus** 132 latent silent bugs.

### Option C (wrapper class with `MatrixStack`-shaped API over `Matrix3x2fStack`) — **disqualified by the Z data**

Smallest raw diff (`sed 's/context\.getMatrices()/Poses.of(context)/'` handles 361 sites in one shot; push/pop/translate/scale/peek then need zero edits). But a wrapper's `translate(x, y, z)` **must silently drop z**, and 28 call sites pass a nonzero z — 24 of them driving the entire HUD layer order. Silent layering regression across `InGameHudMixin` is exactly the "costs days" failure. The compile error at those 28 sites is the only mechanism that will force the decision. Do not suppress it.

### Option A (recommended): `Matrix3x2fStack` is authoritative, plus one 20-line adapter

Work is smaller than it looks because **two choke points absorb most of it**:

- `ShapeProperties.matrix : MatrixStack → Matrix3x2f` (an owned **copy**). `Matrix3x2fStack IS-A Matrix3x2fc`, so **all 148 `ShapeProperties.create(...)` call sites compile unchanged.**
- `GuiMatrix.mat4(Matrix3x2fc)` = `new Matrix4f().mul(pose)` — vanilla's own idiom (`GlyphGuiElementRenderState.setupVertices`). Turns the 117 `peek().getPositionMatrix()` sites into one regex instead of 97 design decisions, and preserves every downstream `Matrix4f` signature (`vertex(Matrix4fc,…)` still exists).

**Net mechanical edits: ~230 across ~50 files. Net judgement sites: ~30.**

Do this first — nothing else in the port can be compiled or tested until it lands.

### Step 0 file — `vorga/phazeclient/base/util/render/GuiMatrix.java`

```java
package vorga.phazeclient.base.util.render;

import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GuiMatrix {
    private GuiMatrix() {}

    /** Vanilla's own promotion: GlyphGuiElementRenderState.setupVertices does new Matrix4f().mul(pose). */
    public static Matrix4f mat4(Matrix3x2fc pose) { return new Matrix4f().mul(pose); }

    /** Scratch-reusing variant for hot HUD paths. */
    public static Matrix4f mat4(Matrix3x2fc pose, Matrix4f dest) { return dest.identity().mul(pose); }

    /** Matrix3x2f has NO getScale(). Shear terms m01/m10 are load-bearing — Phaze rotates. */
    public static Vector3f getScale(Matrix3x2fc p, Vector3f dest) {
        return dest.set((float) Math.sqrt(p.m00() * p.m00() + p.m01() * p.m01()),
                        (float) Math.sqrt(p.m10() * p.m10() + p.m11() * p.m11()), 1.0F);
    }
}
```

---

## 2. Removed API → replacement → call sites

`M` = mechanical (regex-safe). `J` = judgement. Counts are grep hits / distinct files.

| Removed / changed | Replacement | Sites | Files | |
|---|---|---|---|---|
| `DrawContext.getMatrices() : MatrixStack` | `: Matrix3x2fStack` | 361 | 50 | — |
| `…getMatrices().push()` / `.pop()` | `.pushMatrix()` / `.popMatrix()` | 54 / 54 | 11 | **M** |
| `…getMatrices().scale(x,y,1.0f)` | `.scale(x, y)` (all 37 have z==1.0) | 37 | 6 | **M** |
| `…getMatrices().translate(x,y,0)` | `.translate(x, y)` | 13 | ~6 | **M** |
| `…getMatrices().translate(x,y,Z≠0)` | **no equivalent** → `createNewRootLayer()` / order | **28** | 5 | **J** |
| `…getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(a))` | `.rotate((float) Math.toRadians(a))` — **radians** | 1 | 1 | **J** |
| `X.peek().getPositionMatrix()` (GUI) | `GuiMatrix.mat4(X)` | ~97 of 117 | ~31 | **M** |
| `X.peek().getPositionMatrix()` (world `MatrixStack`) | **unchanged, still exists** | ~20 | 6 | — |
| `ShapeProperties.create(MatrixStack, …)` | `create(Matrix3x2fc, …)` + `new Matrix3x2f(…)` copy | 148 | 37 | **M**(1 file) |
| `Matrix4f.getScale(Vector3f)` on a GUI pose | `GuiMatrix.getScale(pose, dest)` or promote first | ~5 | 2 | **M** |
| `RenderSystem.getShaderColor()[3]` | **nothing** → mod-owned `PhazeAlpha` | 9 | 7 | **J** |
| `RenderSystem.setShaderColor` | per-vertex ARGB / `ColorHelper` / `DynamicUniforms` | 32 | 15 | **J** |
| `RenderSystem.enableBlend/disableBlend` | `.withBlend(BlendFunction)` on pipeline | 48+39 | 21/19 | **J** |
| `RenderSystem.defaultBlendFunc` | `new BlendFunction(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)` | 47 | 22 | **J** |
| `RenderSystem.blendFunc/blendFuncSeparate` | `new BlendFunction(...)` | 9 | 5 | **J** |
| `RenderSystem.depthMask` | `.withDepthWrite(boolean)` | 50 | 12 | **J** |
| `RenderSystem.enableDepthTest/disableDepthTest/depthFunc` | `.withDepthTestFunction(LEQUAL_ / NO_DEPTH_TEST)` | 40+27+4 | 14/8/3 | **J** |
| `RenderSystem.enableCull/disableCull` | `.withCull(boolean)` | 18+22 | 5 | **J** |
| `RenderSystem.setShaderTexture` | `RenderPass.bindTexture(name, view, sampler)` / `TextureSetup.of` | 17 | 5 | **J** |
| `RenderSystem.setShader` | `RenderPass.setPipeline(RenderPipeline)` | 7 | 7 | **J** |
| `RenderSystem.enableScissor(x,y,w,h)` | `enableScissorForRenderTypeDraws(x,y,w,h)` — **same order** | 5 | 3 | **M** |
| `RenderSystem.disableScissor()` | `disableScissorForRenderTypeDraws()` | 6 | 5 | **M** |
| `RenderSystem.lineWidth(f)` | `VertexConsumer.lineWidth(f)` **per vertex** | 8 | 3 | **J** |
| `RenderSystem.viewport` | governed by render-pass target | 7 | 2 | **J** |
| `RenderSystem.recordRenderCall` | `queueFencedTask` or inline on render thread | 2 | 2 | **M** |
| `RenderSystem.colorMask` | `.withColorWrite(rgb, alpha)` — RGB not independent | **0** | 0 | free |
| `context.draw()` | **nothing to flush** → delete; layering via `createNewRootLayer()` | 49 | 11 | **J** |
| `RenderLayer::getGuiTextured` | `RenderPipelines.GUI_TEXTURED` (a value) | 13 | 4 | **M** |
| `RenderLayer::getGuiTexturedOverlay` | **no equivalent** | 1 | 1 | **J** |
| `RenderTickCounter.getTickDelta(b)` | `getTickProgress(b)` — identical body | 12 | 6 | **M** |
| `Entity.getPos()` | `getEntityPos()` | 9 | 6 | **M** |
| `Camera.getPos()` | `getCameraPos()` | 2 | 2 | **M** |
| `HitResult.getPos()` / `Chunk.getPos()` | **unchanged** | 11 | 5 | — |
| `VertexFormatElement.get(id)` | `byId(id)` — bare array load, clamp to `MAX_COUNT` | 4 | 4 | **M** |
| `VertexFormatElement.ComponentType` | `VertexFormatElement.Type` | 8 real | 6 | **M** |
| `VertexFormats.LINES` | `POSITION_COLOR_NORMAL_LINE_WIDTH` + `.lineWidth()` per vertex | 9 | 4 | **J** |
| `new SimpleFramebuffer(w,h,depth)` | `(name, w, h, depth)` — **name first** | 49 | 6 | **M** |
| `Framebuffer.beginWrite/endWrite` | `CommandEncoder.createRenderPass(...)` / `outputColorTextureOverride` | 12 | 6 | **J** |
| `Framebuffer.clear/setClearColor` | `clearColorAndDepthTextures(tex, argbInt, depth, 1.0)` | 3 | 3 | **M** |
| `Framebuffer.setTexFilter` | sampler at bind time (`SamplerCache.get(FilterMode)`) | 28 | 4 | **M** |
| `Framebuffer.fbo : int` | no int handle; `getColorAttachmentView() : GpuTextureView` | 33 | 4 | **J** |
| `glBlitFramebuffer` (1:1, NEAREST) | `copyTextureToTexture(src,dst,mip,dstX,dstY,srcX,srcY,w,h)` | ~3 | 2 | **M** |
| `glBlitFramebuffer` (scaling / LINEAR) | **no API** → must become a shader pass | ~2 | 1 | **J** |
| `AbstractTexture.getGlId()` | `((GlTexture) t.getGlTexture()).getGlId()` — GL-backend only | 1 | 1 | **M** |
| `AbstractTexture.setFilter(b,b)` | assign `t.sampler = SamplerCache.get(...)` (needs AW) | 4 | 2 | **M** |
| `ShaderProgram.getUniformOrDefault(n).set(...)` | UBO block + `RenderPass.setUniform` | 40 | **1** (Blur) | **J** |
| `PostEffectPass.getProgram()` | **no replacement** | 1 | 1 | **J** |
| `new KeyBinding(…, String)` | `KeyBinding.Category.create(Identifier.of("phaze","main"))` | 1 | 1 | **M** |
| accesswidener stale entries | 4 lines to fix/delete | 4 | 1 | **M** |

---

## 3. Mechanical transformations (regex-safe, in order)

Run from `D:/projects/Phaze Client 1.21.11/src/main/java`. Git-commit between groups.

```bash
ROOT="D:/projects/Phaze Client 1.21.11/src/main/java"
J="$(find "$ROOT" -name '*.java')"
```

**M-1 — pose-stack declarations.** Do this first: it converts silent ambiguity into compiler errors that point at exactly the sites needing M-2..M-5.
```bash
sed -i -E 's/\bMatrixStack ([A-Za-z_][A-Za-z0-9_]*) = ([A-Za-z_][A-Za-z0-9_]*)\.getMatrices\(\);/Matrix3x2fStack \1 = \2.getMatrices();/g' $J
# 41 sites. Then add `import org.joml.Matrix3x2fStack;` to the touched files and
# drop `import net.minecraft.client.util.math.MatrixStack;` only where javac says it's unused.
```

**M-2 — push/pop** (54 + 54, 11 files)
```bash
sed -i 's/getMatrices()\.push()/getMatrices().pushMatrix()/g;  s/getMatrices()\.pop()/getMatrices().popMatrix()/g' $J
```
Then, in the 11 files touched by M-1, the local-variable forms:
```bash
sed -i -E 's/\b(matrices|matrix|stack|pose)\.push\(\);/\1.pushMatrix();/g; s/\b(matrices|matrix|stack|pose)\.pop\(\);/\1.popMatrix();/g' <files-from-M-1-only>
```
⚠ Never run that second one repo-wide — `.push()`/`.pop()` also appear on `ScissorManager`, `ArrayDeque`, and genuine world `MatrixStack`.

**M-3 — scale** (37, all with z literal `1.0f`/`1.0F` — verified histogram)
```bash
sed -i -E 's/(getMatrices\(\)\.scale\([^;]*), *1\.0[fF]\)/\1)/g' $J
```
Optional follow-up for uniform scales (`scale(s, s)` → `scale(s)`) — cosmetic, skip.
⚠ **Never** rewrite `scale(sx,sy,1.0F)` to `scaleAround(sx,sy,1.0F)`. That binds to `scaleAround(factor, ox, oy)` and compiles clean while meaning something entirely different.

**M-4 — zero-Z translate only** (13 sites)
```bash
sed -i -E 's/(getMatrices\(\)\.translate\([^;]*), *0(\.0)?[fF]?\)/\1)/g' $J
```
The 28 nonzero-Z sites are deliberately left to fail compilation → §4 J-1.

**M-5 — pose read** (~97 GUI sites). Fixed-string, in the M-1 file set only:
```bash
sed -i 's/context\.getMatrices()\.peek()\.getPositionMatrix()/GuiMatrix.mat4(context.getMatrices())/g' <gui-files>   # 39
sed -i 's/matrices\.peek()\.getPositionMatrix()/GuiMatrix.mat4(matrices)/g'                              <gui-files>   # 34
sed -i 's/matrix\.peek()\.getPositionMatrix()/GuiMatrix.mat4(matrix)/g'                                  <gui-files>   # 29
sed -i 's/shape\.getMatrix()\.peek()\.getPositionMatrix()/GuiMatrix.mat4(shape.getMatrix())/g'           $J            # 8
```
**Exclude these world-space files entirely** (their `MatrixStack.peek().getPositionMatrix()` still exists and is correct):
`api/system/render/Render3DUtil.java`, `util/HitboxRenderUtil.java`, `implement/hitrange/HitRangeCircleRenderer.java`, `implement/features/modules/other/{HolyWorldHelperRenderer,FTHelperRenderer,PredictionsRenderer,Predictions,ChangeHand}.java`, and `mixins/{EntityRendererHitboxMixin,EntityRendererMixin,EquipmentRendererMixin,HeldItemRendererMixin,InGameOverlayRendererNoFireMixin,ItemEntityRendererPhysicsMixin,LivingEntityRendererMixin,LivingEntityRendererWorldColorMixin,WorldRendererEntityWorldColorMixin}.java`.

**M-6 — `ShapeProperties` choke point** (1 file, unblocks 148 call sites)
```java
// ShapeProperties.java — replace the MatrixStack import and 3 usages
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

private Matrix3x2f matrix;                          // line 14 — an OWNED COPY, never the live stack

private ShapeProperties(Matrix3x2f matrix, ...)     // line 32

public static ShapePropertiesBuilder create(Matrix3x2fc matrix, double x, double y, double w, double h) {
    return builder().matrix(new Matrix3x2f(matrix))  // ← the copy is MANDATORY, see risk A4
                    .x((float) x).y((float) y).width((float) w).height((float) h);
}
```
All 148 `ShapeProperties.create(context.getMatrices(), …)` compile unchanged.

**M-7 — trivial renames**
```bash
sed -i 's/\.getTickDelta(/.getTickProgress(/g'                                            $J   # 12
sed -i 's/VertexFormatElement\.get(/VertexFormatElement.byId(/g'                          $J   # 4
sed -i 's/VertexFormatElement\.ComponentType\./VertexFormatElement.Type./g'               $J   # 8
sed -i 's/RenderSystem\.enableScissor(/RenderSystem.enableScissorForRenderTypeDraws(/g'   $J   # 5
sed -i 's/RenderSystem\.disableScissor()/RenderSystem.disableScissorForRenderTypeDraws()/g' $J # 6
sed -i 's/RenderLayer::getGuiTextured\b/RenderPipelines.GUI_TEXTURED/g'                   $J   # 12 of 13
sed -i 's/RenderSystem\.getShaderColor()\[3\]/PhazeAlpha.get()/g'                         $J   # 5 of 9
sed -i '/^[[:space:]]*context\.draw();[[:space:]]*$/d'                                    $J   # 49 — see J-2
```
Add `import net.minecraft.client.gl.RenderPipelines;` where `getGuiTextured` was replaced; drop now-unused `import net.minecraft.client.render.RenderLayer;`.

**M-8 — `.getPos()`, per-site (do NOT blanket-sed — `HitResult`/`Chunk` still have it)**
```
→ .getEntityPos() :  shape/implement/Blur.java:1347
                     other/FTHelper.java:456,480,485
                     other/Predictions.java:623,682,752
                     hitrange/HitRangeCircleRenderer.java:106
                     mixins/LivingEntityRendererMixin.java:161
→ .getCameraPos() :  api/system/render/Render3DUtil.java:949
                     shape/implement/Blur.java:1269
LEAVE ALONE       :  FTHelperRenderer.java:236; Predictions.java:532,533,558;
                     PredictionsRenderer.java:656,657,669; ChunkAnimator.java:314,326
```

**M-9 — `SimpleFramebuffer` ctor** (name is the **first** arg)
```bash
# 49 hits / 6 files — do by hand, each needs a distinct debug name:
#   new SimpleFramebuffer(w, h, true)  →  new SimpleFramebuffer("phaze/hud", w, h, true)
```

**M-10 — accesswidener** (`src/main/resources/phaze.accesswidener`) — **fails at launch, not compile**
```diff
- accessible field net/minecraft/client/util/math/MatrixStack stack Ljava/util/Deque;
+ accessible field net/minecraft/client/util/math/MatrixStack stack Ljava/util/List;
- accessible field net/minecraft/client/gui/DrawContext vertexConsumers Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;
- accessible field net/minecraft/client/gui/DrawContext guiAtlasManager Lnet/minecraft/client/texture/GuiAtlasManager;
- accessible field net/minecraft/client/gl/Framebuffer depthAttachment I
+ accessible field net/minecraft/client/gl/Framebuffer depthAttachment Lcom/mojang/blaze3d/textures/GpuTexture;
+ accessible field net/minecraft/client/texture/AbstractTexture sampler Lnet/minecraft/client/gl/GpuSampler;
```
(the last line is needed by M-11)

**M-11 — texture filter → sampler** (4 sites, 2 files)
```java
// MsdfFont.java:98 (was setFilter(true,true))  |  UiMsdfIconAtlas.java:299 (was setFilter(true,false))
texture.sampler = RenderSystem.getSamplerCache().get(
        AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
        FilterMode.LINEAR, /* mag: */ FilterMode.LINEAR /* or NEAREST */, false);
// delete the `filterApplied` latch and the RenderSystem.recordRenderCall wrapper at MsdfFont.java:186
```
`MsdfFont.java:73 getGlId()` → `((net.minecraft.client.texture.GlTexture) texture.getGlTexture()).getGlId()`, guarded — CCE on any non-GL backend. Better: delete `getTextureId()` and use the existing `getTextureView()`.

**M-12 — `KeyBinding`** (`vorga/phaze/core/client/ClientMain.java:24`)
```java
// declare ABOVE the KeyBinding field — static-init order matters, create() throws on duplicate
public static final KeyBinding.Category PHAZE_CATEGORY =
        KeyBinding.Category.create(Identifier.of("phaze", "main"));

new KeyBinding("key.phaze.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, PHAZE_CATEGORY)
```
Lang key changes: `"category.phaze"` → `"key.category.phaze.main"` in every `assets/phaze/lang/*.json` (`Category.getLabel()` = `Text.translatable(id.toTranslationKey("key.category"))`).
⚠ Not verified: whether the pinned Fabric API's `KeyBindingHelper` agrees with this shape. Check the Fabric jar before assuming it compiles.

---

## 4. Judgement cases

**J-1 — 28 nonzero-Z GUI translates. The biggest item in the port.**
There is **no** matrix expression for GUI depth in 1.21.11. `RenderPipelines.GUI_SNIPPET` is `NO_DEPTH_TEST` (verified bytecode), so even emitting a nonzero Z through `vertex(Matrix4fc,x,y,z)` is ignored. Order is `GuiRenderState` root-layer order + submission order.

The 24 `InGameHudMixin` sites use exactly two constants (`HUD_RENDER_Z=400`, `HANDLE_RENDER_Z=450`, +20/+25/+50 offsets), i.e. **three discrete tiers**, not a continuum. That is the fix: replace the Z scheme with explicit layer entry, not per-element depth.

```java
// BEFORE                                              // AFTER
context.getMatrices().translate(x, y, HUD_RENDER_Z);   context.createNewRootLayer();
                                                       context.getMatrices().translate(x, y);
```
Sites (all verified line numbers):
`InGameHudMixin` 1154,1241,1479,1585,1764,1837,2153,2193,2361,2623,2663,2778,3151,3270,3404,3424,3444,4251,5168,5386,5425 · `MainMenuScreen` 432,818,1179,1253 · `AbstractWindow`:75 (280) · `ShulkerPreview`:267 (500) · `PhazeBadgeUtil`:136 (this one is `translate(round(x), round(y+1), 0.0F)` — **zero Z**, M-4 catches it; it only appears in the nonzero grep because of the comma inside `Math.round(...)`).

`createNewRootLayer()` is `currentLayer = new Layer(null); rootLayers.add(currentLayer)` — cheap, but every call fragments batching. Budget a dedicated visual pass.

**J-2 — deleting the 49 `context.draw()` calls.**
`DrawContext.draw()` no longer exists; `drawDeferredElements()` is **not** a substitute (it only re-emits hover/click Style tooltips → double-drawn tooltips). Deletion compiles and never crashes, but in 1.21.4 that call flushed the immediate `VertexConsumerProvider`, which is what made Phaze's raw-GL / Blur / MSDF / `BatchedRectangle` draws interleave correctly with vanilla GUI. Nothing flushes mid-frame now. Distribution: `InGameHudMixin` 25, `ExordiumAnimationBridge` 5, `PlayerListHudMixin` 5, `ShulkerPreview` 3, `AbstractWindow`/`MenuPanoramaRenderer`/`ChatScreenInputFieldMixin`/`HandledScreenMixin` 2 each, 4 files ×1.
Plan: delete all 49, then re-add `createNewRootLayer()` only where a z-order regression is actually observed. Fold into the same visual pass as J-1.

**J-3 — global alpha.** `RenderSystem` has **no** colour accessor of any kind (42-method dump confirmed). There is nothing to read. New file `api/system/draw/PhazeAlpha.java`:
```java
public final class PhazeAlpha {
    private static final float[] STACK = new float[32];
    private static int depth;
    static { STACK[0] = 1.0F; }
    private PhazeAlpha() {}
    public static float get() { return STACK[depth]; }
    public static void push(float mul) { STACK[depth + 1] = STACK[depth] * mul; depth++; }
    public static void pop() { if (depth > 0) depth--; }
    public static void reset() { depth = 0; STACK[0] = 1.0F; }
    public static int tint(int argb) { return ColorHelper.scaleAlpha(argb, STACK[depth]); }
}
```
- 5 sites are pure `get()` (M-7 sed): `Arc:98`, `Blur:295,567,667`, `BatchedRectangle:414`.
- `Render2DUtil:154` → `ColorUtil.multAlpha(color, PhazeAlpha.get())`.
- `WorldColorRenderHelper:41` — the whole `ThreadLocal` float[] pool collapses to `PhazeAlpha.push(k)` / `.pop()`. Delete `ENTITY_SHADER_COLOR_POOL` and `ENTITY_SHADER_COLOR_STACK`.
- `CardSnapshotCache:304` — field `float[] savedShaderColor` → `float savedAlpha`.
- `mixins/DrawContextItemRenderStateMixin.java` — **delete the file and its mixin-JSON entry.** All six of its calls are gone and its purpose (scrubbing leaked global GL state) is moot: state now travels with the pipeline. But note this removes the safety net, so `PhazeAlpha.reset()` must be **actually wired** at HUD render entry and at `Screen.render` entry, and every push/pop wrapped in try/finally. A missed site does not fail to compile — it renders at alpha 1.0 and fade animations pop instead of fading.

**J-4 — GPU state → pipelines (~250 sites).** Cannot be fixed in place; each call must be traced to the draw it guards. Build one `PhazePipelines` holder next to the existing `GpuDraw`; expect 6-10 constants.
```java
public static final BlendFunction PHAZE_DEFAULT_BLEND =
        new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                          SourceFactor.ONE,       DestFactor.ZERO);
```
⚠ **`BlendFunction.TRANSLUCENT` is NOT `defaultBlendFunc`.** TRANSLUCENT's dstAlpha is `ONE_MINUS_SRC_ALPHA`; the old default was `ZERO`. Invisible on the main framebuffer, **not** invisible on alpha-carrying offscreen targets — Blur ping-pong, `CardSnapshotCache`, `ExordiumAnimationBridge` HUD cache all accumulate destination alpha and go progressively milkier the longer they stay cached. Use the explicit constant above for anything targeting an offscreen buffer.
Mapping: `depthMask(b)`→`.withDepthWrite(b)`; `enableDepthTest`→`.withDepthTestFunction(LEQUAL_DEPTH_TEST)`; `disableDepthTest`→`NO_DEPTH_TEST`; `enable/disableCull`→`.withCull(b)`; `blendFuncSeparate(a,b,c,d)`→`new BlendFunction(a,b,c,d)`.
The awkward long tail is the 6 mixins that guard **vanilla's** draws (`InGameHudMixin`, `PlayerListHudMixin`, `HandledScreenMixin`, `WorldRendererSkyDomeTintMixin`, `CloudRendererWorldColorMixin`, `TitleScreenVanillaSwitchMixin`) — you cannot wrap vanilla's pipeline choice from outside; each needs a redirect on the specific draw or a Phaze re-implementation. Cost separately.

**J-5 — `copyTextureToTexture` cannot scale or filter.** Bytecode hardcodes `9728` (GL_NEAREST) and uses the same w/h for both rects. `Blur.java:1026-1041 captureFramebufferInput` (blits framebuffer→smaller target) and `Blur.java:1090 blitColorRegion(..., GL_LINEAR)` will either throw from bounds validation or hard-alias. Both must become a fullscreen-quad shader pass sampling with a `FilterMode.LINEAR` sampler. Cleanest: fold the downsample into the existing dual-Kawase pass. The 1:1 NEAREST blits (`CardSnapshotCache.copyRegionFromFramebuffer`, `Blur:941,947,994,1004`) map cleanly: `copyTextureToTexture(srcTex, dstTex, mip, dstX, dstY, srcX, srcY, w, h)`.

**J-6 — nested render passes throw.** `GlCommandEncoder` holds a `renderPassOpen` flag: `IllegalStateException("Close the existing render pass before creating a new one!")`, and clears/copies throw `"…before performing additional commands"`. `GuiRenderer` keeps **one** pass open across the whole GUI batch. Any Phaze blur/copy/FBO work running mid-HUD-render crashes at **runtime**. Blur must move to either (a) before `GuiRenderer.render`, or (b) a custom `SpecialGuiElementRenderer` — `prepare()` runs entirely before `renderPreparedDraws()` opens the pass, which is exactly why vanilla renders items/entities offscreen there.

**J-7 — HUD capture scope moved.** `InGameHud.render` no longer draws; it appends to `GuiRenderState`. Porting the `MinecraftClient.getFramebuffer()` redirect mixin unchanged compiles, runs, and captures an **empty** framebuffer. Move the injection to `net.minecraft.client.gui.render.GuiRenderer#render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V` (HEAD sets `activeCaptureTarget`, RETURN clears). `HudBuffer.activeCaptureTarget` becomes a `Framebuffer`/`GpuTextureView`, not an `int`. Also: `outputColorTextureOverride` does **not** cover the GUI batch (`renderPreparedDraws` reads `MinecraftClient.getFramebuffer()` directly) — but it **does** cover `RenderLayer.draw`/`WorldRenderer`. Two mechanisms; don't assume one covers both.

**J-8 — `Blur.java`: 40 `getUniformOrDefault` + 10 `setShaderTexture` + all its shaders.** `ShaderProgram.getUniform(String)` still exists and returns non-null — **a trap**: `GlUniform` is a bare marker interface (`public default void close()`) whose only impls are metadata records. No `.set(...)` anywhere. Every loose `uniform vec2 Size;` in Phaze GLSL becomes a `layout(std140) uniform PhazeParams { … };` block declared via `.withUniform("PhazeParams", UniformType.UNIFORM_BUFFER)` and bound with `pass.setUniform("PhazeParams", slice)`. This is a rewrite. Stub Blur (`// TODO 1.21.11 pipeline`) to reach a compiling tree, then do it properly. Same for `ColorCorrectionShader`, `GlintBloomRenderer`, `motionblur/Shader`.
Sampler names must match `.withSampler(name)` exactly — a typo is silently ignored and renders **black**, not an exception. Samplers come from `SamplerCache` and must never be closed.

**J-9 — `PostEffectShader.java:113 pass.getProgram().getUniform(name)` — no replacement at all.** `PostEffectPass` exposes only `render(...)` and `close()`; uniform values come from the JSON pipeline definition, baked into private `uniformBuffers` at construction. Driving them from Java means rebuilding the `PostEffectProcessor` per value change or access-widening `uniformBuffers` and writing the `GpuBuffer`s by hand. Rewrite, not port.

**J-10 — `RenderLayer::getGuiTexturedOverlay` (`ShulkerPreview.java:368`) — no replacement.** No pipeline in `RenderPipelines` matches the old depth-equal/no-depth-write overlay semantics. Substituting `GUI_TEXTURED` compiles and draws the back-panel *on top of* the item. Fix via layer ordering (`createNewRootLayer` before the item), not a pipeline swap.

**J-11 — `VertexFormats.LINES`** (9 sites). Use `POSITION_COLOR_NORMAL_LINE_WIDTH` (this is what `RenderPipelines.LINES` is built with) and add a per-vertex `.lineWidth(w)`:
```java
buffer.vertex(matrix, x, y, z).color(argb).normal(entry, nx, ny, nz).lineWidth(w);
```
This is also where the 8 `RenderSystem.lineWidth` sites go. `PhazeRenderLayers.java:65` needs `.withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH, DrawMode.LINES)` on a pipeline derived from `RenderPipelines.LINES` — the `core/rendertype_lines` shader reads the LINE_WIDTH attribute and will not link against `POSITION_COLOR_NORMAL`.

**J-12 — `Matrix3x2f` traps that compile clean.**
- `transform(x, y, 0, Vector3f)` has the **same erased signature** as `Matrix4f.transformPosition(x,y,z,Vector3f)`. The third float is homogeneous **W**, not Z. Passing `0` zeroes out `m20`/`m21` — every translation vanishes and everything renders at the window origin, correctly scaled. Use `transformPosition(x, y, Vector2f)` for points, `transformDirection(x, y, Vector2f)` for vectors. `BatchedRectangle.java:417` currently passes `0` and must not be mechanically converted.
- `rotate()` takes **radians**; `RotationAxis.POSITIVE_Z.rotationDegrees()` took degrees. ~57× over-rotation, no warning. Sites: `SettingComponent:51`, `Image:80`, `BackgroundComponent:763`.
- `ShapeProperties` must hold a **copy** (M-6). Storing the live `Matrix3x2fStack` means deferred consumers (`CardSnapshotCache`, Blur's deferred passes, batching) read the pose *after* the caller popped it. Vanilla copies unconditionally at all 7 `DrawContext` render-state construction sites.
- `Matrix3x2fStack.hashCode()/equals()` cover the **entire saved stack**, not the top matrix. Copy to a plain `Matrix3x2f` before using a pose as a map key (`CardSnapshotCache`).
- `pushMatrix()/popMatrix()` return `Matrix3x2fStack`, but inherited `translate()/scale()/rotate()` return `Matrix3x2f` — `stack.translate(x,y).popMatrix()` does not compile, and assigning `translate()`'s result to a local gives an **alias**, not a snapshot.

**J-13 — hard stack cap of 15 pushes.** `new Matrix3x2fStack(16)` ⇒ `mats.length == 15`; the 16th `pushMatrix()` throws `IllegalStateException`, and there is **no way to grow it** (array is fixed, DrawContext's stack-taking ctor is private). `MatrixStack` was an unbounded `List`. Mitigations in order: (1) prefer `scaleAround`/`rotateAbout` — costs **zero** stack levels vs push+3 ops+pop; (2) save/restore into an owned local (`Matrix3x2f saved = new Matrix3x2f(stack); … stack.set(saved);`) in deep leaf components; (3) mixin a larger stack into DrawContext's private ctor (safe but ugly). **Audit the deepest menu path (`MenuScreen → AbstractWindow → Category → Module → Setting → Group → Color → MathUtil.scale`) before shipping — this crashes a user, not a compiler.**

**J-14 — `MathUtil.scale` (2 methods, 3 call sites, but the highest-leverage rewrite).** Collapses 5 stack ops into 1 and buys back J-13 headroom:
```java
public void scale(Matrix3x2fStack stack, float x, float y, float scale, Runnable data) {
    if (scale != 1) {
        float s = 0.5F + scale / 2;
        stack.pushMatrix();
        stack.scaleAround(s, x, y);          // scaleAround(float factor, float ox, float oy)
        setAlpha(scale, data);
        stack.popMatrix();
    } else data.run();
}
public void scale(Matrix3x2fStack stack, float x, float y, float sx, float sy, Runnable data) {
    float sum = sx * sy;
    if (sum != 1) {
        stack.pushMatrix();
        stack.scaleAround(sx, sy, x, y);     // scaleAround(float sx, float sy, float ox, float oy)
        setAlpha(sum, data);
        stack.popMatrix();
    } else data.run();
}
```
(`MathUtil.java:31` also has `getTickDelta` — M-7 handles it.)

**J-15 — `DrawContext.enableScissor` argument convention.** Old `RenderSystem.enableScissor(x, y, w, h)`; new `DrawContext.enableScissor(x1, y1, x2, y2)` — bytecode is `new ScreenRect(x1, y1, x2-x1, y2-y1)`. Same arity, same types, **no compiler error**, region silently far too large. It is also a *stack* (a stray `disableScissor` pops an outer region) and it is **transformed by the pose**. `CardSnapshotCache.java:385` already carries a comment `// RenderSystem.enableScissor takes (x, y, width, height)` — true of the method it names, false of `DrawContext.enableScissor`, and the two are one careless swap apart. `enableScissorForRenderTypeDraws` keeps the old convention and is the safe mechanical target (M-7).

---

## 5. Ordering & file-count estimate

### Wave A — reach a compiling tree

| # | Step | Files | Nature |
|---|---|---|---|
| A0 | accesswidener (M-10) — fails at *launch*, fix before anything masks it | 1 | 5 min |
| A1 | Trivial renames M-7 + M-8 (`getTickProgress`, `byId`, `Type`, `getEntityPos`/`getCameraPos`, scissor rename, `GUI_TEXTURED`) | ~20 | 30 min |
| A2 | **Add `GuiMatrix.java`; rewrite `ShapeProperties` (M-6)** — unblocks 148 + 8 sites | 2 new/edit | 30 min |
| A3 | **Matrix mechanical M-1…M-5** (decls, push/pop, scale, zero-Z translate, pose read) | **~50** | 3-4 h |
| A4 | `MathUtil.scale` (J-14), `Render2DUtil` lines 27/28/41/58/77/93/145, the 3 degree→radian rotates | 5 | 1 h |
| A5 | **The 28 nonzero-Z translates (J-1)** — compiler forces every one | 5 (`InGameHudMixin` 21, `MainMenuScreen` 4, `AbstractWindow`, `ShulkerPreview`) | 3-4 h |
| A6 | Delete 49 `context.draw()` (J-2); `getGuiTexturedOverlay` (J-10) | 11 | 1 h |
| A7 | `PhazeAlpha` + delete `DrawContextItemRenderStateMixin` + mixin JSON (J-3) | 9 | 2 h |
| A8 | `SimpleFramebuffer` ctor (M-9), `setTexFilter`→sampler (M-11), `getGlId`, `setClearColor`, `recordRenderCall` | 8 | 2 h |
| A9 | `KeyBinding` + lang files (M-12) | 1 + lang | 30 min |
| A10 | `PhazePipelines` holder + blend/depth/cull migration (J-4), non-mixin files first | ~25 | 1-2 days |
| A11 | **Stub** Blur / `PostEffectShader` / `ColorCorrectionShader` / `GlintBloomRenderer` / `motionblur.Shader` to compile | 6 | 2 h |
| A12 | `VertexFormats.LINES` → `POSITION_COLOR_NORMAL_LINE_WIDTH` + per-vertex `lineWidth` (J-11) | 4 | 2 h |
| A13 | The 6 vanilla-guarding mixins (J-4 long tail) | 6 | 1 day |

**Wave A total: ~85 distinct files.** `InGameHudMixin.java` alone carries 142 `getMatrices()`, 25 `context.draw()`, 21 Z-translates — **port it as one unit and rebuild before touching anything else** (a descriptor mistake in a mixin surfaces as a runtime mixin-apply failure, not a clean javac error).

### Wave B — make it correct (nothing here blocks compilation)

| # | Step | Files |
|---|---|---|
| B1 | HUD capture scope: mixin `GuiRenderer#render`, not `InGameHud#render` (J-7) | 3 |
| B2 | Move Blur/snapshot work out of the open GUI pass (J-6) — `SpecialGuiElementRenderer` or pre-GuiRenderer | 4 |
| B3 | Blur/ColorCorrection/Glint/motionblur: real `RenderPipeline` + std140 UBO blocks + GLSL rewrite (J-8) | 6 + shaders |
| B4 | `PostEffectShader` rewrite (J-9) | 1 |
| B5 | Scaling blits → shader passes (J-5) | 2 |
| B6 | Offscreen-target blend audit: `defaultBlendFunc` ≠ `TRANSLUCENT` (J-4 ⚠) | 3 |
| B7 | Stack-depth audit against the 15-push cap (J-13) | audit |
| B8 | **Visual layering pass** — J-1 + J-2 together, side-by-side against 1.21.4 | ~11 |

**Rebuild checkpoints:** after A3, after A5, after A7, after A10, after A13. `port-build-full.log` currently shows 2376 errors / 108 files; A2+A3 alone should clear well over half.

**Do not** try to make `getUniform(...).set(...)` compile, and **do not** reach for raw LWJGL GL as a fallback — `GlStateManager` exists but caches `readFbo`/`writeFbo` while `GlCommandEncoder` caches `currentPipeline`/`currentProgram`; mixing them inside a pass corrupts subsequent vanilla draws in ways that look like unrelated rendering bugs.

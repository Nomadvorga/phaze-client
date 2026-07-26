# 1.21.4 → 1.21.11 porting notes

Verified against the loom-remapped `minecraft-merged` jar for
`1.21.11 / yarn 1.21.11+build.6`. Everything below is read off the actual
class files, not inferred.

## Progress

| Checkpoint | Errors | Files affected |
|---|---|---|
| Baseline (sources copied as-is) | 142 | 41 / 386 |
| After package-move sweep | 100 | 40 / 386 |

345+ of 386 files already compile untouched.

## Done: package moves

Pure import rewrites, no behaviour change.

| 1.21.4 | 1.21.11 |
|---|---|
| `net.minecraft.client.render.VertexFormat` | `com.mojang.blaze3d.vertex.VertexFormat` |
| `net.minecraft.client.render.VertexFormatElement` | `com.mojang.blaze3d.vertex.VertexFormatElement` |
| `com.mojang.blaze3d.platform.GlStateManager` | `com.mojang.blaze3d.opengl.GlStateManager` |

Unchanged and still present, contrary to first expectations:
`net.minecraft.client.gl.Framebuffer`, `SimpleFramebuffer`,
`net.minecraft.client.render.Tessellator`, `BufferBuilder`,
`net.minecraft.client.render.VertexFormats`.

That matters a lot for `Blur` — its framebuffer ping-pong machinery
survives intact. Only the draw submission inside it has to change.

## Remaining blockers, by symbol

| Missing symbol | Hits | Replacement |
|---|---|---|
| `BufferRenderer` | 42 | gone — see draw model below |
| `ShaderProgramKey` / `ShaderProgramKeys` | 64 | `com.mojang.blaze3d.pipeline.RenderPipeline` / `net.minecraft.client.gl.RenderPipelines` |
| `BackgroundRenderer`, `Fog`, `FogShape` | 24 | fog mixins — targets restructured |
| `EntityRenderDispatcher`, `BlockEntityRenderDispatcher` | 16 | world-color mixins — signatures changed |
| `RenderPhase`, `MultiPhase` | 16 | `HitRangeCircleRenderer` custom layers |
| `ObjectAllocator`, `Pool` | 10 | render dispatch signature change |
| `VertexBufferWriter` | 6 | staged Sodium dep, not real work |
| `BakedModel` | 6 | item render state |

## The draw model change

This is the whole of step 1. `RenderSystem.setShader` and
`BufferRenderer.drawWithGlobalProgram` no longer exist. Drawing is now:

```
RenderSystem.getDevice()            -> com.mojang.blaze3d.systems.GpuDevice
  .createCommandEncoder()           -> CommandEncoder
  .createRenderPass(...)            -> RenderPass  (AutoCloseable)
      .setPipeline(RenderPipeline)
      .bindTexture(String, GpuTextureView, GpuSampler)
      .setUniform(String, GpuBufferSlice)
      .setVertexBuffer(int, GpuBuffer)
      .setIndexBuffer(GpuBuffer, VertexFormat.IndexType)
      .drawIndexed(int, int, int, int)
```

Per-draw uniforms are no longer individual `GlUniform` objects. They are
declared on the pipeline and written through a uniform buffer —
`RenderSystem.getDynamicUniforms()`, bound with
`RenderPass.setUniform(name, GpuBufferSlice)`.

Custom pipelines are buildable, so every Phaze shader can keep its own:

```java
RenderPipeline.builder()
    .withLocation("phaze/mask")
    .withVertexShader(Identifier.of("phaze", "core/mask"))
    .withFragmentShader(Identifier.of("phaze", "core/mask"))
    .withSampler("Sampler0")
    .withUniform("Size", UniformType.VEC2)
    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
    .withBlend(BlendFunction.TRANSLUCENT)
    .withCull(false)
    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
    .build();
```

Full `RenderPipeline.Builder` surface: `withLocation`, `withVertexShader`,
`withFragmentShader`, `withShaderDefine`, `withSampler`, `withUniform`,
`withDepthTestFunction`, `withPolygonMode`, `withCull`, `withBlend`,
`withoutBlend`, `withColorWrite`, `withDepthWrite`, `withColorLogic`,
`withVertexFormat`, `withDepthBias`, `buildSnippet`, `build`.

### Consequences for Phaze

1. Every custom shader needs a `RenderPipeline` constant instead of a
   `ShaderProgramKey`.
2. Every draw site (`Rectangle`, `Arc`, `Image`, `InvertedRectangle`,
   `InvertedArc`, `BatchedRectangle`, `DrawEngineImpl`, `Blur`,
   `FontRenderer`, `MsdfRenderer`, `CardSnapshotCache`, `BatchedHudBuffer`)
   swaps `BufferRenderer.drawWithGlobalProgram(builder.end())` for the
   `RenderPass` sequence above.
3. Every custom `.fsh` / `.vsh` needs its uniform declarations moved into
   the UBO layout the new pipeline expects; the old
   `shader.getUniformOrDefault("X").set(...)` call sites go away.
4. `BatchedRectangle`'s custom GENERIC vertex attributes (`RectBase`,
   `RectSize`, `Radius`, `Params`, `OutlineColor`) need re-declaring as a
   `VertexFormat` built from `com.mojang.blaze3d.vertex.VertexFormatElement`.

## Step 1 findings: the pattern, verified

Three facts change the plan materially.

### 1. `RenderLayer.draw(BuiltBuffer)` still exists

`net.minecraft.client.render.RenderLayer` has `draw(BuiltBuffer)` and
`getRenderPipeline()`. So a custom draw does **not** need hand-rolled
`GpuDevice` / `CommandEncoder` / `RenderPass` code — wrap the custom
`RenderPipeline` in a `RenderLayer` and call `layer.draw(buffer.end())`.
That removes most of the boilerplate feared above.

`net.minecraft.client.gl.Framebuffer` also exposes
`getColorAttachmentView()` / `getDepthAttachmentView()`, which is what
`Blur` will need for its ping-pong targets.

### 2. Individual uniforms are gone — it is UBO or nothing

`UniformType` has exactly two constants: `UNIFORM_BUFFER` and
`TEXEL_BUFFER`. There is no `VEC2` / `VEC4` / `FLOAT`. So
`shader.getUniformOrDefault("size").set(w, h)` has no direct equivalent:
every custom uniform must be packed into a std140 block.

Vanilla's own shaders show the shape:

```glsl
#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
```

```glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
```

Shader assets keep the old location (`assets/<ns>/shaders/core/*.vsh|fsh`)
and gain `#moj_import` includes.

For `Rectangle` that would mean hand-packing a nine-field std140 block
(`size`, `location`, `radius`, `softness`, `thickness`, `color1..4`,
`outlineColor`) per draw, and getting std140 alignment right by hand for
every one of Phaze's ~10 custom shaders. That is exactly where silent
"shader reads garbage" bugs live.

### 3. Phaze already has the uniform-free variant

`BatchedRectangle` + `phaze:core/round_batched` already carry every
per-rect parameter as `GENERIC` vertex attributes (`RectBase`,
`RectSize`, `Radius`, `Params`, `OutlineColor`) with the gradient done
through the standard `COLOR` attribute. Its own javadoc states the output
is pixel-identical to the uniform path.

That is precisely the architecture 1.21.11 wants: no custom uniforms at
all, so it composes with `RenderLayer.draw(BuiltBuffer)` directly.

### Recommended design

Make the vertex-attribute path the **only** path:

- delete `Rectangle`'s eager 9-uniform branch, always route through the batch
- give `BatchedRectangle` a custom `RenderPipeline` + `RenderLayer`
- port `round_batched.vsh/fsh` to `#version 330` + `#moj_import`
- repeat the shape for `Arc`, `Image`, `Inverted*`

Upside: no std140 hand-packing anywhere, fewer draw calls, and it matches
the direction the engine moved.

**Open question for the operator:** today, if `VertexFormatElement.register`
cannot allocate GENERIC slots (Sodium / Iris having claimed them), Phaze
falls back to the eager uniform path. Under this design that fallback
disappears — there would be no non-attribute path left. Options are to
accept that, or to keep a reduced uniform-based fallback with a
hand-packed UBO purely for that case.

Order after the decision: `BatchedRectangle` (pipeline + layer + shader)
→ `Rectangle` → the other shapes → `Blur` → the two font renderers.

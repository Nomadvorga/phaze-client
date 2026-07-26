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

Order to work in: `DrawEngineImpl` and one simple shape (`Rectangle`)
first to establish the pattern, then the rest of the shapes, then
`BatchedRectangle` (custom format), then `Blur`, then the two font
renderers.

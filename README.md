# Phaze Client — 1.21.11 port

Port of Phaze Client from Minecraft 1.21.4 to 1.21.11 (Fabric).

Upstream 1.21.4 sources live in `D:\projects\Phaze Client` and remain the
working version. This repository is the port in progress.

## Toolchain

| | |
|---|---|
| Minecraft | 1.21.11 |
| Yarn | 1.21.11+build.6 |
| Fabric Loader | 0.18.1 |
| Fabric API | 0.141.4+1.21.11 |
| Fabric Loom | 1.14.10 |
| Gradle | 9.5.1 |
| Java | 21 |

Versions cross-checked against the `mc1.21.11` module of the InvBinds
project, which already builds and ships on this Minecraft version.

## Scope decisions

**Custom Glint is not ported.** Deliberately out of scope. Removed:

- `implement/features/modules/other/CustomGlint.java`
- `implement/glint/` (`GlintBloomLayers`, `GlintBloomRenderer`,
  `GlintTextureService`, `LicensedCustomGlintBridge`)
- `mixins/ItemRendererGlintBloomMixin`, `mixins/ItemRendererLicensedGlintBloomMixin`
- `assets/phaze/shaders/core/glint_bloom.*`
- the `overlayLicensedCustomGlint` Gradle task and its licensed-jar overlay

All call sites in `Main`, `ClientMain`, `GameRendererMixin` and
`phaze.mixins.json` were cleaned up with it.

**Third-party compat mixins are staged, not deleted.** Sodium, Iris and
Exordium mixins are parked under `compat-staging/mixins/` so the core port
can be measured and fixed against vanilla alone. `compat-staging/phaze.mixins.json.full`
keeps the original mixin list for restoring them.

Re-enabling Sodium compat also needs the `fabric-loom` plugin bumped to
>= 1.16.1 — Sodium 0.8.13 publishes Loom 1.16.1 metadata, which 1.14.10
refuses to consume.

## Why this is a staged port

1.21.4 → 1.21.11 spans Mojang's rendering rewrite: `ShaderProgram` /
`RenderSystem.setShader`, the `Framebuffer` hierarchy, `Tessellator` /
`BufferBuilder` usage and the post-effect pipeline were all replaced by
the `RenderPipeline` / `GpuDevice` model. Phaze leans on every one of them.

Measured surface in the carried-over sources:

| API touched | Files |
|---|---|
| `DrawContext` | 73 |
| `MatrixStack` | 59 |
| `ShaderProgram` / `RenderSystem.setShader` | 38 |
| `Tessellator` / `BufferBuilder` | 30 |
| `VertexConsumer` | 30 |
| `RenderLayer` | 18 |
| `Framebuffer` / `SimpleFramebuffer` | 16 |
| `PostEffectProcessor` / `GlUniform` / `ShaderLoader` | 11 |

Plus 85 mixins whose injection targets each need re-verifying against
1.21.11 mappings, and a 91-line access widener referencing members that
partly no longer exist.

## Local build prerequisites

`libs/` (arboard, native-utils) is gitignored, same as the upstream repo.
Copy it in from `D:\projects\Phaze Client\libs` before building.

```
.\gradlew.bat build
```

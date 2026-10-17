# PackPreviewAssets integration contract

`src/main/java/vorga/phazeclient/implement/features/modules/other/PackPreviewAssets.java`
is the data loader used by `PackPreviewRenderer.java`.

## Ownership and public API

Load **once per inspected profile**, on the render thread, and retain the handle:

```java
PackPreviewAssets.Data data = PackPreviewAssets.load(client, packId);
List<PackPreviewAssets.Sprite> fourBlocks = data.blocksAt(elapsedMillis);
PackPreviewAssets.Sprite bow = data.items().get("bow");
PackPreviewAssets.Sprite fullHeart = data.hearts().get(0);
// On screen removal, profile changes, and resource reload, on the render thread:
data.close(); // idempotent; releases ALL preview textures
```

`load(MinecraftClient, String)` always returns populated collections for a missing,
unreadable, or disabled profile. An unavailable/corrupt individual custom asset
falls back independently. A missing vanilla asset produces a private generated
checkerboard and a diagnostic. Unexpected upload failures release already-created
textures and propagate to the caller.

`Sprite` is a public record with these accessors:

| Accessor | Meaning |
| --- | --- |
| `id()` | Private `phaze:pack_preview_assets/<unique load>/<number>` dynamic texture |
| `width()`, `height()` | Actual uploaded dimensions; draw the entire texture |
| `originalWidth()`, `originalHeight()` | Source frame dimensions before the 256px preview cap |
| `custom()` | Texture came from the inspected pack; for composed hearts, either layer was custom |

Every sprite, including every vanilla fallback, is decoded and registered as a
`NativeImageBackedTexture`. No returned identifier points to an ordinary vanilla
resource, atlas, or active-pack texture. All color access uses
`NativeImage.getColorArgb` / `setColorArgb`.

`Data` implements `AutoCloseable` and exposes:

- `packId()` — inspected profile ID.
- `blockBatches()` — immutable list of immutable, exactly-four-sprite batches.
- `blocksAt(long elapsedMillis)` — selects an existing batch every **1500ms**;
  no file access, decoding, registration, or new sprite lists.
- `items()` — insertion-ordered immutable map: `bucket`, `lava_bucket`,
  `water_bucket`, `golden_apple`, `ender_pearl`, `bow`, `bow_pulling_0`,
  `bow_pulling_1`, `bow_pulling_2`, `iron_sword`, `iron_pickaxe`,
  `diamond_sword`, `diamond_pickaxe`, `totem_of_undying`.
- `hearts()` — immutable list: **full, half, empty**, each with its container
  border. Container and fill independently prefer custom assets, then vanilla;
  differing resolutions are nearest-neighbor rescaled and alpha-composited.
- `particles()` — insertion-ordered immutable map: `critical_hit`, `enchanted_hit`.
- `resolution()` — most common usable **custom block/item frame width**, with
  a smaller-width tie break; **0 means unknown/no custom block or item PNG**.
  Fallbacks, hearts, particles, and lightmaps do not vote. Widths below 16 are
  preserved, and one oversized icon does not override a common pack resolution.
- `changedBlockCount()` — number of verified changed canonical block IDs in
  the bounded scan, before the retained-sprite cap. A lower bound if limited.
- `animations()`, `lightmap()`, `sounds()` — inspected-pack detection only.
- `diagnostics()` — immutable, deduplicated messages (at most 32) describing
  malformed/unavailable assets and scan/decode/retention limits.
- `textureBytes()` — registered RGBA image bytes; GPU copies use about this
  amount, and `NativeImageBackedTexture` retains equally sized CPU images.
- `isClosed()`, `close()` — ownership state and explicit release.

There is no static Data cache. The integrating screen must bound its own cache
and close all handles on eviction. Do not call `load` inside each render pass.
Never retain sprite IDs after their owning handle is closed.

## Discovery and selection

- Opens `ResourcePackProfile.createResourcePack()` directly, including disabled
  profiles. Iterates sorted `pack.getNamespaces(CLIENT_RESOURCES)` and calls
  `findResources` with the nonempty prefixes `textures`, `sounds`,
  `optifine/lightmap`, and `mcpatcher/lightmap`. Reads `sounds.json` directly.
  Empty prefixes must not be used: real folder packs reject them.
- Vanilla reads use **only** `client.getDefaultResourcePack().open(...)`.
  The active `ResourceManager` is never consulted. The shared default pack is
  not closed; the independently opened profile is closed after inspection.
- Block candidates include `textures/block/**/*.png` and
  `textures/blocks/**/*.png` across namespaces. Canonical aliases are deduplicated,
  sorted, then deterministically shuffled using the profile ID hash.
- Equal encoded PNGs or equal decoded ARGB pixels with equal sheet dimensions
  are **not changed**. RGB differences in fully transparent texels are ignored.
  Re-encoded identical copies do not count. A PNG without a vanilla counterpart
  under its canonical namespaced ID counts as new/changed.
- At least four verified changes: rotate the retained changed textures by one
  position every 1500ms, preserving four distinct choices, including when exactly
  four textures were changed. Fewer than four: always the fixed **vanilla** dirt,
  oak_log, grass_block_top, sand set, in that order.
- Item/block paths prefer modern singular directories, then plural legacy
  directories. Explicit legacy renames include `bow_standby`, `totem`, `log_oak`,
  and `grass_top`. Particle aliases include `crit` and `magic_crit`.
- Hearts prefer individual modern HUD sprites. Classic `textures/gui/icons.png`
  is supported at integer multiples of 256x256: empty at (16,0), full at (52,0),
  half at (61,0), each 9x9 in base-atlas coordinates.
- Textures display their first declared animation frame (or first frame by
  default). Valid metadata width/height overrides determine frame size. This
  loader supplies static preview sprites, not animated GPU textures.

## Detection semantics

- **Animations:** a custom `textures/**/*.png.mcmeta` with a strict-JSON
  `animation` object and a corresponding PNG header. `{ "animation": {} }` is
  valid. Positive integer dimensions/frametime, nonnegative frame indices,
  positive per-frame times, frame geometry, and frame-index bounds are checked.
  A bare `.mcmeta`, unrelated metadata, missing PNG, or malformed entry is false.
- **Sounds:** either a file at `sounds/**/*.ogg` with `OggS` magic (including
  totem sounds), or a strict-JSON `sounds.json` with at least one valid named
  event whose `sounds` array is nonempty. Strings and `{name,type}` file/event
  entries are supported; present replace/stream/preload flags, subtitle, numeric
  weight, volume, pitch and attenuation values are validated. Empty files,
  empty objects, empty sound arrays, and invalid definitions do not count.
  Definitions may reference vanilla sounds/events; local OGG files are not
  required in that case. Detection covers all indexed namespaces.
- **Lightmaps:** PNG-header-backed `optifine/lightmap/**/*.png`,
  `mcpatcher/lightmap/**/*.png`, or `textures/misc/lightmap.png`.

## Bounds and limitations

- Up to 256 namespaces and 16,384 relevant indexed resource IDs.
  The retained index is lexicographically bounded, independent of enumeration order.
- Up to 2,048 canonical block comparisons, 2,048 additional item resolution
  candidates, and 2,048 animation metadata checks.
- Custom decode budget: 64 * 1024 * 1024 pixels cumulatively per load; required
  item/heart/particle icons are prioritized before optional block scanning.
- Total custom PNG/JSON reads are capped at 128MiB, including failed/oversized
  reads, so repeated invalid PNGs cannot cause gigabytes of decompression.
- Each PNG: at most 8MiB encoded, 4 * 1024 * 1024 pixels, and 8192px per edge;
  each JSON: at most 256KiB. PNG dimensions are checked before native decoding.
- Up to 64 retained block sprites and 19 other sprites, capped to 256px per edge:
  at most **20.75MiB uploaded RGBA**, plus the same retained CPU texture memory.
  Decoding/comparison temporarily holds full PNG images, encoded bytes and pixel
  arrays; it is bounded but can temporarily exceed the retained-image budget.
- Index/decode caps can make detection/resolution incomplete; inspect diagnostics.
  Underlying `findResources` still enumerates the pack once; this API does not
  offer cancellation of directory/ZIP enumeration.
- OGG detection validates magic, not audio codec/decodability. Animation/lightmap
  discovery validates PNG headers, not every PNG payload. Rendered assets are
  fully decoded, with per-asset fallback on failure.
- Identical-pixel comparison covers full PNG sheets, not timing-only metadata
  changes. A dimension-changing resample counts as changed even if visually
  similar. Legacy numeric block/item renames beyond the listed aliases, old
  terrain/item atlases, particle atlases, CIT/model-driven overrides, OptiFine
  conditional resources, and tinted block rendering are not interpreted.

## Checks

The normal offline build includes the scanner, search, cycle and composition checks:

```text
./gradlew build --offline
```

`./gradlew verifyPackPreviewAssets --offline` runs only those checks. The scanner
tests use the production code and an isolated ResourcePack implementation, with
totem OGG, JSON sound definitions, animation and lightmap fixtures.

An optional graphical integration run uses the existing Fabric client GameTest API:

```text
./gradlew runPackPreviewSmoke --offline
```

It creates disposable folder/ZIP packs and a genuinely enabled overriding pack,
checks loading and fallback pixels, opens the real PackScreen, tests search and
takes screenshots. All its files/configs are under `build/pack-preview-client-test/`;
the test mod is never included in `build/libs/PhazeClient-1.2.jar`.

## Standalone JDK-only logic check

Requires Node (already used by project scripts) and JDK 21+ on PATH, or JAVA_HOME:

```text
node scripts/test-pack-preview-assets.mjs
```

This command compiles **only the pure helper and its test** in an automatically
removed temporary directory. It does not run Gradle or build the mod. Tests cover
legacy alias deduplication, enumeration-independent shuffling, all batch sizes
4–64, partial-batch coverage, 1500ms boundaries, identical/transparent pixel
comparison, resolution selection, and 1000 randomized alpha-composition cases.
The Minecraft-facing loader is also parsed for Java 21 syntax, without type
analysis or code generation.
Minecraft APIs target Yarn 1.21.4 build.8. Use the graphical integration command
above for client/OpenGL and mixin verification.

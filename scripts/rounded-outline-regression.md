# Rounded outline regression

The shared `round.fsh` and `round_batched.fsh` mixed unassociated RGB even
when fill alpha was zero. At signed distance 0, thickness 2 and softness 1,
coverage is 0.5 and border weight is 1. A transparent fill `(0.05, 0.25, 1, 0)`
and neutral outline `(0.4, 0.4, 0.4, 0.67)` therefore produced the old straight
output `(0.225, 0.325, 0.7, 0.335)`: visibly blue over black. Just outside that
band, the shader's existing outer-edge branch emitted pure neutral outline.
This mismatch makes a colored fringe along rounded edges. The eager shader
also added dither to alpha, so zero-alpha colors were not reliably invisible.

The fix alpha-weights RGB for the fill/stroke interpolation and converts back
to straight alpha for the existing `SRC_ALPHA` blend. Coverage, outline
thickness, softness, outer-edge branch, batching and draw count stay the same.
Dither affects RGB only. `BackgroundComponent` already specifies the ordinary
`MenuStyle.BORDER_LIGHT` outer outline, without a hover/theme overlay. It
already uses `MsdfFonts`/`MsdfRenderer` and needs no legacy font migration.

## Pixel test

The combined test mod registers this `fabric-client-gametest` entrypoint:

`vorga.phazeclient.api.system.shape.RoundedOutlineClientTest`

Run the combined isolated client fixture with its normal runner:

```powershell
.\gradlew.bat build runPackPreviewSmoke --offline --console=plain
```

The test draws directly into the existing screen framebuffer. Its first row
uses the eager renderer, its second the batched renderer. Actual screenshot
pixels assert transparent red/blue fills cannot tint a neutral rounded outline,
an opaque neutral dark panel stays filled, rounded cutouts and partial AA pixels
remain, and an intentionally blue outline remains blue. The two shader paths
must agree within RGB dither/8-bit rounding tolerance. The fixture preserves
the previous screen and shader color and doesn't modify theme or user settings.
It requires at least a 380x190 framebuffer and available batching attributes.

Compilation only, without launching a graphical client:

```powershell
.\gradlew.bat compileJava compileTestJava --offline --no-daemon
```

## Verified result and remaining visual check

The combined command above passed, including the real eager/batched shader pixel
assertions. The screenshot is
`build/pack-preview-client-test/screenshots/0034_rounded-outline-alpha-regression.png`.
The run also passed the 23.5-pixel setting-row interaction tests, continuous MSDF
fade pixels (including fractional scaling and clipping), and Low HP overlay tests.

The real settings-menu screenshot was inspected at rest. Opening/closing and
theme transitions at all GUI scales have not been exhaustively inspected.
The isolated pixel test targets the proven shared shader defect;
it does not reproduce the exact reported screenshot's world/blur backdrop.

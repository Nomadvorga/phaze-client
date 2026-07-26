package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

/**
 * Custom-color replacement for the vanilla block-outline at the
 * player's crosshair. Replaces the hardcoded black with a user-
 * picked ARGB color and exposes a {@code Filled} style that adds a
 * translucent face fill on top of the standard outline.
 *
 * <h3>Why we hijack the vanilla path instead of drawing our own</h3>
 * Vanilla draws the outline through {@code RenderLayer.getLines()}
 * with pixel-perfect 1 px lines. Rolling our own line geometry
 * runs into vendor-specific {@code glLineWidth} clamping (some
 * drivers ignore values >1 for axis-aligned lines, producing the
 * "bottom edges thinner than sides" symptom). Letting vanilla do
 * the line rasterisation and only swapping the {@code color}
 * argument keeps pixel-perfect output across all hardware.
 *
 * <h3>Mixin coupling</h3>
 * {@code WorldRendererBlockOverlayMixin} ModifyArg's the
 * {@code int color} passed to {@code WorldRenderer.drawBlockOutline}.
 * The Filled style additionally draws a translucent face quad over
 * the targeted block before vanilla's outline pass so the outline
 * still reads on top.
 */
public final class BlockOverlay extends Module {
    private static final BlockOverlay INSTANCE = new BlockOverlay();

    public final SectionSetting outlineSection = new SectionSetting("Outline");
    public final ColorSetting outlineColor = new ColorSetting(
            "Outline Color",
            "Color of the outline (alpha controls opacity)"
    ).value(0xFF000000)
            .popupRow();
    public final SelectSetting style = new SelectSetting(
            "Style",
            "Outline only or with a translucent face fill"
    ).value("Outline", "Filled").selected("Outline");
    public final ColorSetting fillColor = new ColorSetting(
            "Fill Color",
            "Color of the translucent face fill (Filled style only)"
    ).value(0x40FFFFFF)
            .popupRow()
            .visible(() -> "Filled".equalsIgnoreCase(style.getSelected()));

    private BlockOverlay() {
        super("block_overlay", "Block Overlay", ModuleCategory.OTHER);
        outlineColor.setFullWidth(true);
        style.setFullWidth(true);
        fillColor.setFullWidth(true);
        setup(outlineSection, outlineColor, style, fillColor);
    }

    public static BlockOverlay getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Recolor the vanilla block outline at your crosshair, optionally fill the faces";
    }

    @Override
    public String getIcon() {
        return "block_overlay.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}

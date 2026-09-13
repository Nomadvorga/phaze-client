package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;

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

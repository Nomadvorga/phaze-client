package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class NametagHud extends RectHudModule {
    private static final NametagHud INSTANCE = new NametagHud();

    /**
     * Hard cap on the effective alpha of the nametag background in
     * <em>custom</em> presets, expressed as a fraction of full opacity.
     * The Background Opacity slider (0..100) is linearly remapped through
     * this constant: slider 100 -&gt; 0.30 alpha, slider 50 -&gt; 0.15,
     * slider 0 -&gt; fully transparent. This keeps nametags subtle no
     * matter how the user moves the slider, while still letting them dial
     * the visibility down smoothly without the background snapping off at
     * any specific slider value.
     */
    private static final float CUSTOM_PRESET_MAX_ALPHA = 0.30F;

    public final SectionSetting nametagSection = new SectionSetting("Nametag");
    public final BooleanSetting nametagTextShadow = new BooleanSetting("Nametag Text Shadow", "Shadow for nametag text").setValue(true);
    public final BooleanSetting thirdPersonNametag = new BooleanSetting("Third Person Nametag", "Render nametag in third person").setValue(true);
    public final BooleanSetting toggleMessage = new BooleanSetting("Toggle Message", "Display toggle message for nametags").setValue(true);
    public final BooleanSetting hideInF1 = new BooleanSetting("Hide in F1", "Hide nametags in F1 mode").setValue(false);
    public final BooleanSetting replaceOwnNameColor = new BooleanSetting("Replace Own Name Color", "Replace own nametag text color").setValue(true);

    public static NametagHud getInstance() {
        return INSTANCE;
    }

    private NametagHud() {
        super("nametag_hud", "Nametag", 22.0f, 408.0f, 1.0f);
        // Nametag-specific override of the parent's 40% default. The
        // slider here means "fraction of CUSTOM_PRESET_MAX_ALPHA" so 100
        // is the natural starting point - users get nametag backgrounds
        // at the documented 30% effective opacity out of the box.
        backgroundOpacity.setValue(100);
        nametagTextShadow.setFullWidth(true);
        thirdPersonNametag.setFullWidth(true);
        toggleMessage.setFullWidth(true);
        hideInF1.setFullWidth(true);
        replaceOwnNameColor.setFullWidth(true);
        setup(showBrackets, nametagSection, nametagTextShadow, thirdPersonNametag, toggleMessage, hideInF1, replaceOwnNameColor);
    }

    /**
     * Nametag bg color resolution differs from generic 2D HUDs in two
     * ways: vanilla preset is pinned to 30% black (no longer dependent
     * on the player's textBackgroundOpacity option) and custom presets
     * scale the slider linearly through {@link #CUSTOM_PRESET_MAX_ALPHA}
     * so 100% slider lands at 30% effective alpha. The linear scaling
     * fixes the perceived "background suddenly disappears" symptom that
     * the previous mixin-side multiplier produced near low slider values
     * - now the alpha simply fades smoothly toward zero.
     */
    @Override
    public int getResolvedBackgroundColor(MinecraftClient client) {
        if (isVanillaPreset()) {
            // 0x4D000000 = alpha 77 (~30%), RGB 0. Independent of the
            // user's Accessibility -> Background Opacity option so every
            // player sees the same nametag style on Vanilla preset.
            int alpha = MathHelper.clamp(Math.round(255.0F * CUSTOM_PRESET_MAX_ALPHA), 0, 255);
            return alpha << 24;
        }
        // Custom preset: take the parent's resolved color (which already
        // applied the slider 0..100 -> alpha 0..255 mapping plus the
        // brightness/preset tint) and rescale just the alpha channel
        // through the 0.3 cap. RGB is preserved untouched so the chosen
        // theme color shows through unchanged.
        int parentColor = super.getResolvedBackgroundColor(client);
        int parentAlpha = (parentColor >>> 24) & 0xFF;
        int scaledAlpha = MathHelper.clamp(Math.round(parentAlpha * CUSTOM_PRESET_MAX_ALPHA), 0, 255);
        return (scaledAlpha << 24) | (parentColor & 0x00FFFFFF);
    }

    @Override
    public String getDescription() {
        return "Nametag options HUD";
    }

    @Override
    public String getIcon() {
        return "nametag_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}

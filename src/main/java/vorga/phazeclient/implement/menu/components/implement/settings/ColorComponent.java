package vorga.phazeclient.implement.menu.components.implement.settings;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColorComponent extends AbstractSettingComponent {
    // Single header rendered above the whole picker so two color
    // pickers in the same module (e.g. HitRange's Color + In Range
    // Color) can't be mistaken for each other. Format:
    // "<module visible name> <setting name>", e.g. "Hit Color Color"
    // for the HitColor module's "Color" setting. The size matches the
    // body label size used by ValueComponent so the typography reads
    // consistent across mixed setting types in the same panel.
    private static final float HEADER_TEXT_SIZE = 7.0F;
    // Vertical gap between the top of our card and the header. The
    // sliders inside the sub-components draw from their own y + 10.5,
    // i.e. our y + 15.5 (after the +5 position offset), so leaving
    // ~3 px of top pad keeps the header centered in the strip above
    // the slider rectangles without clipping the descenders.
    private static final float HEADER_TOP_PAD = 3.0F;

    private final ColorSetting setting;
    private final List<vorga.phazeclient.implement.menu.components.AbstractComponent> components = new ArrayList<>();

    private final HueComponent hueComponent;
    private final SaturationComponent saturationComponent;
    private final AlphaComponent alphaComponent;
    private final ColorEditorComponent colorEditorComponent;
    private final ColorPresetComponent colorPresetComponent;

    public ColorComponent(ColorSetting setting) {
        super(setting);
        this.setting = setting;

        components.addAll(
                Arrays.asList(
                        hueComponent = new HueComponent(setting),
                        saturationComponent = new SaturationComponent(setting),
                        alphaComponent = new AlphaComponent(setting),
                        colorEditorComponent = new ColorEditorComponent(setting),
                        colorPresetComponent = new ColorPresetComponent(setting)
                )
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        if (!setting.isVisible()) {
            return;
        }

        MatrixStack matrix = context.getMatrices();

        // Pump the preset component just to keep its internal state
        // consistent (it still gets mouseClicked etc.); we no longer
        // size ourselves off its 132-px windowHeight because that left
        // ~56 px of dead space below the slider strips, making stacked
        // color settings (e.g. HitRange's Color + In Range Color)
        // feel far more spaced out than the rest of the panel.
        ((ColorPresetComponent) colorPresetComponent.position(x, y)).getWindowHeight();

        alphaComponent.position(x + 5, y + 5);
        hueComponent.position(x + 5, y + 5);
        saturationComponent.position(x + 5, y + 5);
        colorEditorComponent.position(x + 5, y + 5);

        components.forEach(component -> {
            // Hide the alpha column entirely when the ColorSetting
            // opted out via {@code noAlpha()}. Skipping render() is
            // enough - the AlphaComponent is also blocked from
            // mouse / scroll input below so the click area doesn't
            // overlap the (now invisible) widget.
            if (setting.isNoAlpha() && component == alphaComponent) {
                return;
            }
            component.globalAlpha = currentAlpha;
            component.render(context, mouseX, mouseY, delta);
        });

        renderHeader(matrix);

        // Tight height: slider strips inside the sub-components draw
        // from (y + 5) + 10.5 = y + 15.5 down to y + 65.5 (H = 50).
        // Add a single trailing pad below the strips (no more 50-plus
        // px of empty space where the legacy preset palette used to
        // sit) so two color settings in a row sit close together.
        height = 73;
    }

    /**
     * Renders a single "{@code <module> <setting>}" header above the
     * whole picker, e.g. {@code "Hit Color Color"} for the HitColor
     * module's {@code Color} setting. Lets the user tell two adjacent
     * pickers in the same module apart (HitRange's {@code Color} vs
     * {@code In Range Color}) without having to remember which strip
     * is which from the module description.
     *
     * <p>The module visible name is resolved through
     * {@link Module#getVisibleName()} via the {@link
     * vorga.phazeclient.api.feature.module.setting.Setting#getModuleContext()}
     * identifier that {@code Module.setup} stamps on every setting; if
     * the lookup fails (orphan setting, identifier mismatch) we fall
     * back to just the setting's own name so the picker is still
     * labelled, just without the module prefix.
     */
    private void renderHeader(MatrixStack matrix) {
        String settingName = setting.getLocalizedName();
        if (settingName == null) settingName = "";

        String moduleName = resolveModuleVisibleName();

        String header;
        if (!moduleName.isEmpty() && !settingName.isEmpty()) {
            header = moduleName + " " + settingName;
        } else if (!moduleName.isEmpty()) {
            header = moduleName;
        } else {
            header = settingName;
        }
        if (header.isEmpty()) {
            return;
        }

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                header,
                HEADER_TEXT_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha),
                matrix.peek().getPositionMatrix(),
                x + 10,
                y + HEADER_TOP_PAD,
                0.0F
        );
    }

    /**
     * Looks up the visible name of the module that owns this setting,
     * via the {@code moduleContext} identifier that {@code Module.setup}
     * stamps on every setting tree it owns. Returns the empty string
     * (caller treats as "no module prefix") on any lookup miss so a
     * setting that wasn't installed via {@code setup} - or that races
     * a module-list mutation - still renders a label.
     */
    private String resolveModuleVisibleName() {
        String moduleId = setting.getModuleContext();
        if (moduleId == null || moduleId.isEmpty()) {
            return "";
        }
        Main main = Main.getInstance();
        if (main == null || main.getModuleProvider() == null) {
            return "";
        }
        Module module = main.getModuleProvider().get(moduleId);
        if (module == null) {
            return "";
        }
        String visible = module.getVisibleName();
        return visible == null ? "" : visible;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) {
            return false;
        }

        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseClicked(mouseX, mouseY, button);
        });
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseScrolled(mouseX, mouseY, amount);
        });
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        });
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(component -> {
            if (setting.isNoAlpha() && component == alphaComponent) return;
            component.mouseReleased(mouseX, mouseY, button);
        });
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        if (!setting.isVisible()) {
            return false;
        }
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }
}

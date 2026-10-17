package vorga.phazeclient.implement.features.modules.other;

import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.Consumer;

/** Checks the settings contract consumed by the stable two-column detail layout. */
public final class GlintSettingsChecks {
    private static final List<String> MODES = List.of("Custom", "Rainbow", "Turquoise", "Golden", "Ruby");
    private static int checks;

    public static void main(String[] args) {
        GlintColorizer glint = GlintColorizer.getInstance();
        String previousMode = glint.mode.getSelected();
        float previousSpeed = glint.speed.getValue();
        int previousColor = glint.customColor.getColor();
        try {
            check(glint.getIdentifier().equals("glint_colorizer"), "module identifier unchanged");
            check(glint.getVisibleName().equals("Custom Glint"), "module title unchanged");
            check(glint.settings().equals(List.of(glint.generalSection, glint.mode, glint.customColor, glint.speed)),
                    "General precedes Mode, Color, Speed for stable two-column layout");
            check(glint.generalSection.getRawName().equals("General") && glint.generalSection.isFullWidth()
                    && glint.generalSection.isVisible() && !glint.generalSection.isSaveToConfig(),
                    "General is a visible full-width display-only section");
            check(glint.mode.getRawName().equals("Mode"), "raw Mode config key unchanged");
            check(glint.customColor.getRawName().equals("Color"), "raw Color config key unchanged");
            check(glint.speed.getRawName().equals("Speed"), "raw Speed config key unchanged");
            check(glint.mode.getList().equals(MODES), "raw mode choices unchanged");
            check(glint.mode.getDefaultSelected().equals("Custom"), "default mode unchanged");
            check(glint.speed.getMin() == 0.2f && glint.speed.getMax() == 4.0f
                    && glint.speed.getStep() == 0.1f && glint.speed.getDefaultValue() == 1.0f,
                    "speed range, step and default unchanged");
            check(glint.customColor.getDefaultColor() == 0xFF00FFCC, "default color unchanged");

            checkTransitions(glint, glint.mode::selected, ".selected");
            checkTransitions(glint, glint.mode::setSelected, ".setSelected");
            // Match config loading: pass the raw English JSON string to setSelected.
            checkTransitions(glint, value -> glint.mode.setSelected(new JsonPrimitive(value).getAsString()),
                    "raw config value");
            for (String mode : MODES) {
                glint.mode.selected(mode);
                glint.mode.reset();
                checkLayout(glint, "Custom", "reset from " + mode);
                check(!glint.mode.isModified(), "reset clears modified state from " + mode);
            }
            check(glint.speed.getValue() == previousSpeed, "mode changes preserve speed");
            check(glint.customColor.getColor() == previousColor, "mode changes preserve custom color");
        } finally {
            glint.mode.selected(previousMode);
        }
        System.out.println("Glint settings checks passed: " + checks);
    }

    private static void checkTransitions(GlintColorizer glint, Consumer<String> select, String path) {
        for (String from : MODES) {
            for (String to : MODES) {
                select.accept(from);
                checkLayout(glint, from, path + " source " + from);
                select.accept(to);
                checkLayout(glint, to, path + " " + from + " -> " + to);
            }
        }
    }

    private static void checkLayout(GlintColorizer glint, String mode, String context) {
        boolean custom = mode.equals("Custom");
        check(glint.mode.getSelected().equals(mode), context + ": raw selected value unchanged");
        check(glint.mode.isVisible() && !glint.mode.isFullWidth(), context + ": Mode occupies first column");
        check(glint.customColor.isVisible() == custom && !glint.customColor.isFullWidth(),
                context + ": Color occupies second column only in Custom");
        check(glint.speed.isVisible(), context + ": Speed always visible");
        check(glint.speed.isFullWidth() == custom,
                context + ": Speed spans second row only in Custom, otherwise shares Mode row");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

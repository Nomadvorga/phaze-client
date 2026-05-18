package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Renders a small "remaining seconds" number above any hotbar slot
 * whose item is currently on use-cooldown.
 *
 * <h3>What "cooldown" means here</h3>
 * Vanilla's {@link net.minecraft.entity.player.ItemCooldownManager}
 * is the canonical source: every entry keyed by a group identifier
 * carries {@code startTick} and {@code endTick} ints relative to the
 * manager's local {@code tick} field. The "progress" exposed via
 * {@link net.minecraft.entity.player.ItemCooldownManager#getCooldownProgress}
 * is {@code (endTick - currentTick) / (endTick - startTick)} clamped
 * to {@code [0..1]} - it ramps from 1.0 the instant a cooldown is
 * applied to 0.0 the instant it expires. The mixin
 * {@code InGameHudCooldownsMixin} reads this progress directly from
 * the player's cooldown manager (no packet sniffing required) and
 * forwards each non-zero value to the rendering helper, so the
 * displayed seconds and colour stay in lockstep with the cooldown
 * pie that vanilla also paints over the slot.
 *
 * <h3>Why not RectHudModule</h3>
 * Cooldowns aren't a draggable rect - they paint at the hotbar's
 * own coordinates, which the user already controls via vanilla GUI
 * scale. Extending {@link Module} keeps the toggle visible in the
 * HUD tab without implying a per-module x/y/scale that wouldn't be
 * meaningful here.
 *
 * <h3>Logic credits</h3>
 * Detection logic (read from {@code ItemCooldownManager}) and
 * draggable widget shape are adapted from
 * {@code padej.soup.implement.features.draggables.CoolDowns}; the
 * Phaze port replaces the standalone widget with an over-slot
 * number overlay per the user spec.
 */
public final class Cooldowns extends Module {
    private static final Cooldowns INSTANCE = new Cooldowns();

    public final SectionSetting generalSection = new SectionSetting("General");

    public final BooleanSetting textShadow = new BooleanSetting(
            "Text Shadow",
            "Draw the cooldown number with a vanilla-style drop shadow"
    ).setValue(true);

    public final BooleanSetting showDecimals = new BooleanSetting(
            "Show Decimals",
            "Display the remaining cooldown with one decimal place (e.g. \"1.4\") instead of rounding to whole seconds"
    ).setValue(true);

    /**
     * Switches the displayed cooldown number from a flat
     * {@link MenuStyleColors#WHITE WHITE} to a red/yellow/green
     * stoplight based on remaining cooldown progress:
     * <ul>
     *   <li>&gt; 75% remaining: red - "still a long way to go"</li>
     *   <li>26-75% remaining: yellow - "halfway"</li>
     *   <li>&le; 25% remaining: green - "almost ready"</li>
     * </ul>
     * Default OFF so users get vanilla-clean numbers unless they
     * opt in.
     */
    public final BooleanSetting colorByCooldown = new BooleanSetting(
            "Color By Cooldown",
            "Color the displayed number red/yellow/green based on remaining cooldown progress"
    ).setValue(false);

    private Cooldowns() {
        super("cooldowns", "Cooldowns", ModuleCategory.HUD);
        textShadow.setFullWidth(true);
        showDecimals.setFullWidth(true);
        colorByCooldown.setFullWidth(true);
        setup(generalSection, textShadow, showDecimals, colorByCooldown);
    }

    public static Cooldowns getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Shows the remaining cooldown time as a number above each hotbar slot";
    }

    @Override
    public String getIcon() {
        return "cooldowns.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Maps {@link net.minecraft.entity.player.ItemCooldownManager#getCooldownProgress}
     * (a 1.0 -&gt; 0.0 ramp from "just started" to "expiring") onto
     * the user-requested colour palette. Returns
     * {@code 0xFFFFFFFF} when {@link #colorByCooldown} is off so the
     * caller can blindly use the result as the text colour.
     *
     * <p>Boundary semantics match the user spec exactly: thresholds
     * are read as "remaining cooldown is {@code >75%}" / "between 26
     * and 75%" / "{@code <=25%}". {@code progress > 0.75F} catches
     * the first bucket; {@code progress > 0.25F} the second; the
     * else clause covers the last quarter.
     */
    public int colorForProgress(float progress) {
        if (!colorByCooldown.isValue()) {
            return 0xFFFFFFFF;
        }
        if (progress > 0.75F) {
            return 0xFFFF5555; // red
        }
        if (progress > 0.25F) {
            return 0xFFFFAA00; // yellow
        }
        return 0xFF55FF55; // green
    }

    /**
     * Formats the remaining cooldown ticks as the on-screen string.
     * One decimal place when {@link #showDecimals} is on (the
     * upstream Soup-Better widget always shows seconds; Phaze adds a
     * tenths option so quick-cooldown items like wind charges still
     * read meaningfully).
     */
    public String formatSeconds(float seconds) {
        if (showDecimals.isValue()) {
            return String.format(java.util.Locale.ROOT, "%.1f", seconds);
        }
        return String.valueOf(Math.max(1, Math.round(seconds)));
    }
}

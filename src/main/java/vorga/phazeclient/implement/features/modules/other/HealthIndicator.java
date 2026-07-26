
package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.entity.player.PlayerEntity;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;


/**
 * MIT License
 *
 * Phaze port of the "ZakoHealthIndicator" mod by 3ako.
 * Original source: https://github.com/3ako/ZakoHealthIndicator
 *
 * Copyright (c) 2023 3ako
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.* 
 * <h3>What it shows</h3>
 * After the local player attacks another player, the victim's current
 * HP is displayed in a draggable HUD widget for a configurable number
 * of seconds, with the value colour-coded by remaining hearts
 * (red &le; 5, gold &le; 10, yellow &le; 15, green &le; 20,
 * dark-green &gt; 20). Mirrors the colour table of the original mod
 * (see {@code me.zyouime.zakohealthindicator.util.ColorUtil}).
 *
 * <h3>Why it extends {@link RectHudModule} but uses {@link ModuleCategory#OTHER}</h3>
 * The user specifically asked for the widget to live in the
 * {@code OTHER} category of the client menu, but at the same time
 * wanted it to be draggable / resizable the same way the HUDs are.
 * Extending {@code RectHudModule} pulls in the entire drag / resize /
 * background-blur pipeline that {@code InGameHudMixin.renderRectHud}
 * already implements for every other HUD widget; passing
 * {@code ModuleCategory.OTHER} into the new 6-arg
 * {@code RectHudModule} constructor places the menu entry where the
 * user wants it without forking the renderer. The
 * {@code InGameHudMixin} explicitly drives this instance through
 * {@code HealthIndicator.getInstance()} (no category-based discovery),
 * so the rendering pass is unaffected by the category swap.
 *
 * <h3>Background-off default</h3>
 * The user wanted a "rect without background" - so we explicitly set
 * {@link RectHudModule#background} to {@code false} in the constructor
 * AFTER the super call (which itself defaulted it to {@code true} on
 * the inherited setting). The user can re-enable the background from
 * the settings panel and the inherited preset / blur / opacity controls
 * still work as on any other HUD; we only flip the default.
 *
 * <h3>Tracking model</h3>
 * The mod stamps {@link #target} + {@link #lastAttackMs} every time
 * the player lands a hit on another player (via the existing
 * {@code ClientPlayerInteractionManagerMixin.attackEntity} hook), and
 * {@link #hasActiveTarget()} checks both that the recorded victim is
 * still alive AND that we're within the user-configurable
 * {@link #targetDelay} window. Volatile fields because the attack
 * hook fires from the render thread (vanilla {@code MinecraftClient}
 * input dispatch is on the main thread, but the HUD render path in
 * 1.21.4 can re-fetch state from the batched FBO pass on a worker -
 * see {@code InGameHudMixin.renderHudInternal}). A single 8-byte
 * publish per attack is dirt-cheap so the JMM guarantee is worth
 * the negligible cost.
 */
public final class HealthIndicator extends RectHudModule {
    private static final HealthIndicator INSTANCE = new HealthIndicator();

    /**
     * Colour palette mirroring vanilla {@code Formatting} entries used
     * by the original mod's {@code ColorUtil.getColor}. The mod called
     * {@code Text.literal(...).formatted(Formatting.X)} and let the
     * vanilla text renderer pick the §-code colour; Phaze's HUD path
     * goes through {@code renderScaledHudTextColored} which takes a
     * raw ARGB int, so we hard-code the matching values from the
     * {@code Formatting} enum's {@code colorValue} field. Alpha is
     * pinned to {@code 0xFF} (fully opaque) so the colour pops against
     * the no-background default; the text-shadow setting still applies
     * because it's drawn underneath at half-alpha by the inherited
     * {@code renderHudTextColored} call.
     */
    private static final int COLOR_RED         = 0xFFFF5555; // Formatting.RED
    private static final int COLOR_GOLD        = 0xFFFFAA00; // Formatting.GOLD
    private static final int COLOR_YELLOW      = 0xFFFFFF55; // Formatting.YELLOW
    private static final int COLOR_GREEN       = 0xFF55FF55; // Formatting.GREEN
    private static final int COLOR_DARK_GREEN  = 0xFF00AA00; // Formatting.DARK_GREEN

    public final SectionSetting otherSection = new SectionSetting("Other");

    /**
     * How long, in seconds, to keep the victim's HP visible after the
     * last successful hit. Matches the {@code targetDelay} field in
     * the original mod's {@code ModConfig} (which was a millisecond
     * value, default 10000) but exposed in seconds because Phaze's
     * {@link ValueSetting} slider UX is much friendlier with smaller
     * integer ranges and the user almost certainly thinks in seconds
     * anyway. The {@code 1..30} range is wide enough to cover both
     * twitchy PvP ("hide it as soon as the combo ends") and casual
     * sniping ("keep it up while I chase").
     */
    public final ValueSetting targetDelay = new ValueSetting(
            "Target Delay",
            "Seconds to keep showing the target's HP after the last hit"
    ).range(1, 30).setValue(10);

    /**
     * Whether to tint the HP value by current health (matches the
     * mod's colour table). When off, every HP value uses the inherited
     * white {@code HUD_TEXT_COLOR} from {@code InGameHudMixin}, which
     * is what users running custom Theme overlays might prefer so the
     * indicator picks up their theme colour.
     */
    public final BooleanSetting colorByHp = new BooleanSetting(
            "Color By HP",
            "Tint the HP value by remaining hearts (red <= 5, gold <= 10, yellow <= 15, green <= 20, dark-green > 20)"
    ).setValue(true);

    /**
     * Whether to prefix the displayed value with the literal string
     * {@code "HP "}. The original mod showed the bare number; this
     * toggle exists because the user described the feature as "text
     * with hearts" - a literal heart glyph isn't available in Phaze's
     * MSDF font atlas (ASCII-only, max codepoint 126; verified by
     * reading {@code assets/minecraft/msdf/minecraft-ascii-ttf-msdf.json}),
     * so {@code "HP"} is the closest textual stand-in that renders
     * correctly. Default OFF preserves the original ZakoHealthIndicator
     * look (a single colour-coded number).
     */
    public final BooleanSetting hpPrefix = new BooleanSetting(
            "HP Prefix",
            "Prefix the value with \"HP\" (e.g. \"HP 10\" instead of just \"10\")"
    ).setValue(false);

    /**
     * Last player we landed a hit on, or {@code null} when no attack
     * has been recorded yet (or the previous one timed out and was
     * cleared by a tick of {@link #hasActiveTarget}). Volatile because
     * writes come from the input/attack thread inside
     * {@code ClientPlayerInteractionManagerMixin.attackEntity} and
     * reads come from the render-thread HUD pass.
     */
    private volatile PlayerEntity target = null;

    /**
     * Wall-clock timestamp (ms) of the most recent attack. Volatile for
     * the same publish-visibility reason as {@link #target}. Read by
     * {@link #hasActiveTarget()} on the render thread.
     */
    private volatile long lastAttackMs = 0L;

    public static HealthIndicator getInstance() {
        return INSTANCE;
    }

    private HealthIndicator() {
        // Default position is roughly the upper-middle area of a
        // typical 1080p window - close enough to where the crosshair
        // sits at default GUI scale that "drag it once and forget"
        // is the usual interaction pattern, but offset slightly so
        // the user immediately sees the indicator in chat-edit mode
        // even if their crosshair is in the dead centre.
        // Storage id is "health_indicator" so the config file key
        // matches the visible-name slug; if the user ever migrates
        // their Phaze profile across machines, the saved hudX/hudY/
        // hudScale state for this widget continues to bind.
        super("health_indicator", "Health Indicator", ModuleCategory.OTHER, 320.0f, 200.0f, 1.0f);
        // Flip the inherited Background toggle off to match the user
        // request ("без фона"). Inherited preset / blur / opacity
        // controls still exist - they just don't draw anything until
        // the user re-enables Background from the settings panel.
        background.setValue(false);
        otherSection.setFullWidth(true);
        targetDelay.setFullWidth(true);
        colorByHp.setFullWidth(true);
        hpPrefix.setFullWidth(true);
        setup(otherSection, targetDelay, colorByHp, hpPrefix);
    }

    /**
     * Stamp the given player as the most recent victim. Called from
     * {@code ClientPlayerInteractionManagerMixin.attackEntity} for
     * every successful hit on a non-self {@link PlayerEntity}.
     *
     * <p>Tolerates {@code null} (a defensive guard - the mixin already
     * filters out non-player targets via instanceof, but a future
     * refactor of that call site could regress and the worst we want
     * to do here is silently no-op rather than NPE during render).
     * The {@link #isEnabled()} check is intentionally NOT done here:
     * we want the timestamp to keep ticking even while the module is
     * toggled off, so re-enabling mid-combat immediately surfaces the
     * current target instead of waiting for the next hit.
     */
    public void recordAttack(PlayerEntity victim) {
        if (victim == null) {
            return;
        }
        this.target = victim;
        this.lastAttackMs = System.currentTimeMillis();
    }

    /**
     * True if a player has been stamped via {@link #recordAttack} and
     * (a) is still alive AND (b) the {@link #targetDelay} window has
     * not yet elapsed.
     *
     * <p>The {@code targetDelay} is read fresh on every call so a
     * mid-display slider tweak takes effect immediately (e.g. dragging
     * from 10 -&gt; 2 seconds while a value is on screen will hide it
     * the moment the older timestamp falls outside the new window).
     */
    public boolean hasActiveTarget() {
        PlayerEntity t = this.target;
        if (t == null) {
            return false;
        }
        if (!t.isAlive()) {
            return false;
        }
        long windowMs = (long) (targetDelay.getValue() * 1000.0f);
        return System.currentTimeMillis() - lastAttackMs < windowMs;
    }

    /**
     * Current HP text for the live target, or an empty string when no
     * target is active. Used by the render lambda in
     * {@code InGameHudMixin} to short-circuit the entire render when
     * there's nothing to show (so the invisible rect doesn't steal
     * mouse interactions during normal gameplay - we want it dragged
     * only from chat-editing mode, see {@link #getPlaceholderText()}).
     *
     * <p>HP is truncated to int (matches the original mod's
     * {@code (int) player.getHealth()} - a 10.6-HP target reads as
     * "10", not "11"; intentional, since round-up could briefly read
     * as full health when the victim is one regen tick away from
     * death).
     */
    public String getDisplayText() {
        if (!hasActiveTarget()) {
            return "";
        }
        PlayerEntity t = this.target;
        if (t == null) {
            return "";
        }
        int hp = (int) t.getHealth();
        return hpPrefix.isValue() ? "HP " + hp : String.valueOf(hp);
    }

    /**
     * Placeholder shown in chat-editing mode (i.e. when the user is
     * dragging HUDs around) so the rect has visible content even when
     * the player isn't currently in a fight. Without this the widget
     * would render as a zero-width invisible rect during config and
     * the user would have nothing to grab; using a static "20" (full
     * vanilla HP) keeps the layout / sizing realistic.
     */
    public String getPlaceholderText() {
        return hpPrefix.isValue() ? "HP 20" : "20";
    }

    /**
     * ARGB colour the HUD pipeline should tint the rendered text with.
     * Returns the inherited white sentinel ({@code 0xFFFFFFFF}) when
     * {@link #colorByHp} is off or no target is currently tracked -
     * matching the chat-editing placeholder which has no real HP to
     * key on.
     */
    public int getCurrentHpColor() {
        if (!colorByHp.isValue()) {
            return 0xFFFFFFFF;
        }
        PlayerEntity t = this.target;
        if (t == null) {
            return 0xFFFFFFFF;
        }
        float hp = t.getHealth();
        if (hp <= 5.0f) {
            return COLOR_RED;
        }
        if (hp <= 10.0f) {
            return COLOR_GOLD;
        }
        if (hp <= 15.0f) {
            return COLOR_YELLOW;
        }
        if (hp <= 20.0f) {
            return COLOR_GREEN;
        }
        return COLOR_DARK_GREEN;
    }

    @Override
    public String getDescription() {
        return "Shows the HP of the player you most recently hit (port of ZakoHealthIndicator)";
    }

    @Override
    public String getIcon() {
        return "health_indicator.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}

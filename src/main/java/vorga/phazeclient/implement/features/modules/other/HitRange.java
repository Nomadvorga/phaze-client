/*
 * MIT License
 *
 * Phaze port of the "hitrange" mod by uku3lig (uku).
 * Original source: https://github.com/uku3lig/hitrange
 *
 * Copyright (c) 2023 uku
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * Modifications (Phaze):
 *   - Rewired HitRangeConfig fields onto Phaze's Setting framework
 *     (ValueSetting / SelectSetting / BooleanSetting / ColorSetting).
 *   - Replaced ukulib keybind / TabbedConfigScreen wiring with the
 *     Phaze Module class. Bind is inherited from Module.
 *   - Added an explicit Mode enum so the renderer stays decoupled from
 *     the localised SelectSetting strings.
 *   - Wired geometry-impacting settings to a single recompute callback
 *     instead of relying on an upstream "Compute Angles" button.
 */
package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.entity.player.PlayerEntity;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.implement.hitrange.HitRangeCircleRenderer;

public final class HitRange extends Module {
    /**
     * Decoupled from the {@code Render Mode} {@link SelectSetting}
     * string so the renderer doesn't have to know about localisation.
     */
    public enum Mode { LINE, THICK, FILLED }

    private static final HitRange INSTANCE = new HitRange();

    // ---------- Appearance ----------
    // What the circle looks like geometrically: how big, what shape,
    // how thick, how high. The most-tweaked block of settings, kept
    // first so the user lands on it when opening the module.
    public final SectionSetting appearanceSection = new SectionSetting("Appearance");
    public final ValueSetting radius = new ValueSetting(
            "Radius",
            "Circle radius in blocks. Match this to your reach (3.0 = vanilla 1.21 / ~3.0 = 1.8 PvP reach)."
    ).range(0.05f, 5.0f).step(0.05f).setValue(3.0f);
    public final SelectSetting renderMode = new SelectSetting(
            "Render Mode",
            "Line: 1-pixel outline. Thick: ring with adjustable thickness. Filled: solid disc."
    ).value("Line", "Thick", "Filled").selected("Thick");
    public final ValueSetting thickness = new ValueSetting(
            "Thickness",
            "Ring width in blocks. Only used by the Thick render mode."
    ).range(0.01f, 2.0f).step(0.01f).setValue(0.15f);
    public final ValueSetting height = new ValueSetting(
            "Height",
            "Vertical offset above the entity's feet, in blocks. 0 sits flat on the ground."
    ).range(0.0f, 5.0f).step(0.01f).setValue(0.0f);

    // ---------- Targeting ----------
    // Who the circle is drawn AROUND - ranks above colors because
    // there's no point picking the perfect colour if you don't
    // actually see the rings on the entities you care about.
    public final SectionSetting targetingSection = new SectionSetting("Targeting");
    public final BooleanSetting nearestOnly = new BooleanSetting(
            "Nearest Only",
            "Only draw the circle around the single closest player within Max Search Distance."
    ).setValue(false);
    public final BooleanSetting showSelf = new BooleanSetting(
            "Show Self",
            "Also draw the circle around your own player (visible in third-person view)."
    ).setValue(true);
    public final ValueSetting maxSearchDistance = new ValueSetting(
            "Max Search Distance",
            "Cap (in blocks) for the Nearest Only player search."
    ).range(1, 100).step(1.0f).setValue(50);
    public final ValueSetting maxDistance = new ValueSetting(
            "Max Render Distance",
            "Skip drawing for entities farther than this many blocks (culling)."
    ).range(1, 200).step(1.0f).setValue(100);

    // ---------- Colors ----------
    public final SectionSetting colorsSection = new SectionSetting("Colors");
    public final ColorSetting color = new ColorSetting(
            "Color",
            "Base color of the circle when no other modifier applies."
    ).value(0x80FF0000)
            .popupRow();
    public final ColorSetting inRangeColor = new ColorSetting(
            "In Range Color",
            "Color when the target entity is inside the configured radius."
    ).value(0x8000FF00)
            .popupRow();
    public final BooleanSetting colorWhenInRange = new BooleanSetting(
            "Color When In Range",
            "Switch to In Range Color when the target is inside the radius."
    ).setValue(true);
    public final BooleanSetting randomColors = new BooleanSetting(
            "Random Colors",
            "Hash each player's display name into a stable random color (overrides Color / In Range Color)."
    ).setValue(false);

    // ---------- Advanced ----------
    public final SectionSetting advancedSection = new SectionSetting("Advanced");
    public final ValueSetting circleSegments = new ValueSetting(
            "Circle Segments",
            "Polygon edge count of the circle. Lower = more polygonal, higher = smoother."
    ).range(3, 180).step(1.0f).setValue(60);

    /**
     * Result of the most recent {@code World#getClosestPlayer} call from
     * the per-tick nearest-tracking mixin. Read by the per-entity render
     * inject to skip every non-nearest player when {@link #nearestOnly}
     * is on. Volatile-free because the value is only read on the render
     * thread and only written on the client thread, and the render path
     * runs strictly after the tick path.
     */
    private PlayerEntity nearest;

    private HitRange() {
        super("hit_range", "Hit Range", ModuleCategory.OTHER);

        radius.setFullWidth(true);
        renderMode.setFullWidth(true);
        // Thickness is meaningful only in THICK mode - hide it in
        // Line / Filled to keep the panel clean.
        thickness.setFullWidth(true);
        thickness.visible(() -> "Thick".equals(renderMode.getSelected()));
        height.setFullWidth(true);
        nearestOnly.setFullWidth(true);
        showSelf.setFullWidth(true);

        color.setFullWidth(true);
        // Static color is irrelevant when Random Colors hijacks every
        // entity's color from the name hash.
        color.visible(() -> !randomColors.isValue());
        inRangeColor.setFullWidth(true);
        // In Range Color is only consulted when Color When In Range is
        // on AND Random Colors is off.
        inRangeColor.visible(() -> colorWhenInRange.isValue() && !randomColors.isValue());
        randomColors.setFullWidth(true);
        colorWhenInRange.setFullWidth(true);
        colorWhenInRange.visible(() -> !randomColors.isValue());

        circleSegments.setFullWidth(true);
        maxSearchDistance.setFullWidth(true);
        // Max Search Distance is the cap for the nearest-player query;
        // it has no observable effect unless Nearest Only is on.
        maxSearchDistance.visible(nearestOnly::isValue);
        maxDistance.setFullWidth(true);

        // Every geometry-impacting setting punts to the same recompute.
        // Color / boolean settings are read fresh each frame and don't
        // need cached-angle invalidation.
        radius.onChange(v -> HitRangeCircleRenderer.computeAngles());
        thickness.onChange(v -> HitRangeCircleRenderer.computeAngles());
        circleSegments.onChange(v -> HitRangeCircleRenderer.computeAngles());
        renderMode.onChange(s -> HitRangeCircleRenderer.computeAngles());

        setup(
                appearanceSection, radius, renderMode, thickness, height,
                targetingSection, nearestOnly, showSelf, maxSearchDistance, maxDistance,
                colorsSection, color, inRangeColor, colorWhenInRange, randomColors,
                advancedSection, circleSegments
        );
    }

    public static HitRange getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the localised {@link #renderMode} {@code SelectSetting}
     * value into the renderer-friendly enum so the renderer never has
     * to know what label the dropdown is currently showing.
     */
    public Mode mode() {
        return switch (renderMode.getSelected()) {
            case "Line" -> Mode.LINE;
            case "Filled" -> Mode.FILLED;
            default -> Mode.THICK;
        };
    }

    public PlayerEntity getNearest() {
        return nearest;
    }

    public void setNearest(PlayerEntity nearest) {
        this.nearest = nearest;
    }

    @Override
    public String getDescription() {
        return "Draws a configurable PvP-reach circle around entities (port of uku3lig/hitrange, MIT)";
    }

    @Override
    public String getIcon() {
        return "hit_range.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }
}

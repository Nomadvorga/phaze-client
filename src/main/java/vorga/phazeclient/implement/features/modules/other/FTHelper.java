package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * FunTime helper - per-ability item-name detector with rendering
 * hooks for the visualizers each ability uses on the FunTime
 * server-cluster ("трапка" / "пласт" / "божья аура" etc).
 *
 * <h3>What it does</h3>
 * Each enabled ability has a vanilla item type (snowball, ender eye,
 * etc.) and a per-name match string (the item's display name on the
 * FT server contains that string for the magic-imbued variant).
 * When the local player is holding an item that matches both
 * (vanilla item AND name substring), {@link #getHighlightType()}
 * returns the matching {@link HighlightType} so the renderer can
 * paint the appropriate visual overlay (target circle, plast plane,
 * dezorientation ring, etc.). The renderer side is tracked
 * separately and consumes the {@code HighlightType} enum returned
 * here.
 *
 * <h3>Why per-ability name overrides</h3>
 * Server admins occasionally rename items (capitalisation tweaks,
 * adding a clan tag, etc.). The {@link TextSetting} per ability
 * lets the user paste the live item name without rebuilding the
 * client. Comparison is lower-case substring match, so any prefix
 * (rank, level, etc.) the server appends is invisible to the
 * detector.
 *
 * <h3>Logic credits</h3>
 * Detection logic is adapted from
 * {@code winvi.moscow.soupbetter.modules.FTHelperModule}. The Phaze
 * port wires the same flag set into a {@link Module} (so the toggle
 * lives in the standard module list) and exposes the same
 * {@link HighlightType} surface for the future renderer hooks. The
 * server-allowlist gate ({@code ServerUtil.isFTHelperSupported} in
 * the upstream) is replaced by Phaze's own {@code isServerLocked}
 * pipeline, which surfaces the same "is this server in the FT
 * cluster" check via the remote-rules service when configured.
 */
public final class FTHelper extends Module {
    /**
     * Shared preset list used by the Circle and Box color
     * dropdowns. Declared FIRST so the static initializer assigns
     * it before {@link #INSTANCE} runs - {@code SelectSetting.value}
     * does {@code Arrays.asList} which {@code requireNonNull}s the
     * array, so feeding it a null (which would happen if this
     * field was declared after INSTANCE) NPEs the constructor.
     * The "Black" preset is a Predictions/FT helper override that
     * doesn't exist in the Theme palette list - resolveAccentColor
     * short-circuits to pure black for it.
     */
    private static final String[] COLOR_PRESETS = {
            "Black",
            "Lunar Blue", "Mocha Gold", "Rose Quartz", "Emerald Frost",
            "Arctic Mint", "Crimson Silk", "Solar Ember", "Midnight Bloom",
            "Desert Mirage", "Sapphire Steel", "Velvet Plum", "Frosted Peach",
            "Moss Smoke", "Polar Night", "Snow", "Obsidian", "Nebula",
            "Coral", "Jade", "Sunset", "Violet", "Ocean"
    };

    private static final FTHelper INSTANCE = new FTHelper();

    public final SectionSetting abilitiesSection = new SectionSetting("Abilities");

    public final BooleanSetting trapkaEnabled = new BooleanSetting(
            "Trapka",
            "Highlight the trapka target box when holding a netherite-scrap-typed trapka item"
    ).setValue(true);
    public final BooleanSetting drakonTrapkaEnabled = new BooleanSetting(
            "Dragon Trap",
            "Highlight the dragon-trapka variant target box (7x7x3 instead of 3x3x3)"
    ).setValue(true).visible(() -> trapkaEnabled.isValue());
    public final BooleanSetting dezorientationEnabled = new BooleanSetting(
            "Disorientation",
            "Draw the 10-block AOE circle when holding a dezorientation ender eye"
    ).setValue(true);
    public final BooleanSetting yavnayaPylEnabled = new BooleanSetting(
            "Revealing Dust",
            "Draw the 10-block AOE circle when holding явная пыль (sugar)"
    ).setValue(true);
    public final BooleanSetting ognennyiSmerchEnabled = new BooleanSetting(
            "Fire Vortex",
            "Draw the 10-block AOE circle when holding the огненный смерч fire charge"
    ).setValue(true);
    public final BooleanSetting plastEnabled = new BooleanSetting(
            "Plate",
            "Draw the plast placement plane when holding the пласт dried-kelp"
    ).setValue(true);
    public final BooleanSetting bozhestvennayaAuraEnabled = new BooleanSetting(
            "Divine Aura",
            "Draw the божья аура indicator when holding the phantom membrane variant"
    ).setValue(true);
    public final BooleanSetting snezhokZamorozkaEnabled = new BooleanSetting(
            "Freeze Snowball",
            "Predict the impact point of a thrown снежок заморозки and highlight tracked ones in flight (7x7 AOE ring)"
    ).setValue(true);

    public final SectionSetting visualSection = new SectionSetting("Visual");
    public final ValueSetting circleThickness = new ValueSetting(
            "Circle Thickness",
            "World-space thickness of all FT helper circles in blocks"
    ).range(0.05f, 0.50f).step(0.05f).setValue(0.20f);
    public final BooleanSetting circleGlow = new BooleanSetting(
            "Circle Glow",
            "Render a flat soft halo under each circle - lays on the ground, no 3D bump"
    ).setValue(true);
    public final ValueSetting circleGlowStrength = new ValueSetting(
            "Glow Strength",
            "Brightness of the flat halo under each circle"
    ).range(0.20f, 4.0f).step(0.05f).setValue(1.5f)
            .visible(() -> circleGlow.isValue());

    // ---- Color sections (one each for circles and boxes) ----------------
    // Two independent color pickers so the user can paint, say, a
    // dark filled trapka box AND a bright cyan dezo ring at the
    // same time. Each section mirrors the Predictions module:
    // toggle "Theme Color" ON to follow the active client theme,
    // toggle OFF to pick a fixed preset (including a special
    // "Black" option not present in the Theme list). The preset
    // list is declared at the top of the class so it's initialized
    // before INSTANCE runs.

    public final SectionSetting circleColorSection = new SectionSetting("Circle Color");
    public final BooleanSetting circleUseThemeColor = new BooleanSetting(
            "Theme Color",
            "Use the active Theme accent for circle outlines and glow"
    ).setValue(true);
    public final SelectSetting circleColorPreset = new SelectSetting(
            "Color Preset",
            "Fixed color preset for circles (when Theme Color is off)"
    ).value(COLOR_PRESETS).selected("Lunar Blue")
            .visible(() -> !circleUseThemeColor.isValue());

    public final SectionSetting boxColorSection = new SectionSetting("Box Color");
    public final BooleanSetting boxUseThemeColor = new BooleanSetting(
            "Theme Color",
            "Use the active Theme accent for box outlines and fill"
    ).setValue(true);
    public final SelectSetting boxColorPreset = new SelectSetting(
            "Color Preset",
            "Fixed color preset for boxes (when Theme Color is off)"
    ).value(COLOR_PRESETS).selected("Lunar Blue")
            .visible(() -> !boxUseThemeColor.isValue());

    public final SectionSetting nameSection = new SectionSetting("Name Overrides");

    /**
     * Per-ability display-name substrings the server uses. Defaults
     * mirror the upstream FunTime conventions (lowercase russian).
     * Comparison is a case-insensitive substring check so any rank /
     * level prefix the server prepends is invisible to the detector.
     */
    public final TextSetting trapkaName = new TextSetting(
            "Trapka Name",
            "Substring to match in the held item's display name to identify a трапка"
    ).setText("трапка").setMax(48)
            .visible(() -> trapkaEnabled.isValue());
    public final TextSetting drakonTrapkaName = new TextSetting(
            "Dragon Trap Name",
            "Substring to match for the драконья трапка variant"
    ).setText("драконья трапка").setMax(48)
            .visible(() -> trapkaEnabled.isValue() && drakonTrapkaEnabled.isValue());
    public final TextSetting dezorientationName = new TextSetting(
            "Disorientation Name",
            "Substring to match for дезориентация"
    ).setText("дезориентация").setMax(48);
    public final TextSetting yavnayaPylName = new TextSetting(
            "Revealing Dust Name",
            "Substring to match for явная пыль"
    ).setText("явная пыль").setMax(48);
    public final TextSetting ognennyiSmerchName = new TextSetting(
            "Fire Vortex Name",
            "Substring to match for огненный смерч"
    ).setText("огненный смерч").setMax(48);
    public final TextSetting plastName = new TextSetting(
            "Plate Name",
            "Substring to match for пласт"
    ).setText("пласт").setMax(48);
    public final TextSetting bozhestvennayaAuraName = new TextSetting(
            "Divine Aura Name",
            "Substring to match for божья аура"
    ).setText("божья аура").setMax(48);
    public final TextSetting snezhokZamorozkaName = new TextSetting(
            "Freeze Snowball Name",
            "Substring to match for снежок заморозки (used both for in-flight tracking and held-hand prediction)"
    ).setText("снежок заморозки").setMax(48);

    private FTHelper() {
        super("ft_helper", "FT Helper", ModuleCategory.UTILITIES);
        trapkaEnabled.setFullWidth(true);
        drakonTrapkaEnabled.setFullWidth(true);
        dezorientationEnabled.setFullWidth(true);
        yavnayaPylEnabled.setFullWidth(true);
        ognennyiSmerchEnabled.setFullWidth(true);
        plastEnabled.setFullWidth(true);
        bozhestvennayaAuraEnabled.setFullWidth(true);
        snezhokZamorozkaEnabled.setFullWidth(true);
        circleThickness.setFullWidth(true);
        circleGlow.setFullWidth(true);
        circleGlowStrength.setFullWidth(true);
        circleUseThemeColor.setFullWidth(true);
        circleColorPreset.setFullWidth(true);
        boxUseThemeColor.setFullWidth(true);
        boxColorPreset.setFullWidth(true);
        trapkaName.setFullWidth(true);
        drakonTrapkaName.setFullWidth(true);
        dezorientationName.setFullWidth(true);
        yavnayaPylName.setFullWidth(true);
        ognennyiSmerchName.setFullWidth(true);
        plastName.setFullWidth(true);
        bozhestvennayaAuraName.setFullWidth(true);
        snezhokZamorozkaName.setFullWidth(true);
        setup(
                abilitiesSection,
                trapkaEnabled, drakonTrapkaEnabled, dezorientationEnabled,
                yavnayaPylEnabled, ognennyiSmerchEnabled, plastEnabled,
                bozhestvennayaAuraEnabled, snezhokZamorozkaEnabled,
                visualSection, circleThickness, circleGlow, circleGlowStrength,
                circleColorSection, circleUseThemeColor, circleColorPreset,
                boxColorSection, boxUseThemeColor, boxColorPreset,
                nameSection,
                trapkaName, drakonTrapkaName, dezorientationName,
                yavnayaPylName, ognennyiSmerchName, plastName,
                bozhestvennayaAuraName, snezhokZamorozkaName
        );
    }

    public static FTHelper getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "FunTime ability detector and visualizer toggles";
    }

    @Override
    public String getIcon() {
        return "ft_helper.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Visual overlay families the FT renderer uses. Each enum entry
     * maps onto a single helper draw routine; abilities that share a
     * geometric family (the three 10-block AOE circles) collapse to
     * a single value here so the renderer doesn't have to enumerate
     * every detection branch.
     */
    public enum HighlightType {
        NONE,
        TRAPKA,
        TRAPKA_DRAGON,
        CIRCLE_10,
        PLAST,
        BOZHESTVENNAYA_AURA,
        SNEZHOK_PREDICTION
    }

    /**
     * Inspect the local player's main hand stack and return the
     * appropriate highlight family. Branches mirror the upstream
     * {@code FTHelperModule.getHighlightType} order: the first
     * matching predicate wins, so when an item somehow satisfies
     * two (it shouldn't with a proper name set) we deterministically
     * pick the earlier-listed ability.
     *
     * <p>Returns {@link HighlightType#NONE} when the module is
     * disabled, the player is missing, or no held item matches.
     */
    public HighlightType getHighlightType() {
        if (!isEnabled()) {
            return HighlightType.NONE;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return HighlightType.NONE;
        }

        ItemStack mainHand = client.player.getMainHandStack();
        if (mainHand == null || mainHand.isEmpty()) {
            return HighlightType.NONE;
        }
        String itemName = mainHand.getName().getString().toLowerCase();

        if (trapkaEnabled.isValue()
                && mainHand.getItem() == Items.NETHERITE_SCRAP) {
            // Dragon trapka FIRST: its name ("драконья трапка")
            // contains "трапка" as a substring, so checking the
            // base trapka match first would always claim it before
            // the dragon branch ever ran.
            if (drakonTrapkaEnabled.isValue()
                    && itemName.contains(drakonTrapkaName.getText().toLowerCase())) {
                return HighlightType.TRAPKA_DRAGON;
            }
            if (itemName.contains(trapkaName.getText().toLowerCase())) {
                return HighlightType.TRAPKA;
            }
        }
        if (dezorientationEnabled.isValue()
                && mainHand.getItem() == Items.ENDER_EYE
                && itemName.contains(dezorientationName.getText().toLowerCase())) {
            return HighlightType.CIRCLE_10;
        }
        if (yavnayaPylEnabled.isValue()
                && mainHand.getItem() == Items.SUGAR
                && itemName.contains(yavnayaPylName.getText().toLowerCase())) {
            return HighlightType.CIRCLE_10;
        }
        if (ognennyiSmerchEnabled.isValue()
                && mainHand.getItem() == Items.FIRE_CHARGE
                && itemName.contains(ognennyiSmerchName.getText().toLowerCase())) {
            return HighlightType.CIRCLE_10;
        }
        if (plastEnabled.isValue()
                && mainHand.getItem() == Items.DRIED_KELP
                && itemName.contains(plastName.getText().toLowerCase())) {
            return HighlightType.PLAST;
        }
        if (bozhestvennayaAuraEnabled.isValue()
                && mainHand.getItem() == Items.PHANTOM_MEMBRANE
                && itemName.contains(bozhestvennayaAuraName.getText().toLowerCase())) {
            return HighlightType.BOZHESTVENNAYA_AURA;
        }
        if (snezhokZamorozkaEnabled.isValue()
                && mainHand.getItem() == Items.SNOWBALL
                && itemName.contains(snezhokZamorozkaName.getText().toLowerCase())) {
            return HighlightType.SNEZHOK_PREDICTION;
        }
        return HighlightType.NONE;
    }

    /**
     * Block-position of the local player. Returned as
     * {@code BlockPos} (not Vec3d) because the upstream renderers
     * snap the visualisers to block coordinates - all geometry is
     * voxel-aligned. Returns {@code null} when no player is alive.
     */
    public BlockPos getPlayerPos() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getBlockPos() : null;
    }

    /**
     * Block the player's crosshair is currently aimed at. Used by
     * the trapka / plast / aura renderers to anchor their geometry.
     * Returns {@code null} when crosshair isn't on a block (entity
     * hit, miss, etc.) or the player is missing.
     */
    public BlockPos getTargetBlockPos() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    /**
     * Face of the targeted block currently under the crosshair, or
     * {@code null} if no block is aimed at. The plast renderer uses
     * this to orient its placement plane.
     */
    public Direction getTargetBlockSide() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            return blockHit.getSide();
        }
        return null;
    }

    /**
     * Local player's pitch angle. The snezhok-prediction projector
     * needs it to compute the throw arc. Returns 0 when the player
     * is missing instead of throwing - the renderer treats 0 as
     * "horizontal throw" which matches the upstream behaviour.
     */
    public float getPlayerPitch() {
        if (!isEnabled()) return 0.0F;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getPitch() : 0.0F;
    }

    // ---- Snowball tracking (formerly SnowballTracker module) -----------
    // The snowball-tracker functionality used to live in a separate
    // module; the user merged it into FT helper since this module
    // already exposes the toggle for "снежок заморозки" and the two
    // were always used together. The state and tick lifecycle are
    // ported verbatim from SnowballTracker - render side reads
    // {@link #getTrackedSnowballs()} once per frame.
    private final List<TrackedSnowball> tracked = new ArrayList<>();

    /**
     * Network-thread entrypoint forwarded from
     * {@code ClientPlayNetworkHandlerSnowballTrackerMixin}.
     * Filters to snowballs whose display name contains the
     * configured substring, then appends to the tracked list.
     * Synchronisation comes from {@link ArrayList}'s structural
     * stability for single-thread reads from the render thread -
     * the worst case is a one-frame stale list which is invisible
     * to the user.
     */
    public void trackSnowball(SnowballEntity snowball) {
        if (!isEnabled() || !snezhokZamorozkaEnabled.isValue() || snowball == null) {
            return;
        }
        String needle = snezhokZamorozkaName.getText();
        if (needle == null || needle.isEmpty()) {
            return;
        }
        String name = snowball.getName().getString().toLowerCase();
        if (!name.contains(needle.toLowerCase())) {
            return;
        }
        tracked.add(new TrackedSnowball(snowball));
    }

    /** Per-tick pruning of dead snowballs. Called from the player tick mixin. */
    public void tickTrackedSnowballs() {
        Iterator<TrackedSnowball> it = tracked.iterator();
        while (it.hasNext()) {
            TrackedSnowball t = it.next();
            if (t.snowball == null || t.snowball.isRemoved() || !t.snowball.isAlive()) {
                it.remove();
                continue;
            }
            // Append the snowball's current position to its trail
            // history once per tick. Capped at 200 points so a
            // long-flying projectile doesn't grow the list
            // unbounded - 200 ticks @ 20 tps is 10 seconds, more
            // than enough for any thrown snowball arc.
            t.appendTrailPoint(t.snowball.getPos());
        }
    }

    /** Live tracked-snowball list used by the world render mixin. */
    public List<TrackedSnowball> getTrackedSnowballs() {
        return tracked;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        tracked.clear();
    }

    /** Lightweight wrapper around a tracked snowball entity. */
    public static final class TrackedSnowball {
        public final SnowballEntity snowball;
        /** Recorded flight path (camera-space-agnostic, raw world coords). */
        private final List<Vec3d> trail = new ArrayList<>();

        TrackedSnowball(SnowballEntity snowball) {
            this.snowball = snowball;
            if (snowball != null) {
                trail.add(snowball.getPos());
            }
        }

        public Vec3d getPosition() {
            return snowball.getPos();
        }

        public BlockPos getBlockPos() {
            return snowball.getBlockPos();
        }

        /** Append a new flight-path point with cap and dedup. */
        void appendTrailPoint(Vec3d pos) {
            if (pos == null) return;
            // Skip near-duplicate consecutive points so a stationary
            // snowball doesn't bloat the trail list.
            if (!trail.isEmpty()) {
                Vec3d last = trail.get(trail.size() - 1);
                if (last.squaredDistanceTo(pos) < 1e-4) return;
            }
            trail.add(pos);
            if (trail.size() > 200) {
                trail.remove(0);
            }
        }

        /** Read-only view of the recorded flight path. */
        public List<Vec3d> getTrail() {
            return trail;
        }
    }

    // ---- Color resolution (Theme / preset / Black) --------------------
    /**
     * Resolve the active accent color for the circle visualisers
     * (CIRCLE_10, BOZHESTVENNAYA_AURA, SNEZHOK_PREDICTION,
     * snowball tracker rings). When {@code circleUseThemeColor} is
     * on, follows the active client theme; off, looks up the named
     * preset directly. Pure-black is a special override that
     * doesn't exist in the Theme palette list.
     */
    public int resolveCircleColor() {
        return resolvePresetColor(circleUseThemeColor.isValue(), circleColorPreset.getSelected());
    }

    /**
     * Resolve the active accent color for the box visualisers
     * (TRAPKA, TRAPKA_DRAGON, PLAST). Independent from the circle
     * accent so a user can paint a black trapka box and a bright
     * dezo ring at the same time without trade-offs.
     */
    public int resolveBoxColor() {
        return resolvePresetColor(boxUseThemeColor.isValue(), boxColorPreset.getSelected());
    }

    private static int resolvePresetColor(boolean useTheme, String presetName) {
        if (!useTheme && "Black".equalsIgnoreCase(presetName)) {
            return 0xFF000000;
        }
        vorga.phazeclient.implement.menu.MenuPalette palette = useTheme
                ? vorga.phazeclient.implement.features.modules.client.Theme
                        .getInstance().getCurrentMenuPalette()
                : vorga.phazeclient.implement.menu.MenuPalettes.byName(presetName);
        return 0xFF000000 | (palette.chipActive() & 0x00FFFFFF);
    }
}

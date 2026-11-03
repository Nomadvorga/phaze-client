package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.ThrowablePotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Trajectory prediction. Computes the impact point of throwables
 * the player is currently holding (snowball / egg / pearl / xp
 * bottle / splash potion / trident / bow / crossbow) plus every
 * own-projectile already in flight, and exposes the data via
 * accessors the world-render mixin draws over.
 *
 * <h3>Why not bundle the renderer here</h3>
 * The renderer needs a {@link net.minecraft.client.util.math.MatrixStack}
 * scoped to the world-render pass. Keeping math in the module and
 * paint in the mixin lets the same prediction reuse different
 * render passes (debug overlay, block highlight, future radar) and
 * keeps the module test-friendly.
 *
 * <h3>Simulation contract</h3>
 * Each "predict" call simulates up to {@link #MAX_TICKS} ticks of
 * projectile-specific vanilla integration: thrown items apply gravity
 * and drag before moving; arrows/tridents move before gravity. Live
 * projectiles use their actual gravity and fluid state. We stop at
 * the nearest block/entity contact or below the world's build limit.
 *
 * <h3>Adapted from</h3>
 * {@code winvi.moscow.soupbetter.modules.PredictionsModule}. The
 * Phaze port keeps the simulation maths but drops the upstream's
 * Theme-driven helper colour - we expose a per-module colour
 * setting instead.
 */
public final class Predictions extends Module {
    private static final Predictions INSTANCE = new Predictions();
    /** Hard cap on simulation ticks - matches the upstream cap, ~15s of flight at 20Hz. */
    private static final int MAX_TICKS = 300;
    private static final int HELD_PREDICTION_CACHE_SIZE = 4;
    private final CachedPrediction[] heldPredictionCache = new CachedPrediction[HELD_PREDICTION_CACHE_SIZE];
    private Object heldPredictionWorld;
    private long heldPredictionTick = Long.MIN_VALUE;
    private int heldPredictionCacheCount;
    private int heldPredictionCacheWriteIndex;

    // ---- Trajectory ---------------------------------------------------
    // Everything that controls the line itself: should we draw it,
    // how thick, optional fade-out behind the projectile.
    public final SectionSetting trajectorySection = new SectionSetting("Trajectory");
    public final BooleanSetting predictHeld = new BooleanSetting(
            "Predict Held",
            "Project the trajectory of the throwable currently in your hands"
    ).setValue(true);
    public final ValueSetting lineWidth = new ValueSetting(
            "Line Width",
            "Pixel thickness of the trajectory polyline"
    ).range(1, 6).step(1).setValue(2);
    public final BooleanSetting fadeTrail = new BooleanSetting(
            "Smooth Fade",
            "Fade out the trajectory line behind the projectile so it visually dissolves as it flies"
    ).setValue(true);
    public final ValueSetting fadeDistance = new ValueSetting(
            "Fade Distance",
            "Length of the fade region in blocks: how much of the line is already fading at any given moment"
    ).range(0.5f, 8.0f).step(0.1f).setValue(2.0f)
            .visible(() -> fadeTrail.isValue());

    // ---- Impact Marker ------------------------------------------------
    // Marker drawn at the predicted impact point. All settings here
    // are gated behind the master Impact Marker toggle, and a few
    // are mode-specific (Sphere vs. Circle) so they only show up
    // when the corresponding style is selected.
    public final SectionSetting markerSection = new SectionSetting("Impact Marker");
    public final BooleanSetting showImpactSphere = new BooleanSetting(
            "Show Marker",
            "Render a marker at the predicted projectile impact point"
    ).setValue(true);
    public final SelectSetting impactMarkerStyle = new SelectSetting(
            "Marker Style",
            "Sphere = filled ball at impact, Circle = thin floor ring only"
    ).value("Sphere", "Circle").selected("Sphere")
            .visible(() -> showImpactSphere.isValue());
    public final ValueSetting impactSphereRadius = new ValueSetting(
            "Marker Size",
            "Marker radius in blocks"
    ).range(0.10f, 1.5f).step(0.05f).setValue(0.35f)
            .visible(() -> showImpactSphere.isValue());
    public final ValueSetting sphereOpacity = new ValueSetting(
            "Sphere Opacity",
            "How solid the impact sphere body looks (0 = ghost, 100 = fully opaque)"
    ).range(0, 100).step(1).setValue(100)
            .visible(() -> showImpactSphere.isValue()
                    && !"Circle".equalsIgnoreCase(impactMarkerStyle.getSelected()));
    public final ValueSetting sphereYOffset = new ValueSetting(
            "Sphere Y Offset",
            "Vertical offset of the sphere relative to the impact point (negative = lower, positive = higher)"
    ).range(-0.50f, 0.50f).step(0.05f).setValue(-0.10f)
            .visible(() -> showImpactSphere.isValue()
                    && !"Circle".equalsIgnoreCase(impactMarkerStyle.getSelected()));
    public final ValueSetting circleThickness = new ValueSetting(
            "Circle Thickness",
            "World-space thickness of the impact circle in blocks"
    ).range(0.02f, 0.40f).step(0.01f).setValue(0.02f)
            .visible(() -> showImpactSphere.isValue()
                    && "Circle".equalsIgnoreCase(impactMarkerStyle.getSelected()));
    public final BooleanSetting showGlow = new BooleanSetting(
            "Glow",
            "Render a soft bloom halo behind the impact marker"
    ).setValue(true).visible(() -> showImpactSphere.isValue());
    public final ValueSetting glowStrength = new ValueSetting(
            "Glow Strength",
            "How bright / large the bloom halo around the marker is"
    ).range(0.20f, 4.0f).step(0.05f).setValue(1.5f)
            .visible(() -> showImpactSphere.isValue() && showGlow.isValue());
    public final BooleanSetting glowPulsate = new BooleanSetting(
            "Glow Pulsate",
            "Smoothly pulse the glow opacity between 10% and 100%"
    ).setValue(false).visible(() -> showImpactSphere.isValue() && showGlow.isValue());
    public final ValueSetting glowPulsateSpeed = new ValueSetting(
            "Pulsate Speed",
            "Pulses per second. 1.0 = one full pulse per second."
    ).range(0.1f, 4.0f).step(0.05f).setValue(1.0f)
            .visible(() -> showImpactSphere.isValue() && showGlow.isValue() && glowPulsate.isValue());

    // ---- Color selection -----------------------------------------------
    // Two-mode color: by default we follow the active client Theme so
    // the prediction visuals match the rest of the UI without any
    // tweaking. Disabling the toggle reveals a preset picker with
    // exactly the same 22 names the Theme module exposes, so the user
    // can pin a fixed accent regardless of which menu theme is active.
    // The chosen color drives the line, the floor ring, the entity
    // ring, and tints the bloom glow billboard.
    public final SectionSetting colorSection = new SectionSetting("Color");
    public final BooleanSetting useThemeColor = new BooleanSetting(
            "Theme Color",
            "Use the active Theme accent color for the line, ring and glow"
    ).setValue(true);
    public final SelectSetting colorPreset = new SelectSetting(
            "Color Preset",
            "Pick a fixed color preset (same names as the Theme list)"
    ).value(
            "Black",
            "Lunar Blue",
            "Mocha Gold",
            "Rose Quartz",
            "Emerald Frost",
            "Arctic Mint",
            "Crimson Silk",
            "Solar Ember",
            "Midnight Bloom",
            "Desert Mirage",
            "Sapphire Steel",
            "Velvet Plum",
            "Frosted Peach",
            "Moss Smoke",
            "Polar Night",
            "Snow",
            "Obsidian",
            "Nebula",
            "Coral",
            "Jade",
            "Sunset",
            "Violet",
            "Ocean"
    ).selected("Lunar Blue")
            .visible(() -> !useThemeColor.isValue());

    // ---- Entity-hit color override -------------------------------------
    // When the prediction lands on a mob / player, override the
    // accent with a dedicated colour (default red) so the visual
    // distinction "you'll hit a target vs. you'll hit a wall" is
    // immediate at a glance. Mode mirrors the main color: a Theme
    // toggle that follows the active menu accent, a preset picker
    // when the toggle is off (with a "Default" option that locks to
    // the requested {@code #FE5053} red).
    public final BooleanSetting entityColorEnabled = new BooleanSetting(
            "Entity Hit Color",
            "Use a different colour when the prediction lands on a mob or player"
    ).setValue(true);
    public final BooleanSetting entityUseThemeColor = new BooleanSetting(
            "Entity Theme Color",
            "Use the active Theme accent for entity hits instead of a preset"
    ).setValue(false)
            .visible(() -> entityColorEnabled.isValue());
    public final SelectSetting entityColorPreset = new SelectSetting(
            "Entity Color Preset",
            "Pick a fixed colour for entity hits. Default = #FE5053"
    ).value(
            "Default",
            "Black",
            "Lunar Blue",
            "Mocha Gold",
            "Rose Quartz",
            "Emerald Frost",
            "Arctic Mint",
            "Crimson Silk",
            "Solar Ember",
            "Midnight Bloom",
            "Desert Mirage",
            "Sapphire Steel",
            "Velvet Plum",
            "Frosted Peach",
            "Moss Smoke",
            "Polar Night",
            "Snow",
            "Obsidian",
            "Nebula",
            "Coral",
            "Jade",
            "Sunset",
            "Violet",
            "Ocean"
    ).selected("Default")
            .visible(() -> entityColorEnabled.isValue() && !entityUseThemeColor.isValue());

    private Predictions() {
        super("predictions", "Predictions", ModuleCategory.UTILITIES);
        predictHeld.setFullWidth(true);
        lineWidth.setFullWidth(true);
        fadeTrail.setFullWidth(true);
        fadeDistance.setFullWidth(true);
        showImpactSphere.setFullWidth(true);
        impactMarkerStyle.setFullWidth(true);
        impactSphereRadius.setFullWidth(true);
        sphereOpacity.setFullWidth(true);
        sphereYOffset.setFullWidth(true);
        circleThickness.setFullWidth(true);
        showGlow.setFullWidth(true);
        glowStrength.setFullWidth(true);
        glowPulsate.setFullWidth(true);
        glowPulsateSpeed.setFullWidth(true);
        useThemeColor.setFullWidth(true);
        colorPreset.setFullWidth(true);
        entityColorEnabled.setFullWidth(true);
        entityUseThemeColor.setFullWidth(true);
        entityColorPreset.setFullWidth(true);
        // Order: Trajectory -> Impact Marker -> Color. Within
        // Impact Marker we go style-first then size, opacity, the
        // mode-specific tweaks (Y offset for Sphere, thickness for
        // Circle), then glow toggles. Mirrors the visual editing
        // flow: pick what kind of marker, set its size, fine-tune
        // the chosen mode, optionally light it up.
        setup(trajectorySection, predictHeld, lineWidth, fadeTrail, fadeDistance,
                markerSection, showImpactSphere, impactMarkerStyle, impactSphereRadius,
                sphereOpacity, sphereYOffset, circleThickness, showGlow, glowStrength,
                glowPulsate, glowPulsateSpeed,
                colorSection, useThemeColor, colorPreset,
                entityColorEnabled, entityUseThemeColor, entityColorPreset);
    }

    public static Predictions getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Projects the predicted impact path of throwables in your hands and your own projectiles in flight";
    }

    @Override
    public String getIcon() {
        return "predictions.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /** True iff the held-hand prediction should be drawn this frame. */
    public boolean shouldPredictHeld() {
        if (!isEnabled() || !predictHeld.isValue()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return false;
        return classifyHeldStack(mc.player.getMainHandStack()) != HeldType.NONE
                || classifyHeldStack(mc.player.getOffHandStack()) != HeldType.NONE;
    }

    /**
     * Resolve the active accent color used for line / ring / glow.
     * Theme mode reads the active menu palette's {@code chipActive}
     * accent (the same one driving the GUI accents); preset mode
     * looks up the named palette directly so the visual pin doesn't
     * shift when the menu theme changes. Always returns ARGB with
     * full alpha because per-element alpha is decided at the draw
     * site (e.g. the glow halo modulates its own alpha by strength).
     */
    public int resolveAccentColor() {
        // "Black" is a Predictions-only override that doesn't exist
        // in the Theme palette list - short-circuit before trying
        // the palette lookup so we don't fall back to the default
        // Lunar Blue accent for it. Full opacity, pure black RGB.
        if (!useThemeColor.isValue() && "Black".equalsIgnoreCase(colorPreset.getSelected())) {
            return 0xFF000000;
        }
        vorga.phazeclient.implement.menu.MenuPalette palette;
        if (useThemeColor.isValue()) {
            palette = vorga.phazeclient.implement.features.modules.client.Theme
                    .getInstance().getCurrentMenuPalette();
        } else {
            palette = vorga.phazeclient.implement.menu.MenuPalettes.byName(colorPreset.getSelected());
        }
        // chipActive is the strongest accent in the palette and the
        // one used for "selected" highlights in the GUI - perfect
        // for a high-contrast world overlay. Force full alpha; per
        // draw site re-applies its own alpha as needed.
        return 0xFF000000 | (palette.chipActive() & 0x00FFFFFF);
    }

    /**
     * Resolve the colour used when the prediction lands on a living
     * entity (mob or player). Returns {@link #resolveAccentColor()}
     * when the entity-color override is disabled, so callers can
     * use the same code path regardless of the toggle state.
     *
     * <p>Modes:
     * <ul>
     *   <li><b>Theme on</b> - active palette {@code chipActive},
     *       same accent the rest of the GUI uses.</li>
     *   <li><b>Preset = Default</b> - the requested {@code #FE5053}
     *       red, which doesn't exist in any palette and so needs an
     *       explicit fallback.</li>
     *   <li><b>Preset = Black</b> - mirrors the main color picker's
     *       Black escape hatch.</li>
     *   <li><b>Other preset</b> - palette lookup by name.</li>
     * </ul>
     * Always returns ARGB with full alpha; per-element alpha is
     * applied at the draw site.
     */
    public int resolveEntityHitColor() {
        if (!entityColorEnabled.isValue()) {
            return resolveAccentColor();
        }
        if (entityUseThemeColor.isValue()) {
            vorga.phazeclient.implement.menu.MenuPalette palette =
                    vorga.phazeclient.implement.features.modules.client.Theme
                            .getInstance().getCurrentMenuPalette();
            return 0xFF000000 | (palette.chipActive() & 0x00FFFFFF);
        }
        String preset = entityColorPreset.getSelected();
        // "Default" is the requested fixed red - it isn't a palette
        // name so we hardcode the ARGB here. Same idea as the main
        // picker's Black, just on a different accent colour.
        if ("Default".equalsIgnoreCase(preset)) {
            return 0xFFFE5053;
        }
        if ("Black".equalsIgnoreCase(preset)) {
            return 0xFF000000;
        }
        vorga.phazeclient.implement.menu.MenuPalette palette =
                vorga.phazeclient.implement.menu.MenuPalettes.byName(preset);
        return 0xFF000000 | (palette.chipActive() & 0x00FFFFFF);
    }

    /** Vanilla release speed; no ballistic preview for rockets or Riptide. */
    public double initialVelocityFor(HeldType type, ItemStack stack, MinecraftClient mc) {
        return switch (type) {
            case SNOWBALL, EGG, ENDER_PEARL -> 1.5;
            case EXPERIENCE_BOTTLE -> (double) 0.7F;
            case SPLASH_POTION -> 0.5;
            case TRIDENT -> mc.player.getItemUseTime() >= 10
                    && EnchantmentHelper.getTridentSpinAttackStrength(stack, mc.player) <= 0.0F ? 2.5 : 0.0;
            case BOW -> ProjectilePhysics.bowSpeed(BowItem.getPullProgress(mc.player.getItemUseTime()));
            case CROSSBOW -> CrossbowItem.isCharged(stack) && !hasLoadedFirework(stack)
                    ? (double) 3.15F : 0.0;
            case NONE -> 0.0;
        };
    }

    private static boolean hasLoadedFirework(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
                .getProjectiles().stream().anyMatch(projectile -> projectile.isOf(Items.FIREWORK_ROCKET));
    }

    /** Vanilla launch velocity without random spread, including the shooter's movement. */
    static Vec3d initialMotionFor(HeldType type, float yaw, float pitch, double speed,
                                  Vec3d playerMovement, boolean onGround) {
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float roll = switch (type) {
            case EXPERIENCE_BOTTLE, SPLASH_POTION -> -20.0F;
            default -> 0.0F;
        };
        float cosPitch = MathHelper.cos(pitchRad);
        float dx = -MathHelper.sin(yawRad) * cosPitch;
        float dy = -MathHelper.sin((pitch + roll) * ((float) Math.PI / 180.0F));
        float dz = MathHelper.cos(yawRad) * cosPitch;
        Vec3d motion = new Vec3d(dx, dy, dz).normalize().multiply(speed);
        // CrossbowItem.shoot uses the direction-only overload and does not inherit movement.
        return type == HeldType.CROSSBOW ? motion
                : motion.add(playerMovement.x, onGround ? 0.0 : playerMovement.y, playerMovement.z);
    }

    /** Classifier for held throwable types, mirrors vanilla item-class hierarchy. */
    public HeldType classifyHeldStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return HeldType.NONE;
        var item = stack.getItem();
        // Wind-charge extends SnowballItem in vanilla but its
        // server-side flight model is different (linear, no gravity,
        // explosive impact handled by WindChargeEntity). Drawing
        // the standard parabola would lie about where it lands, so
        // skip prediction entirely on a held wind charge.
        if (item instanceof net.minecraft.item.WindChargeItem) return HeldType.NONE;
        if (item instanceof SnowballItem) return HeldType.SNOWBALL;
        if (item instanceof EggItem) return HeldType.EGG;
        if (item instanceof EnderPearlItem) return HeldType.ENDER_PEARL;
        if (item instanceof ExperienceBottleItem) return HeldType.EXPERIENCE_BOTTLE;
        if (item instanceof ThrowablePotionItem) return HeldType.SPLASH_POTION;
        if (item instanceof TridentItem) return HeldType.TRIDENT;
        if (item instanceof BowItem) return HeldType.BOW;
        if (item instanceof CrossbowItem) return HeldType.CROSSBOW;
        return HeldType.NONE;
    }

    /**
     * True if the held crossbow has the Multishot enchantment.
     * Used by the renderer to fan three trajectories at the
     * vanilla {@code -10° / 0° / +10°} local-up spread that vanilla's
     * {@code RangedWeaponItem.shootAll} produces when the enchant
     * is present (one shot fires straight, the other two fan out
     * 10 degrees on each side of the look direction).
     *
     * <p>Reads the enchantment level directly off the stack's
     * {@code ENCHANTMENTS} component instead of going through the
     * server-only {@code EnchantmentHelper.getProjectileCount}, so
     * the check works on the client without a server world.
     */
    public boolean hasMultishot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.component.type.ItemEnchantmentsComponent comp =
                stack.getOrDefault(net.minecraft.component.DataComponentTypes.ENCHANTMENTS,
                        net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        // Walk the stack's enchantments and check the registry key
        // matches MULTISHOT directly. Avoids the server-only
        // {@code EnchantmentHelper.getProjectileCount} which needs a
        // ServerWorld; iteration works fine on the client.
        for (var entry : comp.getEnchantments()) {
            if (entry.matchesKey(net.minecraft.enchantment.Enchantments.MULTISHOT)) {
                return comp.getLevel(entry) > 0;
            }
        }
        return false;
    }

    int loadedCrossbowProjectileCount(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
                .getProjectiles().size();
    }

    /** Cache held trajectories within one world tick; launch changes invalidate the entry. */
    TrajectoryResult predictCached(Vec3d startPos, Vec3d startMotion, ProjectilePhysics.Profile profile, Entity owner) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        long worldTick = mc.world.getTime();
        if (heldPredictionWorld != mc.world || heldPredictionTick != worldTick) {
            heldPredictionWorld = mc.world;
            heldPredictionTick = worldTick;
            heldPredictionCacheCount = 0;
            heldPredictionCacheWriteIndex = 0;
        }
        for (int i = 0; i < heldPredictionCacheCount; i++) {
            CachedPrediction cached = heldPredictionCache[i];
            if (cached != null && cached.matches(startPos, startMotion, profile, owner)) {
                return cached.result;
            }
        }

        TrajectoryResult result = predictInto(startPos, startMotion, profile, owner, null, new ArrayList<>(64));
        heldPredictionCache[heldPredictionCacheWriteIndex] = new CachedPrediction(
                startPos, startMotion, profile, owner, result
        );
        heldPredictionCacheWriteIndex = (heldPredictionCacheWriteIndex + 1) % HELD_PREDICTION_CACHE_SIZE;
        heldPredictionCacheCount = Math.min(HELD_PREDICTION_CACHE_SIZE, heldPredictionCacheCount + 1);
        return result;
    }

    private TrajectoryResult predictInto(
            Vec3d startPos,
            Vec3d startMotion,
            ProjectilePhysics.Profile profile,
            Entity owner,
            ProjectileEntity projectile,
            List<Vec3d> path
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        double gravity = projectile == null ? profile.gravity
                : projectile.hasNoGravity() ? 0.0 : projectile.getFinalGravity();
        float width = projectile == null ? profile.size : projectile.getWidth();
        float height = projectile == null ? profile.size : projectile.getHeight();
        HitResult hit = ProjectilePhysics.simulate(startPos, startMotion, profile, gravity, width, height,
                projectile != null && projectile.isTouchingWater(),
                new PredictionEnvironment(mc.world, owner, projectile), path, MAX_TICKS,
                mc.world.getBottomY() - 64.0);
        net.minecraft.util.math.Direction face = hit instanceof BlockHitResult block
                ? block.getSide() : net.minecraft.util.math.Direction.UP;
        Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        return new TrajectoryResult(path, hit.getPos(), hit.getType(), face, target);
    }

    /** Read-only world queries; never tick or reposition a real entity while predicting. */
    private static final class PredictionEnvironment implements ProjectilePhysics.Environment {
        private final Entity owner;
        private final Entity[] ownerVehicle;
        private final PredictionBlockAccess blocks;
        private final ProjectileFluidSampler fluids;
        private final ProjectileEntityQuery<Entity> entities;
        private final net.minecraft.world.border.WorldBorder border;
        private final boolean pushedByFluids;
        private boolean leftOwner;

        PredictionEnvironment(World world, Entity owner, ProjectileEntity projectile) {
            this.owner = owner;
            ownerVehicle = owner == null ? new Entity[0] : owner.getRootVehicle().streamSelfAndPassengers()
                    .filter(EntityPredicates.CAN_HIT).toArray(Entity[]::new);
            blocks = new PredictionBlockAccess(world, ShapeContext.of(projectile != null ? projectile : owner));
            fluids = new ProjectileFluidSampler(world, blocks, world.getDimension().ultrawarm());
            border = world.getWorldBorder();
            pushedByFluids = projectile == null || projectile.isPushedByFluids();
            leftOwner = owner == null || (projectile != null && hasLeftOwner(
                    projectile.getBoundingBox(), projectile.getVelocity()));
            entities = new ProjectileEntityQuery<>(new ProjectileEntityQuery.Source<>() {
                @Override public List<Entity> collect(Box bounds) {
                    return world.getOtherEntities(projectile, bounds, Entity::canBeHitByProjectile);
                }
                @Override public Box bounds(Entity entity) { return entity.getBoundingBox(); }
                @Override public boolean canHit(Entity entity) {
                    return entity.canBeHitByProjectile()
                            && (leftOwner || owner == null || !owner.isConnectedThroughVehicle(entity));
                }
            });
        }

        @Override
        public BlockHitResult blockCollision(Vec3d start, Vec3d end) {
            return blocks.collision(start, end, border);
        }

        @Override
        public HitResult entityCollision(Vec3d start, Vec3d clippedEnd, Box searchBox) {
            var contact = entities.collision(start, clippedEnd, searchBox);
            return contact == null ? null : new EntityHitResult(contact.entity(), contact.position());
        }

        private boolean hasLeftOwner(Box bounds, Vec3d motion) {
            Box search = bounds.stretch(motion).expand(1.0);
            for (Entity entity : ownerVehicle) {
                if (search.intersects(entity.getBoundingBox())) return false;
            }
            return true;
        }

        @Override
        public ProjectilePhysics.FluidMotion fluids(Box bounds, Vec3d motion) {
            if (!leftOwner) leftOwner = hasLeftOwner(bounds, motion);
            return fluids.sample(bounds, motion, pushedByFluids);
        }
    }

    /** Held-stack throwable classification used by the renderer to pick the launch math. */
    public enum HeldType {
        NONE, SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE, SPLASH_POTION, TRIDENT, BOW, CROSSBOW
    }

    // ----------------------------------------------------------------
    // Projectile trail tracking (own-projectile flight prediction)
    // ----------------------------------------------------------------
    // Whenever the LOCAL PLAYER spawns a projectile we register it
    // here, simulate its full flight forward at spawn time, and
    // store the path. Renderer reads this map to draw the REMAINING
    // path in front of each in-flight projectile - the line shrinks
    // as the projectile moves along it ("eats the line"). At the end
    // we draw the same impact marker (sphere / circle) that the
    // held-hand prediction uses, so the visual is consistent whether
    // the user is still aiming or has already thrown.
    private final Map<Integer, ProjectileTrail> trails = new HashMap<>();

    /** Network-thread entrypoint. Filters to LOCAL-PLAYER-owned
     *  projectiles only - other players' projectiles are ignored. */
    public void trackProjectile(ProjectileEntity projectile) {
        if (projectile == null || !isEnabled()) return;
        // Fireworks have player-controlled trajectory while elytra-
        // boosting (the user steers them), so a static spawn-time
        // prediction is meaningless for them. Skip the firework
        // family entirely.
        if (projectile instanceof net.minecraft.entity.projectile.FireworkRocketEntity) return;
        // Wind charges fly on a linear, gravity-less, fast-decaying
        // trajectory handled entirely by WindChargeEntity. Drawing
        // the standard ballistic-parabola trail would mislead the
        // user about where the charge actually lands, so skip
        // tracking them entirely - the held-hand check already
        // refuses to predict from a held wind charge stack, this
        // mirrors the same rule for in-flight entities.
        if (projectile instanceof net.minecraft.entity.projectile.WindChargeEntity) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        // Only track projectiles WE threw / shot. The local player
        // is the projectile's owner if and only if they're the one
        // that spawned it on the server.
        Entity owner = projectile.getOwner();
        if (owner == null || owner.getId() != mc.player.getId()) return;

        ProjectilePhysics.Profile profile = flightProfile(projectile);
        if (profile == null || isReturning(projectile)) return;
        // Use the actual server-provided launch state, including its random spread.
        Vec3d startPos = projectile.getPos();
        Vec3d startMotion = projectile.getVelocity();
        TrajectoryResult result = predictInto(startPos, startMotion, profile, owner, projectile, new ArrayList<>(64));
        if (result == null || result.path() == null || result.path().size() < 2) return;
        trails.put(projectile.getId(), new ProjectileTrail(projectile, result));
    }

    private static ProjectilePhysics.Profile flightProfile(ProjectileEntity projectile) {
        if (projectile instanceof TridentEntity) return ProjectilePhysics.Profile.TRIDENT;
        if (projectile instanceof PersistentProjectileEntity) return ProjectilePhysics.Profile.ARROW;
        if (projectile instanceof ExperienceBottleEntity) return ProjectilePhysics.Profile.EXPERIENCE_BOTTLE;
        if (projectile instanceof PotionEntity) return ProjectilePhysics.Profile.POTION;
        return projectile instanceof ThrownEntity ? ProjectilePhysics.Profile.THROWN : null;
    }

    private static boolean isReturning(ProjectileEntity projectile) {
        return projectile instanceof PersistentProjectileEntity persistent && persistent.isNoClip();
    }

    /** Per-tick: re-predict trajectory from current pos+velocity
     *  and prune dead. Re-predicting each tick lets the impact
     *  marker follow the projectile's ACTUAL flight (vanilla adds
     *  a small random spread at spawn, plus fluids / collisions
     *  can shift the path mid-flight) instead of being pinned to
     *  the initial spawn-time prediction. The marker uses that impact
     *  directly rather than lagging behind an animated position. */
    public void tickTrails() {
        if (trails.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Iterator<Map.Entry<Integer, ProjectileTrail>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            ProjectileTrail t = it.next().getValue();
            if (t.entity == null || t.entity.isRemoved() || !t.entity.isAlive() || isReturning(t.entity)) {
                it.remove();
                continue;
            }
            // Drop trails for projectiles that have effectively
            // stopped moving. Persistent projectiles (arrows /
            // tridents) zero their velocity the moment the server
            // pins them to a block, and snowball-like projectiles
            // also leave a near-zero velocity once they've hit and
            // are pending despawn. A single velocity-magnitude
            // check covers both cases without needing access to
            // the protected {@code isInGround} flag.
            Vec3d v = t.entity.getVelocity();
            if (v.lengthSquared() < 1.0E-4) {
                it.remove();
                continue;
            }
            // Re-simulate once per tick from the live entity, not from its held preview.
            Entity owner = t.entity.getOwner();
            if (owner == null && mc != null) owner = mc.player;
            Vec3d startPos = t.entity.getPos();
            Vec3d startMotion = v;
            ProjectilePhysics.Profile profile = flightProfile(t.entity);
            if (profile == null) {
                it.remove();
                continue;
            }
            TrajectoryResult fresh = predictInto(startPos, startMotion, profile, owner, t.entity, t.simulationPath);
            if (fresh != null && fresh.path() != null && fresh.path().size() >= 2) {
                t.result = fresh;
            }
        }
    }

    /** Read-only access to the live trail list for the renderer. */
    public java.util.Collection<ProjectileTrail> getTrails() {
        return trails.values();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        trails.clear();
    }

    /** Per-projectile precomputed flight path + impact marker. */
    public static final class ProjectileTrail {
        public final ProjectileEntity entity;
        /** Latest predicted trajectory; refreshed every tick by
         *  {@link #tickTrails()} so the path tracks the projectile's
         *  actual flight (vanilla random spread at spawn + drag +
         *  collisions). Mutable rather than final because we re-run
         *  the simulation each tick. */
        public TrajectoryResult result;

        private final List<Vec3d> simulationPath = new ArrayList<>(64);
        private final List<Vec3d> remainingPath = new ArrayList<>(64);

        ProjectileTrail(ProjectileEntity entity, TrajectoryResult result) {
            this.entity = entity;
            if (result != null && result.path() != null) {
                simulationPath.addAll(result.path());
                this.result = new TrajectoryResult(
                        simulationPath,
                        result.impact(),
                        result.type(),
                        result.face(),
                        result.entity()
                );
            } else {
                this.result = result;
            }
        }

        /** Live position of the projectile (camera-frame irrelevant - raw world coords). */
        public Vec3d getCurrentPos() {
            return entity != null ? entity.getPos() : null;
        }

        /** Returns the slice of the predicted path AHEAD of the
         *  projectile's current position, prepended with the
         *  projectile's actual location so the polyline starts
         *  exactly at the entity. The {@code currentPos} parameter
         *  is the lerped (partial-tick) position the renderer
         *  computes per frame, so the line "starts at the visible
         *  projectile" rather than at the last tick boundary - that
         *  removes the jitter when the line endpoint snaps every
         *  50ms while the projectile model interpolates smoothly.
         *
         *  <p>The previous implementation found the closest path
         *  WAYPOINT and started from there, which produced a visible
         *  V-shaped kink at the join: the line went projectile -&gt;
         *  next-waypoint, but next-waypoint was offset to the side
         *  of where the projectile actually was. Now we find the
         *  closest path SEGMENT, project the projectile onto it, and
         *  start the remaining-path slice from the END of that
         *  segment - so the line goes projectile -&gt; segment-end
         *  -&gt; ... -&gt; impact, with no detour. */
        public List<Vec3d> getRemainingPath(Vec3d currentPos) {
            if (result == null || result.path() == null) return java.util.Collections.emptyList();
            List<Vec3d> path = result.path();
            if (path.size() < 2) return path;
            Vec3d cur = currentPos != null ? currentPos : getCurrentPos();
            if (cur == null) return path;

            // Find the closest SEGMENT, not the closest waypoint.
            // Project cur onto each segment a->b and pick the one
            // with minimum perpendicular distance.
            int bestSegIdx = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i < path.size() - 1; i++) {
                Vec3d a = path.get(i);
                Vec3d b = path.get(i + 1);
                double abX = b.x - a.x;
                double abY = b.y - a.y;
                double abZ = b.z - a.z;
                double abLen2 = abX * abX + abY * abY + abZ * abZ;
                if (abLen2 < 1e-9) continue;
                double t = ((cur.x - a.x) * abX + (cur.y - a.y) * abY + (cur.z - a.z) * abZ) / abLen2;
                if (t < 0.0) t = 0.0;
                else if (t > 1.0) t = 1.0;
                double projX = a.x + abX * t;
                double projY = a.y + abY * t;
                double projZ = a.z + abZ * t;
                double dx = projX - cur.x;
                double dy = projY - cur.y;
                double dz = projZ - cur.z;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestDist) {
                    bestDist = d;
                    bestSegIdx = i;
                }
            }

            // Output: live projectile position + every waypoint
            // strictly AFTER the segment we're inside (i.e. starting
            // at index bestSegIdx + 1). The projectile is somewhere
            // along the segment [bestSegIdx, bestSegIdx+1], so
            // joining (cur) directly to (bestSegIdx+1) avoids any
            // backtrack, and the rest of the path continues forward.
            remainingPath.clear();
            remainingPath.add(cur);
            for (int i = bestSegIdx + 1; i < path.size(); i++) {
                remainingPath.add(path.get(i));
            }
            return remainingPath;
        }

        /** No-arg overload uses the entity's tick-boundary position.
         *  Prefer the lerped overload from the renderer. */
        public List<Vec3d> getRemainingPath() {
            return getRemainingPath(getCurrentPos());
        }
    }

    /** Path + first projectile contact (or simulation limit). */
    public record TrajectoryResult(java.util.List<Vec3d> path, Vec3d impact, HitResult.Type type,
                                    net.minecraft.util.math.Direction face, Entity entity) {
    }

    private record CachedPrediction(
            Vec3d startPos,
            Vec3d startMotion,
            ProjectilePhysics.Profile profile,
            Entity owner,
            TrajectoryResult result
    ) {
        private boolean matches(Vec3d start, Vec3d motion, ProjectilePhysics.Profile profile, Entity owner) {
            return same(startPos, start)
                    && same(startMotion, motion)
                    && this.profile == profile
                    && this.owner == owner;
        }

        private static boolean same(Vec3d first, Vec3d second) {
            return first != null && second != null
                    && Double.doubleToLongBits(first.x) == Double.doubleToLongBits(second.x)
                    && Double.doubleToLongBits(first.y) == Double.doubleToLongBits(second.y)
                    && Double.doubleToLongBits(first.z) == Double.doubleToLongBits(second.z);
        }
    }
}

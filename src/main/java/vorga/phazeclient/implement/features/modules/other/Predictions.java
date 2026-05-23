package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
 * forward Euler integration mirroring the vanilla
 * {@code ProjectileEntity.tick}: position += velocity, velocity *=
 * drag, velocity.y -= gravity. Drag is 0.99 in air / 0.8 in water
 * for thrown items, 0.6 in water for arrows. Gravity is read from
 * vanilla's {@code Entity.getFinalGravity} which already accounts
 * for slow-falling and item-specific overrides. We bail out early
 * on block hit, entity hit, or y &lt; -128.
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
        useThemeColor.setFullWidth(true);
        colorPreset.setFullWidth(true);
        // Order: Trajectory -> Impact Marker -> Color. Within
        // Impact Marker we go style-first then size, opacity, the
        // mode-specific tweaks (Y offset for Sphere, thickness for
        // Circle), then glow toggles. Mirrors the visual editing
        // flow: pick what kind of marker, set its size, fine-tune
        // the chosen mode, optionally light it up.
        setup(trajectorySection, predictHeld, lineWidth, fadeTrail, fadeDistance,
                markerSection, showImpactSphere, impactMarkerStyle, impactSphereRadius,
                sphereOpacity, sphereYOffset, circleThickness, showGlow, glowStrength,
                colorSection, useThemeColor, colorPreset);
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
     * Estimate the forward velocity for a held throwable. Bow / crossbow
     * scale with the use-time-charge progress, others have a fixed
     * launch speed mirroring vanilla. Returns 0 for non-throwables.
     */
    public double initialVelocityFor(HeldType type, ItemStack stack, MinecraftClient mc) {
        return switch (type) {
            case SNOWBALL, EGG, ENDER_PEARL -> 1.5;
            case EXPERIENCE_BOTTLE -> 0.7;
            case SPLASH_POTION -> 0.5;
            case TRIDENT -> 2.5;
            case BOW -> {
                int useTicks = mc.player.getItemUseTime();
                yield 3.0 * MathHelper.clamp((useTicks + mc.getRenderTickCounter().getTickDelta(false)) / 20.0F, 0.0F, 1.0F);
            }
            case CROSSBOW -> CrossbowItem.isCharged(stack) ? 3.15 : 0.0;
            case NONE -> 0.0;
        };
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
        if (item instanceof SplashPotionItem) return HeldType.SPLASH_POTION;
        if (item instanceof TridentItem) return HeldType.TRIDENT;
        if (item instanceof BowItem) return HeldType.BOW;
        if (item instanceof CrossbowItem) return HeldType.CROSSBOW;
        return HeldType.NONE;
    }

    /**
     * True if the held crossbow has the Multishot enchantment.
     * Used by the renderer to fan three trajectories at the
     * vanilla {@code -10° / 0° / +10°} yaw spread that vanilla's
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

    /**
     * Forward-integrate a projectile state and return the impact point.
     * Mirrors {@code ProjectileEntity.tick} exactly: position += velocity,
     * velocity *= drag, velocity.y -= gravity. Returns {@code null} if
     * the projectile flies past {@link #MAX_TICKS} without hitting
     * anything.
     */
    public TrajectoryResult predict(Vec3d startPos, Vec3d startMotion, double gravity, boolean trident, Entity owner) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        Vec3d pos = startPos;
        Vec3d motion = startMotion;
        java.util.List<Vec3d> path = new java.util.ArrayList<>();
        path.add(pos);
        for (int i = 0; i < MAX_TICKS; i++) {
            Vec3d prev = pos;
            pos = pos.add(motion);
            // Drag selection: trident always 0.99, persistent in water
            // 0.6, throwables in water 0.8, otherwise 0.99.
            BlockPos bp = BlockPos.ofFloored(prev);
            boolean inWater = mc.world.getBlockState(bp).getFluidState().isIn(FluidTags.WATER);
            float drag = trident ? 0.99F : (inWater ? 0.8F : 0.99F);
            motion = motion.multiply(drag).add(0.0, -gravity, 0.0);

            HitResult result = mc.world.raycast(new RaycastContext(prev, pos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    owner));
            if (result.getType() != HitResult.Type.MISS) {
                // Capture the block face we hit so the renderer can
                // orient the floor/wall ring against that surface
                // normal. For non-block hits the renderer falls back
                // to UP (horizontal floor ring).
                net.minecraft.util.math.Direction face =
                        (result instanceof net.minecraft.util.hit.BlockHitResult bhr)
                                ? bhr.getSide()
                                : net.minecraft.util.math.Direction.UP;
                path.add(result.getPos());
                return new TrajectoryResult(path, result.getPos(), HitResult.Type.BLOCK, face);
            }

            // Cheap entity intersect: any living non-self entity whose
            // expanded box crosses the segment counts as a hit.
            // Crucially we do NOT filter by visibility - an entity
            // that's fully invisible (invisibility potion, no armor,
            // no glow) still has a server-tracked bounding box, so
            // checking the box geometry directly lets the prediction
            // marker land on a target even when nothing is rendered
            // there visually. This is the "see-through invisibles"
            // behaviour: invisibility is a render-side trick, the
            // hitbox still exists.
            Vec3d a = prev, b = pos;
            Entity hitEntity = null;
            if (mc.world != null) {
                for (Entity ent : mc.world.getEntities()) {
                    if (!(ent instanceof LivingEntity)) continue;
                    if (ent == mc.player) continue;
                    if (owner != null && ent == owner) continue;
                    if (!ent.isAlive()) continue;
                    // No isInvisible() filter - we deliberately treat
                    // invisible entities the same as visible ones so
                    // the prediction can still target them.
                    if (ent.getBoundingBox().expand(0.25).intersects(a, b)) {
                        hitEntity = ent;
                        break;
                    }
                }
            }
            if (hitEntity != null) {
                path.add(pos);
                return new TrajectoryResult(path, pos, HitResult.Type.ENTITY,
                        net.minecraft.util.math.Direction.UP);
            }

            path.add(pos);
            if (pos.y < -128) {
                return new TrajectoryResult(path, pos, HitResult.Type.MISS,
                        net.minecraft.util.math.Direction.UP);
            }
        }
        return new TrajectoryResult(path, pos, HitResult.Type.MISS,
                net.minecraft.util.math.Direction.UP);
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;
        // Only track projectiles WE threw / shot. The local player
        // is the projectile's owner if and only if they're the one
        // that spawned it on the server.
        Entity owner = projectile.getOwner();
        if (owner == null || owner.getId() != mc.player.getId()) return;

        // Precompute the entire forward trajectory at spawn time,
        // mirroring the held-hand prediction. We use the projectile's
        // starting velocity and gravity bucket, then run the same
        // forward-Euler simulation that {@link #predict} does for
        // the held visual. Storing the path once means the renderer
        // only has to slice it on each frame, not re-simulate.
        Vec3d startPos = projectile.getPos();
        Vec3d startMotion = projectile.getVelocity();
        // Gravity bucket: same classification used for held items.
        // We default to the thrown-item bucket (0.03) for everything
        // but bow-arrow types (which use 0.05). Tridents land in
        // their own branch via instanceof check below.
        double gravity = 0.03;
        boolean trident = false;
        // Net.minecraft.entity.projectile types we care about:
        // PersistentProjectileEntity covers arrow / trident / spectral arrow.
        if (projectile instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
            gravity = 0.05;
            if (projectile instanceof net.minecraft.entity.projectile.TridentEntity) {
                trident = true;
            }
        }
        TrajectoryResult result = predict(startPos, startMotion, gravity, trident, owner);
        if (result == null || result.path() == null || result.path().size() < 2) return;
        trails.put(projectile.getId(), new ProjectileTrail(projectile, result));
    }

    /** Per-tick: re-predict trajectory from current pos+velocity
     *  and prune dead. Re-predicting each tick lets the impact
     *  marker follow the projectile's ACTUAL flight (vanilla adds
     *  a small random spread at spawn, plus drag / wind / collisions
     *  can shift the path mid-flight) instead of being pinned to
     *  the initial spawn-time prediction. The renderer then
     *  exponentially smooths the marker position toward the new
     *  prediction so the user sees a plump glide rather than a
     *  per-tick teleport. */
    public void tickTrails() {
        if (trails.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        Iterator<Map.Entry<Integer, ProjectileTrail>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            ProjectileTrail t = it.next().getValue();
            if (t.entity == null || t.entity.isRemoved() || !t.entity.isAlive()) {
                it.remove();
                continue;
            }
            // Re-simulate from current state. Player owner is the
            // entity-collision blacklist for the predict() helper -
            // it skips intersecting our own player so the line
            // doesn't think we're our own target.
            Entity owner = t.entity.getOwner();
            if (owner == null && mc != null) owner = mc.player;
            Vec3d startPos = t.entity.getPos();
            Vec3d startMotion = t.entity.getVelocity();
            double gravity = 0.03;
            boolean trident = false;
            if (t.entity instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
                gravity = 0.05;
                if (t.entity instanceof net.minecraft.entity.projectile.TridentEntity) {
                    trident = true;
                }
            }
            TrajectoryResult fresh = predict(startPos, startMotion, gravity, trident, owner);
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

        /** Smoothed impact-marker position. Exponentially decays
         *  toward {@code result.impact()} every frame so the user
         *  sees a plump glide rather than a per-tick teleport when
         *  the prediction shifts due to vanilla randomness. */
        public Vec3d smoothedImpact;
        /** Last frame timestamp (nanoseconds) used to compute the
         *  delta-time for the exponential smoother. {@code 0} means
         *  "no previous frame" so the first paint snaps directly to
         *  the live impact. */
        public long lastSmoothNanos = 0L;

        ProjectileTrail(ProjectileEntity entity, TrajectoryResult result) {
            this.entity = entity;
            this.result = result;
            this.smoothedImpact = result != null ? result.impact() : null;
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
                Vec3d ab = b.subtract(a);
                double abLen2 = ab.lengthSquared();
                if (abLen2 < 1e-9) continue;
                double t = cur.subtract(a).dotProduct(ab) / abLen2;
                if (t < 0.0) t = 0.0;
                else if (t > 1.0) t = 1.0;
                Vec3d proj = a.add(ab.multiply(t));
                double d = proj.squaredDistanceTo(cur);
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
            List<Vec3d> remaining = new ArrayList<>(path.size() - bestSegIdx);
            remaining.add(cur);
            for (int i = bestSegIdx + 1; i < path.size(); i++) {
                remaining.add(path.get(i));
            }
            return remaining;
        }

        /** No-arg overload uses the entity's tick-boundary position.
         *  Prefer the lerped overload from the renderer. */
        public List<Vec3d> getRemainingPath() {
            return getRemainingPath(getCurrentPos());
        }
    }

    /** Path + impact info returned by {@link #predict}. */
    public record TrajectoryResult(java.util.List<Vec3d> path, Vec3d impact, HitResult.Type type,
                                    net.minecraft.util.math.Direction face) {
    }
}

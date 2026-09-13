package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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

public final class Predictions extends Module {
    private static final Predictions INSTANCE = new Predictions();

    private static final int MAX_TICKS = 300;
    private static final int HELD_PREDICTION_CACHE_SIZE = 4;
    private final CachedPrediction[] heldPredictionCache = new CachedPrediction[HELD_PREDICTION_CACHE_SIZE];
    private Object heldPredictionWorld;
    private long heldPredictionTick = Long.MIN_VALUE;
    private int heldPredictionCacheCount;
    private int heldPredictionCacheWriteIndex;

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

    public boolean shouldPredictHeld() {
        if (!isEnabled() || !predictHeld.isValue()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return false;
        return classifyHeldStack(mc.player.getMainHandStack()) != HeldType.NONE
                || classifyHeldStack(mc.player.getOffHandStack()) != HeldType.NONE;
    }

    public int resolveAccentColor() {

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

        return 0xFF000000 | (palette.chipActive() & 0x00FFFFFF);
    }

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

    public double initialVelocityFor(HeldType type, ItemStack stack, MinecraftClient mc) {
        return switch (type) {
            case SNOWBALL, EGG, ENDER_PEARL -> 1.5;
            case EXPERIENCE_BOTTLE -> 0.7;
            case SPLASH_POTION -> 0.5;
            case TRIDENT -> 2.5;
            case BOW -> {
                int useTicks = mc.player.getItemUseTime();
                yield 3.0 * MathHelper.clamp((useTicks + mc.getRenderTickCounter().getTickProgress(false)) / 20.0F, 0.0F, 1.0F);
            }
            case CROSSBOW -> CrossbowItem.isCharged(stack) ? 3.15 : 0.0;
            case NONE -> 0.0;
        };
    }

    public HeldType classifyHeldStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return HeldType.NONE;
        var item = stack.getItem();

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

    public boolean hasMultishot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.component.type.ItemEnchantmentsComponent comp =
                stack.getOrDefault(net.minecraft.component.DataComponentTypes.ENCHANTMENTS,
                        net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);

        for (var entry : comp.getEnchantments()) {
            if (entry.matchesKey(net.minecraft.enchantment.Enchantments.MULTISHOT)) {
                return comp.getLevel(entry) > 0;
            }
        }
        return false;
    }

    public TrajectoryResult predictCached(Vec3d startPos, Vec3d startMotion, double gravity, boolean trident, Entity owner) {
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
            if (cached != null && cached.matches(startPos, startMotion, gravity, trident, owner)) {
                return cached.result;
            }
        }

        TrajectoryResult result = predict(startPos, startMotion, gravity, trident, owner);
        heldPredictionCache[heldPredictionCacheWriteIndex] = new CachedPrediction(
                startPos, startMotion, gravity, trident, owner, result
        );
        heldPredictionCacheWriteIndex = (heldPredictionCacheWriteIndex + 1) % HELD_PREDICTION_CACHE_SIZE;
        heldPredictionCacheCount = Math.min(HELD_PREDICTION_CACHE_SIZE, heldPredictionCacheCount + 1);
        return result;
    }

    public TrajectoryResult predict(Vec3d startPos, Vec3d startMotion, double gravity, boolean trident, Entity owner) {
        return predictInto(startPos, startMotion, gravity, trident, owner, new ArrayList<>(64));
    }

    private TrajectoryResult predictInto(
            Vec3d startPos,
            Vec3d startMotion,
            double gravity,
            boolean trident,
            Entity owner,
            List<Vec3d> path
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return null;
        Vec3d pos = startPos;
        Vec3d motion = startMotion;
        path.clear();
        path.add(pos);
        BlockPos.Mutable fluidPos = new BlockPos.Mutable();
        for (int i = 0; i < MAX_TICKS; i++) {
            Vec3d prev = pos;
            pos = pos.add(motion);

            fluidPos.set(MathHelper.floor(prev.x), MathHelper.floor(prev.y), MathHelper.floor(prev.z));
            boolean inWater = mc.world.getBlockState(fluidPos).getFluidState().isIn(FluidTags.WATER);
            float drag = trident ? 0.99F : (inWater ? 0.8F : 0.99F);
            motion = motion.multiply(drag).add(0.0, -gravity, 0.0);

            HitResult result = mc.world.raycast(new RaycastContext(prev, pos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    owner));
            if (result.getType() != HitResult.Type.MISS) {

                net.minecraft.util.math.Direction face =
                        (result instanceof net.minecraft.util.hit.BlockHitResult bhr)
                                ? bhr.getSide()
                                : net.minecraft.util.math.Direction.UP;
                path.add(result.getPos());
                return new TrajectoryResult(path, result.getPos(), HitResult.Type.BLOCK, face, null);
            }

            Vec3d a = prev, b = pos;
            net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(a, b).expand(1.0);
            Entity source = owner != null ? owner : mc.player;
            net.minecraft.util.hit.EntityHitResult entityHit = ProjectileUtil.raycast(
                    source, a, b, searchBox,
                    ent -> ent instanceof LivingEntity
                            && ent != mc.player
                            && (owner == null || ent != owner)
                            && ent.isAlive()
                            && !ent.isSpectator()
                            && (!ent.isInvisible() || ent.isGlowing()),
                    a.squaredDistanceTo(b) + 1.0E-7
            );
            if (entityHit != null && entityHit.getEntity() != null) {
                Vec3d hitPos = entityHit.getPos();
                path.add(hitPos);
                return new TrajectoryResult(path, hitPos, HitResult.Type.ENTITY,
                        net.minecraft.util.math.Direction.UP, entityHit.getEntity());
            }

            path.add(pos);
            if (pos.y < -128) {
                return new TrajectoryResult(path, pos, HitResult.Type.MISS,
                        net.minecraft.util.math.Direction.UP, null);
            }
        }
        return new TrajectoryResult(path, pos, HitResult.Type.MISS,
                net.minecraft.util.math.Direction.UP, null);
    }

    public enum HeldType {
        NONE, SNOWBALL, EGG, ENDER_PEARL, EXPERIENCE_BOTTLE, SPLASH_POTION, TRIDENT, BOW, CROSSBOW
    }

    private final Map<Integer, ProjectileTrail> trails = new HashMap<>();

    public void trackProjectile(ProjectileEntity projectile) {
        if (projectile == null || !isEnabled()) return;

        if (projectile instanceof net.minecraft.entity.projectile.FireworkRocketEntity) return;

        if (projectile instanceof net.minecraft.entity.projectile.WindChargeEntity) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        Entity owner = projectile.getOwner();
        if (owner == null || owner.getId() != mc.player.getId()) return;

        Vec3d startPos = projectile.getEntityPos();
        Vec3d startMotion = projectile.getVelocity();

        double gravity = 0.03;
        boolean trident = false;

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

            Vec3d v = t.entity.getVelocity();
            if (v.lengthSquared() < 1.0E-4) {
                it.remove();
                continue;
            }

            Entity owner = t.entity.getOwner();
            if (owner == null && mc != null) owner = mc.player;

            Vec3d startPos = t.entity.getEntityPos();
            Vec3d startMotion = v;
            double gravity = 0.03;
            boolean trident = false;
            if (t.entity instanceof net.minecraft.entity.projectile.PersistentProjectileEntity) {
                gravity = 0.05;
                if (t.entity instanceof net.minecraft.entity.projectile.TridentEntity) {
                    trident = true;
                }
            }
            TrajectoryResult fresh = predictInto(startPos, startMotion, gravity, trident, owner, t.simulationPath);
            if (fresh != null && fresh.path() != null && fresh.path().size() >= 2) {
                t.result = fresh;
            }
        }
    }

    public java.util.Collection<ProjectileTrail> getTrails() {
        return trails.values();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        trails.clear();
    }

    public static final class ProjectileTrail {
        public final ProjectileEntity entity;

        public TrajectoryResult result;

        public Vec3d smoothedImpact;

        public long lastSmoothNanos = 0L;
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
            this.smoothedImpact = result != null ? result.impact() : null;
        }

        public Vec3d getCurrentPos() {

            return entity != null ? entity.getEntityPos() : null;
        }

        public List<Vec3d> getRemainingPath(Vec3d currentPos) {
            if (result == null || result.path() == null) return java.util.Collections.emptyList();
            List<Vec3d> path = result.path();
            if (path.size() < 2) return path;
            Vec3d cur = currentPos != null ? currentPos : getCurrentPos();
            if (cur == null) return path;

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

            remainingPath.clear();
            remainingPath.add(cur);
            for (int i = bestSegIdx + 1; i < path.size(); i++) {
                remainingPath.add(path.get(i));
            }
            return remainingPath;
        }

        public List<Vec3d> getRemainingPath() {
            return getRemainingPath(getCurrentPos());
        }
    }

    public record TrajectoryResult(java.util.List<Vec3d> path, Vec3d impact, HitResult.Type type,
                                    net.minecraft.util.math.Direction face, Entity entity) {
    }

    private record CachedPrediction(
            Vec3d startPos,
            Vec3d startMotion,
            double gravity,
            boolean trident,
            Entity owner,
            TrajectoryResult result
    ) {
        private boolean matches(Vec3d start, Vec3d motion, double gravity, boolean trident, Entity owner) {
            return same(startPos, start)
                    && same(startMotion, motion)
                    && Double.doubleToLongBits(this.gravity) == Double.doubleToLongBits(gravity)
                    && this.trident == trident
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

package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class FTHelper extends Module {

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

    public final MultiSelectSetting enabledAbilities = new MultiSelectSetting(
            "Enabled Abilities",
            "Pick which FunTime abilities should stay active in the helper."
    ).value(
            "Trapka",
            "Dragon Trap",
            "Disorientation",
            "Revealing Dust",
            "Fire Vortex",
            "Plate",
            "Divine Aura",
            "Freeze Snowball"
    ).selected(
            "Trapka",
            "Dragon Trap",
            "Disorientation",
            "Revealing Dust",
            "Fire Vortex",
            "Plate",
            "Divine Aura",
            "Freeze Snowball"
    );

    public final BooleanLike trapkaEnabled = () -> enabledAbilities.getSelected().contains("Trapka");
    public final BooleanLike drakonTrapkaEnabled = () -> enabledAbilities.getSelected().contains("Dragon Trap");
    public final BooleanLike dezorientationEnabled = () -> enabledAbilities.getSelected().contains("Disorientation");
    public final BooleanLike yavnayaPylEnabled = () -> enabledAbilities.getSelected().contains("Revealing Dust");
    public final BooleanLike ognennyiSmerchEnabled = () -> enabledAbilities.getSelected().contains("Fire Vortex");
    public final BooleanLike plastEnabled = () -> enabledAbilities.getSelected().contains("Plate");
    public final BooleanLike bozhestvennayaAuraEnabled = () -> enabledAbilities.getSelected().contains("Divine Aura");
    public final BooleanLike snezhokZamorozkaEnabled = () -> enabledAbilities.getSelected().contains("Freeze Snowball");

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

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    private FTHelper() {
        super("ft_helper", "FT Helper", ModuleCategory.UTILITIES);
        enabledAbilities.setFullWidth(true);
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
                enabledAbilities,
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

    public enum HighlightType {
        NONE,
        TRAPKA,
        TRAPKA_DRAGON,
        CIRCLE_10,
        PLAST,
        BOZHESTVENNAYA_AURA,
        SNEZHOK_PREDICTION
    }

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

    public BlockPos getPlayerPos() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getBlockPos() : null;
    }

    public BlockPos getTargetBlockPos() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    public Direction getTargetBlockSide() {
        if (!isEnabled()) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return null;
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            return blockHit.getSide();
        }
        return null;
    }

    public float getPlayerPitch() {
        if (!isEnabled()) return 0.0F;
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null ? client.player.getPitch() : 0.0F;
    }

    private final List<TrackedSnowball> tracked = new ArrayList<>();

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

    public void tickTrackedSnowballs() {
        Iterator<TrackedSnowball> it = tracked.iterator();
        while (it.hasNext()) {
            TrackedSnowball t = it.next();
            if (t.snowball == null || t.snowball.isRemoved() || !t.snowball.isAlive()) {
                it.remove();
                continue;
            }

            t.appendTrailPoint(t.snowball.getEntityPos());
        }
    }

    public List<TrackedSnowball> getTrackedSnowballs() {
        return tracked;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        tracked.clear();
    }

    public static final class TrackedSnowball {
        public final SnowballEntity snowball;

        private final List<Vec3d> trail = new ArrayList<>();

        TrackedSnowball(SnowballEntity snowball) {
            this.snowball = snowball;
            if (snowball != null) {

                trail.add(snowball.getEntityPos());
            }
        }

        public Vec3d getPosition() {

            return snowball.getEntityPos();
        }

        public Vec3d getRenderPosition(float tickDelta) {
            return new Vec3d(
                    MathHelper.lerp(tickDelta, snowball.lastRenderX, snowball.getX()),
                    MathHelper.lerp(tickDelta, snowball.lastRenderY, snowball.getY()),
                    MathHelper.lerp(tickDelta, snowball.lastRenderZ, snowball.getZ())
            );
        }

        public BlockPos getBlockPos() {
            return snowball.getBlockPos();
        }

        void appendTrailPoint(Vec3d pos) {
            if (pos == null) return;

            if (!trail.isEmpty()) {
                Vec3d last = trail.get(trail.size() - 1);
                if (last.squaredDistanceTo(pos) < 1e-4) return;
            }
            trail.add(pos);
            if (trail.size() > 200) {
                trail.remove(0);
            }
        }

        public List<Vec3d> getTrail() {
            return trail;
        }
    }

    public int resolveCircleColor() {
        return resolvePresetColor(circleUseThemeColor.isValue(), circleColorPreset.getSelected());
    }

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

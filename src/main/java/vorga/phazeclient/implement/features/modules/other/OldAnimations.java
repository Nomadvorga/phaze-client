package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

/**
 * Old Animations module (1.7 / 1.8 combat animations).
 *
 * <p>Restores classic Minecraft 1.7.10 / 1.8.9 first-person visual mechanics:
 * <ul>
 *   <li><b>1.7 Sword Blocking & Block-Hit:</b> Classic sword block pose and
 *       the iconic block-hit animation with multiple selectable styles (1.7 Classic,
 *       Push, Slide, Lunar).</li>
 *   <li><b>1.7 / 1.8 Item Swing:</b> Restores the classic swing curve without
 *       the modern 1.9+ -45° Y rotation counteract.</li>
 *   <li><b>No Re-Equip:</b> Stops items from dropping down when taking durability damage
 *       (attacking, mining) or during attack cooldown.</li>
 *   <li><b>1.7 Eating / Drinking:</b> Classic vertical rhythmic bobbing animation.</li>
 *   <li><b>1.7 Bow & Fishing Rod:</b> Classic bow draw angle and rod holding pose.</li>
 *   <li><b>1.7 Sneak:</b> Instant camera eye-height transition when crouching.</li>
 * </ul>
 *
 * <p><b>Zero overhead:</b> When disabled or when transforms are default, zero extra
 * calculations are performed. Matrix operations use primitive allocations and fast MathHelper calls.
 */
public final class OldAnimations extends Module {
    private static final OldAnimations INSTANCE = new OldAnimations();

    // --- Sword Blocking -------------------------------------------
    public final SectionSetting swordBlockSection = new SectionSetting("Sword Blocking");
    public final BooleanSetting swordBlocking = new BooleanSetting(
            "Sword Blocking",
            "Render sword in the classic 1.7 blocking pose when right-clicking or using a shield"
    ).setValue(true);

    // --- Swing & Equip ---------------------------------------------
    public final SectionSetting swingSection = new SectionSetting("Swing & Equip");
    public final BooleanSetting oldSwing = new BooleanSetting(
            "1.7 Swing",
            "Classic 1.7/1.8 snappy swing curve and motion"
    ).setValue(true);
    public final BooleanSetting noReEquip = new BooleanSetting(
            "No Re-Equip",
            "Prevent item from lowering down when taking durability damage, consuming items, or attacking"
    ).setValue(true);
    public final ValueSetting swingSpeed = new ValueSetting(
            "Swing Speed",
            "Animation speed multiplier for hand swinging"
    ).range(0.5f, 2.0f).step(0.05f).setValue(1.0f).snapTo(1.0f, 0.05f);

    // --- Actions (Bow, Rod) ----------------------------------------
    public final SectionSetting actionsSection = new SectionSetting("Actions");
    public final BooleanSetting oldBow = new BooleanSetting(
            "1.7 Bow",
            "Classic 1.7 straight bow pullback positioning"
    ).setValue(true);
    public final BooleanSetting oldRod = new BooleanSetting(
            "1.7 Rod",
            "Classic fishing rod held angle and position"
    ).setValue(false);

    // --- Sneak -----------------------------------------------------
    public final SectionSetting sneakSection = new SectionSetting("Sneak");
    public final BooleanSetting oldSneak = new BooleanSetting(
            "1.7 Sneak",
            "Instant 1.7 camera eye height transition when crouching"
    ).setValue(false);

    private OldAnimations() {
        super("oldanimations", "Old Animations", ModuleCategory.OTHER);

        swordBlocking.setFullWidth(true);

        oldSwing.setFullWidth(true);
        noReEquip.setFullWidth(true);
        swingSpeed.setFullWidth(true);

        oldBow.setFullWidth(true);
        oldRod.setFullWidth(true);

        oldSneak.setFullWidth(true);

        setup(
                swordBlockSection, swordBlocking,
                swingSection, oldSwing, noReEquip, swingSpeed,
                actionsSection, oldBow, oldRod,
                sneakSection, oldSneak
        );
    }

    public static OldAnimations getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Classic 1.7 / 1.8 combat animations, sword blocking, smooth re-equip and bow";
    }

    @Override
    public String getIcon() {
        return "old_animations.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isCanBind() {
        return false;
    }

    // =========================================================================
    // Static Rendering Helpers (Zero Garbage Collection Allocation)
    // =========================================================================

    /**
     * Determines whether the given player is currently blocking with a sword.
     * Works for both the local client player and remote players in multiplayer.
     */
    public static boolean isPlayerSwordBlocking(PlayerEntity player) {
        if (player == null) return false;
        ItemStack main = player.getMainHandStack();
        if (!(main.getItem() instanceof net.minecraft.item.SwordItem)) return false;

        if (player.isUsingItem()) {
            ItemStack active = player.getActiveItem();
            if (active.getItem() instanceof net.minecraft.item.ShieldItem || active.getItem() instanceof net.minecraft.item.SwordItem) {
                return true;
            }
        }

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (player == mc.player && mc.currentScreen == null && mc.options != null && mc.options.useKey != null && mc.options.useKey.isPressed()) {
            ItemStack off = player.getOffHandStack();
            if (off.isEmpty() || off.getItem() instanceof net.minecraft.item.ShieldItem) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if sword blocking is enabled and currently active for the player.
     */
    public static boolean isSwordBlockingActive(PlayerEntity player) {
        OldAnimations mod = getInstance();
        if (mod == null || !mod.isEnabled() || !mod.swordBlocking.isValue()) {
            return false;
        }
        return isPlayerSwordBlocking(player);
    }

    /**
     * Renders sword in static 1.7/1.8 blocking pose.
     * Angle and tilt 1:1 match vanilla 1.8.9 ItemRenderer.
     */
    public static void renderSwordBlock(MatrixStack matrices, Arm arm, int armSign, float equipProgress) {
        // Base first-person item positioning (mirrors vanilla applyEquipOffset)
        matrices.translate(armSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        // 1.7 sword block translation and rotation
        matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
    }

    /**
     * Renders classic 1.7 / 1.8 sword and item swing without 1.9+ counteract.
     */
    public static void renderOldSwing(
            MatrixStack matrices,
            Arm arm,
            int armSign,
            float swingProgress,
            float equipProgress
    ) {
        float f = -0.42F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float g = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
        float h = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        matrices.translate(armSign * f, g, h);

        // Base item position
        matrices.translate(armSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        // Turn sword inward edge-on during strike so less broad texture is visible
        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sinSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F + sin1 * -25.0F - sinSqrt * 20.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * sinSqrt * 45.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrt * -80.0F));
    }
}

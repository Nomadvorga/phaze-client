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

    // --- Block Hit & Sword Blocking --------------------------------
    public final SectionSetting blockHitSection = new SectionSetting("Block Hit");
    public final BooleanSetting swordBlocking = new BooleanSetting(
            "Sword Blocking",
            "Render sword in the classic 1.7 blocking pose when right-clicking or using a shield"
    ).setValue(true);
    public final BooleanSetting blockHit = new BooleanSetting(
            "Block Hit",
            "Enable 1.7 sword block-hit animation when swinging while blocking"
    ).setValue(true);
    public final SelectSetting blockHitStyle = new SelectSetting(
            "Block Hit Style",
            "Visual style of the sword block-hit animation"
    ).value("1.7 Classic", "Push", "Slide", "Lunar").selected("1.7 Classic");
    public final BooleanSetting lowerShield = new BooleanSetting(
            "Lower Shield",
            "Lowers the offhand shield so it does not obstruct your crosshair or view while blocking"
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

    // --- Actions (Eating, Bow, Rod) --------------------------------
    public final SectionSetting actionsSection = new SectionSetting("Actions");
    public final BooleanSetting oldEating = new BooleanSetting(
            "1.7 Eating",
            "Classic rhythmic bouncing animation when eating food or drinking potions"
    ).setValue(true);
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
        blockHit.setFullWidth(true);
        blockHitStyle.setFullWidth(true);
        blockHitStyle.visible(blockHit::isValue);
        lowerShield.setFullWidth(true);
        lowerShield.visible(swordBlocking::isValue);

        oldSwing.setFullWidth(true);
        noReEquip.setFullWidth(true);
        swingSpeed.setFullWidth(true);

        oldEating.setFullWidth(true);
        oldBow.setFullWidth(true);
        oldRod.setFullWidth(true);

        oldSneak.setFullWidth(true);

        setup(
                blockHitSection, swordBlocking, blockHit, blockHitStyle, lowerShield,
                swingSection, oldSwing, noReEquip, swingSpeed,
                actionsSection, oldEating, oldBow, oldRod,
                sneakSection, oldSneak
        );
    }

    public static OldAnimations getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Classic 1.7 / 1.8 combat animations, sword blocking, block-hit, smooth re-equip and eating";
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

        // 1.8 transformFirstPersonItem base Y rotation (crucial for 45° diagonal angle)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 45.0F));

        // 1.8 doBlockTransformations
        matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
    }

    /**
     * Renders sword block-hit animation with selected style.
     */
    public static void renderBlockHit(
            MatrixStack matrices,
            Arm arm,
            int armSign,
            float swingProgress,
            float equipProgress,
            String style
    ) {
        // Base first-person item positioning
        matrices.translate(armSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sinSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        if ("Push".equalsIgnoreCase(style)) {
            // 1.8 base block pose + forward punch translation
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 45.0F));
            matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
            matrices.translate(0.0F, sinSqrt * 0.05F, sinSqrt * 0.22F);
        } else if ("Slide".equalsIgnoreCase(style)) {
            // 1.8 base block pose + vertical slash translation
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 45.0F));
            matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
            matrices.translate(0.0F, -sinSqrt * 0.22F, 0.0F);
        } else if ("Lunar".equalsIgnoreCase(style)) {
            // Snappy Lunar/1.8 block-hit flick with authentic inward curve
            matrices.translate(armSign * -0.16F * sinSqrt, 0.16F * sinSqrt, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F + sin1 * -20.0F - sinSqrt * 12.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * sinSqrt * -22.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrt * -82.0F));
            matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
        } else {
            // 1.7 Classic: Authentic 1.7.10 swing + block transformation with inward tilt
            matrices.translate(armSign * -0.16F * sinSqrt, 0.16F * sinSqrt, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F + sin1 * -20.0F - sinSqrt * 12.0F)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * sinSqrt * -22.0F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrt * -82.0F));
            matrices.translate(armSign * -0.14142136F, 0.08F, 0.14142136F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * 13.365F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * 78.05F));
        }
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
        float f = -0.48F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float g = 0.22F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) (Math.PI * 2));
        float h = -0.22F * MathHelper.sin(swingProgress * (float) Math.PI);
        matrices.translate(armSign * f, g, h);

        // Base item position
        matrices.translate(armSign * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);

        // 1.7 swing rotations with authentic inward tilt (Photo 2)
        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sinSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * (45.0F + sin1 * -20.0F - sinSqrt * 12.0F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * sinSqrt * -22.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrt * -82.0F));
    }

    /**
     * Renders 1.7 classic eating bounce and tilt.
     */
    public static void renderOldEating(
            MatrixStack matrices,
            float tickDelta,
            Arm arm,
            ItemStack stack,
            PlayerEntity player
    ) {
        float useTime = (float) player.getItemUseTimeLeft() - tickDelta + 1.0F;
        float maxUseTime = (float) stack.getMaxUseTime(player);
        if (maxUseTime <= 0.0F) maxUseTime = 32.0F;
        float progress = 1.0F - (useTime / maxUseTime);
        float inv = 1.0F - progress;
        inv = inv * inv * inv;
        inv = inv * inv * inv;
        inv = inv * inv * inv;
        float factor = 1.0F - inv;

        int armSign = (arm == Arm.RIGHT) ? 1 : -1;

        // 1.7 vertical rhythmic bounce
        if (progress > 0.2F) {
            float bounce = MathHelper.abs(MathHelper.cos(useTime / 4.0F * (float) Math.PI) * 0.1F);
            matrices.translate(0.0F, bounce, 0.0F);
        }

        // 1.7 classic eating angle
        matrices.translate(armSign * factor * 0.6F, -factor * 0.5F, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(armSign * factor * 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(factor * 10.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(armSign * factor * 30.0F));
    }
}

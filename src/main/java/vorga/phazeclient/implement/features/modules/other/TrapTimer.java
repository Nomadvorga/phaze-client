package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.implement.features.modules.hud.RectHudModule;

import java.util.Locale;

public final class TrapTimer extends RectHudModule {
    private static final TrapTimer INSTANCE = new TrapTimer();
    public enum TrapType {
        NORMAL(15_000L),
        DRAGON(20_000L);

        public final long durationMs;

        TrapType(long durationMs) {
            this.durationMs = durationMs;
        }
    }

    public final SectionSetting trapSection = new SectionSetting("Trap");

    public final SelectSetting trapType = new SelectSetting(
            "Trap Type",
            "Which trapka variant the timer measures (regular = 15s, dragon = 20s)"
    ).value("Normal", "Dragon").selected("Normal");

    public final TextSetting trapName = new TextSetting(
            "Trap Name",
            "Substring matched against the held item's display name when deciding to start the timer"
    ).setText("трапка").setMax(48);

    private long timerStartMs = 0L;
    private boolean timerActive = false;
    private Text activeItemText = Text.empty();

    private TrapTimer() {
        super("trap_timer", "Trap Timer", ModuleCategory.UTILITIES, 0.0F, 0.0F, 1.0F);
        background.setValue(false);
        trapSection.setFullWidth(true);
        trapType.setFullWidth(true);
        trapName.setFullWidth(true);
        setup(trapSection, trapType, trapName, otherSection, cornerRounding);
    }

    public static TrapTimer getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return Lang.translate("Shows a draggable text timer for the FunTime trapka / dragon trapka cooldown");
    }

    @Override
    public String getIcon() {
        return "trap_timer.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public TrapType getSelectedType() {
        return "Dragon".equalsIgnoreCase(trapType.getSelected()) ? TrapType.DRAGON : TrapType.NORMAL;
    }

    public void onItemUse(PlayerEntity player, Hand hand) {
        if (!isEnabled() || player == null || hand == null) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.NETHERITE_SCRAP) {
            return;
        }
        String needle = trapName.getText();
        if (needle == null || needle.isEmpty()) {
            return;
        }
        String itemName = stack.getName().getString().toLowerCase();
        if (!itemName.contains(needle.toLowerCase())) {
            return;
        }
        startTimer(stack.getName().copy());
    }

    public void tick() {
        if (!timerActive || !isEnabled()) {
            return;
        }
        if (getRemainingSecondsPrecise() > 0.0F) {
            return;
        }
        timerActive = false;
        activeItemText = Text.empty();
    }

    public float getRemainingSecondsPrecise() {
        if (!timerActive) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - timerStartMs;
        long remainingMs = getSelectedType().durationMs - elapsed;
        return Math.max(0.0F, remainingMs / 1000.0F);
    }

    public float getProgress() {
        if (!timerActive) {
            return 0.0F;
        }
        long elapsed = System.currentTimeMillis() - timerStartMs;
        long duration = getSelectedType().durationMs;
        return Math.min(1.0F, (float) elapsed / (float) duration);
    }

    public boolean isTimerActive() {
        return timerActive;
    }

    public Text getDisplayText(boolean preview) {
        if (!timerActive) {
            if (!preview) {
                return null;
            }
            String previewSeconds = String.format(Locale.ROOT, "%.1f", getSelectedType().durationMs / 1000.0F);
            return buildDisplayText(Text.literal(Lang.t("trap_timer.preview_item")), previewSeconds);
        }
        String seconds = String.format(Locale.ROOT, "%.1f", getRemainingSecondsPrecise());
        Text itemLabel = activeItemText == null || activeItemText.getString().isBlank()
                ? Text.literal(Lang.t("trap_timer.preview_item"))
                : activeItemText.copy();
        return buildDisplayText(itemLabel, seconds);
    }

    public void ensureDefaultHudPosition(float screenWidth, float screenHeight, float baseWidth, float baseHeight) {
        if (getHudX() > 1.0F || getHudY() > 1.0F) {
            return;
        }
        float scale = getRenderHudScale();
        float hudWidth = baseWidth * scale;
        float hudHeight = baseHeight * scale;
        float crosshairCenterY = screenHeight * 0.5F;
        float hotbarTopY = screenHeight - 22.0F;
        float targetCenterY = (crosshairCenterY + hotbarTopY) * 0.5F;
        setHudX(Math.max(0.0F, (screenWidth - hudWidth) * 0.5F));
        setHudY(Math.max(0.0F, targetCenterY - hudHeight * 0.5F));
    }

    private MutableText buildDisplayText(Text itemName, String seconds) {
        MutableText line = Text.literal(Lang.t("trap_timer.prefix"))
                .append(itemName)
                .append(Text.literal(Lang.t("trap_timer.middle")))
                .append(Text.literal(seconds))
                .append(Text.literal(Lang.t("trap_timer.suffix")));
        if (!background.isValue() && showBrackets.isValue()) {
            return Text.literal("[")
                    .append(line)
                    .append(Text.literal("]"));
        }
        return line;
    }

    private void startTimer(Text itemName) {
        timerStartMs = System.currentTimeMillis();
        timerActive = true;
        activeItemText = itemName == null ? Text.empty() : itemName.copy();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        timerActive = false;
        activeItemText = Text.empty();
    }
}

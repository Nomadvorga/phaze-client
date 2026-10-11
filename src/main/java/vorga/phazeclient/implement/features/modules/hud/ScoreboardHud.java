package vorga.phazeclient.implement.features.modules.hud;

import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

public final class ScoreboardHud extends RectHudModule {
    private static final ScoreboardHud INSTANCE = new ScoreboardHud();

    @FunctionalInterface
    public interface BooleanLike {
        boolean isValue();
    }

    public static ScoreboardHud getInstance() {
        return INSTANCE;
    }

    private boolean positionInitialized = false;

    public final BooleanLike showNumbers = () -> true;
    public final BooleanLike showZeros = () -> true;
    public final BooleanSetting showTitle = new BooleanSetting("Show Title", "Show scoreboard title").setValue(true);

    private ScoreboardHud() {
        // Default position: right side, vertically centered
        super("scoreboard_hud", "Scoreboard", 0.0f, 0.0f, 1.0f);
        showTitle.setFullWidth(true);
        setup(showTitle);
    }

    public boolean isPositionInitialized() {
        return positionInitialized;
    }

    public void setPositionInitialized(boolean positionInitialized) {
        this.positionInitialized = positionInitialized;
    }

    @Override
    public void setHudPosition(float x, float y, int refWidth, int refHeight) {
        super.setHudPosition(x, y, refWidth, refHeight);
        this.positionInitialized = true;
    }

    @Override
    public void setHudX(float hudX) {
        super.setHudX(hudX);
        this.positionInitialized = true;
    }

    @Override
    public void setHudY(float hudY) {
        super.setHudY(hudY);
        this.positionInitialized = true;
    }

    @Override
    public void setHudXRatio(float ratio) {
        super.setHudXRatio(ratio);
        this.positionInitialized = true;
    }

    @Override
    public void setHudYRatio(float ratio) {
        super.setHudYRatio(ratio);
        this.positionInitialized = true;
    }

    @Override
    public void resetHudTransform() {
        super.resetHudTransform();
        this.positionInitialized = false;
    }

    @Override
    public String getDescription() {
        return "Modify vanilla scoreboard with blur, custom colors, position, and scale";
    }

    @Override
    public String getIcon() {
        return "scoreboard_hud.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public float getMinHudScale() {
        return 0.5f;
    }

    @Override
    public float getMaxHudScale() {
        return 6.0f;
    }

    public boolean shouldUseVanillaColors() {
        return background.isValue() && isVanillaPreset();
    }
}

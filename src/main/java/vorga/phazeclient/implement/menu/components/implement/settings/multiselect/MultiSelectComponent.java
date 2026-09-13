package vorga.phazeclient.implement.menu.components.implement.settings.multiselect;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.setting.implement.MultiSelectSetting;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class MultiSelectComponent extends AbstractSettingComponent {

    private static final float CHIP_TEXT_SIZE = 5.7F;

    private static final float CHIP_PADDING_X = 6.0F;

    private static final float CHIP_HEIGHT = 13.0F;

    private static final float CHIP_GAP_X = 4.0F;

    private static final float CHIP_GAP_Y = 4.0F;

    private static final float CHIPS_LEFT_PAD = 10.0F;

    private static final float CHIPS_RIGHT_PAD = 18.0F;

    private static final float LABEL_TO_CHIPS_GAP = 4.0F;

    private static final float LABEL_TOP_PAD = 7.0F;

    private static final float LABEL_TEXT_SIZE = 5.7F;

    private final MultiSelectSetting setting;

    private final Map<String, ChipAnimations> chipAnimations = new IdentityHashMap<>();

    private float expandedHeight;

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.setting = setting;
    }

    public static void handleGlobalClick(double mouseX, double mouseY) {

    }

    public static void closeAllDropdowns() {

    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        Matrix3x2fStack matrices = context.getMatrices();
        Matrix4f positionMatrix = GuiMatrix.mat4(matrices);

        float labelMaxWidth = Math.max(40.0F, width - CHIPS_RIGHT_PAD - 18.0F);
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) labelMaxWidth, 14);

        ChipLayout layout = computeChipLayout();
        float labelBandHeight = LABEL_TOP_PAD + LABEL_TEXT_SIZE + LABEL_TO_CHIPS_GAP;
        float chipBlockHeight = layout.totalRows() * CHIP_HEIGHT + Math.max(0, layout.totalRows() - 1) * CHIP_GAP_Y;
        float computed = labelBandHeight + chipBlockHeight + 6.0F;
        height = (int) Math.ceil(computed);
        expandedHeight = height;

        renderSettingCard(context, 0.0F, 0.0F);

        float labelX = x + 10.0F;
        float labelY = y + LABEL_TOP_PAD;
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                wrapped,
                LABEL_TEXT_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, currentAlpha),
                positionMatrix,
                labelX,
                labelY,
                0.0F
        );

        renderChips(context, matrices, positionMatrix, layout, mouseX, mouseY);

    }

    private void renderChips(DrawContext context, Matrix3x2fc matrices, Matrix4f positionMatrix, ChipLayout layout, int mouseX, int mouseY) {
        for (int i = 0; i < layout.entries.size(); i++) {
            ChipEntry entry = layout.entries.get(i);
            renderChip(matrices, positionMatrix, entry, mouseX, mouseY);
        }
    }

    private void renderChip(Matrix3x2fc matrices, Matrix4f positionMatrix, ChipEntry entry, int mouseX, int mouseY) {
        boolean selected = setting.getSelected().contains(entry.name);
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, entry.x, entry.y, entry.width, CHIP_HEIGHT);
        if (hovered) {
            vorga.phazeclient.api.system.cursor.CursorManager.requestHand();
        }

        ChipAnimations anim = chipAnimations.computeIfAbsent(entry.name, n -> new ChipAnimations());

        if (!anim.seeded) {
            anim.selected.setDirectionAndFinish(selected ? Direction.FORWARDS : Direction.BACKWARDS);
            anim.seeded = true;
        } else {
            anim.selected.setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);
        }
        float selP = anim.selected.getOutputFloat();

        int idle = MenuStyle.PANEL_CHIP;
        int activeFill = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.PANEL_CHIP, 0.35F);
        int fill = MenuStyle.mix(idle, activeFill, selP);
        int fillFinal = MenuStyle.withAlpha(fill, currentAlpha * (0.55F + 0.45F * selP));

        int outlineIdle = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.BORDER_LIGHT, 0.40F);
        int outlineActive = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.TEXT_PRIMARY, 0.45F);
        int outline = MenuStyle.mix(outlineIdle, outlineActive, selP);
        int outlineFinal = MenuStyle.withAlpha(outline, currentAlpha);

        rectangle.render(ShapeProperties.create(matrices, entry.x, entry.y, entry.width, CHIP_HEIGHT)
                .round(2.5F)
                .thickness(1.5F)
                .softness(0.6F)
                .color(fillFinal)
                .outlineColor(outlineFinal)
                .build());

        int textColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, selP);
        int textFinal = MenuStyle.withAlpha(textColor, currentAlpha);

        MsdfFont font = MsdfFonts.bold();

        String chipLabel = Lang.translate(entry.name);
        float textW = font.getWidth(chipLabel, CHIP_TEXT_SIZE);
        float textX = entry.x + (entry.width - textW) / 2.0F;
        float textY = MenuStyle.centerMsdfTextY(CHIP_TEXT_SIZE, entry.y, CHIP_HEIGHT);
        MsdfRenderer.renderText(font, chipLabel, CHIP_TEXT_SIZE, textFinal, positionMatrix, textX, textY, 0.0F);
    }

    private ChipLayout computeChipLayout() {
        List<ChipEntry> out = new ArrayList<>();
        List<String> options = setting.getList();
        if (options == null || options.isEmpty()) {
            return new ChipLayout(out, 1);
        }

        MsdfFont font = MsdfFonts.bold();
        float availableWidth = Math.max(20.0F, width - CHIPS_LEFT_PAD - CHIPS_RIGHT_PAD);
        float baseX = x + CHIPS_LEFT_PAD;
        float baseY = y + LABEL_TOP_PAD + LABEL_TEXT_SIZE + LABEL_TO_CHIPS_GAP;

        float cursorX = 0.0F;
        float cursorY = 0.0F;
        int rows = 1;

        for (String option : options) {

            float labelW = font.getWidth(Lang.translate(option), CHIP_TEXT_SIZE);
            float chipW = Math.max(20.0F, labelW + CHIP_PADDING_X * 2.0F);

            if (cursorX > 0 && cursorX + chipW > availableWidth) {
                cursorX = 0.0F;
                cursorY += CHIP_HEIGHT + CHIP_GAP_Y;
                rows++;
            }

            out.add(new ChipEntry(option, baseX + cursorX, baseY + cursorY, chipW));
            cursorX += chipW + CHIP_GAP_X;
        }

        return new ChipLayout(out, rows);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        ChipLayout layout = computeChipLayout();
        for (ChipEntry entry : layout.entries) {
            if (MathUtil.isHovered(mouseX, mouseY, entry.x, entry.y, entry.width, CHIP_HEIGHT)) {
                playButtonClickSound();
                List<String> selected = new ArrayList<>(setting.getSelected());
                if (selected.contains(entry.name)) {
                    selected.remove(entry.name);
                } else {
                    selected.add(entry.name);
                    selected.sort(Comparator.comparingInt(setting.getList()::indexOf));
                }
                setting.setSelected(selected);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    @Override
    public float getExpandedHeight() {
        return expandedHeight > 0.0F ? expandedHeight : height;
    }

    private static final class ChipAnimations {
        final Animation selected = new DecelerateAnimation().setMs(220).setValue(1);

        boolean seeded = false;

        ChipAnimations() {
            selected.setDirection(Direction.BACKWARDS);
        }
    }

    private record ChipEntry(String name, float x, float y, float width) {}

    private record ChipLayout(List<ChipEntry> entries, int totalRows) {}
}

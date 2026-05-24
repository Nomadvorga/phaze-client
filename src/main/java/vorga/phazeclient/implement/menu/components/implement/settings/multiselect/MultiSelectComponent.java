package vorga.phazeclient.implement.menu.components.implement.settings.multiselect;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
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

/**
 * Inline-chip multi-select.
 *
 * <h3>Visual layout</h3>
 * The label sits at the top-left of the setting card (same place
 * every other setting puts it). Directly under the label, each
 * option is rendered as its own chip - a small rounded rect with
 * the option's name centred inside. Chips flow left-to-right and
 * wrap onto a new row when they would overflow the available
 * width. There is no dropdown panel and no chevron caret: every
 * option is always visible at a glance, the way the user's
 * reference screenshot shows.
 *
 * <h3>Animation per chip</h3>
 * Each chip owns a pair of {@link DecelerateAnimation} instances:
 * one tracking hover, one tracking selection. Both run on the
 * standard "ease-out" curve the rest of the menu uses, so a chip
 * fades smoothly between idle / hover / active states instead of
 * snapping. The animation pair is cached in
 * {@link #chipAnimations} keyed by option name so we don't
 * reallocate per frame and the in-flight animation state survives
 * across frames.
 *
 * <h3>Why static no-ops for the legacy global-click hooks</h3>
 * {@code MenuScreen} calls
 * {@link #handleGlobalClick(double, double)} and
 * {@link #closeAllDropdowns()} from its outside-click and close
 * paths because the previous version had a floating dropdown that
 * needed teardown on outside clicks. Inline chips don't need
 * either, but the entry points stay so {@code MenuScreen} doesn't
 * need to be updated alongside this rewrite. They're cheap no-ops
 * and the call sites are warning-free.
 *
 * <h3>Why MSDF</h3>
 * Same reason every other refreshed component uses MSDF: SDF
 * glyphs stay sharp at any GUI scale, and we already standardised
 * on {@link MsdfFonts#bold()} for inline chrome elsewhere in the
 * menu. The chip text feels visually continuous with the rest of
 * the panel.
 */
public class MultiSelectComponent extends AbstractSettingComponent {
    /** Pixel size of the option label inside each chip. */
    private static final float CHIP_TEXT_SIZE = 5.7F;
    /** Padding inside a chip on each side of the label. */
    private static final float CHIP_PADDING_X = 6.0F;
    /** Vertical extent of the chip rect. */
    private static final float CHIP_HEIGHT = 13.0F;
    /** Horizontal gap between adjacent chips on the same row. */
    private static final float CHIP_GAP_X = 4.0F;
    /** Vertical gap between two chip rows when wrapping. */
    private static final float CHIP_GAP_Y = 4.0F;
    /** Distance from the card's left edge to the chip row. */
    private static final float CHIPS_LEFT_PAD = 10.0F;
    /** Right margin for the chip flow - keeps chips clear of reset icon. */
    private static final float CHIPS_RIGHT_PAD = 18.0F;
    /** Vertical gap between the label and the first chip row. */
    private static final float LABEL_TO_CHIPS_GAP = 4.0F;
    /** Y offset from the top of the card down to the label baseline anchor. */
    private static final float LABEL_TOP_PAD = 7.0F;
    /** Pixel size of the setting label MSDF text. */
    private static final float LABEL_TEXT_SIZE = 5.7F;

    private final MultiSelectSetting setting;

    /**
     * Per-option animation state. We key on the option name (the
     * very thing the setting itself tracks selection by) so reset /
     * value-import / programmatic toggles all reuse the same
     * animation continuity. Identity-hash map because the setting's
     * list of names is stable across frames - the lookup is
     * effectively a pointer comparison after the first record.
     */
    private final Map<String, ChipAnimations> chipAnimations = new IdentityHashMap<>();

    /** Current expanded height in pixels - recomputed every frame. */
    private float expandedHeight;

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.setting = setting;
    }

    /**
     * Legacy entry point. The previous floating-panel version of
     * this component routed outside-click teardown through here;
     * inline chips have no panel to close so this is a no-op kept
     * to preserve the {@code MenuScreen} call site.
     */
    public static void handleGlobalClick(double mouseX, double mouseY) {
        // No-op - inline chips don't need outside-click handling.
    }

    /**
     * Legacy entry point. Same rationale as
     * {@link #handleGlobalClick(double, double)}: kept as a no-op
     * so {@code MenuScreen.close} doesn't need to change.
     */
    public static void closeAllDropdowns() {
        // No-op - inline chips have no dropdown state.
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateVisibilityAnimation();

        MatrixStack matrices = context.getMatrices();
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        // Height is "label band + chip rows" - we lay out chips
        // first to know how tall the card has to be, then fall
        // through to the actual draw. This way the card outline
        // matches the chip flow exactly without a second-frame
        // catch-up.
        float labelMaxWidth = Math.max(40.0F, width - CHIPS_RIGHT_PAD - 18.0F);
        String wrapped = StringUtil.wrap(setting.getLocalizedName(), (int) labelMaxWidth, 14);

        ChipLayout layout = computeChipLayout();
        float labelBandHeight = LABEL_TOP_PAD + LABEL_TEXT_SIZE + LABEL_TO_CHIPS_GAP;
        float chipBlockHeight = layout.totalRows() * CHIP_HEIGHT + Math.max(0, layout.totalRows() - 1) * CHIP_GAP_Y;
        float computed = labelBandHeight + chipBlockHeight + 6.0F;
        height = (int) Math.ceil(computed);
        expandedHeight = height;

        // No card-level hover lift - selection happens per chip and
        // the user explicitly asked for the surrounding card to stay
        // visually static while the cursor moves over it.
        renderSettingCard(context, 0.0F, 0.0F);

        // Label.
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

        // Chip flow.
        renderChips(context, matrices, positionMatrix, layout, mouseX, mouseY);

        // Reset icon intentionally omitted: the user asked for the
        // chip multi-select to be non-resettable in the Consumable
        // module's Items setting. Without a reset surface there's
        // no UI to draw here; the per-chip toggles remain the only
        // way to mutate the selection.
    }

    private void renderChips(DrawContext context, MatrixStack matrices, Matrix4f positionMatrix, ChipLayout layout, int mouseX, int mouseY) {
        for (int i = 0; i < layout.entries.size(); i++) {
            ChipEntry entry = layout.entries.get(i);
            renderChip(matrices, positionMatrix, entry, mouseX, mouseY);
        }
    }

    private void renderChip(MatrixStack matrices, Matrix4f positionMatrix, ChipEntry entry, int mouseX, int mouseY) {
        boolean selected = setting.getSelected().contains(entry.name);

        ChipAnimations anim = chipAnimations.computeIfAbsent(entry.name, n -> new ChipAnimations());
        // First-render seeding: a chip should land already in its
        // resting state instead of fading into it. The user
        // explicitly asked to drop the colour fade when entering a
        // module's settings (the previous flow ramped the
        // selected-state animation from 0 to its target over 220 ms,
        // which read as a flash on every open). Snap to the end
        // value on first sight, then let the animation drive
        // normally for subsequent user toggles.
        if (!anim.seeded) {
            anim.selected.setDirectionAndFinish(selected ? Direction.FORWARDS : Direction.BACKWARDS);
            anim.seeded = true;
        } else {
            anim.selected.setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);
        }
        float selP = anim.selected.getOutputFloat();

        // Idle base = panel-chip surface (very subtle), selected
        // blends into the accent. Hover is intentionally absent -
        // the user asked for chips to not visually react to the
        // cursor before a click commits the selection.
        int idle = MenuStyle.PANEL_CHIP;
        int activeFill = MenuStyle.mix(MenuStyle.CHIP_ACTIVE, MenuStyle.PANEL_CHIP, 0.35F);
        int fill = MenuStyle.mix(idle, activeFill, selP);
        int fillFinal = MenuStyle.withAlpha(fill, currentAlpha * (0.55F + 0.45F * selP));

        // Outline always rendered at full chip alpha (gated only by
        // the menu's open/close globalAlpha) so even the unselected
        // chips show a visible border instead of being a fill-only
        // pill. Active outline tone is offset toward TEXT_PRIMARY so
        // it visibly separates from the fill - without that lift,
        // CHIP_ACTIVE outline on a CHIP_ACTIVE-tinted fill blends
        // into a single flat shape and the user can't tell at a
        // glance which chip is selected. The mix factor is small
        // enough to keep the active accent dominant while still
        // providing the rim contrast the user asked for.
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
        // Display localized label (Lang map) but keep selection
        // storage on canonical English keys: setting.getSelected()
        // and the chip animation cache are still keyed on
        // {@code entry.name}. This way RU users see "Частицы атаки"
        // while the underlying setting still writes "Hit Particles"
        // and any onChange callback / config file stays language-
        // agnostic.
        String chipLabel = Lang.translate(entry.name);
        float textW = font.getWidth(chipLabel, CHIP_TEXT_SIZE);
        float textX = entry.x + (entry.width - textW) / 2.0F;
        float textY = MenuStyle.centerMsdfTextY(CHIP_TEXT_SIZE, entry.y, CHIP_HEIGHT);
        MsdfRenderer.renderText(font, chipLabel, CHIP_TEXT_SIZE, textFinal, positionMatrix, textX, textY, 0.0F);
    }

    /**
     * Plan chip placement. Walks the option list in declaration
     * order, measures the MSDF width of each label, and packs
     * chips left-to-right with wrap-on-overflow. Returns the
     * absolute (x, y, width) of every chip plus the total number
     * of rows so {@link #render} can size the card.
     */
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
            // Width is measured against the localized label so a
            // long Russian translation gets a proportionally wider
            // chip - otherwise "Splash Potion Particles" -> "Частицы
            // взрывных зелий" would overflow the chip's box.
            float labelW = font.getWidth(Lang.translate(option), CHIP_TEXT_SIZE);
            float chipW = Math.max(20.0F, labelW + CHIP_PADDING_X * 2.0F);

            // Wrap if the chip won't fit on the current row. The
            // {@code cursorX > 0} guard keeps the very first chip on
            // the row even if it's slightly wider than the available
            // width - we'd rather clip the trailing edge than drop
            // an empty row.
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
        // Reset surface intentionally absent - matches the render
        // path which omits the reset icon.

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

    /**
     * Per-option animation track. Only "selected" is animated -
     * hover is intentionally absent because the user asked chips
     * to not visually react to the cursor before a click commits
     * the selection. Caching here is per-name so toggling a chip
     * doesn't reset the in-flight selection animation.
     */
    private static final class ChipAnimations {
        final Animation selected = new DecelerateAnimation().setMs(220).setValue(1);
        /** First-render seed flag. {@code render} snaps the
         *  selection animation to its end value on the very first
         *  frame so chips don't fade in when the settings panel
         *  opens; subsequent toggles animate normally. */
        boolean seeded = false;

        ChipAnimations() {
            selected.setDirection(Direction.BACKWARDS);
        }
    }

    /** Geometry of one rendered chip, anchored to absolute screen pixels. */
    private record ChipEntry(String name, float x, float y, float width) {}

    /** Result of the per-frame layout pass. */
    private record ChipLayout(List<ChipEntry> entries, int totalRows) {}
}

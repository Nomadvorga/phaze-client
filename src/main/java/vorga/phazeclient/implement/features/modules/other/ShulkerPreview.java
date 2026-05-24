package vorga.phazeclient.implement.features.modules.other;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Tooltip-style preview of a shulker-box's contents while hovering
 * the stack in any inventory screen. Rendered with the vanilla
 * chest-GUI texture so the preview reads as a familiar mini chest
 * UI instead of a stray rectangle.
 *
 * <h3>Tinting</h3>
 * When {@link #colorByShulker} is on, the chest-GUI texture is
 * tinted with the shulker box's dye colour ({@link DyeColor#getEntityColor})
 * so a hovered red shulker shows a red chest, a green one a green
 * chest, etc. Tint is applied at the texture-bind level via
 * {@link DrawContext#setShaderColor}; the icon-pass that follows
 * resets the tint back to white so item sprites render at their
 * native colours.
 */
public final class ShulkerPreview extends Module {
    private static final ShulkerPreview INSTANCE = new ShulkerPreview();

    /** Vanilla chest GUI texture - the same one
     *  {@code GenericContainerScreen} uses for its background. */
    private static final Identifier CHEST_TEXTURE =
            Identifier.ofVanilla("textures/gui/container/generic_54.png");

    /** Texture atlas size; vanilla constant. */
    private static final int ATLAS_SIZE = 256;

    /** Width of the chest-GUI window. Lifted from
     *  {@code HandledScreen.backgroundWidth} which defaults to 176
     *  for {@code GenericContainerScreen}. */
    private static final int PREVIEW_W = 176;

    /** Top closing frame: pull the first 7px decorative strip
     *  from the chest texture (atlas y=0..7). Used as a thin cap
     *  matching the bottom border, so the preview doesn't carry
     *  the vanilla 10px title-bar gap that would otherwise sit
     *  between the top edge and row 1 of slots. */
    private static final int TOP_BORDER_HEIGHT = 7;

    /** Source y in the atlas where the 3-row slot strip starts.
     *  Vanilla's chest panel reserves y=7..17 for the container
     *  title; we skip it and pick up rendering exactly at the
     *  first slot row. */
    private static final int SLOTS_SRC_Y = 18;

    /** 3 slot rows worth of pixels. */
    private static final int SLOTS_HEIGHT = 3 * 18;

    /** Height of the closing bottom border carved out of the
     *  texture's lower block. The full bottom block in
     *  {@code generic_54.png} is 96px tall (y=126..222) and ends in
     *  a ~7px decorative frame; we only need that last strip to
     *  cap our preview without stamping the whole player-inventory
     *  section underneath. The strip's source y in the atlas is
     *  {@code 222 - 7 = 215}. */
    private static final int BOTTOM_BORDER_HEIGHT = 7;
    private static final int BOTTOM_BORDER_SRC_Y = 215;

    /** Total preview height: thin top border + 3 slot rows + thin bottom border. */
    private static final int PREVIEW_H = TOP_BORDER_HEIGHT + SLOTS_HEIGHT + BOTTOM_BORDER_HEIGHT;

    /** Inset inside the chest texture before the first slot starts.
     *  Horizontal inset matches vanilla (8px). Vertical inset is
     *  just the top border height now since we strip out the
     *  title-bar region. */
    private static final int SLOT_ORIGIN_X = 8;
    private static final int SLOT_ORIGIN_Y = TOP_BORDER_HEIGHT;

    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 3;
    private static final int CURSOR_OFFSET = 14;

    /**
     * Per-tick cache of the resolved shulker contents for the
     * stack the user is currently hovering. Refreshed when either
     * the hovered stack identity changes (different shulker, or
     * cursor moved off the preview-eligible slot) OR a new game
     * tick has started since the last refresh.
     *
     * <p>Why per tick: shulker contents only change when the user
     * shuffles items between inventory slots, which is gated by the
     * server-side "interaction" tick rate. Re-walking
     * {@code container.streamNonEmpty()} every render frame at
     * 240 FPS is wasted work - the visible result is identical
     * across all 12 frames within one tick. The cache also
     * pre-evaluates the overlay flag (count != 1 / damaged /
     * cooldown) so the per-frame loop becomes a flat array
     * lookup + drawItem call.
     */
    private static final ItemStack[] CACHED_STACKS = new ItemStack[GRID_COLS * GRID_ROWS];
    private static final boolean[] CACHED_OVERLAYS = new boolean[GRID_COLS * GRID_ROWS];
    private static int cachedSlotsToDraw = 0;
    private static ItemStack lastHoveredStack = null;
    private static ContainerComponent lastContainer = null;
    private static int lastTick = Integer.MIN_VALUE;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting alwaysShow = new BooleanSetting(
            "Always Show",
            "Always show shulker contents while hovering, without holding a key"
    ).setValue(false);
    public final BindSetting showBind = new BindSetting(
            "Show Bind",
            "Hold this key to show the preview while Always Show is off"
    );
    public final BooleanSetting colorByShulker = new BooleanSetting(
            "Color By Shulker",
            "Tint the chest preview background with the shulker box's dye colour"
    ).setValue(true);

    private ShulkerPreview() {
        super("shulker_preview", "Shulker Preview", ModuleCategory.UTILITIES);
        alwaysShow.setFullWidth(true);
        showBind.setFullWidth(true);
        showBind.visible(() -> !alwaysShow.isValue());
        colorByShulker.setFullWidth(true);
        setup(generalSection, alwaysShow, showBind, colorByShulker);
    }

    public static ShulkerPreview getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Shows shulker box contents in a tooltip while hovering it in any inventory";
    }

    @Override
    public String getIcon() {
        return "shulker_preview.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * True if the user wants the preview to render right now: either Always
     * Show is on, or the configured bind key is currently held.
     */
    public boolean shouldShow() {
        if (alwaysShow.isValue()) {
            return true;
        }
        int key = showBind.getKey();
        if (key == GLFW.GLFW_KEY_UNKNOWN) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
    }

    /**
     * @return the {@link ContainerComponent} stored on the stack if it's a
     *         shulker box (any color) carrying contents, otherwise null.
     */
    public ContainerComponent extractContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        Block block = blockItem.getBlock();
        if (!(block instanceof ShulkerBoxBlock)) {
            return null;
        }
        return stack.get(DataComponentTypes.CONTAINER);
    }

    /**
     * Resolve the {@link DyeColor} of a shulker stack, or {@code null}
     * when the box is the un-dyed (purple) variant. Used by the
     * tint pass to pick the right colour.
     */
    private static DyeColor resolveShulkerColor(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        if (blockItem.getBlock() instanceof ShulkerBoxBlock shulker) {
            return shulker.getColor();
        }
        return null;
    }

    public void renderPreview(DrawContext context, int mouseX, int mouseY, ContainerComponent container) {
        renderPreview(context, mouseX, mouseY, container, null);
    }

    /**
     * Render the chest-style preview at {@code mouseX, mouseY}.
     * {@code hoveredStack} is the stack the cursor is on; needed
     * here to resolve the shulker dye colour for the tint pass.
     * Falls back to the un-tinted ("purple") look when the stack
     * isn't a coloured shulker or {@code colorByShulker} is off.
     */
    public void renderPreview(DrawContext context, int mouseX, int mouseY,
                              ContainerComponent container, ItemStack hoveredStack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen == null) {
            return;
        }

        int screenW = mc.currentScreen.width;
        int screenH = mc.currentScreen.height;

        // Position to the right of cursor by default; flip to left if it would clip.
        int x = mouseX + CURSOR_OFFSET;
        if (x + PREVIEW_W > screenW) {
            x = mouseX - CURSOR_OFFSET - PREVIEW_W;
        }
        int y = mouseY - PREVIEW_H / 2;
        if (y < 4) y = 4;
        if (y + PREVIEW_H > screenH - 4) y = screenH - 4 - PREVIEW_H;

        // Flush whatever the screen already queued so its slot
        // items / cursor stack render BELOW our preview, then
        // render at a high Z so even vanilla tooltips (Z=400) can't
        // poke through.
        context.draw();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 500.0F);

        // Texture pass: vanilla chest-GUI sprite, optionally tinted
        // with the shulker dye colour. setShaderColor is global GL
        // state and must be reset before drawItem runs the icon
        // pass - otherwise every item sprite would inherit the
        // tint. We restore right after the drawTexture call by
        // flushing the texture batch and setting the colour back to
        // white.
        DyeColor dye = colorByShulker.isValue() ? resolveShulkerColor(hoveredStack) : null;
        if (dye != null) {
            int rgb = dye.getEntityColor();
            float r = ((rgb >> 16) & 0xFF) / 255.0F;
            float g = ((rgb >> 8) & 0xFF) / 255.0F;
            float b = (rgb & 0xFF) / 255.0F;
            // Lift tint toward white so the chest texture stays
            // readable under saturated dye colours like red / blue.
            // 0.65 is the same factor vanilla uses for shulker
            // entity tinting.
            float lift = 0.65F;
            r = r + (1.0F - r) * lift;
            g = g + (1.0F - g) * lift;
            b = b + (1.0F - b) * lift;
            RenderSystem.setShaderColor(r, g, b, 1.0F);
        }
        // Top border: 7px decorative strip from the very top of
        // the atlas (y=0..7). Replaces the full vanilla 17px
        // title-bar header so the preview's top edge mirrors the
        // bottom edge instead of leaving a wide empty band above
        // the first slot row.
        context.drawTexture(RenderLayer::getGuiTextured, CHEST_TEXTURE,
                x, y,
                0.0F, 0.0F,
                PREVIEW_W, TOP_BORDER_HEIGHT,
                ATLAS_SIZE, ATLAS_SIZE);
        // Slot strip: 3 rows of slots pulled from atlas y=18..72.
        // Skipping the y=7..18 title region keeps the panel
        // compact and avoids an empty band above the first row.
        context.drawTexture(RenderLayer::getGuiTextured, CHEST_TEXTURE,
                x, y + TOP_BORDER_HEIGHT,
                0.0F, SLOTS_SRC_Y,
                PREVIEW_W, SLOTS_HEIGHT,
                ATLAS_SIZE, ATLAS_SIZE);
        // Bottom closing frame: pull the last 7px strip from the
        // chest texture (atlas y=215..222) and stamp it directly
        // under the slot rows. Without this the panel would have
        // no bottom border and the lowest slot row would sit
        // visually open.
        context.drawTexture(RenderLayer::getGuiTextured, CHEST_TEXTURE,
                x, y + TOP_BORDER_HEIGHT + SLOTS_HEIGHT,
                0.0F, BOTTOM_BORDER_SRC_Y,
                PREVIEW_W, BOTTOM_BORDER_HEIGHT,
                ATLAS_SIZE, ATLAS_SIZE);
        // Flush the texture batch BEFORE resetting the shader
        // colour - drawTexture queues into the GUI buffer, and the
        // colour state is captured at flush time, so resetting too
        // early would draw the chest at full white.
        context.draw();
        if (dye != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // Refresh the per-tick cache if either the hovered stack
        // changed (cursor moved to a different slot) or at least
        // one game tick has elapsed. This collapses the
        // {@code container.streamNonEmpty()} walk + per-stack
        // overlay-flag computation to once per tick instead of
        // once per frame.
        int tick = mc.inGameHud != null ? mc.inGameHud.getTicks() : 0;
        if (hoveredStack != lastHoveredStack || container != lastContainer || tick != lastTick) {
            lastHoveredStack = hoveredStack;
            lastContainer = container;
            lastTick = tick;
            int idx = 0;
            int cap = GRID_COLS * GRID_ROWS;
            // streamNonEmpty allocates an iterator per call; we keep
            // it confined to the once-per-tick refresh path so the
            // per-frame render path stays alloc-free.
            java.util.Iterator<ItemStack> iter = container.streamNonEmpty().iterator();
            while (iter.hasNext() && idx < cap) {
                ItemStack stack = iter.next();
                CACHED_STACKS[idx] = stack;
                CACHED_OVERLAYS[idx] = computeOverlayFlag(stack, mc);
                idx++;
            }
            cachedSlotsToDraw = idx;
            // Null out tail so a previous, larger payload doesn't
            // leak references through the cache.
            for (int i = idx; i < cap; i++) {
                CACHED_STACKS[i] = null;
                CACHED_OVERLAYS[i] = false;
            }
        }

        int gridOriginX = x + SLOT_ORIGIN_X;
        int gridOriginY = y + SLOT_ORIGIN_Y;

        // Icon pass. Slot bg is already painted by the chest
        // texture, so this loop only handles items + the optional
        // count overlay. drawItem bakes the model and binds the
        // texture; the overlay flag was already evaluated during
        // the once-per-tick refresh above so this loop only does
        // a flat array read + the unavoidable drawItem call.
        for (int i = 0; i < cachedSlotsToDraw; i++) {
            ItemStack stack = CACHED_STACKS[i];
            if (stack == null || stack.isEmpty()) continue;
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int slotX = gridOriginX + col * SLOT_SIZE;
            int slotY = gridOriginY + row * SLOT_SIZE;
            context.drawItem(stack, slotX, slotY);
            if (CACHED_OVERLAYS[i]) {
                context.drawStackOverlay(mc.textRenderer, stack, slotX, slotY);
            }
        }

        // Flush our preview before popping so the high-Z translation actually
        // sticks for the queued draws (DrawContext snapshots the matrix at
        // submit time, so popping before draw() would still be correct, but
        // explicit flushing here keeps our preview from re-batching with
        // anything rendered later in the same frame).
        context.draw();
        context.getMatrices().pop();
    }

    /**
     * Cheap predicate guarding {@code drawStackOverlay} - the
     * vanilla helper formats the count int into a string and
     * pushes both a text-renderer batch and two damage / cooldown
     * fills even when none of them produce visible pixels. For
     * plain stacks (count 1, undamaged, no active cooldown) the
     * skip is invisible and saves a noticeable chunk of CPU on
     * dense previews where most slots are single-item. Result is
     * cached per tick so the per-frame render path doesn't pay
     * for the cooldown lookup.
     */
    private static boolean computeOverlayFlag(ItemStack stack, MinecraftClient mc) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getCount() != 1) return true;
        if (stack.isItemBarVisible()) return true;
        if (mc != null && mc.player != null
                && mc.player.getItemCooldownManager().getCooldownProgress(stack, 0.0F) > 0.0F) {
            return true;
        }
        return false;
    }
}

package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.gl.RenderPipelines;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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

public final class ShulkerPreview extends Module {
    private static final ShulkerPreview INSTANCE = new ShulkerPreview();

    private static final Identifier PANEL_SPRITE =
            Identifier.of("phaze", "shulker_box_tooltip");

    private static final int UNCOLORED_SHULKER_RGB = 9922455;

    private static final Identifier SLOT_HIGHLIGHT_BACK =
            Identifier.ofVanilla("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT =
            Identifier.ofVanilla("container/slot_highlight_front");

    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 3;
    private static final int SLOT_OFFSET_X = 8;
    private static final int SLOT_OFFSET_Y = 8;

    private static final int PREVIEW_W = 14 + GRID_COLS * SLOT_SIZE;
    private static final int PREVIEW_H = 14 + GRID_ROWS * SLOT_SIZE;

    private static final int HIGHLIGHT_SIZE = 24;
    private static final int HIGHLIGHT_OFFSET = 4;

    private static final int CURSOR_OFFSET = 14;

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

        return InputUtil.isKeyPressed(mc.getWindow(), key);
    }

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

    private static DyeColor resolveShulkerColor(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        if (blockItem.getBlock() instanceof ShulkerBoxBlock shulker) {
            return shulker.getColor();
        }
        return null;
    }

    private static boolean isShulkerBox(ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static int clampDyeChannel(int channel) {
        return channel < 38 ? 38 : channel;
    }

    public void renderPreview(DrawContext context, int mouseX, int mouseY, ContainerComponent container) {
        renderPreview(context, mouseX, mouseY, container, null);
    }

    public void renderPreview(DrawContext context, int mouseX, int mouseY,
                              ContainerComponent container, ItemStack hoveredStack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen == null) {
            return;
        }

        int screenW = mc.currentScreen.width;
        int screenH = mc.currentScreen.height;

        int x = mouseX + CURSOR_OFFSET;
        if (x + PREVIEW_W > screenW) {
            x = mouseX - CURSOR_OFFSET - PREVIEW_W;
        }
        int y = mouseY - PREVIEW_H / 2;
        if (y < 4) y = 4;
        if (y + PREVIEW_H > screenH - 4) y = screenH - 4 - PREVIEW_H;

        context.createNewRootLayer();

        int tintArgb = -1;
        if (colorByShulker.isValue()) {
            DyeColor dye = resolveShulkerColor(hoveredStack);
            if (dye != null) {
                int rgb = dye.getEntityColor();
                int rr = clampDyeChannel((rgb >> 16) & 0xFF);
                int gg = clampDyeChannel((rgb >> 8) & 0xFF);
                int bb = clampDyeChannel(rgb & 0xFF);
                tintArgb = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            } else if (isShulkerBox(hoveredStack)) {
                tintArgb = 0xFF000000 | UNCOLORED_SHULKER_RGB;
            }
        }

        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE,
                x, y, PREVIEW_W, PREVIEW_H, tintArgb);

        int tick = mc.inGameHud != null ? mc.inGameHud.getTicks() : 0;
        if (hoveredStack != lastHoveredStack || container != lastContainer || tick != lastTick) {
            lastHoveredStack = hoveredStack;
            lastContainer = container;
            lastTick = tick;
            int idx = 0;
            int cap = GRID_COLS * GRID_ROWS;

            java.util.Iterator<ItemStack> iter = container.streamNonEmpty().iterator();
            while (iter.hasNext() && idx < cap) {
                ItemStack stack = iter.next();
                CACHED_STACKS[idx] = stack;
                CACHED_OVERLAYS[idx] = computeOverlayFlag(stack, mc);
                idx++;
            }
            cachedSlotsToDraw = idx;
            for (int i = idx; i < cap; i++) {
                CACHED_STACKS[i] = null;
                CACHED_OVERLAYS[i] = false;
            }
        }

        int gridOriginX = x + SLOT_OFFSET_X;
        int gridOriginY = y + SLOT_OFFSET_Y;

        int hoveredSlot = -1;
        int relX = mouseX + 1 - x - SLOT_OFFSET_X;
        int relY = mouseY + 1 - y - SLOT_OFFSET_Y;
        if (relX >= 0 && relY >= 0) {
            int sx = relX / SLOT_SIZE;
            int sy = relY / SLOT_SIZE;
            if (sx >= 0 && sx < GRID_COLS && sy >= 0 && sy < GRID_ROWS) {
                hoveredSlot = sy * GRID_COLS + sx;
            }
        }

        for (int i = 0; i < cachedSlotsToDraw; i++) {
            ItemStack stack = CACHED_STACKS[i];
            if (stack == null || stack.isEmpty()) continue;
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int slotX = gridOriginX + col * SLOT_SIZE;
            int slotY = gridOriginY + row * SLOT_SIZE;
            boolean highlighted = i == hoveredSlot;

            if (highlighted) {

                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                        SLOT_HIGHLIGHT_BACK,
                        slotX - HIGHLIGHT_OFFSET, slotY - HIGHLIGHT_OFFSET,
                        HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
            }

            context.drawItem(stack, slotX, slotY);
            if (CACHED_OVERLAYS[i]) {
                context.drawStackOverlay(mc.textRenderer, stack, slotX, slotY);
            }

            if (highlighted) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
                        SLOT_HIGHLIGHT_FRONT,
                        slotX - HIGHLIGHT_OFFSET, slotY - HIGHLIGHT_OFFSET,
                        HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
            }
        }

    }

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

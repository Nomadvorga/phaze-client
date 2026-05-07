package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BindSetting;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

import java.util.ArrayList;
import java.util.List;

public final class ShulkerPreview extends Module {
    private static final ShulkerPreview INSTANCE = new ShulkerPreview();

    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 3;
    private static final int PADDING = 4;
    private static final int PREVIEW_W = GRID_COLS * SLOT_SIZE + PADDING * 2;
    private static final int PREVIEW_H = GRID_ROWS * SLOT_SIZE + PADDING * 2;
    private static final int CURSOR_OFFSET = 14;
    private static final int BG_COLOR = 0xF0100010;
    private static final int BORDER_COLOR = 0xFF5050FF;
    private static final int SLOT_COLOR = 0x80383838;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting alwaysShow = new BooleanSetting(
            "Always Show",
            "Always show shulker contents while hovering, without holding a key"
    ).setValue(false);
    public final BindSetting showBind = new BindSetting(
            "Show Bind",
            "Hold this key to show the preview while Always Show is off"
    );

    private ShulkerPreview() {
        super("shulker_preview", "Shulker Preview", ModuleCategory.UTILITIES);
        alwaysShow.setFullWidth(true);
        showBind.setFullWidth(true);
        showBind.visible(() -> !alwaysShow.isValue());
        setup(generalSection, alwaysShow, showBind);
    }

    public static ShulkerPreview getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Shows shulker box contents in a tooltip while hovering it in any inventory";
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

    public void renderPreview(DrawContext context, int mouseX, int mouseY, ContainerComponent container) {
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

        // Background.
        context.fill(x, y, x + PREVIEW_W, y + PREVIEW_H, BG_COLOR);
        // Outline (1px on each side).
        context.fill(x, y, x + PREVIEW_W, y + 1, BORDER_COLOR);
        context.fill(x, y + PREVIEW_H - 1, x + PREVIEW_W, y + PREVIEW_H, BORDER_COLOR);
        context.fill(x, y, x + 1, y + PREVIEW_H, BORDER_COLOR);
        context.fill(x + PREVIEW_W - 1, y, x + PREVIEW_W, y + PREVIEW_H, BORDER_COLOR);

        // Pull out up to 27 stacks; ContainerComponent.copyTo / iterateNonEmpty
        // semantics differ between MC versions, so just collect via stream.
        List<ItemStack> stacks = new ArrayList<>(GRID_COLS * GRID_ROWS);
        container.streamNonEmpty().limit(GRID_COLS * GRID_ROWS).forEach(stacks::add);

        int gridOriginX = x + PADDING;
        int gridOriginY = y + PADDING;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slotX = gridOriginX + col * SLOT_SIZE;
                int slotY = gridOriginY + row * SLOT_SIZE;
                context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_COLOR);
            }
        }

        for (int i = 0; i < stacks.size(); i++) {
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int slotX = gridOriginX + col * SLOT_SIZE + 1;
            int slotY = gridOriginY + row * SLOT_SIZE + 1;
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) continue;
            context.drawItem(stack, slotX, slotY);
            context.drawStackOverlay(mc.textRenderer, stack, slotX, slotY);
        }
    }
}

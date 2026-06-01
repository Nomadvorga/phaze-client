package vorga.phazeclient.implement.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.setting.implement.ItemPickerSetting;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.base.util.Lang;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.implement.features.modules.client.Theme;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

public final class ItemPickerScreen extends Screen implements QuickImports {
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_ICON_SIZE = 16;
    private static final int GRID_COLUMNS = 9;
    private static final int STORAGE_ROWS = 3;
    private static final int HOTBAR_ROWS = 1;
    private static final int PANEL_PADDING = 14;
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 28;
    private static final int ROW_GAP = 4;
    private static final int SECTION_GAP = 7;

    private final Screen parent;
    private final ItemPickerSetting targetSetting;

    private SlotHit hoveredSlot;
    private float panelX;
    private float panelY;
    private float panelWidth;
    private float panelHeight;

    public ItemPickerScreen(Screen parent, ItemPickerSetting targetSetting) {
        super(Text.of(Lang.translate("Choose Highlight Item")));
        this.parent = parent;
        this.targetSetting = targetSetting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Theme.getInstance().applyMenuTheme();
        renderBackground(context, mouseX, mouseY, delta);

        hoveredSlot = null;

        panelWidth = PANEL_PADDING * 2.0F + GRID_COLUMNS * SLOT_SIZE;
        panelHeight = HEADER_HEIGHT + FOOTER_HEIGHT + PANEL_PADDING * 2.0F
                + STORAGE_ROWS * SLOT_SIZE + HOTBAR_ROWS * SLOT_SIZE + ROW_GAP * 2.0F + SECTION_GAP;
        panelX = width / 2.0F - panelWidth / 2.0F;
        panelY = height / 2.0F - panelHeight / 2.0F;

        context.fill(0, 0, width, height, MenuStyle.detailScrim(0.92F));

        rectangle.render(ShapeProperties.create(context.getMatrices(), panelX, panelY, panelWidth, panelHeight)
                .round(7.0F)
                .thickness(1.2F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, 0.92F))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_BG, 0.97F))
                .build());

        FontRenderer titleFont = Fonts.getSize(15, INTER_BOLD);
        FontRenderer subtitleFont = Fonts.getSize(11);

        float textX = panelX + PANEL_PADDING - 7.0F;
        int headerTitleColor = MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, 0.55F);
        int headerSubtitleColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, 0xFFFFFFFF, 0.40F);
        titleFont.drawString(context.getMatrices(), Lang.translate("Choose Highlight Item"), textX, panelY + 11.0F, headerTitleColor);
        subtitleFont.drawString(
                context.getMatrices(),
                Lang.translate("Click any inventory item to add it to Item Highlighter"),
                textX,
                panelY + 25.0F,
                headerSubtitleColor
        );

        PlayerInventory inventory = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getInventory()
                : null;

        float gridX = panelX + PANEL_PADDING;
        float storageY = panelY + HEADER_HEIGHT + PANEL_PADDING - 2.0F;
        float hotbarY = storageY + STORAGE_ROWS * SLOT_SIZE + SECTION_GAP;

        if (inventory == null) {
            FontRenderer emptyFont = Fonts.getSize(12, INTER_BOLD);
            String line1 = Lang.translate("Join a world to choose an item");
            String line2 = Lang.translate("Click outside or press Esc to go back");
            emptyFont.drawString(context.getMatrices(), line1, panelX + (panelWidth - emptyFont.getStringWidth(line1)) / 2.0F, panelY + panelHeight / 2.0F - 10.0F, MenuStyle.TEXT_PRIMARY);
            subtitleFont.drawString(context.getMatrices(), line2, panelX + (panelWidth - subtitleFont.getStringWidth(line2)) / 2.0F, panelY + panelHeight / 2.0F + 4.0F, MenuStyle.TEXT_MUTED);
            return;
        }

        renderInventoryRows(context, mouseX, mouseY, inventory, gridX, storageY, 9, 27, true);
        renderInventoryRows(context, mouseX, mouseY, inventory, gridX, hotbarY, 0, 9, false);
        context.draw();

        float footerY = panelY + panelHeight - FOOTER_HEIGHT;
        rectangle.render(ShapeProperties.create(context.getMatrices(), panelX + 1.0F, footerY, panelWidth - 2.0F, FOOTER_HEIGHT - 1.0F)
                .round(6.0F)
                .thickness(1.0F)
                .outlineColor(MenuStyle.withAlpha(MenuStyle.BORDER, 0.55F))
                .color(MenuStyle.withAlpha(MenuStyle.PANEL_CHIP, 0.78F))
                .build());

        String footerTitle;
        String footerSubtitle;
        if (hoveredSlot != null && !hoveredSlot.stack.isEmpty()) {
            footerTitle = hoveredSlot.stack.getName().getString();
            footerSubtitle = hoveredSlot.stack.getCount() > 1
                    ? hoveredSlot.stack.getCount() + "x"
                    : Lang.translate("Click to choose item");
        } else if (targetSetting.hasSelection()) {
            footerTitle = Lang.translate("Current selection");
            footerSubtitle = targetSetting.getRowTitle();
        } else {
            footerTitle = Lang.translate("Open inventory picker");
            footerSubtitle = Lang.translate("Selected items are highlighted immediately");
        }

        titleFont = Fonts.getSize(12, INTER_BOLD);
        subtitleFont = Fonts.getSize(10);
        titleFont.drawString(context.getMatrices(), trimToWidth(titleFont, footerTitle, panelWidth - PANEL_PADDING * 2.0F), textX, footerY + 6.0F, MenuStyle.TEXT_PRIMARY);
        subtitleFont.drawString(context.getMatrices(), trimToWidth(subtitleFont, footerSubtitle, panelWidth - PANEL_PADDING * 2.0F), textX, footerY + 16.0F, MenuStyle.TEXT_MUTED);
    }

    private void renderInventoryRows(DrawContext context, int mouseX, int mouseY, PlayerInventory inventory, float startX, float startY, int startIndex, int slotCount, boolean storage) {
        int rows = storage ? STORAGE_ROWS : HOTBAR_ROWS;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int slotIndex = startIndex + row * GRID_COLUMNS + column;
                ItemStack stack = inventory.main.get(slotIndex);
                float slotX = startX + column * SLOT_SIZE;
                float slotY = startY + row * SLOT_SIZE;
                boolean hovered = MathUtil.isHovered(mouseX, mouseY, slotX, slotY, SLOT_SIZE - 1.0F, SLOT_SIZE - 1.0F);
                boolean selectable = stack != null && !stack.isEmpty();
                boolean selected = selectable && targetSetting.matchesSelectedItem(stack);

                float hoverAmount = hovered ? 1.0F : 0.0F;
                int slotBackground = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.settingSurface(false), MenuStyle.settingSurface(true), selected ? 0.42F : hoverAmount * 0.14F), 0.96F);
                int slotOutline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER, targetSetting.getAccentColor() | 0xFF000000, selected ? 0.85F : hoverAmount * 0.18F), 0.92F);

                rectangle.render(ShapeProperties.create(context.getMatrices(), slotX, slotY, SLOT_SIZE - 1.0F, SLOT_SIZE - 1.0F)
                        .round(4.0F)
                        .thickness(1.05F)
                        .outlineColor(slotOutline)
                        .color(slotBackground)
                        .build());

                if (selectable) {
                    context.drawItem(stack, Math.round(slotX + 1.0F), Math.round(slotY + 1.0F));
                    context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, stack, Math.round(slotX + 1.0F), Math.round(slotY + 1.0F));
                }

                if (hovered && selectable) {
                    hoveredSlot = new SlotHit(slotX, slotY, slotIndex, stack);
                    CursorManager.requestHand();
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!MathUtil.isHovered(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
            playClick();
            close();
            return true;
        }

        if (hoveredSlot != null && hoveredSlot.stack != null && !hoveredSlot.stack.isEmpty()) {
            targetSetting.selectFromStack(hoveredSlot.stack);
            playClick();
            close();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    private void playClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static String trimToWidth(FontRenderer font, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        float ellipsisWidth = font.getStringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (font.getStringWidth(builder.toString() + ch) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(ch);
        }
        return builder.isEmpty() ? ellipsis : builder + ellipsis;
    }

    private record SlotHit(float x, float y, int inventoryIndex, ItemStack stack) {
    }
}

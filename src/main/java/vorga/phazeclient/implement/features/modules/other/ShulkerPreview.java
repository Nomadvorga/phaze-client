/*
 * Portions of the chest-frame rendering approach (9-slice GUI sprite
 * + slot-highlight pattern + per-vertex tint flow) are adapted from
 * ShulkerBoxTooltip by MisterPeModder (Yanis Guaye), MIT License.
 *
 *   Source: https://github.com/MisterPeModder/ShulkerBoxTooltip
 *   Specifically: common/src/main/java/com/misterpemodder/shulkerboxtooltip/
 *                 impl/renderer/ModPreviewRenderer.java
 *
 * The bundled {@code shulker_box_tooltip.png} sprite + .mcmeta in
 * {@code assets/phaze/textures/gui/sprites/} are the same texture
 * shipped under MIT in the upstream project's
 * {@code shulkerboxtooltip} resource pack.
 *
 * MIT License
 * Copyright (c) 2019 Yanis Guaye
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the standard MIT license terms.
 * Full license text in {@code .scripts/sbt-LICENSE}.
 */
package vorga.phazeclient.implement.features.modules.other;

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
 * the stack in any inventory screen. Adapted from MisterPeModder's
 * {@code ModPreviewRenderer} (MIT, see file header).
 *
 * <h3>Rendering</h3>
 * Single 9-slice GUI sprite call paints the entire chest-frame
 * panel - background, all four borders, drop-shadow - via the
 * vanilla {@code RenderType.guiTextured} pipeline. The sprite is
 * registered in our own {@code phaze} namespace through the
 * {@code .mcmeta} sidecar, so it does NOT collide with vanilla GUI
 * atlas entries (those live under {@code minecraft}).
 *
 * <h3>Slot highlight</h3>
 * Hovered slot gets a 24x24 halo using vanilla's
 * {@code container/slot_highlight_back} (under the item) and
 * {@code container/slot_highlight_front} (over the item) sprites,
 * matching the upstream mod and the vanilla bundle behaviour.
 *
 * <h3>Tinting (Color By Shulker)</h3>
 * Dye-coloured shulker boxes tint the panel sprite via the
 * per-vertex {@code int color} channel of {@code drawGuiTexture}.
 * The 1.21.4 GUI shader samples that channel directly, so the tint
 * reaches every pixel including the chest-frame borders. Each RGB
 * channel is clamped to a minimum of 0.15 (matching upstream
 * {@code ColorKey.ofDye}) so very dark dyes (black, gray) don't
 * crush the texture detail to mud. Uncolored shulker boxes get a
 * fixed light-purple tint ({@code 0x977FD7}) matching upstream
 * {@code ColorKey.SHULKER_BOX}.
 *
 * <h3>Per-tick caching</h3>
 * Container contents change at server-tick rate (~20 Hz). Re-walking
 * {@code container.streamNonEmpty()} at 240 FPS would be wasted
 * work, so we cache the resolved stacks + overlay flags and refresh
 * only when the hovered stack identity changes or the game tick
 * counter advances.
 */
public final class ShulkerPreview extends Module {
    private static final ShulkerPreview INSTANCE = new ShulkerPreview();

    /** 9-slice chest-frame sprite. Resource path:
     *  {@code assets/phaze/textures/gui/sprites/shulker_box_tooltip.png}.
     *  Sidecar {@code .mcmeta} declares {@code nine_slice} scaling
     *  (border=7, 32x32 source, {@code stretch_inner=false}) so all
     *  four corners stay pixel-locked while the inner area tiles to
     *  fit the panel footprint - identical to upstream's bundled
     *  sprite. */
    private static final Identifier PANEL_SPRITE =
            Identifier.of("phaze", "shulker_box_tooltip");

    /** Light-purple tint applied to uncolored shulker boxes, taken
     *  verbatim from upstream {@code ColorKey.SHULKER_BOX}
     *  ({@code ofRgb(9922455)}). Matches the chest-frame colour the
     *  upstream mod shows for the default purple shulker. */
    private static final int UNCOLORED_SHULKER_RGB = 9922455;

    /** Vanilla slot-highlight sprites - same ones MisterPeModder's
     *  mod uses. The "back" sprite goes UNDER the item icon, the
     *  "front" sprite goes OVER it. */
    private static final Identifier SLOT_HIGHLIGHT_BACK =
            Identifier.ofVanilla("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT =
            Identifier.ofVanilla("container/slot_highlight_front");

    /** Slot footprint and grid. Mirrors
     *  {@code ModPreviewRenderer(18, 18, 8, 8)} from upstream:
     *  18x18 cells, 8px inset = 7px 9-slice border + 1px breathing
     *  room. */
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLS = 9;
    private static final int GRID_ROWS = 3;
    private static final int SLOT_OFFSET_X = 8;
    private static final int SLOT_OFFSET_Y = 8;

    /** Total panel footprint. {@code 14 + cols*18} by
     *  {@code 14 + rows*18}, where 14 = 2 * 7 accounts for the
     *  9-slice border. Mirrors {@code getWidth()/getHeight()} in
     *  upstream. */
    private static final int PREVIEW_W = 14 + GRID_COLS * SLOT_SIZE;
    private static final int PREVIEW_H = 14 + GRID_ROWS * SLOT_SIZE;

    /** Slot-highlight sprite dimensions: 24x24, centred on a slot
     *  with a 4px outer offset (so the halo extends past the slot
     *  border by 3px on every side). */
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
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
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

    /** Clamp a 0-255 channel value to the equivalent of
     *  {@code Math.max(0.15F, channel / 255F)} from upstream's
     *  {@code ColorKey.ofDye}. 0.15 * 255 = 38.25, rounded to 38. */
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

        // Tint resolution. The 1.21.4 GUI render layer samples
        // {@code .color(int)} per vertex - global setShaderColor is
        // ignored. So we feed the tint through drawGuiTexture's
        // {@code int color} overload. -1 (0xFFFFFFFF) = no tint.
        //
        // Mirrors upstream's {@code ModPreviewRenderer.getColor()}:
        //   - colors disabled -> ColorKey.DEFAULT (white, no tint)
        //   - uncolored shulker -> ColorKey.SHULKER_BOX (light purple)
        //   - dyed shulker -> ColorKey.ofDye(dye), which clamps each
        //     RGB channel to a min of 0.15 (38/255) so very dark dyes
        //     stay visible.
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

        // Single 9-slice draw paints the whole chest-frame panel
        // (background + all four borders + drop-shadow) with the
        // dye tint baked into the per-vertex color channel. The
        // {@code .mcmeta} sidecar declares nine_slice scaling
        // (border=7, source 32x32), so corners stay pixel-locked
        // while the inner area stretches to PREVIEW_W x PREVIEW_H.
        context.drawGuiTexture(RenderLayer::getGuiTextured, PANEL_SPRITE,
                x, y, PREVIEW_W, PREVIEW_H, tintArgb);
        context.draw();

        // Refresh the per-tick cache.
        int tick = mc.inGameHud != null ? mc.inGameHud.getTicks() : 0;
        if (hoveredStack != lastHoveredStack || container != lastContainer || tick != lastTick) {
            lastHoveredStack = hoveredStack;
            lastContainer = container;
            lastTick = tick;
            int idx = 0;
            int cap = GRID_COLS * GRID_ROWS;
            // streamNonEmpty allocates an iterator; confined to the
            // once-per-tick refresh path so the per-frame render
            // stays alloc-free.
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

        // Determine which slot (if any) the cursor is currently
        // over. We use the SAME bbox math as MisterPeModder's
        // {@code BasePreviewRenderer.getSlotAt}: the slot is
        // {@code (mouseX - panelX - slotOffsetX) / slotSize}, with
        // a -1 fudge to compensate for the 1px breathing room the
        // upstream panel uses. Returns -1 when out of bounds.
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

        // Slot pass: highlight (back) -> item -> overlay -> highlight (front).
        // Layering matches upstream so the halo halo's outer rim
        // sits OVER the item, while the inner glow sits UNDER it.
        for (int i = 0; i < cachedSlotsToDraw; i++) {
            ItemStack stack = CACHED_STACKS[i];
            if (stack == null || stack.isEmpty()) continue;
            int row = i / GRID_COLS;
            int col = i % GRID_COLS;
            int slotX = gridOriginX + col * SLOT_SIZE;
            int slotY = gridOriginY + row * SLOT_SIZE;
            boolean highlighted = i == hoveredSlot;

            // Layer order matches upstream ModPreviewRenderer.drawSlot:
            //   1. BACK sprite via getGuiTexturedOverlay (under item,
            //      additive blend so the inner glow shines through)
            //   2. Item icon + count/durability overlay
            //   3. FRONT sprite via getGuiTextured (over item, opaque
            //      so the outer rim sits cleanly on top)
            if (highlighted) {
                context.drawGuiTexture(RenderLayer::getGuiTexturedOverlay,
                        SLOT_HIGHLIGHT_BACK,
                        slotX - HIGHLIGHT_OFFSET, slotY - HIGHLIGHT_OFFSET,
                        HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
            }

            context.drawItem(stack, slotX, slotY);
            if (CACHED_OVERLAYS[i]) {
                context.drawStackOverlay(mc.textRenderer, stack, slotX, slotY);
            }

            if (highlighted) {
                context.drawGuiTexture(RenderLayer::getGuiTextured,
                        SLOT_HIGHLIGHT_FRONT,
                        slotX - HIGHLIGHT_OFFSET, slotY - HIGHLIGHT_OFFSET,
                        HIGHLIGHT_SIZE, HIGHLIGHT_SIZE);
            }
        }

        context.draw();
        context.getMatrices().pop();
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

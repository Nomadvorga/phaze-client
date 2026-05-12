package vorga.phazeclient.implement.menu.components.implement.other;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.FontRenderer;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;

public class SearchComponent extends AbstractComponent {
    public static boolean typing = false;
    private int cursorPosition = 0;
    private long lastClickTime = 0;
    private float xOffset = 0;
    @Getter
    private String text = "";
    @Setter
    private ModuleCategory previousCategory = ModuleCategory.ALL;

    private float animatedWidth = 82;
    private static final float COLLAPSED_WIDTH = 82;
    private static final float EXPANDED_WIDTH = 82;
    private static final float ICON_SIZE = 7;
    private static final float SEARCH_HEIGHT = 14;
    private final Animation hoverAnimation = new DecelerateAnimation().setMs(200).setValue(0.15f);
    private final Animation selectAnimation = new DecelerateAnimation().setMs(200).setValue(1);

    public float getAnimationProgress() {
        float denominator = EXPANDED_WIDTH - COLLAPSED_WIDTH;
        if (Math.abs(denominator) < 0.001f) {
            return 1.0f;
        }
        return (animatedWidth - COLLAPSED_WIDTH) / denominator;
    }

    public void setText(String text) {
        this.text = text;
        cursorPosition = text.length();
    }

    public void setCursorPosition(int position) {
        this.cursorPosition = Math.max(0, Math.min(position, text.length()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(12);

        updateXOffset(font, cursorPosition);

        float targetWidth = typing ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
        animatedWidth = MathHelper.lerp(delta * 0.5F, animatedWidth, targetWidth);

        width = animatedWidth;
        height = SEARCH_HEIGHT;

        boolean isSearchSelected = MenuScreen.INSTANCE.getCategory() == ModuleCategory.SEARCH;

        boolean isHovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        hoverAnimation.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);

        selectAnimation.setDirection(isSearchSelected ? Direction.FORWARDS : Direction.BACKWARDS);

        float hoverProgress = hoverAnimation.getOutputFloat();
        float selectProgress = selectAnimation.getOutputFloat();

        int outlineColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.CHIP_ACTIVE, selectProgress);
        outlineColor = MenuStyle.mix(outlineColor, MenuStyle.CHIP_ACTIVE, hoverProgress * 0.5F);
        int iconColor = MenuStyle.mix(MenuStyle.TEXT_MUTED, MenuStyle.TEXT_PRIMARY, selectProgress);

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(2).thickness(3.0F).softness(1).outlineColor(applyGlobalAlpha(outlineColor)).color(0).build());

        float iconX;
        float iconY = y + (height - ICON_SIZE) / 2;

        if (animatedWidth < EXPANDED_WIDTH - 1) {
            float progress = (animatedWidth - COLLAPSED_WIDTH) / (EXPANDED_WIDTH - COLLAPSED_WIDTH);
            float collapsedIconX = x + 4;
            float expandedIconX = x + 4;
            iconX = MathHelper.lerp(progress, collapsedIconX, expandedIconX);
        } else {
            iconX = x + 4;
        }

        image.setTexture("textures/search_lunar.png").render(ShapeProperties.create(matrix, iconX, iconY, ICON_SIZE, ICON_SIZE).color(applyGlobalAlpha(iconColor)).build());

        float animationProgress = getAnimationProgress();
        float textAlpha = Math.max(0, Math.min(1.0F, (animationProgress - 0.2F) / 0.6F));
        float textX = x + 13 - xOffset;

        if (textAlpha > 0.01F) {
            String displayText = text;
            String centeredText = displayText.isEmpty() ? "Search" : displayText;
            float inputTextY = centeredTextY(font, centeredText, y, height);

            ScissorManager scissor = Main.getInstance().getScissorManager();
            scissor.push(matrix.peek().getPositionMatrix(), x + 13, y, width - 15, height);

                if (!text.isEmpty() && typing) {
                    String searchText = text.toLowerCase();

                    int autocompleteAlpha = (int) (applyGlobalAlpha(textAlpha) * 0x77) << 24;
                    int autocompleteColor = autocompleteAlpha | 0x777777;

                    Main.getInstance().getModuleProvider().getModules().stream()
                            .filter(mod -> mod.getLocalizedName().toLowerCase().startsWith(searchText))
                            .findFirst()
                            .ifPresentOrElse(module -> {
                                String completion = module.getLocalizedName();
                                String remainingText = completion.substring(text.length());
                                float textWidth = font.getStringWidth(text);

                                FontRenderer italicFont = Fonts.getSize(12, Fonts.Type.INTER_DEFAULT);
                                italicFont.drawString(context.getMatrices(), remainingText,
                                        textX + textWidth, centeredTextY(italicFont, remainingText, y, height), autocompleteColor);
                            }, () -> findFirstMatchingSetting(searchText).ifPresent(setting -> {
                                String completion = setting.getLocalizedName();
                                String remainingText = completion.substring(text.length());
                                float textWidth = font.getStringWidth(text);

                                FontRenderer italicFont = Fonts.getSize(12, Fonts.Type.INTER_DEFAULT);
                                italicFont.drawString(context.getMatrices(), remainingText,
                                        textX + textWidth, centeredTextY(italicFont, remainingText, y, height), autocompleteColor);
                            }));
                }

                if (displayText.isEmpty() && !typing) {
                    font.drawString(context.getMatrices(), "Search", x + 13, inputTextY, MenuStyle.withAlpha(MenuStyle.TEXT_MUTED, applyGlobalAlpha(textAlpha)));
                } else {
                    font.drawString(context.getMatrices(), displayText, textX, inputTextY, MenuStyle.withAlpha(0xFFFFFFFF, applyGlobalAlpha(textAlpha)));
                }

                scissor.pop();

                long currentTime = System.currentTimeMillis();
                boolean focused = typing && (currentTime % 1000 < 500);

                if (focused && textAlpha > 0.5F) {
                    float cursorX = font.getStringWidth(text.substring(0, cursorPosition));
                    int cursorColor = (int) (applyGlobalAlpha(textAlpha) * 255) << 24 | 0xFFFFFF;
                    float cursorHeight = Math.max(6.0F, renderedTextHeight(font, "I") - 1.0F);
                    rectangle.render(ShapeProperties.create(matrix, x + 13 - xOffset + cursorX, inputTextY - 2.5F, 0.5F, cursorHeight)
                            .color(cursorColor).build());
                }
            }
    }

    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        if (hovered && button == 0) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 250 && typing) {
                cursorPosition = text.length();
            } else {

                boolean wasTyping = typing;
                typing = true;
                lastClickTime = currentTime;

                if (wasTyping && animatedWidth > EXPANDED_WIDTH - 5) {
                    cursorPosition = getCursorIndexAt(mouseX);
                } else {
                    text = "";
                    cursorPosition = 0;
                    xOffset = 0;
                }

                MenuScreen menuScreen = MenuScreen.INSTANCE;
                if (menuScreen.getCategory() != ModuleCategory.SEARCH) {
                    previousCategory = menuScreen.getCategory();
                }
                if (!text.isEmpty()) {
                    menuScreen.setCategory(ModuleCategory.SEARCH);
                }
            }
        } else {
            if (typing) {
                typing = false;
                if (text.isEmpty()) {
                    MenuScreen menuScreen = MenuScreen.INSTANCE;
                    if (menuScreen.getCategory() == ModuleCategory.SEARCH) {
                        menuScreen.setCategory(previousCategory);
                    }
                }
            }
        }
        return hovered && button == 0;
    }

    

    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        float maxTextWidth = EXPANDED_WIDTH - ICON_SIZE - 17;
        if (typing && Fonts.getSize(12).getStringWidth(text) < maxTextWidth) {
            if ((chr == '/' || chr == '.') && text.isEmpty()) {
                return true;
            }

            if (text.isEmpty()) {
                MenuScreen menuScreen = MenuScreen.INSTANCE;
                if (menuScreen.getCategory() != ModuleCategory.SEARCH) {
                    previousCategory = menuScreen.getCategory();
                    menuScreen.setCategory(ModuleCategory.SEARCH);
                }
            }

            text = text.substring(0, cursorPosition) + chr + text.substring(cursorPosition);
            cursorPosition++;
            return true;
        }
        return false;
    }

    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ENTER -> handleTextModification(keyCode);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> moveCursor(keyCode);
                case GLFW.GLFW_KEY_TAB -> handleTabCompletion();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    


    private void handleTextModification(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPosition > 0) {
                text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                cursorPosition--;

                if (text.isEmpty()) {
                    MenuScreen menuScreen = MenuScreen.INSTANCE;
                    if (menuScreen.getCategory() == ModuleCategory.SEARCH) {
                        menuScreen.setCategory(previousCategory);
                    }
                }
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            typing = false;
            if (text.isEmpty()) {
                MenuScreen menuScreen = MenuScreen.INSTANCE;
                if (menuScreen.getCategory() == ModuleCategory.SEARCH) {
                    menuScreen.setCategory(previousCategory);
                }
            }
        }
    }

    
    private void moveCursor(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
            cursorPosition--;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT && cursorPosition < text.length()) {
            cursorPosition++;
        }
    }

    

    
    private int getCursorIndexAt(double mouseX) {
        FontRenderer font = Fonts.getSize(12);
        float relativeX = (float) mouseX - x - 13 + xOffset;
        int position = 0;
        while (position < text.length()) {
            float textWidth = font.getStringWidth(text.substring(0, position + 1));
            if (textWidth > relativeX) {
                break;
            }
            position++;
        }
        return position;
    }

    
    private void updateXOffset(FontRenderer font, int cursorPosition) {
        float cursorX = font.getStringWidth(text.substring(0, cursorPosition));
        float availableWidth = width - ICON_SIZE - 17;
        if (cursorX < xOffset) {
            xOffset = cursorX;
        } else if (cursorX - xOffset > availableWidth) {
            xOffset = cursorX - availableWidth;
        }
    }


    private void handleTabCompletion() {
        if (!text.isEmpty()) {
            String searchText = text.toLowerCase();

            Main.getInstance().getModuleProvider().getModules().stream()
                    .filter(mod -> mod.getLocalizedName().toLowerCase().startsWith(searchText))
                    .findFirst()
                    .ifPresentOrElse(module -> {
                        text = module.getLocalizedName();
                        cursorPosition = text.length();
                    }, () -> {
                        findFirstMatchingSetting(searchText).ifPresent(setting -> {
                            text = setting.getLocalizedName();
                            cursorPosition = text.length();
                        });
                    });
        }
    }

    private java.util.Optional<Setting> findFirstMatchingSetting(String searchText) {
        return Main.getInstance().getModuleProvider().getModules().stream()
                .flatMap(module -> findMatchingSettingInModule(module, searchText).stream())
                .findFirst();
    }

    private java.util.Optional<Setting> findMatchingSettingInModule(
            Module module, String searchText) {
        return module.settings().stream()
                .flatMap(setting -> findMatchingSettingRecursive(setting, searchText).stream())
                .findFirst();
    }

    private java.util.Optional<Setting> findMatchingSettingRecursive(
            Setting setting, String searchText) {

        if (setting.getLocalizedName().toLowerCase().startsWith(searchText)) {
            return java.util.Optional.of(setting);
        }

        if (setting instanceof GroupSetting groupSetting) {
            return groupSetting.getSubSettings().stream()
                    .flatMap(subSetting -> findMatchingSettingRecursive(subSetting, searchText).stream())
                    .findFirst();
        }

        return java.util.Optional.empty();
    }
}

package vorga.phazeclient.implement.menu.components.implement.module;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingComponentAdder;
import vorga.phazeclient.api.feature.module.setting.implement.*;
import vorga.phazeclient.api.system.animation.Animation;
import vorga.phazeclient.api.system.animation.Direction;
import vorga.phazeclient.api.system.animation.implement.DecelerateAnimation;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.other.StringUtil;
import vorga.phazeclient.implement.menu.MenuScreen;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static vorga.phazeclient.api.system.font.Fonts.Type.INTER_BOLD;

@Getter
public class ModuleComponent extends AbstractComponent {
    private static final Gson GSON_COMPACT = new Gson();
    private static final long COPY_PASTE_COOLDOWN_MS = 150;
    private static final float TITLE_TEXT_SIZE = 8.4F;
    private static final float ROW_TEXT_SIZE = 6.45F;
    private static final float BIND_TEXT_SIZE = 5.8F;
    private static final float CARD_INSET = 1.3F;
    private static final float STANDALONE_OPTIONS_RADIUS = 5.2F;
    private static final float OPTIONS_ROW_HEIGHT = 18.0F;
    private static final float ENABLED_ROW_HEIGHT = 18.0F;
    private static final float BASE_HEIGHT_ENABLED = 86.0F;
    private static final float BASE_HEIGHT_SIMPLE = 62.0F;
    private static final float BASE_HEIGHT_OPTIONS_ONLY = BASE_HEIGHT_ENABLED - ENABLED_ROW_HEIGHT;

    private final List<AbstractSettingComponent> components = new ArrayList<>();
    private final Module module;

    private boolean binding;
    private boolean isHovered = false;

    private long lastCopyTime = 0;
    private long lastPasteTime = 0;

    private final Animation hoverAnimation = new DecelerateAnimation().setMs(200).setValue(1);
    private final Animation optionsHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);
    private final Animation outlineColorAnimation = new DecelerateAnimation().setMs(300).setValue(1);
    private final Animation stateRowHoverAnimation = new DecelerateAnimation().setMs(180).setValue(1);

    private float visualX, visualY;
    private float prevX, prevY;
    private boolean lastModuleState;

    public ModuleComponent(Module module) {
        this.module = module;
        new SettingComponentAdder().addSettingComponent(module.settings(), components);
        this.visualX = this.prevX = 0;
        this.visualY = this.prevY = 0;
        this.lastModuleState = module.isState();
        this.hoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.optionsHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.stateRowHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
        this.outlineColorAnimation.setDirectionAndFinish(module.isState() ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        visualX = x;
        visualY = y;

        float offsetX = visualX - x;
        float offsetY = visualY - y;

        // While the user is in SEARCH mode, every module card renders in a
        // completely static state - no hover thickening, no smooth
        // enabled/disabled transitions, no row-hover shimmer. setDirectionAndFinish
        // pins each animation at its target value so getOutput() returns the
        // neutral/state-synced value for the rest of this render() and inner
        // helpers (renderOptionsRow / renderStateRow) skip their own
        // setDirection calls.
        boolean inSearchMode = isInSearchMode();
        if (inSearchMode) {
            hoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
            optionsHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
            stateRowHoverAnimation.setDirectionAndFinish(Direction.BACKWARDS);
            outlineColorAnimation.setDirectionAndFinish(module.isState() ? Direction.FORWARDS : Direction.BACKWARDS);
            lastModuleState = module.isState();
            this.isHovered = false;
        } else {
            this.isHovered = isHover(mouseX, mouseY);
            hoverAnimation.setDirection(isHovered ? Direction.FORWARDS : Direction.BACKWARDS);

            boolean currentModuleState = module.isState();
            if (currentModuleState != lastModuleState) {
                outlineColorAnimation.setDirection(currentModuleState ? Direction.FORWARDS : Direction.BACKWARDS);
                lastModuleState = currentModuleState;
            }
        }

        float hoverProgress = hoverAnimation.getOutput().floatValue();
        float baseHeight = getBaseHeightFloat();
        boolean showOptionsRow = hasSettings();
        boolean showStateRow = module.isShowEnable();
        float optionsY = y + baseHeight - (showStateRow ? (OPTIONS_ROW_HEIGHT + ENABLED_ROW_HEIGHT) : OPTIONS_ROW_HEIGHT);

        context.getMatrices().push();
        context.getMatrices().translate(offsetX, offsetY, 0);

        int outlineColor = MenuStyle.mix(MenuStyle.BORDER, MenuStyle.CHIP_ACTIVE, outlineColorAnimation.getOutput().floatValue() * 0.75f);
        outlineColor = MenuStyle.withAlpha(outlineColor, applyGlobalAlpha(0.96F));
        // Card background is intentionally transparent so the module blends with the GUI panel background.
        height = getComponentHeight();
        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                .round(8).softness(1).thickness(3.6F).outlineColor(outlineColor).color(0x00000000).build());

        float iconSize = module.getIconSize();
        float iconX = x + (width - iconSize) / 2.0F;
        String iconTexture = module.getIcon() != null ? "phaze:textures/modules/" + module.getIcon() : "textures/modules/" + module.getCategory().getIdentifier() + ".png";
        image.setTexture(iconTexture)
                .render(ShapeProperties.create(context.getMatrices(), iconX, y + 12.0F, iconSize, iconSize)
                        .color(applyGlobalAlpha(MenuStyle.TEXT_PRIMARY)).build());

        String moduleName = module.getLocalizedName();
        float titleAreaY = y + 31.0F;
        float titleAreaBottom = showOptionsRow ? optionsY - 1.0F : y + baseHeight - 7.0F;
        float titleAreaHeight = Math.max(10.0F, titleAreaBottom - titleAreaY);
        MsdfRenderer.renderText(
                MsdfFonts.medium(),
                moduleName,
                TITLE_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.medium(), moduleName, TITLE_TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(TITLE_TEXT_SIZE, titleAreaY, titleAreaHeight),
                0.0F
        );

        if (showOptionsRow) {
            renderOptionsRow(context, mouseX, mouseY, optionsY, !showStateRow, inSearchMode);
        }

        if (showStateRow) {
            float enabledY = optionsY + OPTIONS_ROW_HEIGHT;
            if (!inSearchMode) {
                boolean stateRowHovered = MathUtil.isHovered(mouseX, mouseY, x, enabledY, width, ENABLED_ROW_HEIGHT);
                stateRowHoverAnimation.setDirection(stateRowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
            }
            renderStateRow(context, enabledY, stateRowHoverAnimation.getOutput().floatValue());
        }

        if (showStateRow && module.isCanBind()) {
            drawBind(context, x, y);
        }

        context.getMatrices().pop();
    }

    private void renderOptionsRow(DrawContext context, int mouseX, int mouseY, float optionsY, boolean standaloneRow, boolean inSearchMode) {
        boolean hasSettings = hasSettings();
        int baseRowColor = hasSettings
                ? MenuStyle.CARD_OPTIONS
                : MenuStyle.mix(MenuStyle.CARD_OPTIONS, MenuStyle.PANEL_CHIP, 0.45F);
        float iconSectionWidth = 26.0F;
        float rowX = standaloneRow ? x : x + CARD_INSET;
        float rowWidth = standaloneRow ? width : width - CARD_INSET * 2.0F;
        float dividerX = rowX + rowWidth - iconSectionWidth;
        if (!inSearchMode) {
            boolean rowHovered = MathUtil.isHovered(mouseX, mouseY, rowX, optionsY, rowWidth, OPTIONS_ROW_HEIGHT);
            optionsHoverAnimation.setDirection(rowHovered ? Direction.FORWARDS : Direction.BACKWARDS);
        }
        float rowHoverProgress = optionsHoverAnimation.getOutput().floatValue();
        int rowColor = MenuStyle.mix(baseRowColor, MenuStyle.TEXT_PRIMARY, rowHoverProgress * 0.055F);
        int borderColor = MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, applyGlobalAlpha(standaloneRow ? (0.45F + rowHoverProgress * 0.17F) : (0.35F + rowHoverProgress * 0.13F)));

        rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, optionsY, rowWidth, OPTIONS_ROW_HEIGHT)
                .round(0, standaloneRow ? STANDALONE_OPTIONS_RADIUS : 0, 0, standaloneRow ? STANDALONE_OPTIONS_RADIUS : 0).color(applyGlobalAlpha(rowColor)).build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), rowX, optionsY, rowWidth, 1.0F)
                .color(borderColor).build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), dividerX, optionsY, 1.0F, OPTIONS_ROW_HEIGHT - (standaloneRow ? 0.35F : 0.0F))
                .color(MenuStyle.withAlpha(MenuStyle.BORDER_LIGHT, applyGlobalAlpha(0.55F + rowHoverProgress * 0.15F))).build());

        String optionsLabel = "OPTIONS";
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                optionsLabel,
                ROW_TEXT_SIZE,
                applyGlobalAlpha(hasSettings ? MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, rowHoverProgress * 0.10F) : MenuStyle.mix(MenuStyle.TEXT_MUTED, 0xFFFFFFFF, rowHoverProgress * 0.08F)),
                context.getMatrices().peek().getPositionMatrix(),
                rowX + 11.0F,
                MenuStyle.centerMsdfTextY(ROW_TEXT_SIZE, optionsY, OPTIONS_ROW_HEIGHT),
                0.0F
        );

        if (hasSettings) {
            image.setTexture("textures/settings.png")
                    .render(ShapeProperties.create(context.getMatrices(), dividerX + (iconSectionWidth - 9.0F) / 2.0F, optionsY + (OPTIONS_ROW_HEIGHT - 9.0F) / 2.0F, 9.0F, 9.0F)
                            .color(applyGlobalAlpha(MenuStyle.mix(MenuStyle.TEXT_PRIMARY, 0xFFFFFFFF, rowHoverProgress * 0.10F)))
                            .build());
        }
    }

    private void renderStateRow(DrawContext context, float enabledY, float hoverProgress) {
        boolean locked = module.isServerLocked();
        int stateColor;
        String stateText;

        if (locked) {
            stateColor = applyGlobalAlpha(0xFF888986);
            stateText = "LOCKED";
        } else {
            float stateProgress = outlineColorAnimation.getOutput().floatValue();
            int base = MenuStyle.mix(0xFFA01E40, MenuStyle.ACCENT_GREEN, stateProgress);
            stateColor = applyGlobalAlpha(MenuStyle.mix(base, MenuStyle.TEXT_PRIMARY, hoverProgress * 0.15F));
            stateText = module.isState() ? "ENABLED" : "DISABLED";
        }

        rectangle.render(ShapeProperties.create(context.getMatrices(), x + CARD_INSET, enabledY, width - CARD_INSET * 2.0F, ENABLED_ROW_HEIGHT - 1)
                .round(0, 7, 0, 7).color(stateColor).build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                stateText,
                ROW_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_PRIMARY),
                context.getMatrices().peek().getPositionMatrix(),
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), stateText, ROW_TEXT_SIZE, x, width),
                MenuStyle.centerMsdfTextY(ROW_TEXT_SIZE, enabledY, ENABLED_ROW_HEIGHT - 1),
                0.0F
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float baseHeight = getBaseHeightFloat();
        boolean showOptionsRow = hasSettings();
        boolean showStateRow = module.isShowEnable();
        float optionsY = y + baseHeight - (showStateRow ? (OPTIONS_ROW_HEIGHT + ENABLED_ROW_HEIGHT) : OPTIONS_ROW_HEIGHT);
        float enabledY = optionsY + OPTIONS_ROW_HEIGHT;
        boolean optionsHovered = MathUtil.isHovered(mouseX, mouseY, x, optionsY, width, OPTIONS_ROW_HEIGHT);
        boolean settingsClick = button == 0 || button == 1;

        if (showOptionsRow && settingsClick && optionsHovered) {
            playButtonClickSound();
            MenuScreen.INSTANCE.openModuleDetail(module);
            return true;
        }

        if (showStateRow && button == 0 && MathUtil.isHovered(mouseX, mouseY, x, enabledY, width, ENABLED_ROW_HEIGHT)) {
            if (module.isServerLocked()) {
                return true;
            }
            playButtonClickSound();
            module.switchState();
            return true;
        }

        if (showStateRow && module.isCanBind()) {
            String bindName = StringUtil.getBindName(module.getKey());
            float stringWidth = MsdfFonts.bold().getWidth(bindName, BIND_TEXT_SIZE);
            if (MathUtil.isHovered(mouseX, mouseY, x + width - 15 - stringWidth, y + 8, stringWidth + 6, 9) && button == 0) {
                playButtonClickSound();
                binding = !binding;
                return true;
            } else if (binding) {
                module.setKey(button);
                binding = false;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        float menuTop = menuScreen.y;
        float menuBottom = menuScreen.y + menuScreen.height;
        float menuLeft = menuScreen.x;
        float menuRight = menuScreen.x + menuScreen.width;

        if (mouseX < menuLeft || mouseX > menuRight || mouseY < menuTop || mouseY > menuBottom) {
            return false;
        }

        boolean hovered = MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
        if (!hovered) {
            return false;
        }

        float visibleTop = Math.max(y, menuTop);
        float visibleBottom = Math.min(y + height, menuBottom);
        return mouseY >= visibleTop && mouseY <= visibleBottom;
    }

    private boolean hasSettings() {
        return !components.isEmpty();
    }

    private float getBaseHeightFloat() {
        if (module.isShowEnable()) {
            return BASE_HEIGHT_ENABLED;
        }
        return hasSettings() ? BASE_HEIGHT_OPTIONS_ONLY : BASE_HEIGHT_SIMPLE;
    }

    public int getComponentHeight() {
        return Math.round(getBaseHeightFloat());
    }

    private boolean isInSearchMode() {
        return MenuScreen.INSTANCE != null && MenuScreen.INSTANCE.getCategory() == ModuleCategory.SEARCH;
    }

    private void drawBind(DrawContext context, float renderX, float renderY) {
        String bindName = StringUtil.getBindName(module.getKey());
        String name = binding ? "(" + bindName + ") ..." : bindName;
        float stringWidth = MsdfFonts.bold().getWidth(name, BIND_TEXT_SIZE);
        float bindX = renderX + width - stringWidth - 18.0F;
        float bindY = renderY + 6.0F;

        rectangle.render(ShapeProperties.create(context.getMatrices(), bindX, bindY, stringWidth + 8.0F, 10.0F)
                .round(2).thickness(1.2F).outlineColor(applyGlobalAlpha(MenuStyle.BORDER)).color(applyGlobalAlpha(MenuStyle.PANEL_CHIP)).build());

        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                name,
                BIND_TEXT_SIZE,
                applyGlobalAlpha(MenuStyle.TEXT_MUTED),
                context.getMatrices().peek().getPositionMatrix(),
                bindX + 4.0F,
                MenuStyle.centerMsdfTextY(BIND_TEXT_SIZE, bindY, 10.0F),
                0.0F
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
        if (binding && module.isCanBind()) {
            module.setKey(key);
            binding = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (isHovered) {
                copyModuleConfig();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            pasteModuleConfig();
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void copyModuleConfig() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCopyTime < COPY_PASTE_COOLDOWN_MS) {
            return;
        }
        lastCopyTime = currentTime;

        try {
            JsonObject rootObject = new JsonObject();
            JsonObject moduleData = new JsonObject();

            rootObject.addProperty("moduleId", module.getIdentifier());
            rootObject.addProperty("moduleName", module.getName());

            moduleData.addProperty("enabled", module.isState());
            moduleData.addProperty("bind", module.getKey());

            for (Setting setting : module.settings()) {
                saveSetting(setting, moduleData);
            }

            rootObject.add("config", moduleData);

            String jsonString = GSON_COMPACT.toJson(rootObject);

            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return;
            }

            long windowHandle = client.getWindow().getHandle();
            GLFW.glfwSetClipboardString(windowHandle, jsonString);
        } catch (Exception ignored) {
        }
    }

    private void pasteModuleConfig() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPasteTime < COPY_PASTE_COOLDOWN_MS) {
            return;
        }
        lastPasteTime = currentTime;

        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.getWindow() == null) {
                return;
            }

            long windowHandle = client.getWindow().getHandle();
            String jsonString = GLFW.glfwGetClipboardString(windowHandle);

            if (jsonString == null || jsonString.trim().isEmpty()) {
                return;
            }

            JsonObject rootObject = GSON_COMPACT.fromJson(jsonString.trim(), JsonObject.class);
            if (!rootObject.has("moduleId") || !rootObject.has("config")) {
                return;
            }

            String clipboardModuleId = rootObject.get("moduleId").getAsString();
            if (!clipboardModuleId.equals(module.getIdentifier())) {
                return;
            }

            JsonObject moduleData = rootObject.getAsJsonObject("config");

            if (moduleData.has("enabled")) {
                module.setStateSilent(moduleData.get("enabled").getAsBoolean());
            }

            if (moduleData.has("bind")) {
                module.setKey(moduleData.get("bind").getAsInt());
            }

            for (Setting setting : module.settings()) {
                try {
                    loadSetting(setting, moduleData);
                } catch (Exception ignored) {
                }
            }

            if (module.isEnabled()) {
                module.setStateSilent(true);
            }
        } catch (Exception ignored) {
        }
    }

    private void saveSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();

        switch (setting) {
            case BooleanSetting booleanSetting -> moduleData.addProperty(key, booleanSetting.isValue());
            case ValueSetting valueSetting -> moduleData.addProperty(key, valueSetting.getValue());
            case TextSetting textSetting -> {
                String value = textSetting.getText();
                value = value.replace(" ", "%%").replace("/", "++");
                moduleData.addProperty(key, value);
            }
            case BindSetting bindSetting -> moduleData.addProperty(key, bindSetting.getKey());
            case ColorSetting colorSetting -> moduleData.addProperty(key, colorSetting.getColor());
            case SelectSetting selectSetting -> moduleData.addProperty(key, selectSetting.getSelected());
            case MultiSelectSetting multiSelectSetting -> moduleData.addProperty(key, String.join(",", multiSelectSetting.getSelected()));
            case MultiColorSetting multiColor -> {
                JsonObject colorObject = new JsonObject();
                colorObject.addProperty("selectedColorIndex", multiColor.getSelectedColorIndex());

                com.google.gson.JsonArray colorsArray = new com.google.gson.JsonArray();
                for (ColorSetting color : multiColor.getAllColors()) {
                    colorsArray.add(color.getColor());
                }
                colorObject.add("colors", colorsArray);
                moduleData.add(key, colorObject);
            }
            case GroupSetting group -> {
                JsonObject groupObject = new JsonObject();
                groupObject.addProperty("state", group.isValue());

                for (Setting subSetting : group.getSubSettings()) {
                    saveSetting(subSetting, groupObject);
                }

                moduleData.add(key, groupObject);
            }
            default -> {
            }
        }
    }

    private void loadSetting(Setting setting, JsonObject moduleData) {
        if (!setting.isSaveToConfig()) {
            return;
        }

        String key = setting.getNameKey();
        if (!moduleData.has(key)) {
            return;
        }

        com.google.gson.JsonElement element = moduleData.get(key);

        switch (setting) {
            case BooleanSetting booleanSetting -> booleanSetting.setValue(element.getAsBoolean());
            case ValueSetting valueSetting -> valueSetting.setValue(element.getAsFloat());
            case TextSetting textSetting -> {
                String value = element.getAsString();
                value = value.replace("%%", " ").replace("++", "/");
                textSetting.setText(value);
            }
            case BindSetting bindSetting -> bindSetting.setKey(element.getAsInt());
            case ColorSetting colorSetting -> colorSetting.setColor(element.getAsInt());
            case SelectSetting selectSetting -> selectSetting.setSelected(element.getAsString());
            case MultiSelectSetting multiSelectSetting -> {
                String value = element.getAsString();
                List<String> selected = new ArrayList<>(Arrays.asList(value.split(",")));
                selected.removeIf(s -> !multiSelectSetting.getList().contains(s));
                multiSelectSetting.setSelected(selected);
            }
            case MultiColorSetting multiColor -> {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    int oldColor = element.getAsInt();
                    if (multiColor.getColor1() != null) {
                        multiColor.getColor1().setColor(oldColor);
                    }
                    return;
                }

                JsonObject colorObject = element.getAsJsonObject();
                if (colorObject.has("selectedColorIndex")) {
                    multiColor.setSelectedColorIndex(colorObject.get("selectedColorIndex").getAsInt());
                }

                if (colorObject.has("colors")) {
                    com.google.gson.JsonArray colorsArray = colorObject.getAsJsonArray("colors");
                    List<ColorSetting> colorSettings = multiColor.getAllColors();

                    for (int i = 0; i < Math.min(colorsArray.size(), colorSettings.size()); i++) {
                        colorSettings.get(i).setColor(colorsArray.get(i).getAsInt());
                    }
                }
            }
            case GroupSetting group -> {
                JsonObject groupObject = element.getAsJsonObject();
                if (groupObject.has("state")) {
                    group.setValue(groupObject.get("state").getAsBoolean());
                }

                for (Setting subSetting : group.getSubSettings()) {
                    loadSetting(subSetting, groupObject);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModuleComponent that = (ModuleComponent) o;
        return module.equals(that.module);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module);
    }
}

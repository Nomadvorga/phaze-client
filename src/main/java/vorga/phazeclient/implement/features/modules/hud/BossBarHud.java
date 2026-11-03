package vorga.phazeclient.implement.features.modules.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ColorSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;

import java.util.Arrays;
import java.util.Collection;

/** Edits the vanilla boss-bar renderer instead of replacing its draw path. */
public final class BossBarHud extends RectHudModule {
    private static final BossBarHud INSTANCE = new BossBarHud();
    public static final Text PREVIEW_NAME = Text.literal("Boss Bar");
    private static final long RAINBOW_CACHE_INTERVAL = 1_000_000_000L / 60;

    public final BooleanSetting renderHealth = new BooleanSetting("Render Health", "Show the boss health bar").setValue(true);
    public final BooleanSetting renderText = new BooleanSetting("Render Text", "Show the boss bar name").setValue(false);
    public final SectionSetting colorOptions = new SectionSetting("Color");
    public final SelectSetting colorMode = new SelectSetting("Mode", "Boss bar color mode") {
        @Override
        public boolean isFullWidth() {
            return isSelected("Rainbow");
        }
    }.value("Custom", "Rainbow").selected("Custom");
    public final ColorSetting fillColor = new ColorSetting("Fill Color", "Boss bar fill color")
            .value(0xFFAA55FF).noAlpha().popupRow().visible(() -> colorMode.isSelected("Custom"));
    public final ValueSetting rainbowSpeed = new ValueSetting("Rainbow Speed", "Speed of the boss bar rainbow cycle")
            .range(0.1F, 1.0F).step(0.05F).setValue(0.5F).visible(() -> colorMode.isSelected("Rainbow"));

    private Text[] measuredNames = new Text[8];
    private int[] measuredWidths = new int[8];
    private int baseWidth = 182;
    private int baseHeight = 5;
    private int nativeX;
    private int nativeY = 12;
    private boolean liveBars;
    private boolean positionInitialized;
    private boolean layoutDirty = true;
    private int layoutScreenWidth = -1, layoutScreenHeight = -1, layoutRows;
    private boolean layoutHealth, layoutText;
    private long lastHueNanos = -1;
    private float rainbowHue;
    private int activeFillColor = 0xFFAA55FF;

    private boolean cacheInitialized;
    private boolean lastEnabled, lastHealth, lastText, lastRainbow, lastChat;
    private float lastX, lastY, lastScale, lastGuiScale, lastRainbowSpeed;
    private int lastCustomColor;
    private long lastCacheNanos;

    private BossBarHud() {
        // Keep the identifier so existing configs and server rules still apply.
        super("bossbar_tweaker", "Boss Bar", 0.0F, 0.0F, 1.0F);
        settings().clear();
        background.setValue(false);
        rainbowSpeed.setFullWidth(true);
        setup(mainSection, renderHealth, renderText, colorOptions, colorMode, fillColor, rainbowSpeed);
    }

    public static BossBarHud getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Customizable boss bar";
    }

    @Override
    public String getIcon() {
        return "scoreboard_hud.png";
    }

    @Override
    public float getRenderHudScale() {
        return getHudScale() * guiScale();
    }

    public int getBaseWidth() {
        return baseWidth;
    }

    public int getBaseHeight() {
        return baseHeight;
    }

    public boolean hasLiveBars() {
        return liveBars;
    }

    public int getActiveFillColor() {
        return activeFillColor;
    }

    public void updateLayout(MinecraftClient client, Collection<ClientBossBar> bars) {
        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();
        int maxRows = Math.max(1, (scaledHeight / 3 - 12 + 18) / 19);
        int rows = Math.min(Math.max(1, bars.size()), maxRows);
        liveBars = !bars.isEmpty();
        boolean health = renderHealth.isValue(), text = renderText.isValue();
        if (!layoutDirty && scaledWidth == layoutScreenWidth && scaledHeight == layoutScreenHeight
                && rows == layoutRows && health == layoutHealth && text == layoutText) {
            updateDefaultPosition(scaledWidth);
            return;
        }
        layoutDirty = false;
        layoutScreenWidth = scaledWidth;
        layoutScreenHeight = scaledHeight;
        layoutRows = rows;
        layoutHealth = health;
        layoutText = text;
        baseWidth = 182;
        if (text) {
            if (rows > measuredNames.length) {
                measuredNames = Arrays.copyOf(measuredNames, rows);
                measuredWidths = Arrays.copyOf(measuredWidths, rows);
            }
            int index = 0;
            if (liveBars) {
                for (ClientBossBar bar : bars) {
                    measureName(client, index++, bar.getName());
                    if (index == rows) break;
                }
            } else {
                measureName(client, 0, PREVIEW_NAME);
            }
        }
        nativeX = scaledWidth / 2 - baseWidth / 2;
        nativeY = text ? 3 : 12;
        int rowHeight = text ? (health ? 14 : 9) : 5;
        baseHeight = (rows - 1) * 19 + rowHeight;
        updateDefaultPosition(scaledWidth);
    }

    private void updateDefaultPosition(int scaledWidth) {
        if (!positionInitialized) {
            float scale = getRenderHudScale();
            super.setHudX(Math.max(0.0F, scaledWidth / 2 * guiScale() - baseWidth * scale / 2));
            super.setHudY(nativeY * guiScale());
        }
    }

    private void measureName(MinecraftClient client, int index, Text name) {
        if (measuredNames[index] != name) {
            measuredNames[index] = name;
            measuredWidths[index] = client.textRenderer.getWidth(name);
        }
        baseWidth = Math.max(baseWidth, (measuredWidths[index] + 3) & ~1);
    }

    public int getNameWidth(TextRenderer renderer, StringVisitable name) {
        for (int index = 0; index < layoutRows; index++) {
            if (measuredNames[index] == name) return measuredWidths[index];
        }
        return renderer.getWidth(name);
    }

    public void invalidateNameWidths() {
        Arrays.fill(measuredNames, null);
        layoutDirty = true;
    }

    public void applyTransform(DrawContext context) {
        float guiScale = guiScale();
        context.getMatrices().translate(getHudX() / guiScale, getHudY() / guiScale, 0.0F);
        context.getMatrices().scale(getHudScale(), getHudScale(), 1.0F);
        context.getMatrices().translate(-nativeX, -nativeY, 0.0F);
    }

    public void beginFrame(long nowNanos) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean rainbow = isEnabled() && renderHealth.isValue() && colorMode.isSelected("Rainbow")
                && (liveBars || client != null && client.currentScreen instanceof ChatScreen);
        if (rainbow) {
            if (lastHueNanos >= 0) {
                rainbowHue = advanceRainbowHue(rainbowHue, nowNanos - lastHueNanos, rainbowSpeed.getValue());
            }
            lastHueNanos = nowNanos;
            activeFillColor = 0xFF000000 | MathHelper.hsvToRgb(rainbowHue, 1.0F, 1.0F);
        } else {
            lastHueNanos = -1;
            activeFillColor = fillColor.getColor() | 0xFF000000;
        }
    }

    static float rainbowCycleSeconds(float speed) {
        float value = MathHelper.clamp(speed, 0.1F, 1.0F);
        return value <= 0.5F ? MathHelper.lerp((value - 0.1F) / 0.4F, 20.0F, 5.0F)
                : MathHelper.lerp((value - 0.5F) / 0.5F, 5.0F, 1.0F);
    }

    static float advanceRainbowHue(float hue, long elapsedNanos, float speed) {
        return (float) ((hue + Math.max(0L, elapsedNanos) / (1_000_000_000.0 * rainbowCycleSeconds(speed))) % 1.0);
    }

    /** Only Exordium calls this; unchanged Custom bars keep its normal cache. */
    public boolean shouldRefreshExordium(long nowNanos) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean enabled = isEnabled();
        boolean health = renderHealth.isValue();
        boolean text = renderText.isValue();
        boolean rainbow = colorMode.isSelected("Rainbow");
        boolean chat = client != null && client.currentScreen instanceof ChatScreen;
        float x = getHudX(), y = getHudY(), scale = getHudScale(), guiScale = guiScale();
        int customColor = fillColor.getColor();
        float speed = rainbowSpeed.getValue();
        boolean changed = !cacheInitialized || enabled != lastEnabled || health != lastHealth || text != lastText
                || rainbow != lastRainbow || chat != lastChat || x != lastX || y != lastY || scale != lastScale
                || guiScale != lastGuiScale || customColor != lastCustomColor || speed != lastRainbowSpeed;
        boolean animate = enabled && health && liveBars && rainbow && nowNanos - lastCacheNanos >= RAINBOW_CACHE_INTERVAL;
        cacheInitialized = true;
        lastEnabled = enabled;
        lastHealth = health;
        lastText = text;
        lastRainbow = rainbow;
        lastChat = chat;
        lastX = x;
        lastY = y;
        lastScale = scale;
        lastGuiScale = guiScale;
        lastCustomColor = customColor;
        lastRainbowSpeed = speed;
        if (changed || animate) lastCacheNanos = nowNanos;
        return changed || animate;
    }

    @Override
    public void setHudX(float x) {
        if (x != super.getHudX()) positionInitialized = true;
        super.setHudX(x);
    }

    @Override
    public void setHudY(float y) {
        if (y != super.getHudY()) positionInitialized = true;
        super.setHudY(y);
    }

    @Override
    public void setHudPosition(float x, float y, int width, int height) {
        positionInitialized = true;
        super.setHudPosition(x, y, width, height);
    }

    @Override
    public void setHudPositionRatio(float x, float y) {
        positionInitialized = true;
        super.setHudPositionRatio(x, y);
    }

    @Override
    public void resetHudTransform() {
        super.resetHudTransform();
        positionInitialized = false;
    }

    public boolean isHudPositionAutomatic() {
        return !positionInitialized;
    }

    public void resetAutomaticPosition() {
        positionInitialized = false;
    }

    /** Both config formats used the same legacy setting keys. New HUD data wins. */
    public static JsonObject migrateLegacyData(JsonObject source) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client == null || client.getWindow() == null ? 0 : client.getWindow().getWidth();
        int height = client == null || client.getWindow() == null ? 0 : client.getWindow().getHeight();
        return migrateLegacyData(source, width, height, guiScale());
    }

    static JsonObject migrateLegacyData(JsonObject source, int width, int height, float guiScale) {
        boolean nested = source.has("settings") && source.get("settings").isJsonObject();
        JsonObject oldSettings = nested ? source.getAsJsonObject("settings") : source;
        if (!oldSettings.has("Scale") && !oldSettings.has("Y Offset") && !oldSettings.has("Hide Bossbar")) return source;
        JsonObject data = source.deepCopy();
        JsonObject settings = nested ? data.getAsJsonObject("settings") : data;
        float scale = MathHelper.clamp(legacyNumber(settings.get("Scale"), 1.0F), 0.5F, MAX_HUD_SCALE);
        if (!data.has("hud_scale")) data.addProperty("hud_scale", scale);
        else scale = MathHelper.clamp(legacyNumber(data.get("hud_scale"), scale), 0.5F, MAX_HUD_SCALE);
        boolean hasPosition = data.has("hud_x_ratio") || data.has("hud_y_ratio") || data.has("hud_x") || data.has("hud_y")
                || data.has("hud_ref_w") || data.has("hud_ref_h") || data.has("hud_auto_position");
        if (!hasPosition && width > 0 && height > 0) {
            float offset = legacyNumber(settings.get("Y Offset"), 0.0F);
            data.addProperty("hud_x", width * 0.5F - 91.0F * scale * guiScale);
            data.addProperty("hud_y", (12.0F * scale + offset) * guiScale);
            data.addProperty("hud_ref_w", width);
            data.addProperty("hud_ref_h", height);
            data.addProperty("hud_auto_position", false);
        }
        JsonElement hide = settings.get("Hide Bossbar");
        if (hide != null && hide.isJsonPrimitive() && hide.getAsJsonPrimitive().isBoolean()) {
            boolean hidden = hide.getAsBoolean();
            if (!settings.has("Render Health")) settings.addProperty("Render Health", !hidden);
            if (hidden && !settings.has("Render Text")) settings.addProperty("Render Text", false);
        }
        settings.remove("Scale");
        settings.remove("Y Offset");
        settings.remove("Hide Bossbar");
        return data;
    }

    private static float legacyNumber(JsonElement value, float fallback) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        float number = value.getAsFloat();
        return Float.isFinite(number) ? number : fallback;
    }

    private static float guiScale() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.getWindow() == null ? 1.0F : (float) client.getWindow().getScaleFactor();
    }
}

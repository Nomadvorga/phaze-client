package vorga.phazeclient.api.feature.module.setting.implement;

import lombok.Getter;
import lombok.Setter;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.base.util.math.MathUtil;

import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static vorga.phazeclient.base.util.math.MathUtil.*;

@Getter
@Setter
public class ColorSetting extends Setting {
    private static final int[] DEFAULT_PRESETS = {
            0xFF95a5a6, 0xFFe91e63, 0xFFe74c3c, 0xFFe67e22, 0xFFf1c40f, 0xFF2ecc71,
            0xFF1abc9c, 0xFF3574f0, 0xFF3498db, 0xFF9b59b6, 0xFFa582ed,
            0xFF778889, 0xFFba1850, 0xFFba3d30, 0xFFba651b, 0xFFc19d0c, 0xFF25a35a,
            0xFF15967d, 0xFF2b5ec0, 0xFF2a7ab0, 0xFF7d4791, 0xFF8568be,
            0xFF5a6566, 0xFF8c123c, 0xFF8c2e24, 0xFF8c4c14, 0xFF91760a, 0xFF1c7a44,
            0xFF10715e, 0xFF204790, 0xFF1f5c85, 0xFF5e366d, 0xFF64508f
    };

    private float hue = 0,
            saturation = 1,
            brightness = 1,
            alpha = 1;

    private int[] presets;
    private Integer defaultColor;
    private Consumer<Integer> onChangeCallback;
    /** When true, the alpha picker row is hidden in the GUI and
     *  alpha is locked to 1. Used by modules where alpha is
     *  irrelevant (e.g. fog colour, where the engine takes a flat
     *  RGB and writes alpha itself). Set via {@link #noAlpha()}. */
    private boolean noAlpha = false;

    public ColorSetting(String name, String description) {
        super(name, description);
        this.presets = DEFAULT_PRESETS;
    }

    public ColorSetting value(int value) {
        if (defaultColor == null) {
            defaultColor = value;
        }
        setColorInternal(value);
        return this;
    }

    public ColorSetting presets(int... presets) {
        this.presets = presets;
        return this;
    }

    public ColorSetting visible(Supplier<Boolean> visible) {
        setVisible(visible);
        return this;
    }

    public ColorSetting onChange(Consumer<Integer> callback) {
        this.onChangeCallback = callback;
        return this;
    }

    /** Hide the alpha picker row and lock alpha to full. */
    public ColorSetting noAlpha() {
        this.noAlpha = true;
        this.alpha = 1.0F;
        return this;
    }

    public boolean isNoAlpha() {
        return noAlpha;
    }

    public int getColor() {
        return (getColorWithAlpha() & 0x00FFFFFF) | (Math.round(alpha * 255) << 24);
    }

    public int getColorWithAlpha() {
        return HSBtoRGB(hue, saturation, brightness);
    }

    public ColorSetting setColor(int color) {
        if (defaultColor == null) {
            defaultColor = color;
        }
        setColorInternal(color);
        return this;
    }

    public void setHue(float hue) {
        this.hue = hue;
        notifyColorChange();
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
        notifyColorChange();
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
        notifyColorChange();
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
        notifyColorChange();
    }

    private void notifyColorChange() {
        notifyChange();
        if (onChangeCallback != null) {
            onChangeCallback.accept(getColor());
        }
    }

    private void setColorInternal(int color) {
        float[] hsb = RGBtoHSB(
                getRed(color),
                getGreen(color),
                getBlue(color)
        );

        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        alpha = (MathUtil.getAlpha(color) / 255f);

        notifyColorChange();
    }

    @Override
    public boolean isModified() {
        if (defaultColor == null) {
            return false;
        }
        return getColor() != defaultColor;
    }

    @Override
    public void reset() {
        if (defaultColor != null) {
            setColorInternal(defaultColor);
        }
    }

    private static int getRed(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int getGreen(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int getBlue(int color) {
        return color & 0xFF;
    }

    private static float[] RGBtoHSB(int r, int g, int b) {
        float[] hsb = new float[3];

        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        hsb[2] = max;

        if (max != 0) {
            hsb[1] = delta / max;
        } else {
            hsb[1] = 0;
        }

        if (delta == 0) {
            hsb[0] = 0;
        } else {
            if (rf == max) {
                hsb[0] = (gf - bf) / delta;
            } else if (gf == max) {
                hsb[0] = 2 + (bf - rf) / delta;
            } else {
                hsb[0] = 4 + (rf - gf) / delta;
            }
            hsb[0] /= 6f;
            if (hsb[0] < 0) {
                hsb[0] += 1f;
            }
        }

        return hsb;
    }

    private static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;

        if (saturation == 0) {
            r = g = b = (int) (brightness * 255f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1f - saturation);
            float q = brightness * (1f - saturation * f);
            float t = brightness * (1f - (saturation * (1f - f)));

            switch ((int) h) {
                case 0:
                    r = (int) (brightness * 255f + 0.5f);
                    g = (int) (t * 255f + 0.5f);
                    b = (int) (p * 255f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255f + 0.5f);
                    g = (int) (brightness * 255f + 0.5f);
                    b = (int) (p * 255f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255f + 0.5f);
                    g = (int) (brightness * 255f + 0.5f);
                    b = (int) (t * 255f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255f + 0.5f);
                    g = (int) (q * 255f + 0.5f);
                    b = (int) (brightness * 255f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255f + 0.5f);
                    g = (int) (p * 255f + 0.5f);
                    b = (int) (brightness * 255f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255f + 0.5f);
                    g = (int) (p * 255f + 0.5f);
                    b = (int) (q * 255f + 0.5f);
                    break;
            }
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
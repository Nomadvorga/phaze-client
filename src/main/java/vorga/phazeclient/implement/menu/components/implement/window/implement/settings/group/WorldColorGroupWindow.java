package vorga.phazeclient.implement.menu.components.implement.window.implement.settings.group;

import vorga.phazeclient.base.util.render.GuiMatrix;

import org.joml.Matrix3x2fStack;

import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.feature.module.setting.SettingComponentAdder;
import vorga.phazeclient.api.feature.module.setting.implement.GroupSetting;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Blur;
import vorga.phazeclient.base.util.color.ColorUtil;
import vorga.phazeclient.base.util.math.MathUtil;
import vorga.phazeclient.base.util.render.ScissorManager;
import vorga.phazeclient.core.Main;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.implement.menu.MenuStyle;
import vorga.phazeclient.implement.menu.components.AbstractComponent;
import vorga.phazeclient.implement.menu.components.implement.settings.AbstractSettingComponent;
import vorga.phazeclient.implement.menu.components.implement.window.AbstractWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Getter
public final class WorldColorGroupWindow extends AbstractWindow {
    private static final float TITLE_SIZE = 7.0F;
    private static final float HEADER_HEIGHT = 24.0F;
    private static final float CONTENT_TOP = 26.0F;
    private static final float CONTENT_BOTTOM = 4.0F;
    private static final float MIN_WINDOW_HEIGHT = 48.0F;

    private final List<AbstractSettingComponent> components = new ArrayList<>();
    private final GroupSetting setting;
    private int cachedComponentHeight = 0;

    public WorldColorGroupWindow(GroupSetting setting) {
        this.setting = setting;
        new SettingComponentAdder().addSettingComponent(setting.getSubSettings(), components);
        getScaleAnimation().setMs(200);
    }

    @Override
    protected void drawWindow(DrawContext context, int mouseX, int mouseY, float delta) {
        Matrix3x2fStack matrices = context.getMatrices();
        Matrix4f positionMatrix = GuiMatrix.mat4(matrices);
        ScissorManager scissorManager = Main.getInstance().getScissorManager();

        renderWindowBlur(matrices);

        cachedComponentHeight = calculateComponentHeight();
        float desiredHeight = CONTENT_TOP + cachedComponentHeight + CONTENT_BOTTOM;
        float viewportHeight = Math.max(0.0F, height - CONTENT_TOP - CONTENT_BOTTOM);
        boolean clip = desiredHeight > height;

        int outline = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.BORDER_LIGHT, 0xFFFFFFFF, 0.06F), globalAlpha * 0.96F);
        int panelFill = MenuStyle.withAlpha(MenuStyle.PANEL_BG, globalAlpha * 0.94F);
        int panelInner = MenuStyle.withAlpha(MenuStyle.mix(MenuStyle.PANEL_BG_SOFT, MenuStyle.PANEL_CONTENT, 0.42F), globalAlpha * 0.98F);

        rectangle.render(ShapeProperties.create(matrices, x, y, width, height)
                .round(9.0F)
                .softness(1.1F)
                .thickness(1.15F)
                .outlineColor(outline)
                .color(panelFill)
                .build());

        rectangle.render(ShapeProperties.create(matrices, x + 1.0F, y + 1.0F, width - 2.0F, height - 2.0F)
                .round(8.0F)
                .thickness(0.0F)
                .color(panelInner)
                .build());

        String title = setting.getLocalizedName();
        MsdfRenderer.renderText(
                MsdfFonts.bold(),
                title,
                TITLE_SIZE,
                MenuStyle.withAlpha(MenuStyle.TEXT_PRIMARY, globalAlpha),
                positionMatrix,
                MenuStyle.centerMsdfTextX(MsdfFonts.bold(), title, TITLE_SIZE, x, width),
                MenuStyle.centerMsdfTextY(TITLE_SIZE, y, HEADER_HEIGHT),
                0.0F
        );

        float contentY = (float) (y + CONTENT_TOP + smoothedScroll);
        if (clip) {
            scissorManager.push(GuiMatrix.mat4(matrices), x + 4.0F, y + CONTENT_TOP - 2.0F, width - 8.0F, viewportHeight + 2.0F);
        }

        float offset = 0.0F;
        int totalHeight = 0;
        for (int i = components.size() - 1; i >= 0; i--) {
            AbstractSettingComponent component = components.get(i);
            Supplier<Boolean> visible = component.getSetting().getVisible();
            if (visible != null && !visible.get()) {
                continue;
            }

            component.x = x + 6.0F;
            component.y = contentY + offset;
            component.width = width - 12.0F;
            component.globalAlpha = this.globalAlpha;
            component.setExternalAlpha(this.globalAlpha);
            component.render(context, mouseX, mouseY, delta);
            component.setExternalAlpha(1.0F);

            offset += component.height;
            totalHeight += (int) component.height;
        }

        if (clip) {
            scissorManager.pop();
        }

        int maxScroll = (int) Math.max(0, totalHeight - viewportHeight);
        scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        smoothedScroll = MathHelper.lerp(0.1F, smoothedScroll, scroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        draggable(MathUtil.isHovered(mouseX, mouseY, x, y, width, HEADER_HEIGHT));
        if (!isHovered(mouseX, mouseY)) {
            return false;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (AbstractSettingComponent component : components) {
            if (component.isHover(mouseX, mouseY) && component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        for (AbstractComponent component : components) {
            if (component.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHover(mouseX, mouseY);
    }

    @Override
    public boolean isHovered(double mouseX, double mouseY) {
        for (AbstractComponent component : components) {
            if (component.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHovered(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        components.forEach(component -> component.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        boolean scrolled = isHovered(mouseX, mouseY) && cachedComponentHeight + CONTENT_TOP + CONTENT_BOTTOM > height;
        if (scrolled) {
            scroll += amount * 20.0F;
        }
        components.forEach(component -> component.mouseScrolled(mouseX, mouseY, amount));
        return scrolled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        components.forEach(component -> component.keyPressed(keyCode, scanCode, modifiers));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        components.forEach(component -> component.charTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    private int calculateComponentHeight() {
        float total = 0.0F;
        for (AbstractSettingComponent component : components) {
            Supplier<Boolean> visible = component.getSetting().getVisible();
            if (visible != null && !visible.get()) {
                continue;
            }
            total += component.height;
        }
        return (int) total;
    }

    // 1.21.11: the GUI pose is a 2D Matrix3x2fStack, which is what ShapeProperties.create now takes.
    private void renderWindowBlur(Matrix3x2fc matrices) {
        float blurRadius = Theme.getInstance().getMenuBlurRadius();
        if (blurRadius <= 0.0F) {
            return;
        }

        Blur.INSTANCE.renderGaussianOverlay(ShapeProperties.create(matrices, x, y, width, height)
                .round(9.0F)
                .softness(1.1F)
                .quality(blurRadius * 2.0F)
                .color(MenuStyle.withAlpha(0xFFFFFFFF, globalAlpha))
                .build());
    }
}

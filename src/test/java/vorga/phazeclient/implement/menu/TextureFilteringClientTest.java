package vorga.phazeclient.implement.menu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import vorga.phazeclient.api.system.hud.Render2DState;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.implement.Image;
import vorga.phazeclient.implement.menu.components.implement.other.BackgroundComponent;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.SettingColorPickerWindow;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component.AlphaComponent;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.color.component.SaturationComponent;
import vorga.phazeclient.implement.menu.components.implement.window.implement.settings.itempicker.ItemPickerColorWindow;
import vorga.phazeclient.implement.menu.loading.BootstrapLoadingRenderer;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL32C.*;

/** Warm resource draws must never change a previously bound world/HUD texture's sampler. */
public final class TextureFilteringClientTest implements FabricClientGameTest {
    private static final int ATLAS_UNIT = GL_TEXTURE0 + BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX;
    private static final String HUE = "textures/color_picker/hue.png";
    private static final String TRIANGLE = "textures/color_picker/triangle.png";
    private static final List<String> UI_TEXTURES = List.of(
            "textures/back_arrow.png", "textures/settings.png", "textures/trash.png",
            "textures/reset.png", "textures/file.png", "textures/file_import.png",
            "textures/edit.png", "textures/share.png", HUE, TRIANGLE,
            "textures/color_picker/alpha.png", "phaze:textures/menu/cross.png");
    private int checks;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        context.runOnClient(this::verifyDraws);
        var reload = context.computeOnClient(MinecraftClient::reloadResources);
        context.waitFor(client -> reload.isDone() && client.getOverlay() == null);
        check(!reload.isCompletedExceptionally(), "real resource reload succeeds");
        context.runOnClient(this::verifyDraws);
        System.out.println("Texture filtering GL checks passed: " + checks + " (including real resource reload)");
    }

    private void verifyDraws(MinecraftClient client) {
        try (Render2DState original = Render2DState.capture()) {
            int savedActive = glGetInteger(GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(ATLAS_UNIT);
            int savedAtlasBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(GL_TEXTURE0);
            SimpleFramebuffer target = new SimpleFramebuffer(64, 64, false);
            SimpleFramebuffer hud = new SimpleFramebuffer(8, 8, false);
            try {
                hud.setTexFilter(GL_NEAREST);
                target.beginWrite(true);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 64, 64, 0, -100, 100), ProjectionType.ORTHOGRAPHIC);
                RenderSystem.getModelViewStack().identity();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.disableDepthTest();
                RenderSystem.disableCull();
                RenderSystem.disableScissor();
                MatrixStack matrices = new MatrixStack();
                for (String texture : UI_TEXTURES) client.getTextureManager().getTexture(Identifier.of(texture));
                UiMsdfIconAtlas.warmup();
                List<Draw> draws = draws(matrices);
                // The dangerous pattern often looks safe on a cold load, because the
                // texture loader happens to bind the image. Exercise already-loaded images.
                for (Draw draw : draws) {
                    prime(hud.getColorAttachment(), GL_TEXTURE0);
                    draw.action.run();
                }
                hud.setTexFilter(GL_NEAREST);
                for (int min : new int[]{GL_NEAREST, GL_NEAREST_MIPMAP_LINEAR}) {
                    RenderSystem.activeTexture(GL_TEXTURE0);
                    RenderSystem.bindTexture(hud.getColorAttachment());
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, min);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                    Sampler expected = sampler(hud.getColorAttachment());
                    for (int unit : new int[]{GL_TEXTURE0, GL_TEXTURE1, GL_TEXTURE2, ATLAS_UNIT}) {
                        for (int repeat = 0; repeat < 4; repeat++) {
                            for (Draw draw : draws) {
                                prime(hud.getColorAttachment(), unit);
                                draw.action.run();
                                check(expected.equals(sampler(hud.getColorAttachment())),
                                        draw.name + " preserves cached HUD filters with active unit " + (unit - GL_TEXTURE0));
                                verifyBindingCache();
                            }
                        }
                    }
                }
                verifyWorldAtlas(client, draws);
                verifyLoading(hud.getColorAttachment());
                for (String texture : UI_TEXTURES) {
                    int id = client.getTextureManager().getTexture(Identifier.of(texture)).getGlId();
                    Sampler actual = sampler(id);
                    check(actual.min == GL_LINEAR && actual.mag == GL_LINEAR,
                            "UI image keeps smooth filtering from load metadata: " + texture);
                }
                check(glGetError() == GL_NO_ERROR, "UI/loading draws leave no GL errors");
            } finally {
                BootstrapLoadingRenderer.close();
                target.delete();
                hud.delete();
                RenderSystem.activeTexture(ATLAS_UNIT);
                RenderSystem.bindTexture(savedAtlasBinding);
                RenderSystem.activeTexture(savedActive);
            }
        }
    }

    private List<Draw> draws(MatrixStack matrices) {
        List<Draw> result = new ArrayList<>();
        Image raw = new Image().setTexture("textures/back_arrow.png");
        Image msdf = new Image().setTexture("textures/settings.png");
        var shape = ShapeProperties.create(matrices, 2, 2, 12, 12).build();
        result.add(new Draw("Image raster fallback", () -> raw.render(shape)));
        result.add(new Draw("Image MSDF icon", () -> msdf.render(shape)));
        for (Class<?> type : List.of(AlphaComponent.class, SaturationComponent.class)) {
            Method strip = method(type, "renderVerticalGradientStrip", MatrixStack.class, String.class,
                    float.class, float.class, float.class, float.class, int.class);
            String texture = type == AlphaComponent.class ? "textures/color_picker/alpha.png" : HUE;
            result.add(new Draw(type.getSimpleName() + " gradient", () -> invoke(strip, null, matrices, texture, 2F, 2F, 6F, 30F, -1)));
            Method triangle = method(type, "renderLeftPointingTriangle", MatrixStack.class, String.class,
                    float.class, float.class, float.class, float.class, int.class);
            result.add(new Draw(type.getSimpleName() + " triangle", () -> invoke(triangle, null, matrices, TRIANGLE, 2F, 2F, 6F, 6F, -1)));
        }
        for (Class<?> type : List.of(SettingColorPickerWindow.class, ItemPickerColorWindow.class)) {
            Method hue = method(type, "renderHorizontalHueTexture", MatrixStack.class,
                    float.class, float.class, float.class, float.class, int.class);
            result.add(new Draw(type.getSimpleName() + " hue", () -> invoke(hue, null, matrices, 2F, 2F, 30F, 6F, -1)));
        }
        Method cross = method(BackgroundComponent.class, "renderConfigCross", MatrixStack.class,
                float.class, float.class, float.class, int.class);
        BackgroundComponent background = new BackgroundComponent();
        result.add(new Draw("configuration cross", () -> invoke(cross, background, matrices, 2F, 2F, 10F, -1)));
        return result;
    }

    private void verifyWorldAtlas(MinecraftClient client, List<Draw> draws) {
        int atlas = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).getGlId();
        Sampler expected = sampler(atlas);
        try {
            for (Draw draw : draws) {
                prime(atlas, GL_TEXTURE0);
                draw.action.run();
                check(expected.equals(sampler(atlas)), draw.name + " preserves the real block/item atlas sampler");
            }
        } finally {
            // A failing baseline must not leave the fixture's real Minecraft atlas corrupted.
            RenderSystem.activeTexture(GL_TEXTURE0);
            RenderSystem.bindTexture(atlas);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, expected.min);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, expected.mag);
        }
    }

    private void verifyLoading(int sentinel) {
        for (int cycle = 0; cycle < 3; cycle++) {
            BootstrapLoadingRenderer.close();
            for (int unit : new int[]{GL_TEXTURE0, ATLAS_UNIT}) {
                prime(sentinel, ATLAS_UNIT);
                RenderSystem.activeTexture(unit);
                Sampler expected = sampler(sentinel);
                int selected0 = RenderSystem.getShaderTexture(0);
                int selected7 = RenderSystem.getShaderTexture(BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX);
                BootstrapLoadingRenderer.render(64, 64, 0.5F, 1);
                check(glGetInteger(GL_ACTIVE_TEXTURE) == unit, "loading restores the active texture unit");
                RenderSystem.activeTexture(ATLAS_UNIT);
                check(glGetInteger(GL_TEXTURE_BINDING_2D) == sentinel, "loading restores reserved-unit binding");
                check(expected.equals(sampler(sentinel)), "loading init/draw never changes another texture's sampler");
                check(RenderSystem.getShaderTexture(0) == selected0
                                && RenderSystem.getShaderTexture(BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX) == selected7,
                        "loading preserves selected shader samplers (not just hardware bindings)");
                verifyBindingCache();
            }
            // Deliberately leave an owned atlas bound on two units during disposal.
            // Raw glDeleteTextures would unbind it in GL but leave a stale name in the cache.
            Object renderer = field(BootstrapLoadingRenderer.class, "instance", null);
            Object font = field(renderer.getClass(), "medium", renderer);
            int owned = (int) field(font.getClass(), "texture", font);
            prime(owned, GL_TEXTURE2);
            prime(owned, ATLAS_UNIT);
            BootstrapLoadingRenderer.close();
            BootstrapLoadingRenderer.close();
            check(!glIsTexture(owned), "bootstrap disposal releases its owned atlas");
            for (int index : new int[]{2, BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX}) {
                Object[] textures = (Object[]) field(GlStateManager.class, "TEXTURES", null);
                check((int) field(textures[index].getClass(), "boundTexture", textures[index]) != owned,
                        "bootstrap disposal invalidates GlStateManager's deleted-texture cache on unit " + index);
            }
            prime(sentinel, ATLAS_UNIT);
        }
    }

    private static void prime(int texture, int unit) {
        RenderSystem.activeTexture(unit);
        RenderSystem.bindTexture(texture);
    }

    private static Sampler sampler(int texture) {
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        RenderSystem.activeTexture(GL_TEXTURE0);
        int previous = glGetInteger(GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(texture);
        Sampler result = new Sampler(glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER),
                glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER));
        RenderSystem.bindTexture(previous);
        RenderSystem.activeTexture(active);
        return result;
    }

    private void verifyBindingCache() {
        int active = glGetInteger(GL_ACTIVE_TEXTURE);
        check(GlStateManager._getActiveTexture() == active, "cached and hardware active units agree");
        Object[] textures = (Object[]) field(GlStateManager.class, "TEXTURES", null);
        for (int index : new int[]{0, 1, 2, BootstrapLoadingRenderer.ATLAS_TEXTURE_INDEX}) {
            RenderSystem.activeTexture(GL_TEXTURE0 + index);
            int cached = (int) field(textures[index].getClass(), "boundTexture", textures[index]);
            int hardware = glGetInteger(GL_TEXTURE_BINDING_2D);
            check(cached == hardware || (cached == -1 && hardware == 0),
                    "cached and hardware bindings agree (or are invalidated after deletion) on unit " + index);
        }
        RenderSystem.activeTexture(active);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            Method method = type.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect texture draw " + name, e);
        }
    }

    private static void invoke(Method method, Object receiver, Object... arguments) {
        try {
            method.invoke(receiver, arguments);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Texture draw failed: " + method, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object field(Class<?> type, String name, Object receiver) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(receiver);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot inspect texture state " + name, e);
        }
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private record Draw(String name, Runnable action) { }
    private record Sampler(int min, int mag) { }
}

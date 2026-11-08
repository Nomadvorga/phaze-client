package vorga.phazeclient.api.system.font.msdf;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.implement.menu.font.GuiFonts;
import vorga.phazeclient.implement.menu.font.GuiTextRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in render-thread regression checks. Root owns Fabric client-test entrypoint registration. */
public final class MsdfTextClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        Screen saved = context.computeOnClient(client -> client.currentScreen);
        Fixture fixture = context.computeOnClient(client -> new Fixture());
        try {
            context.runOnClient(client -> client.setScreen(fixture));
            context.waitFor(client -> fixture.finished);
            context.runOnClient(client -> {
                if (fixture.failure != null) throw new AssertionError("MSDF text client regression", fixture.failure);
            });
        } finally {
            context.runOnClient(client -> client.setScreen(saved));
        }
    }

    private static final class Fixture extends Screen {
        private boolean finished;
        private Throwable failure;

        private Fixture() { super(Text.literal("MSDF text state and recovery fixture")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (finished) return;
            context.draw();
            try (TextRenderState outer = new TextRenderState(true)) {
                MsdfFont font = MsdfFonts.medium();
                Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
                check(!MsdfRenderer.isUsingFallback(), "fixture requires the real shader initially");
                for (boolean blend : new boolean[]{false, true}) {
                    for (boolean cull : new boolean[]{false, true}) {
                        seed(font, blend, cull);
                        State before = State.capture();
                        MsdfRenderer.renderText(font, "AV Цвет HUD", 7, 0xAD12ABEF, matrix, 20, 20, 0);
                        check(!MsdfRenderer.isUsingFallback(), "normal rendering uses MSDF");
                        check(before.equals(State.capture()), "successful draw restores incoming GL/cache state");
                    }
                }
                earlyFailure(font, matrix);
                partialGlyphFailure(font, matrix);
                GuiTextRenderer explicit = GuiFonts.atSize(7, Fonts.Type.INTER_DEFAULT);
                var legacy = GuiFonts.getSize(14, Fonts.Type.INTER_DEFAULT);
                check(explicit == legacy, "explicit and legacy sizes share their cached adapter");
                near(explicit.measure("Цвет HUD").width(), legacy.getStringWidth("Цвет HUD"), "width compatibility");
                near(explicit.measure("A\nB").height() * 2, legacy.getStringHeight("A\nB"), "doubled legacy height is isolated");
                State before = State.capture();
                MsdfRenderer.renderText(font, "ignored", Float.NaN, -1, matrix, 0, 0, 0);
                check(before.equals(State.capture()), "invalid size is a safe no-op");
                System.out.println("MSDF text state, partial-buffer cleanup and reload recovery PASSED");
            } catch (Throwable error) {
                failure = error;
            } finally {
                MsdfFonts.onResourcesReloaded();
                finished = true;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void earlyFailure(MsdfFont font, Matrix4f matrix) throws Exception {
        ShaderProgram shader = RenderSystem.setShader(MsdfRenderer.MSDF_FONT_SHADER_KEY);
        check(shader != null, "MSDF shader is loaded");
        Map<String, GlUniform> uniforms = (Map<String, GlUniform>) field(ShaderProgram.class, "uniformsByName").get(shader);
        GlUniform range = uniforms.remove("Range");
        check(range != null, "fixture removed an existing required uniform");
        try {
            seed(font, false, false);
            State before = State.capture();
            MsdfRenderer.renderText(font, "AV Цвет", 14, 0xCCFFFFFF, matrix, 20, 40, 0);
            check(MsdfRenderer.isUsingFallback(), "missing uniform selects fallback");
            check(MsdfRenderer.getLastFailure().getMessage().contains("Range"), "failure diagnostic names the missing uniform");
            check(before.equals(State.capture()), "failure and immediately flushed vanilla fallback restore state");
            Throwable diagnostic = MsdfRenderer.getLastFailure();
            MsdfRenderer.renderText(font, "AV Цвет", 7, -1, matrix, 20, 60, 0);
            check(MsdfRenderer.getLastFailure() == diagnostic, "subsequent frames retain one diagnostic without retry/log spam");
            check(before.equals(State.capture()), "latched fallback restores state too");
        } finally {
            uniforms.put("Range", range);
            MsdfFonts.onResourcesReloaded();
        }
        check(!MsdfRenderer.isUsingFallback() && MsdfRenderer.getLastFailure() == null, "reload re-arms failed renderer");
        MsdfRenderer.renderText(font, "Recovered", 7, -1, matrix, 20, 80, 0);
        check(!MsdfRenderer.isUsingFallback(), "MSDF succeeds after recovery");
    }

    @SuppressWarnings("unchecked")
    private static void partialGlyphFailure(MsdfFont font, Matrix4f matrix) throws Exception {
        Field glyphField = field(MsdfFont.class, "glyphs");
        Int2ObjectMap<MsdfGlyph> original = (Int2ObjectMap<MsdfGlyph>) glyphField.get(font);
        Int2ObjectOpenHashMap<MsdfGlyph> injected = new Int2ObjectOpenHashMap<>(original) {
            private int lookups;
            @Override
            public MsdfGlyph get(int codepoint) {
                if (++lookups == 2) throw new IllegalStateException("Expected fixture failure after first glyph");
                return super.get(codepoint);
            }
        };
        glyphField.set(font, injected);
        // Latin and Cyrillic glyphs are served from a direct array; empty it so lookups reach the map.
        MsdfGlyph[] direct = (MsdfGlyph[]) field(MsdfFont.class, "directGlyphs").get(font);
        MsdfGlyph[] directOriginal = direct.clone();
        java.util.Arrays.fill(direct, null);
        try {
            seed(font, true, true);
            State before = State.capture();
            MsdfRenderer.renderText(font, "AB", 9, -1, matrix, 20, 100, 0);
            check(MsdfRenderer.isUsingFallback(), "partial glyph failure selects fallback");
            check(before.equals(State.capture()), "partial glyph failure/fallback restores all incoming state");
            BufferAllocator allocator = (BufferAllocator) field(MsdfRenderer.class, "glyphAllocator").get(null);
            check(field(BufferAllocator.class, "offset").getInt(allocator) == 0, "unfinished glyph allocation was fully returned");
            check(field(BufferAllocator.class, "refCount").getInt(allocator) == 0, "no BuiltBuffer references leaked");
        } finally {
            glyphField.set(font, original);
            System.arraycopy(directOriginal, 0, direct, 0, direct.length);
            MsdfFonts.onResourcesReloaded();
        }
        check(field(MsdfRenderer.class, "glyphAllocator").get(null) == null, "reload releases owned allocator");
        check(!field(MsdfFont.class, "filterApplied").getBoolean(font), "reload re-arms atlas filtering");
        MsdfRenderer.renderText(font, "AB", 9, -1, matrix, 20, 120, 0);
        check(!MsdfRenderer.isUsingFallback(), "subsequent MSDF draw is not poisoned by the unfinished builder");
    }

    private static void seed(MsdfFont font, boolean blend, boolean cull) {
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_DST_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_GEQUAL);
        RenderSystem.colorMask(true, false, true, false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.setShaderColor(0.25F, 0.5F, 0.75F, 0.6F);
        RenderSystem.setShaderTexture(0, font.getTextureId());
        RenderSystem.setShaderTexture(2, font.getTextureId());
        for (int unit = 0; unit <= 2; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(font.getTextureId());
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
    }

    private record State(ShaderProgram shader, List<Integer> gl, List<Float> color) {
        private static State capture() {
            List<Integer> values = new ArrayList<>();
            for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_CULL_FACE, GL11.GL_DEPTH_TEST, GL11.GL_SCISSOR_TEST}) {
                values.add(GL11.glIsEnabled(flag) ? 1 : 0);
            }
            for (int parameter : new int[]{GL11.GL_DEPTH_WRITEMASK, GL11.GL_DEPTH_FUNC,
                    GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
                    GL20.GL_CURRENT_PROGRAM, GL30.GL_VERTEX_ARRAY_BINDING, GL15.GL_ARRAY_BUFFER_BINDING,
                    GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING, GL13.GL_ACTIVE_TEXTURE}) {
                values.add(GL11.glGetInteger(parameter));
            }
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            for (int unit = 0; unit <= 2; unit++) {
                values.add(RenderSystem.getShaderTexture(unit));
                GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
                values.add(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
            }
            GlStateManager._activeTexture(active);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) values.add((int) mask.get(i));
            }
            float[] color = RenderSystem.getShaderColor();
            return new State(RenderSystem.getShader(), values, List.of(color[0], color[1], color[2], color[3]));
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package vorga.phazeclient.implement.menu.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Resource-reload-independent loading artwork, including the very first loading frame.
 * Call on the render thread with its OpenGL 3.2 core context current, after flushing any
 * queued GUI work. The currently bound draw framebuffer is used; dimensions are real
 * framebuffer pixels, not scaled GUI units. No Minecraft singleton or shader loader is used.
 * Programs, geometry and bundled font atlases survive resource reloads; call {@link #close()}
 * before destroying the context. A later render after close initializes a fresh cache.
 */
public final class BootstrapLoadingRenderer {
    private static final String SHADERS = "/assets/phaze/shaders/loading/";
    /** Texture-unit index reserved for the bootstrap MSDF atlases. */
    public static final int ATLAS_TEXTURE_INDEX = LoadingGlState.ATLAS_TEXTURE_INDEX;
    private static BootstrapLoadingRenderer instance;

    private final Thread thread = Thread.currentThread();
    private final GLCapabilities context = GL.getCapabilities();
    private Program shapes;
    private Program text;
    private LoadingFont bold;
    private LoadingFont medium;
    private int vao;
    private int vbo;

    private BootstrapLoadingRenderer() { }

    /** Progress and alpha are clamped to [0, 1]; minimized (zero-sized) targets are skipped. */
    public static void render(int framebufferWidth, int framebufferHeight, float progress, float alpha) {
        if (!Float.isFinite(progress) || !Float.isFinite(alpha)) {
            throw new IllegalArgumentException("Bootstrap loading progress and alpha must be finite");
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        alpha = clamp(alpha, 0, 1);
        if (alpha == 0) return;
        if (!GL.getCapabilities().OpenGL32) {
            throw new IllegalStateException("Bootstrap loading renderer requires an OpenGL 3.2 context");
        }
        if (instance != null) instance.checkContext();
        try (LoadingGlState state = new LoadingGlState()) {
            state.prepare(framebufferWidth, framebufferHeight);
            if (instance == null) {
                BootstrapLoadingRenderer renderer = new BootstrapLoadingRenderer();
                try {
                    renderer.initialize();
                    instance = renderer;
                } catch (IOException | RuntimeException | Error exception) {
                    renderer.release();
                    throw new IllegalStateException("Failed to initialize independent Phaze loading renderer", exception);
                }
            }
            instance.draw(framebufferWidth, framebufferHeight, clamp(progress, 0, 1), alpha);
        }
    }

    /** Idempotent. Must run on the owning render thread/context, outside a render call. */
    public static void close() {
        if (instance == null) return;
        instance.checkContext();
        instance.release();
        instance = null;
    }

    private void checkContext() {
        if (thread != Thread.currentThread() || context != GL.getCapabilities()) {
            throw new IllegalStateException("Bootstrap loading renderer must use its owning render thread and GL context");
        }
    }

    private void initialize() throws IOException {
        shapes = Program.create("rounded.fsh", "Color", "Radius", "BorderColor", "BorderWidth");
        text = Program.create("msdf.fsh", "Atlas", "UvRect", "DistanceRange", "AtlasSize", "Alpha");
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        if (vao == 0 || vbo == 0) throw new IOException("Cannot allocate loading VAO/VBO");
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{
                0, 0, 0, 0,  0, 1, 0, 1,  1, 1, 1, 1,
                0, 0, 0, 0,  1, 1, 1, 1,  1, 0, 1, 0
        }, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        bold = LoadingFont.load("bold", "PHAZE");
        medium = LoadingFont.load("medium", "CLIENT");
    }

    private void draw(int width, int height, float progress, float alpha) {
        glBindVertexArray(vao);
        shapes.use(width, height);
        rectangle(0, 0, width, height, 0, 79 / 255.0F, 157 / 255.0F, 118 / 255.0F, alpha, 0, 0);

        // Same responsive proportions/limits as SplashOverlayMixin, in framebuffer coordinates.
        float titleSize = Math.min(clamp(height * 0.16F, 32, 180), width * 0.7F / bold.width);
        float subtitleSize = titleSize * 0.36F;
        float gap = Math.max(2, titleSize * 0.035F);
        float logoHeight = titleSize + gap + subtitleSize;
        float logoTop = height * 0.46F - logoHeight / 2;
        text.use(width, height);
        glUniform1i(text.uniform("Atlas"), LoadingGlState.ATLAS_TEXTURE_INDEX);
        glUniform1f(text.uniform("Alpha"), alpha);
        word(bold, titleSize, width, logoTop, titleSize);
        word(medium, subtitleSize, width, logoTop + titleSize + gap, subtitleSize);

        float barWidth = Math.min(width, Math.max(32, Math.min(width - 48, Math.max(220, width * 0.52F))));
        float barHeight = Math.min(height, clamp(height * 0.009F, 6, 12) + 4.0F);
        float barX = (width - barWidth) / 2;
        float barY = Math.min(height - barHeight, height * 0.82F);
        shapes.use(width, height);
        rectangle(barX, barY, barWidth, barHeight, barHeight / 2,
                1, 1, 1, alpha * 0.20F, alpha * 0.75F, Math.min(1, barHeight / 4));
        if (progress > 0) {
            float fillWidth = barWidth * progress;
            rectangle(barX, barY, fillWidth, barHeight, Math.min(fillWidth, barHeight) / 2,
                    1, 1, 1, alpha, 0, 0);
        }
    }

    private void word(LoadingFont font, float size, int screenWidth, float boxTop, float boxHeight) {
        float x = (screenWidth - font.width * size) / 2 - font.left * size;
        float baseline = boxTop + (boxHeight - font.height * size) / 2 - font.top * size;
        // Do not bypass GlStateManager's texture binding cache. The loading
        // atlas is temporary, while the GL context is shared with Minecraft.
        RenderSystem.activeTexture(LoadingGlState.ATLAS_TEXTURE_UNIT);
        RenderSystem.bindTexture(font.texture());
        glUniform1f(text.uniform("DistanceRange"), font.range);
        glUniform2f(text.uniform("AtlasSize"), font.atlasWidth, font.atlasHeight);
        for (LoadingFont.Glyph glyph : font.glyphs) {
            glUniform4f(text.rect, x + glyph.x() * size, baseline + glyph.y() * size,
                    glyph.width() * size, glyph.height() * size);
            glUniform4f(text.uniform("UvRect"), glyph.u(), glyph.v(), glyph.uw(), glyph.vh());
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
    }

    private void rectangle(float x, float y, float width, float height, float radius,
                           float red, float green, float blue, float alpha, float borderAlpha, float borderWidth) {
        glUniform4f(shapes.rect, x, y, width, height);
        glUniform1f(shapes.uniform("Radius"), radius);
        glUniform4f(shapes.uniform("Color"), red, green, blue, alpha);
        glUniform4f(shapes.uniform("BorderColor"), 1, 1, 1, borderAlpha);
        glUniform1f(shapes.uniform("BorderWidth"), borderWidth);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    private void release() {
        if (bold != null) bold.close();
        if (medium != null) medium.close();
        if (shapes != null) glDeleteProgram(shapes.id);
        if (text != null) glDeleteProgram(text.id);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (vao != 0) glDeleteVertexArrays(vao);
    }

    static InputStream resource(String path) throws IOException {
        InputStream stream = BootstrapLoadingRenderer.class.getResourceAsStream(path);
        if (stream == null) throw new IOException("Missing bundled loading resource: " + path);
        return stream;
    }

    private static String source(String file) throws IOException {
        try (InputStream stream = resource(SHADERS + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Program {
        final int id;
        final int rect;
        final int viewport;
        private final java.util.Map<String, Integer> uniforms = new java.util.HashMap<>();

        private Program(int id, String[] names) throws IOException {
            this.id = id;
            rect = location("Rect");
            viewport = location("Viewport");
            for (String name : names) uniforms.put(name, location(name));
        }

        int uniform(String name) {
            return uniforms.get(name);
        }

        private int location(String name) throws IOException {
            int location = glGetUniformLocation(id, name);
            if (location < 0) throw new IOException("Missing loading shader uniform '" + name + "' in program " + id);
            return location;
        }

        void use(int width, int height) {
            glUseProgram(id);
            glUniform2f(viewport, width, height);
        }

        static Program create(String fragment, String... uniforms) throws IOException {
            int vertex = 0, pixel = 0, program = 0;
            boolean linked = false;
            try {
                vertex = compile(GL_VERTEX_SHADER, "quad.vsh");
                pixel = compile(GL_FRAGMENT_SHADER, fragment);
                program = glCreateProgram();
                if (program == 0) throw new IOException("Cannot allocate loading program " + fragment);
                glAttachShader(program, vertex);
                glAttachShader(program, pixel);
                glBindAttribLocation(program, 0, "Position");
                glBindAttribLocation(program, 1, "UV");
                glBindFragDataLocation(program, 0, "FragColor");
                glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                    throw new IOException("Loading shader link failed (" + fragment + "): " + glGetProgramInfoLog(program));
                }
                Program result = new Program(program, uniforms);
                linked = true;
                return result;
            } finally {
                if (program != 0 && vertex != 0) glDetachShader(program, vertex);
                if (program != 0 && pixel != 0) glDetachShader(program, pixel);
                if (vertex != 0) glDeleteShader(vertex);
                if (pixel != 0) glDeleteShader(pixel);
                if (!linked && program != 0) glDeleteProgram(program);
            }
        }

        private static int compile(int type, String file) throws IOException {
            String source = source(file);
            int shader = glCreateShader(type);
            if (shader == 0) throw new IOException("Cannot allocate loading shader " + file);
            boolean compiled = false;
            try {
                glShaderSource(shader, source);
                glCompileShader(shader);
                if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                    throw new IOException("Loading shader compilation failed (" + SHADERS + file + "): " + glGetShaderInfoLog(shader));
                }
                compiled = true;
                return shader;
            } finally {
                if (!compiled) glDeleteShader(shader);
            }
        }
    }
}

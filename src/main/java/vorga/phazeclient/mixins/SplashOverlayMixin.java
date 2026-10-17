package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.font.msdf.MsdfFont;
import vorga.phazeclient.api.system.font.msdf.MsdfFonts;
import vorga.phazeclient.api.system.font.msdf.MsdfRenderer;
import vorga.phazeclient.api.system.shape.ShapeProperties;
import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.implement.menu.MenuStyle;

import java.util.Optional;
import java.util.function.Consumer;

/** Fully replaces the vanilla resource-reload artwork and keeps its lifecycle. */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    private static final int BACKGROUND_COLOR = 0xFF4F9D76;
    private static final int LOGO_COLOR = 0xFFFFFFFF;
    private static final int BAR_TRACK_COLOR = 0x33152E22;
    private static final int BAR_BORDER_COLOR = 0xBFFFFFFF;
    private static final int BAR_FILL_COLOR = 0xFFFFFFFF;

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private ResourceReload reload;
    @Shadow @Final private Consumer<Optional<Throwable>> exceptionHandler;
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Unique
    private boolean phaze$roundShaderUnavailable;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
    private void phaze$renderLoadingScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long now = Util.getMeasuringTimeMs();
        if (reloading && reloadStartTime == -1L) {
            reloadStartTime = now;
        }

        if (reloadCompleteTime == -1L && reload.isComplete()) {
            try {
                reload.throwException();
                exceptionHandler.accept(Optional.empty());
            } catch (Throwable throwable) {
                exceptionHandler.accept(Optional.of(throwable));
            }
            reloadCompleteTime = now;
        }

        float overlayAlpha = 1.0F;
        if (reloadCompleteTime != -1L) {
            float fadeOut = (now - reloadCompleteTime) / 1000.0F;
            if (fadeOut >= 1.0F) {
                client.setOverlay(null);
                ci.cancel();
                return;
            }
            overlayAlpha = 1.0F - MathHelper.clamp(fadeOut, 0.0F, 1.0F);
        } else if (reloading && reloadStartTime != -1L) {
            float fadeIn = MathHelper.clamp((now - reloadStartTime) / 500.0F, 0.15F, 1.0F);
            overlayAlpha = fadeIn;
        }

        renderCustomScreen(context, overlayAlpha);
        ci.cancel();
    }

    @Unique
    private void renderCustomScreen(DrawContext context, float alpha) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        float progressValue = MathHelper.clamp(reload.getProgress(), 0.0F, 1.0F);
        progress = MathHelper.clamp(progress * 0.95F + progressValue * 0.05F, 0.0F, 1.0F);

        context.fill(0, 0, screenWidth, screenHeight, MenuStyle.withAlpha(BACKGROUND_COLOR, alpha));

        float titleSize = MathHelper.clamp(screenHeight * 0.105F, 32.0F, 64.0F);
        float subtitleSize = titleSize * 0.36F;
        float gap = Math.max(2.0F, titleSize * 0.035F);
        float logoHeight = titleSize + gap + subtitleSize;
        float logoTop = screenHeight * 0.46F - logoHeight / 2.0F;

        // The initial overlay can render before the MSDF shader and atlas have
        // been applied. Do not probe them early: the custom background/bar
        // remains visible, then the logo appears during the completion fade.
        if (reload.isComplete()) {
            context.draw();
            renderCenteredText(context, "PHAZE", MsdfFonts.bold(), titleSize, logoTop, titleSize, alpha);
            renderCenteredText(context, "CLIENT", MsdfFonts.medium(), subtitleSize,
                    logoTop + titleSize + gap, subtitleSize, alpha);
        }

        float barWidth = Math.max(32.0F, Math.min(screenWidth - 48.0F, Math.max(220.0F, screenWidth * 0.52F)));
        float barHeight = MathHelper.clamp(screenHeight * 0.006F, 4.0F, 8.0F);
        float barX = (screenWidth - barWidth) / 2.0F;
        float barY = screenHeight * 0.82F;
        renderRoundedProgressBar(context, barX, barY, barWidth, barHeight, progress, alpha);
    }

    @Unique
    private static void renderCenteredText(DrawContext context, String text, MsdfFont font,
                                           float size, float boxY, float boxHeight, float alpha) {
        float textX = MenuStyle.centerMsdfTextX(font, text, size, 0.0F, context.getScaledWindowWidth());
        float textY = MenuStyle.centerMsdfTextY(size, boxY, boxHeight);
        MsdfRenderer.renderText(font, text, size, MenuStyle.withAlpha(LOGO_COLOR, alpha),
                context.getMatrices().peek().getPositionMatrix(), textX, textY, 0.0F);
    }

    @Unique
    private void renderRoundedProgressBar(DrawContext context, float x, float y,
                                          float width, float height, float progress, float alpha) {
        // Minecraft only exposes custom core shader programs after the
        // resource apply phase. Before that point use the same custom bar
        // geometry without probing a shader that cannot exist yet.
        if (!reload.isComplete()) {
            renderFallbackBar(context, x, y, width, height, progress, alpha);
            return;
        }

        try {
            if (phaze$roundShaderUnavailable) {
                renderFallbackBar(context, x, y, width, height, progress, alpha);
                return;
            }

            BatchedRectangle.beginScope();
            if (!BatchedRectangle.isBatching()) {
                BatchedRectangle.endScope();
                renderFallbackBar(context, x, y, width, height, progress, alpha);
                return;
            }

            BatchedRectangle.submit(ShapeProperties.create(context.getMatrices(), x, y, width, height)
                    .round(height / 2.0F)
                    .softness(1.0F)
                    .thickness(1.0F)
                    .outlineColor(MenuStyle.withAlpha(BAR_BORDER_COLOR, alpha))
                    .color(MenuStyle.withAlpha(BAR_TRACK_COLOR, alpha))
                    .build());
            float fillWidth = Math.max(height, width * MathHelper.clamp(progress, 0.0F, 1.0F));
            BatchedRectangle.submit(ShapeProperties.create(context.getMatrices(), x, y, fillWidth, height)
                    .round(height / 2.0F)
                    .softness(1.0F)
                    .color(MenuStyle.withAlpha(BAR_FILL_COLOR, alpha))
                    .build());
            BatchedRectangle.endScope();
        } catch (Throwable ignored) {
            // The first client-resource frame can predate the round shader.
            // Keep the custom screen alive until it becomes available.
            // A new SplashOverlay instance retries the shader on the next
            // resource-pack reload.
            phaze$roundShaderUnavailable = true;
            renderFallbackBar(context, x, y, width, height, progress, alpha);
        }
    }

    @Unique
    private static void renderFallbackBar(DrawContext context, float x, float y,
                                          float width, float height, float progress, float alpha) {
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        int fillRight = Math.round(x + Math.max(1.0F, width * MathHelper.clamp(progress, 0.0F, 1.0F)));
        context.fill(left, top, right, bottom, MenuStyle.withAlpha(BAR_TRACK_COLOR, alpha));
        context.fill(left, top, fillRight, bottom, MenuStyle.withAlpha(BAR_FILL_COLOR, alpha));
    }
}

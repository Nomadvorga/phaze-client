package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.hud.BossBarColorContext;
import vorga.phazeclient.api.system.hud.BossBarHudAccess;

import java.util.Map;
import java.util.UUID;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin implements BossBarHudAccess {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Map<UUID, ClientBossBar> bossBars;
    @Shadow @Final private static Identifier[] PROGRESS_TEXTURES;

    @Invoker("renderBossBar")
    protected abstract void phaze$renderBossBar(DrawContext context, int x, int y, BossBar bossBar);

    @Unique private ClientBossBar phaze$preview;

    @Override
    public void phaze$updateLayout() {
        vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance().updateLayout(client, bossBars.values());
    }

    @WrapMethod(method = "render")
    private void phaze$renderTransformed(DrawContext context, Operation<Void> original) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        if (!module.isEnabled() || bossBars.isEmpty()) {
            original.call(context);
            return;
        }
        phaze$updateLayout();
        context.getMatrices().push();
        try {
            module.applyTransform(context);
            original.call(context);
        } finally {
            context.getMatrices().pop();
        }
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/hud/BossBarHud;renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V"), require = 1)
    private boolean phaze$renderHealth(BossBarHud hud, DrawContext context, int x, int y, BossBar bar) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        return !module.isEnabled() || module.renderHealth.isValue();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)I"), require = 1)
    private int phaze$renderText(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color, Operation<Integer> original) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        return !module.isEnabled() || module.renderText.isValue() ? original.call(context, renderer, text, x, y, color) : 0;
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I"), require = 1)
    private int phaze$measureText(TextRenderer renderer, StringVisitable text, Operation<Integer> original) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        if (!module.isEnabled()) return original.call(renderer, text);
        return module.renderText.isValue() ? module.getNameWidth(renderer, text) : 0;
    }

    @WrapMethod(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V")
    private void phaze$tintProgress(DrawContext context, int x, int y, BossBar bar, int width,
                                   Identifier[] textures, Identifier[] notchedTextures, Operation<Void> original) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        int previous = BossBarColorContext.setTint(textures == PROGRESS_TEXTURES && module.isEnabled()
                ? module.getActiveFillColor() : 0xFFFFFFFF);
        try {
            original.call(context, x, y, bar, width, textures, notchedTextures);
        } finally {
            BossBarColorContext.setTint(previous);
        }
    }

    @ModifyArg(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIIIII)V", ordinal = 0), index = 1, require = 1)
    private Identifier phaze$neutralProgressTexture(Identifier texture, @Local(argsOnly = true, ordinal = 0) Identifier[] textures) {
        if (textures == PROGRESS_TEXTURES && vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance().isEnabled()) {
            for (Identifier vanilla : PROGRESS_TEXTURES) {
                if (texture.equals(vanilla)) return PROGRESS_TEXTURES[BossBar.Color.WHITE.ordinal()];
            }
        }
        return texture;
    }

    @Override
    public void phaze$renderPreview(DrawContext context) {
        var module = vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance();
        if (!bossBars.isEmpty() || !module.isEnabled()) return;
        if (phaze$preview == null) {
            phaze$preview = new ClientBossBar(new UUID(0, 0), vorga.phazeclient.implement.features.modules.hud.BossBarHud.PREVIEW_NAME,
                    0.75F, BossBar.Color.PURPLE, BossBar.Style.PROGRESS, false, false, false);
        }
        context.getMatrices().push();
        try {
            module.applyTransform(context);
            int centerX = context.getScaledWindowWidth() / 2;
            if (module.renderHealth.isValue()) phaze$renderBossBar(context, centerX - 91, 12, phaze$preview);
            if (module.renderText.isValue()) {
                Text name = phaze$preview.getName();
                context.drawTextWithShadow(client.textRenderer, name, centerX - module.getNameWidth(client.textRenderer, name) / 2, 3, 0xFFFFFFFF);
            }
        } finally {
            context.getMatrices().pop();
        }
    }

    @Inject(method = {"handlePacket", "clear"}, at = @At("TAIL"))
    private void phaze$invalidateLayout(CallbackInfo ci) {
        vorga.phazeclient.implement.features.modules.hud.BossBarHud.getInstance().invalidateNameWidths();
    }
}

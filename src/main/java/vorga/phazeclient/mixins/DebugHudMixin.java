package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.implement.features.modules.other.BetterF3;
import vorga.phazeclient.implement.features.modules.other.BetterF3Renderer;
import vorga.phazeclient.implement.features.modules.other.FakeFps;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @Unique
    private static boolean phaze$isF3Open() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.debugHudEntryList != null
                && client.debugHudEntryList.isF3Enabled();
    }

    private static final String[] PHAZE_COORD_PREFIXES = {
            "XYZ:",
            "Block:",
            "Chunk:"
    };

    private static final Pattern PHAZE_FPS_PREFIX = Pattern.compile("^\\d+\\s*fps");

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$replaceDebugHud(DrawContext context, CallbackInfo ci) {
        BetterF3 module = BetterF3.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }

        if (!phaze$isF3Open()) {
            return;
        }
        BetterF3Renderer.render(context);
        ci.cancel();
    }

    @Inject(
            method = "drawText(Lnet/minecraft/client/gui/DrawContext;Ljava/util/List;Z)V",
            at = @At("HEAD")
    )
    private void phaze$rewriteDebugLines(DrawContext context, List<String> lines, boolean left, CallbackInfo ci) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        StreamerMode streamer = StreamerMode.getInstance();
        if (streamer != null && streamer.isHideCoordinatesEnabled()) {
            for (int i = 0; i < lines.size(); i++) {
                lines.set(i, phaze$maskLine(lines.get(i)));
            }
        }

        FakeFps fakeFps = FakeFps.getInstance();
        if (fakeFps != null && fakeFps.isEnabled()) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) continue;
                Matcher m = PHAZE_FPS_PREFIX.matcher(line);
                if (m.find()) {
                    lines.set(i, fakeFps.getFakeFps() + " fps" + line.substring(m.end()));
                    break;
                }
            }
        }
    }

    private static String phaze$maskLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String probe = line.startsWith(" ") ? line.stripLeading() : line;
        for (String prefix : PHAZE_COORD_PREFIXES) {
            if (probe.startsWith(prefix)) {
                int colon = line.indexOf(':');
                return colon >= 0 ? line.substring(0, colon + 1) + " hidden" : "hidden";
            }
        }
        return line;
    }
}

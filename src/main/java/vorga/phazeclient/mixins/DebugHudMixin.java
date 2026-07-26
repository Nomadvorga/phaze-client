package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.BetterF3;
import vorga.phazeclient.implement.features.modules.other.BetterF3Renderer;
import vorga.phazeclient.implement.features.modules.other.FakeFps;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consolidated {@link DebugHud} mixin: BetterF3 layout replacement,
 * StreamerMode coord masking, FakeFps line rewrite. Three
 * independent injectors at different points (HEAD/RETURN); merged
 * into one file because all three share the same target class.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    /** Coord-line prefixes for StreamerMode mask. F3 is not
     *  localised so prefix-match against literal English labels is
     *  stable across language switches. */
    private static final String[] PHAZE_COORD_PREFIXES = {
            "XYZ:",
            "Block:",
            "Chunk:"
    };

    /** Regex for the leading {@code "<n> fps"} prefix that FakeFps
     *  rewrites. Compiled once at class init. */
    private static final Pattern PHAZE_FPS_PREFIX = Pattern.compile("^\\d+\\s*fps");

    /** BetterF3 replaces the entire vanilla render with a compact
     *  layout. HEAD-cancellable so vanilla never draws. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void phaze$replaceDebugHud(DrawContext context, CallbackInfo ci) {
        BetterF3 module = BetterF3.getInstance();
        if (module == null || !module.isEnabled()) {
            return;
        }
        BetterF3Renderer.render(context);
        ci.cancel();
    }

    /**
     * StreamerMode + FakeFps post-process the left-text list. Both
     * features fire on the same RETURN; we walk the list once and
     * apply both transforms in sequence. Order matters: FakeFps
     * rewrites the {@code "<n> fps"} prefix on the first matching
     * line, StreamerMode replaces matching coord lines with a
     * fixed {@code "Label: hidden"} - they target different lines
     * so there's no interaction.
     */
    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void phaze$maskCoordinates(CallbackInfoReturnable<List<String>> cir) {
        StreamerMode streamer = StreamerMode.getInstance();
        if (streamer == null || !streamer.isHideCoordinatesEnabled()) {
            return;
        }
        List<String> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        List<String> masked = new ArrayList<>(original.size());
        for (String line : original) {
            masked.add(phaze$maskLine(line));
        }
        cir.setReturnValue(masked);
    }

    @ModifyReturnValue(method = "getLeftText", at = @At("RETURN"))
    private List<String> phaze$rewriteFpsLine(List<String> original) {
        FakeFps module = FakeFps.getInstance();
        if (module == null || !module.isEnabled() || original == null || original.isEmpty()) {
            return original;
        }
        // Defensive copy so we never mutate vanilla's list (the
        // StreamerMode inject above already may have replaced it,
        // but ModifyReturnValue runs on the final return value
        // regardless).
        List<String> patched = new ArrayList<>(original);
        for (int i = 0; i < patched.size(); i++) {
            String line = patched.get(i);
            if (line == null) continue;
            Matcher m = PHAZE_FPS_PREFIX.matcher(line);
            if (m.find()) {
                int fake = module.getFakeFps();
                patched.set(i, fake + " fps" + line.substring(m.end()));
                break;
            }
        }
        return patched;
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

package vorga.phazeclient.mixins;

import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vorga.phazeclient.implement.features.modules.other.StreamerMode;

import java.util.ArrayList;
import java.util.List;

/**
 * F3 coordinate masking for {@link StreamerMode}. Vanilla emits its
 * left-side debug overlay as a plain {@code List<String>} returned from
 * {@link DebugHud#getLeftText()}; we intercept the return value and
 * replace any line whose prefix identifies it as a position-revealing
 * row with a fixed "hidden" placeholder. The right-side text
 * ({@code getRightText}) is left alone - in 1.21.x it shows machine
 * info (Java / CPU / memory / GPU) and the targeted-block / fluid debug
 * lines, none of which leak the player's own coordinates.
 *
 * <p>Substring-prefix detection is intentional rather than a regex
 * because every line vanilla produces here is built from the literal
 * English label inside {@code DebugHud}; F3 is not localised, so a
 * prefix match against the same English literals will keep working
 * across language switches without an extra resolution layer.
 *
 * <p>Mask shape: {@code "<Label>: hidden"} keeps the visual hierarchy
 * (label still readable, value gone) and is short enough to never
 * stretch the F3 backdrop wider than the unmasked layout.
 */
@Mixin(DebugHud.class)
public class DebugHudCoordsMixin {

    /**
     * F3 line prefixes that reveal the player's location. {@code XYZ}
     * carries the precise floating-point position, {@code Block} the
     * snapped integer block coords, and {@code Chunk} the chunk-local
     * plus chunk-grid coords. {@code Facing} is intentionally NOT in
     * this list - it only exposes the camera yaw / pitch and a cardinal
     * direction, which a streamer's viewers can already see from the
     * camera angle.
     */
    @Unique
    private static final String[] PHAZE$COORD_PREFIXES = {
            "XYZ:",
            "Block:",
            "Chunk:"
    };

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

    /**
     * Returns {@code line} unchanged unless its leading characters match
     * one of the {@link #PHAZE$COORD_PREFIXES}, in which case the
     * portion after the colon is replaced with " hidden". Trims a leading
     * space first because vanilla occasionally indents auxiliary lines
     * (eg "  Block: ..." after the primary "XYZ:" row in some F3 modes).
     */
    @Unique
    private static String phaze$maskLine(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String probe = line.startsWith(" ") ? line.stripLeading() : line;
        for (String prefix : PHAZE$COORD_PREFIXES) {
            if (probe.startsWith(prefix)) {
                int colon = line.indexOf(':');
                return colon >= 0 ? line.substring(0, colon + 1) + " hidden" : "hidden";
            }
        }
        return line;
    }
}

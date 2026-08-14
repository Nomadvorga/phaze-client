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

/**
 * Consolidated {@link DebugHud} mixin: BetterF3 layout replacement,
 * StreamerMode coord masking, FakeFps line rewrite. Merged into one
 * file because all three share the same target class.
 *
 * <p>1.21.11 rewrote the debug HUD. {@code getLeftText()} and
 * {@code getRightText()} are gone; the F3 text is now produced by a
 * registry of {@code DebugHudEntry} implementations
 * ({@code net.minecraft.client.gui.hud.debug.*}) that push strings
 * into a {@code DebugHudLines} sink, and {@code render} assembles the
 * result into two local {@code ArrayList}s that it hands to the
 * private {@code drawText(DrawContext, List, boolean)} - {@code true}
 * for the left column, {@code false} for the right.
 *
 * <p>Consequently the coord/fps post-processing now runs at the HEAD
 * of {@code drawText} and edits the list in place. It deliberately
 * does <em>not</em> filter on the {@code left} flag: the position
 * lines ({@code XYZ}/{@code Block}/{@code Chunk}) are contributed as
 * a <em>section</em> by {@code PlayerPositionDebugHudEntry}, and
 * {@code render} distributes whole sections across the two columns
 * by index, so they can legitimately land on the right. Gating on
 * {@code left} would let StreamerMode leak coordinates whenever two
 * or more sections are visible. Both scans stop at the first match
 * per list, and any given line exists in exactly one of the two
 * lists, so nothing is rewritten twice.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    /**
     * Is the F3 overlay itself up?
     *
     * <p>1.21.11 moved the F3 check INSIDE {@code render}. Through 1.21.4 the
     * caller only invoked {@code DebugHud.render} while F3 was up, so a HEAD
     * inject was implicitly gated by it. Now the chain
     * {@code GameRenderer -> InGameHud.renderDebugHud -> DebugHud.render}
     * runs every frame the HUD is visible and no screen is open - the guards
     * at the top of {@code render} are only isFinishedLoading / hudHidden /
     * currentScreen.
     *
     * <p>Deliberately NOT {@code DebugHud.shouldShowDebugHud()}: that is
     * {@code isF3Enabled() || !getVisibleEntries().isEmpty()}, and the second
     * half is true whenever any standalone debug overlay is on - F3+B
     * (hitboxes) being the obvious one. Gating on it made BetterF3 appear on
     * F3+B with F3 itself closed.
     */
    @Unique
    private static boolean phaze$isF3Open() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && client.debugHudEntryList != null
                && client.debugHudEntryList.isF3Enabled();
    }

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
        // Explicitly gate on F3 - see phaze$isF3Open for why this is no
        // longer implied by simply being inside render().
        if (!phaze$isF3Open()) {
            return;
        }
        BetterF3Renderer.render(context);
        ci.cancel();
    }

    /**
     * StreamerMode + FakeFps post-process one rendered column of F3
     * text. We walk the list once and apply both transforms in
     * sequence, in the same order the old {@code getLeftText}
     * {@code @Inject}/{@code @ModifyReturnValue} pair ran: StreamerMode
     * replaces matching coord lines with a fixed
     * {@code "Label: hidden"}, then FakeFps rewrites the
     * {@code "<n> fps"} prefix on the first matching line. They target
     * different lines so there's no interaction.
     *
     * <p>Mutating in place is safe: {@code render} allocates both
     * lists fresh every frame and passes them nowhere else.
     */
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

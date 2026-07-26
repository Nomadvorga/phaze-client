package vorga.phazeclient.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vorga.phazeclient.api.system.cursor.CursorManager;
import vorga.phazeclient.implement.features.modules.other.Animations;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Hand cursor for the row currently under the mouse inside an
 * {@link EntryListWidget} (option lists, server lists, world list,
 * resource pack list, modded list-based screens). Targets only real
 * rows - the dark padding between rows, the empty area below a
 * short list, and the scrollbar dead space stay on the default
 * arrow.
 *
 * <h3>Hover detection</h3>
 * Reads the private {@code hoveredEntry} field reflectively. The
 * field is non-null whenever vanilla considers the pointer to be
 * over a clickable row, which is exactly the precision we need.
 *
 * <p>Reflection (rather than {@code @Shadow} or {@code @Invoker})
 * because both alternatives broke at class-load time on production
 * mappings: {@code @Shadow} on {@code hoveredEntry} couldn't be
 * resolved against the obfuscated descriptor, and {@code @Invoker}
 * for {@code getHoveredEntry} fails Mixin's annotation-processor
 * check because the generic type variable in the return descriptor
 * doesn't survive erasure cleanly.
 *
 * <p>The {@link Field} resolution chain runs at most once per
 * launch and tolerates every mapping flavour:
 * <ol>
 *   <li>Try the {@code "hoveredEntry"} dev / Yarn name directly.</li>
 *   <li>If that's missing (production / intermediary), walk all
 *       declared fields and pick the unique non-static, non-final
 *       field whose type is an abstract inner class of
 *       {@link EntryListWidget} - that's the {@code Entry} type
 *       under any mapping, and it's distinguishable from the
 *       concrete inner {@code Entries} list class by the
 *       {@code abstract} modifier.</li>
 * </ol>
 *
 * <p>If both attempts fail, the cursor pipeline silently falls back
 * to no row-precision treatment - the default arrow stays over list
 * dead space, which is preferable to a crash.
 */
@Mixin(EntryListWidget.class)
public abstract class EntryListWidgetCursorMixin {

    /** Cached field handle. {@code null} after a failed lookup means
     *  "row-precision unavailable on this mapping; do nothing". */
    private static Field phaze$hoveredField = null;
    /** Guard so the field walk runs at most once per JVM. */
    private static boolean phaze$lookupDone = false;

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void phaze$cursorRequestHand(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!Animations.getInstance().isDynamicCursorEnabled()) {
            return;
        }
        EntryListWidget<?> self = (EntryListWidget<?>) (Object) this;
        // Bail early if the pointer isn't even in the gross widget
        // bounds - cheaper than reflecting when the mouse is off
        // the list entirely.
        if (!self.isMouseOver(mouseX, mouseY)) {
            return;
        }
        Field f = phaze$resolveField();
        if (f == null) {
            return;
        }
        try {
            Object entry = f.get(self);
            if (entry == null) {
                return;
            }
            // World/Server list special case: only flag the row's
            // 32x32 icon column (left edge of the row strip) as
            // "clickable hand". The rest of the row - title text,
            // status icons, secondary buttons - either has its own
            // ClickableWidget cursor handling (the inline join /
            // edit / delete buttons are real buttons and get the
            // hand from ClickableWidgetCursorMixin) or shouldn't
            // suggest clickability at all.
            if (phaze$isIconOnlyScreen() && !phaze$pointerOverIconColumn(self, mouseX, mouseY)) {
                return;
            }
            CursorManager.requestHand();
        } catch (Throwable ignored) {
            // Defensive: a single failed read must not take down the
            // cursor pipeline for the rest of the screen lifetime.
        }
    }

    /**
     * True when the currently active screen is one of the vanilla
     * list screens whose row "click target" is conceptually the icon
     * thumbnail rather than the whole row. World list rows display a
     * world preview thumbnail, server list rows the server icon -
     * those are what the user thinks of as the clickable image.
     */
    private static boolean phaze$isIconOnlyScreen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }
        Screen s = mc.currentScreen;
        return s instanceof SelectWorldScreen || s instanceof MultiplayerScreen;
    }

    /**
     * True when the mouse pointer falls inside the row's icon column
     * - the leftmost {@link #ICON_COLUMN_WIDTH} pixels of the row
     * strip. Vanilla draws the 32x32 thumbnail flush with the row's
     * left edge in both {@code SelectWorldScreen} and
     * {@code MultiplayerScreen}; everything to the right (title,
     * subtitle, hover buttons) is either text or its own clickable
     * widget and shouldn't get the row-level hand from this mixin.
     */
    private static boolean phaze$pointerOverIconColumn(EntryListWidget<?> self, double mouseX, double mouseY) {
        try {
            int left = self.getRowLeft();
            return mouseX >= left && mouseX < left + ICON_COLUMN_WIDTH;
        } catch (Throwable t) {
            // If the row geometry isn't queryable, fall back to the
            // safer "no hand" answer rather than the wrong one.
            return false;
        }
    }

    /** 32-pixel icon column width matches vanilla's {@code itemHeight}
     *  on both list screens (rows are 36 px tall, icon is 32 px square
     *  with 2 px vertical padding, drawn flush left). */
    private static final int ICON_COLUMN_WIDTH = 32;

    private static Field phaze$resolveField() {
        if (phaze$lookupDone) {
            return phaze$hoveredField;
        }
        phaze$lookupDone = true;
        Class<?> cls = EntryListWidget.class;

        // Pass 1: try the dev / yarn name directly. Cheapest path
        // and works in the dev runtime where Loom has already
        // remapped to named.
        try {
            Field f = cls.getDeclaredField("hoveredEntry");
            f.setAccessible(true);
            phaze$hoveredField = f;
            return f;
        } catch (NoSuchFieldException ignored) {
            // Fall through to the structural probe.
        }

        // Pass 2: structural probe. The hoveredEntry field is the
        // unique non-static, non-final field on EntryListWidget
        // whose type is an abstract inner class declared on
        // EntryListWidget itself - the Entry generic erased to its
        // raw inner-class type. The other inner-class field
        // (Entries, the children list) is concrete (extends
        // AbstractList) so the abstract-modifier check
        // discriminates between them under any mapping.
        try {
            for (Field f : cls.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                Class<?> t = f.getType();
                if (t.getDeclaringClass() != cls) {
                    continue;
                }
                if (!Modifier.isAbstract(t.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                phaze$hoveredField = f;
                return f;
            }
        } catch (Throwable ignored) {
            // SecurityManager / weird classloader - fall through to
            // null below.
        }
        return null;
    }
}

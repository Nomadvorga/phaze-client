package vorga.phazeclient.implement.menu.font;

import vorga.phazeclient.api.system.font.Fonts.Type;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Menu-only font cache. Gameplay HUD callers continue to use Fonts unchanged. */
public final class GuiFonts {
    private static final int CACHE_LIMIT = 128;
    private static final Map<Key, MsdfGuiFontRenderer> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, MsdfGuiFontRenderer> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private GuiFonts() {
    }

    public static MsdfGuiFontRenderer getSize(int size) {
        return getSize(size, Type.INTER_BOLD);
    }

    /** Retains Fonts' doubled-size convention and maps UI weights to the bundled Cyrillic atlases. */
    public static MsdfGuiFontRenderer getSize(int size, Type type) {
        if (size <= 0) throw new IllegalArgumentException("GUI font size must be positive");
        return renderer(size / 2.0F, type);
    }

    /** Explicit API for new menu code: a 7-unit font is requested as 7, not legacy size 14. */
    public static GuiTextRenderer atSize(float size, Type type) {
        return renderer(size, type);
    }

    private static MsdfGuiFontRenderer renderer(float size, Type type) {
        Objects.requireNonNull(type, "type");
        if (!Float.isFinite(size) || size <= 0) throw new IllegalArgumentException("GUI font size must be finite and positive");
        boolean bold = switch (type) {
            case INTER_BOLD, SF_BOLD, JET_BOLD -> true;
            case INTER_DEFAULT, SF_DEFAULT, JET_DEFAULT, ICO -> false;
        };
        return CACHE.computeIfAbsent(new Key(size, bold), key -> new MsdfGuiFontRenderer(key.size(), key.bold()));
    }

    private record Key(float size, boolean bold) {
    }
}

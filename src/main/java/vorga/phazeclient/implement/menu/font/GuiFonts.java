package vorga.phazeclient.implement.menu.font;

import vorga.phazeclient.api.system.font.Fonts.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Menu-only font cache. Gameplay HUD callers continue to use Fonts unchanged. */
public final class GuiFonts {
    private static final Map<Key, MsdfGuiFontRenderer> CACHE = new HashMap<>();

    private GuiFonts() {
    }

    public static MsdfGuiFontRenderer getSize(int size) {
        return getSize(size, Type.INTER_BOLD);
    }

    /** Retains Fonts' doubled-size convention and maps UI weights to the bundled Cyrillic atlases. */
    public static MsdfGuiFontRenderer getSize(int size, Type type) {
        Objects.requireNonNull(type, "type");
        if (size <= 0) throw new IllegalArgumentException("GUI font size must be positive");
        boolean bold = switch (type) {
            case INTER_BOLD, SF_BOLD, JET_BOLD -> true;
            case INTER_DEFAULT, SF_DEFAULT, JET_DEFAULT, ICO -> false;
        };
        return CACHE.computeIfAbsent(new Key(size, bold), key -> new MsdfGuiFontRenderer(key.size() / 2.0F, key.bold()));
    }

    private record Key(int size, boolean bold) {
    }
}

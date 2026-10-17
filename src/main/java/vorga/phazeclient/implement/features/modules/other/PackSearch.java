package vorga.phazeclient.implement.features.modules.other;

import java.util.Locale;

/** Presentation-only matching: no pack is enabled, disabled, or reordered here. */
public final class PackSearch {
    private PackSearch() {
    }

    public static boolean matches(String query, String id, String name, String description) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || contains(id, needle) || contains(name, needle) || contains(description, needle);
    }

    private static boolean contains(String text, String needle) {
        // Pack authors often embed legacy formatting codes in their titles.
        return text.replaceAll("§.", "").toLowerCase(Locale.ROOT).contains(needle);
    }
}

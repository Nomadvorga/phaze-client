package vorga.phazeclient.api.system.nametag;

import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class NametagTextCache {
    private static final Map<String, TextCacheEntry> textCache = new HashMap<>();
    private static final int CACHE_SIZE = 256;
    private static final long CACHE_DURATION = 60000; // 1 minute

    public static class TextCacheEntry {
        public final float width;
        public final long timestamp;

        public TextCacheEntry(float width) {
            this.width = width;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static float getCachedTextWidth(Text text) {
        String key = text.getString();
        TextCacheEntry entry = textCache.get(key);
        if (entry != null && (System.currentTimeMillis() - entry.timestamp) < CACHE_DURATION) {
            return entry.width;
        }
        return -1; // Not cached
    }

    public static void cacheTextWidth(Text text, float width) {
        String key = text.getString();
        textCache.put(key, new TextCacheEntry(width));
        
        // Remove old entries if cache is too large
        if (textCache.size() > CACHE_SIZE) {
            textCache.entrySet().removeIf(e -> 
                (System.currentTimeMillis() - e.getValue().timestamp) > CACHE_DURATION);
        }
    }

    public static void clear() {
        textCache.clear();
    }
}

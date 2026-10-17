package vorga.phazeclient.implement.features.modules.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Minecraft-independent preview rules; exercised by the standalone regression test. */
final class PackPreviewAssetLogic {
    private PackPreviewAssetLogic() {
    }

    static String canonicalPath(String path) {
        path = path.replace("textures/blocks/", "textures/block/")
                .replace("textures/items/", "textures/item/");
        return switch (path) {
            case "textures/item/bow_standby.png" -> "textures/item/bow.png";
            case "textures/item/totem.png" -> "textures/item/totem_of_undying.png";
            case "textures/block/log_oak.png" -> "textures/block/oak_log.png";
            case "textures/block/grass_top.png" -> "textures/block/grass_block_top.png";
            default -> path;
        };
    }

    static List<String> aliases(String canonical) {
        List<String> paths = new ArrayList<>();
        paths.add(canonical);
        paths.add(canonical.replace("textures/block/", "textures/blocks/")
                .replace("textures/item/", "textures/items/"));
        String legacy = switch (canonical) {
            case "textures/item/bow.png" -> "textures/items/bow_standby.png";
            case "textures/item/totem_of_undying.png" -> "textures/items/totem.png";
            case "textures/block/oak_log.png" -> "textures/blocks/log_oak.png";
            case "textures/block/grass_block_top.png" -> "textures/blocks/grass_top.png";
            case "textures/particle/critical_hit.png" -> "textures/particle/crit.png";
            case "textures/particle/enchanted_hit.png" -> "textures/particle/magic_crit.png";
            default -> null;
        };
        if (legacy != null) {
            paths.add(legacy);
            paths.add(legacy.replace("textures/items/", "textures/item/")
                    .replace("textures/blocks/", "textures/block/"));
        }
        return paths.stream().distinct().toList();
    }

    static <T extends Comparable<? super T>> List<T> shuffled(List<T> values, String packId) {
        List<T> result = new ArrayList<>(values);
        Collections.sort(result);
        Collections.shuffle(result, new Random(packId.hashCode()));
        return result;
    }

    static int batchIndex(long elapsedMillis, int batchCount) {
        return (int) Math.floorMod(Math.floorDiv(elapsedMillis, 1500L), (long) batchCount);
    }

    static <T> List<List<T>> blockBatches(List<T> sprites) {
        if (sprites.size() < 4) {
            return List.of();
        }
        List<List<T>> batches = new ArrayList<>();
        // Rotate even when there are exactly four changes: every 1.5s the
        // visible positions change, without ever repeating an icon in a row.
        for (int i = 0; i < sprites.size(); i++) {
            List<T> batch = new ArrayList<>(4);
            for (int j = 0; j < 4; j++) {
                batch.add(sprites.get((i + j) % sprites.size()));
            }
            batches.add(List.copyOf(batch));
        }
        return List.copyOf(batches);
    }

    /** Most common custom frame width, with a stable smaller-width tie break; zero means unknown. */
    static int resolution(Map<Integer, Integer> widths) {
        int result = 0;
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : widths.entrySet()) {
            if (entry.getValue() > count || entry.getValue() == count && entry.getKey() < result) {
                result = entry.getKey();
                count = entry.getValue();
            }
        }
        return result;
    }

    static boolean samePixels(int width, int height, int[] a, int otherWidth, int otherHeight, int[] b) {
        if (width != otherWidth || height != otherHeight || a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            // RGB in a fully transparent texel is not a visible pack change.
            if (visible(a[i]) != visible(b[i])) {
                return false;
            }
        }
        return true;
    }

    private static int visible(int argb) {
        return argb >>> 24 == 0 ? 0 : argb;
    }

    /** Straight-alpha source-over in ARGB, including partially transparent custom borders. */
    static int over(int background, int foreground) {
        int fa = foreground >>> 24;
        int ba = background >>> 24;
        int alphaNumerator = fa * 255 + ba * (255 - fa);
        if (alphaNumerator == 0) {
            return 0;
        }
        int result = ((alphaNumerator + 127) / 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int f = foreground >>> shift & 255;
            int b = background >>> shift & 255;
            int channel = (f * fa * 255 + b * ba * (255 - fa) + alphaNumerator / 2) / alphaNumerator;
            result |= channel << shift;
        }
        return result;
    }

    static Map<String, String> itemPaths() {
        Map<String, String> paths = new LinkedHashMap<>();
        for (String item : List.of("bucket", "lava_bucket", "water_bucket", "golden_apple", "ender_pearl",
                "bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2", "iron_sword", "iron_pickaxe",
                "diamond_sword", "diamond_pickaxe", "totem_of_undying")) {
            paths.put(item, "textures/item/" + item + ".png");
        }
        return paths;
    }
}

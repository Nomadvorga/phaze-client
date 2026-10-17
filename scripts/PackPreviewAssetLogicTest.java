package vorga.phazeclient.implement.features.modules.other;

import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** No Minecraft, Gson, JUnit, network, or OpenGL required. */
public final class PackPreviewAssetLogicTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        aliases();
        deterministicBatches();
        pixelComparison();
        resolution();
        hearts();
        if (args.length > 0) {
            parseLoader(args[0]);
        }
        System.out.println("Pack preview regression checks passed: " + checks);
    }

    private static void parseLoader(String path) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "--release", "21"), null, files.getJavaFileObjects(path));
            parser.parse(); // Syntax only: no dependency resolution, type analysis, class output or mod build.
            check(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                    "Minecraft-facing loader parses as Java 21: " + diagnostics.getDiagnostics());
        }
    }

    private static void aliases() {
        for (String path : PackPreviewAssetLogic.itemPaths().values()) {
            check(PackPreviewAssetLogic.aliases(path).getFirst().equals(path), "modern item takes precedence");
            check(PackPreviewAssetLogic.aliases(path).stream().anyMatch(p -> p.startsWith("textures/items/")),
                    "plural legacy item directory supported");
        }
        for (String path : List.of("textures/item/bow.png", "textures/item/totem_of_undying.png",
                "textures/block/oak_log.png", "textures/block/grass_block_top.png")) {
            for (String alias : PackPreviewAssetLogic.aliases(path)) {
                check(PackPreviewAssetLogic.canonicalPath(alias).equals(path), "legacy aliases deduplicate before counting");
            }
        }
        check(PackPreviewAssetLogic.itemPaths().size() == 14, "all requested item variants exposed");
    }

    private static void deterministicBatches() {
        List<String> order = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            order.add("pack:textures/block/" + i + ".png");
        }
        List<String> expected = PackPreviewAssetLogic.shuffled(order, "disabled-pack");
        Collections.reverse(order);
        check(expected.equals(PackPreviewAssetLogic.shuffled(order, "disabled-pack")),
                "pack enumeration order cannot change selection");
        check(PackPreviewAssetLogic.blockBatches(List.of(1, 2, 3)).isEmpty(),
                "fewer than four verified changes requests the fixed vanilla set");
        for (int count = 4; count <= 64; count++) {
            List<List<String>> batches = PackPreviewAssetLogic.blockBatches(expected.subList(0, count));
            HashSet<String> shown = new HashSet<>();
            for (List<String> batch : batches) {
                check(batch.size() == 4 && new HashSet<>(batch).size() == 4, "every batch contains exactly four choices");
                shown.addAll(batch);
            }
            check(shown.size() == count, "partial final batches do not lose a changed texture");
        }
        check(PackPreviewAssetLogic.batchIndex(0, 3) == 0, "starts with first batch");
        check(PackPreviewAssetLogic.batchIndex(1499, 3) == 0, "holds for 1500 milliseconds");
        check(PackPreviewAssetLogic.batchIndex(1500, 3) == 1, "cycles at exactly 1500 milliseconds");
        check(PackPreviewAssetLogic.batchIndex(4500, 3) == 0, "cycles back without resource reads");
        check(PackPreviewAssetLogic.batchIndex(-1, 3) == 2, "clock wrap stays in bounds");
        var four = PackPreviewAssetLogic.blockBatches(List.of(1, 2, 3, 4));
        check(four.size() == 4 && !four.get(0).equals(four.get(1)), "exactly four changed blocks still rotate");
    }

    private static void pixelComparison() {
        int[] vanilla = {0xFF112233, 0x00000000, 0x80123456, 0xFFFFFFFF};
        int[] copy = vanilla.clone();
        check(PackPreviewAssetLogic.samePixels(2, 2, vanilla, 2, 2, copy), "identical pixel copies are not changed");
        copy[1] = 0x00ABCDEF;
        check(PackPreviewAssetLogic.samePixels(2, 2, vanilla, 2, 2, copy), "transparent RGB differences are invisible");
        copy[2] = 0x80123457;
        check(!PackPreviewAssetLogic.samePixels(2, 2, vanilla, 2, 2, copy), "visible color change is detected");
        copy = vanilla.clone();
        copy[2] = 0x81123456;
        check(!PackPreviewAssetLogic.samePixels(2, 2, vanilla, 2, 2, copy), "alpha change is detected");
        check(!PackPreviewAssetLogic.samePixels(2, 2, vanilla, 1, 4, vanilla), "sheet geometry matters");
    }

    private static void resolution() {
        check(PackPreviewAssetLogic.resolution(Map.of()) == 0, "vanilla fallback does not invent custom resolution");
        check(PackPreviewAssetLogic.resolution(Map.of(8, 7)) == 8, "sub-16 packs remain sub-16");
        check(PackPreviewAssetLogic.resolution(Map.of(32, 100, 256, 1)) == 32,
                "one oversized icon cannot override the pack's common block/item frame width");
        check(PackPreviewAssetLogic.resolution(Map.of(32, 2, 64, 2)) == 32, "resolution ties are stable");
    }

    private static void hearts() {
        check(PackPreviewAssetLogic.over(0xFF111111, 0x0000FF00) == 0xFF111111, "transparent heart preserves border");
        check(PackPreviewAssetLogic.over(0xFF111111, 0xFFFF0000) == 0xFFFF0000, "opaque heart fills container");
        check(PackPreviewAssetLogic.over(0, 0x80FF0000) == 0x80FF0000, "straight alpha is preserved");
        check(PackPreviewAssetLogic.over(0xFF0000FF, 0x80FF0000) == 0xFF80007F, "partial alpha blends correctly");
        Random random = new Random(27);
        for (int i = 0; i < 1000; i++) {
            int background = random.nextInt();
            int foreground = random.nextInt();
            int actual = PackPreviewAssetLogic.over(background, foreground);
            double fa = (foreground >>> 24) / 255.0;
            double ba = (background >>> 24) / 255.0;
            double alpha = fa + ba * (1 - fa);
            check(Math.abs((actual >>> 24) - Math.round(alpha * 255)) <= 1, "composited alpha matches source-over");
            if (alpha > 0) {
                for (int shift = 0; shift <= 16; shift += 8) {
                    double f = foreground >>> shift & 255;
                    double b = background >>> shift & 255;
                    long expected = Math.round((f * fa + b * ba * (1 - fa)) / alpha);
                    check(Math.abs((actual >>> shift & 255) - expected) <= 1, "composited color matches source-over");
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

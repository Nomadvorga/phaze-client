package vorga.phazeclient.implement.menu;

import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Offline guards against deferred shader selection followed by ambient GL texture mutations. */
public final class TextureFilteringChecks {
    private static final String MENU = "vorga/phazeclient/implement/menu/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (String name : List.of(
                "vorga/phazeclient/api/system/shape/implement/Image",
                MENU + "components/implement/other/BackgroundComponent",
                MENU + "components/implement/window/implement/settings/color/component/AlphaComponent",
                MENU + "components/implement/window/implement/settings/color/component/SaturationComponent",
                MENU + "components/implement/window/implement/settings/color/SettingColorPickerWindow",
                MENU + "components/implement/window/implement/settings/itempicker/ItemPickerColorWindow")) {
            for (MethodNode method : read(name).methods) {
                List<MethodInsnNode> calls = calls(method);
                check(calls.stream().noneMatch(call -> call.name.toLowerCase().contains("texparameter")
                                || call.name.equals("setFilter")),
                        name + "." + method.name + " never mutates ambient texture filtering during drawing");
                check(calls.stream().noneMatch(call -> call.name.startsWith("glGet")),
                        name + "." + method.name + " adds no per-draw GL queries");
            }
        }
        ClassNode font = read(MENU + "loading/LoadingFont");
        MethodNode load = font.methods.stream().filter(method -> method.name.equals("load")).findFirst().orElseThrow();
        MethodNode close = font.methods.stream().filter(method -> method.name.equals("close")).findFirst().orElseThrow();
        check(calls(load).stream().anyMatch(call -> call.owner.equals("com/mojang/blaze3d/platform/TextureUtil")
                        && call.name.equals("generateTextureId")), "bootstrap allocates through Minecraft's texture lifecycle");
        check(calls(close).stream().anyMatch(call -> call.owner.equals("com/mojang/blaze3d/platform/TextureUtil")
                        && call.name.equals("releaseTextureId")), "bootstrap disposal invalidates all cached texture bindings");
        check(calls(close).stream().noneMatch(call -> call.name.equals("glDeleteTextures")), "no raw bootstrap texture disposal");
        int bind = calls(load).stream().filter(call -> call.name.equals("bindTexture")).mapToInt(load.instructions::indexOf).min().orElseThrow();
        int filter = calls(load).stream().filter(call -> call.name.equals("glTexParameteri")).mapToInt(load.instructions::indexOf).min().orElseThrow();
        check(bind < filter, "bootstrap atlas is explicitly bound before filtering/upload");
        for (String name : List.of("LoadingFont", "LoadingGlState", "BootstrapLoadingRenderer")) {
            check(read(MENU + "loading/" + name).methods.stream().flatMap(method -> calls(method).stream())
                            .noneMatch(call -> call.name.equals("glBindTexture") || call.name.equals("glActiveTexture")),
                    "bootstrap texture units/bindings always go through the Minecraft cache: " + name);
        }
        for (String file : List.of("back_arrow", "settings", "trash", "reset", "file", "file_import", "edit", "share")) {
            linear("assets/minecraft/textures/" + file + ".png");
        }
        for (String file : List.of("hue", "alpha", "triangle")) linear("assets/minecraft/textures/color_picker/" + file + ".png");
        linear("assets/phaze/textures/menu/cross.png");
        // Preserve Image's raster fallback quality even if the MSDF icon atlas is unavailable.
        try (var files = Files.list(Path.of("src/main/resources/assets/phaze/textures/modules"))) {
            for (Path png : files.filter(file -> file.toString().endsWith(".png")).toList()) {
                linear("assets/phaze/textures/modules/" + png.getFileName());
            }
        }
        check(TextureFilteringChecks.class.getClassLoader().getResource("assets/minecraft/textures/atlas/blocks.png.mcmeta") == null,
                "does not override vanilla atlas filtering or mipmap policy");
        System.out.println("Texture filtering checks passed: " + checks + " (no GL context)");
    }

    private static void linear(String png) throws Exception {
        try (InputStream image = TextureFilteringChecks.class.getClassLoader().getResourceAsStream(png);
             InputStream metadata = TextureFilteringChecks.class.getClassLoader().getResourceAsStream(png + ".mcmeta")) {
            check(image != null && metadata != null, "bundled UI image and filter metadata are packaged: " + png);
            var texture = JsonParser.parseReader(new InputStreamReader(metadata, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("texture");
            check(texture != null && texture.get("blur").getAsBoolean(), "UI-only linear filter is defined at load time: " + png);
        }
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = TextureFilteringChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import vorga.phazeclient.implement.features.modules.other.TrapTimer;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Offline checks for vanilla debug charts, pack icons/text, HUD scale and background opacity. */
public final class HudCompatibilityChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        checks = 0;
        debugTextHooks();
        packIconDraw();
        packIconFit();
        packTextLayout();
        packTextFormatting();
        hudScaleLimits();
        hudBackgroundOpacity();
        System.out.println("HUD compatibility checks passed: " + checks);
    }

    private static void debugTextHooks() throws Exception {
        ClassNode mixin = readClass("vorga/phazeclient/mixins/DebugHudMixin");
        boolean left = false;
        boolean right = false;
        for (MethodNode method : mixin.methods) {
            AnnotationNode inject = findInject(method);
            if (inject == null) continue;
            List<?> targets = (List<?>) annotationValue(inject, "method");
            boolean cancellable = Boolean.TRUE.equals(annotationValue(inject, "cancellable"));
            check(!(targets.contains("render") && cancellable), "Custom F3 must not cancel vanilla chart rendering");
            if (targets.contains("drawLeftText") || targets.contains("drawRightText")) {
                check(cancellable && Integer.valueOf(1).equals(annotationValue(inject, "require")),
                        "both text replacements are required cancellable hooks");
                List<?> points = (List<?>) annotationValue(inject, "at");
                check(points.size() == 1 && "HEAD".equals(annotationValue((AnnotationNode) points.getFirst(), "value")),
                        "text replacements avoid building the unused vanilla columns");
                long customDraws = calls(method).stream().filter(call -> call.owner.equals(
                        "vorga/phazeclient/implement/features/modules/other/BetterF3Renderer") && call.name.equals("render")).count();
                if (targets.contains("drawLeftText")) {
                    left = true;
                    check(customDraws == 1, "custom text is drawn exactly once from the left column");
                } else {
                    right = true;
                    check(customDraws == 0, "hiding the right column does not duplicate custom text");
                }
            }
        }
        check(left && right, "both vanilla text columns are replaced independently of charts");

        MethodNode render = method(readClass("net/minecraft/client/gui/hud/DebugHud"), "render");
        for (String chart : Set.of("PieChart", "RenderingChart", "TickChart", "PacketSizeChart", "PingChart")) {
            check(calls(render).stream().anyMatch(call -> call.owner.equals(
                            "net/minecraft/client/gui/hud/debug/" + chart) && call.name.equals("render")),
                    "the preserved vanilla render path contains " + chart);
        }
        MethodNode keyboard = method(readClass("net/minecraft/client/Keyboard"), "processF3");
        for (String toggle : Set.of("toggleRenderingChart", "toggleRenderingAndTickCharts", "togglePacketSizeAndPingCharts")) {
            check(calls(keyboard).stream().anyMatch(call -> call.owner.equals("net/minecraft/client/gui/hud/DebugHud")
                    && call.name.equals(toggle)), "vanilla F3 key processing retains " + toggle);
        }
    }

    private static void packIconDraw() throws Exception {
        ClassNode module = readClass("vorga/phazeclient/implement/features/modules/hud/PackDisplayHud");
        MethodNode draw = method(module, "drawPackIcon");
        List<MethodInsnNode> textureDraws = calls(draw).stream().filter(call -> call.owner.equals(
                "net/minecraft/client/gui/DrawContext") && call.name.equals("drawTexture")).toList();
        check(textureDraws.size() == 1, "pack icon needs only one textured-quad draw");
        check(textureDraws.getFirst().desc.equals(
                        "(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIII)V"),
                "drawTexture uses separate destination size and full source-image region");
        List<String> dimensions = new ArrayList<>();
        for (AbstractInsnNode instruction : draw.instructions) {
            if (instruction == textureDraws.getFirst()) break;
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(module.name) && field.name.startsWith("cached") && field.desc.equals("I")) {
                dimensions.add(field.name);
            }
        }
        check(dimensions.subList(dimensions.size() - 6, dimensions.size()).equals(List.of(
                        "cachedDrawIconWidth", "cachedDrawIconHeight", "cachedIconWidth", "cachedIconHeight",
                        "cachedIconWidth", "cachedIconHeight")),
                "UVs sample the entire cached PNG, not a 28-by-28 corner");
        check(calls(draw).stream().noneMatch(call -> call.name.equals("loadIcon") || call.name.equals("setIconSize")
                        || call.name.equals("registerTexture") || call.owner.contains("NativeImage") || call.owner.startsWith("java/io/")),
                "rendering neither decodes nor uploads nor refits the cached image");
        check(calls(method(module, "updatePackName")).stream().noneMatch(call -> call.name.equals("loadIcon")
                        || call.owner.contains("NativeImage") || call.name.equals("registerTexture")),
                "the pack scan never decodes or uploads on the render thread");
        List<MethodInsnNode> requestCalls = calls(method(module, "requestIcon"));
        check(requestCalls.stream().anyMatch(call -> call.name.equals("supplyAsync"))
                        && requestCalls.stream().anyMatch(call -> call.name.equals("thenAcceptAsync")),
                "pack.png is decoded on a worker and handed back to the client thread");
        check(calls(method(module, "loadIcon")).stream().noneMatch(call -> call.name.equals("registerTexture")
                        || call.owner.endsWith("NativeImageBackedTexture")),
                "the worker step never touches GL textures");
        List<MethodInsnNode> applyCalls = calls(method(module, "applyIcon"));
        check(applyCalls.stream().anyMatch(call -> call.name.equals("releaseOwnedIcon"))
                        && applyCalls.stream().anyMatch(call -> call.name.equals("registerTexture")),
                "a new icon replaces the single owned texture, and the fallback releases it");
        check(calls(method(module, "onResourcesReloaded")).stream().noneMatch(call -> call.name.equals("releaseOwnedIcon")),
                "resource reload keeps the current icon until its replacement is decoded");
    }

    private static void packIconFit() throws Exception {
        PackDisplayHud module = PackDisplayHud.getInstance();
        Method size = PackDisplayHud.class.getDeclaredMethod("setIconSize", int.class, int.class);
        size.setAccessible(true);
        Field drawWidth = PackDisplayHud.class.getDeclaredField("cachedDrawIconWidth");
        Field drawHeight = PackDisplayHud.class.getDeclaredField("cachedDrawIconHeight");
        drawWidth.setAccessible(true);
        drawHeight.setAccessible(true);
        int previousWidth = module.getPackIconWidth();
        int previousHeight = module.getPackIconHeight();
        try {
            for (int[] dimensions : new int[][]{{16, 16}, {28, 28}, {64, 64}, {128, 128}, {512, 512},
                    {4096, 4096}, {128, 64}, {64, 128}, {320, 180}, {3, 5}, {1, 4096}, {4096, 1}, {0, 0}}) {
                size.invoke(module, dimensions[0], dimensions[1]);
                int sourceWidth = Math.max(1, dimensions[0]);
                int sourceHeight = Math.max(1, dimensions[1]);
                int width = drawWidth.getInt(module);
                int height = drawHeight.getInt(module);
                check(module.getPackIconWidth() == sourceWidth && module.getPackIconHeight() == sourceHeight,
                        "cached source dimensions retain the entire original image");
                check(width >= 1 && height >= 1 && Math.max(width, height) == PackDisplayHud.ICON_SIZE,
                        "square and rectangular icons fit wholly inside the reserved icon box");
                float fit = (float) PackDisplayHud.ICON_SIZE / Math.max(sourceWidth, sourceHeight);
                check(Math.abs(width - sourceWidth * fit) <= 1 && Math.abs(height - sourceHeight * fit) <= 1,
                        "fitting preserves aspect ratio within pixel rounding");
                int x = (PackDisplayHud.ICON_SIZE - width) / 2;
                int y = (PackDisplayHud.ICON_SIZE - height) / 2;
                check(x >= 0 && y >= 0 && x + width <= PackDisplayHud.ICON_SIZE && y + height <= PackDisplayHud.ICON_SIZE,
                        "the complete icon is centered without clipping");
            }
        } finally {
            size.invoke(module, previousWidth, previousHeight);
        }
    }

    private static void packTextLayout() throws Exception {
        ClassNode moduleClass = readClass("vorga/phazeclient/implement/features/modules/hud/PackDisplayHud");
        MethodNode render = method(readClass("vorga/phazeclient/implement/hud/CustomHudRenderer"), "renderPackDisplayText");
        List<MethodInsnNode> renderCalls = calls(render);
        MethodInsnNode left = renderCalls.stream().filter(call -> call.name.equals("getTextStartX")).findFirst().orElseThrow();
        AbstractInsnNode store = left.getNext();
        while (store != null && !(store instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ISTORE)) {
            store = store.getNext();
        }
        check(store != null, "pack text calculates its shared left edge once");
        int textX = ((VarInsnNode) store).var;
        List<MethodInsnNode> draws = renderCalls.stream().filter(call -> call.name.equals("drawText")).toList();
        check(draws.size() == 2, "one title draw and one cached description-line draw");
        for (MethodInsnNode draw : draws) {
            boolean sharedLeft = false;
            AbstractInsnNode argument = draw.getPrevious();
            for (int i = 0; argument != null && i < 20; i++, argument = argument.getPrevious()) {
                sharedLeft |= argument instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ILOAD && variable.var == textX;
            }
            check(sharedLeft, "title and every description line use exactly the same left coordinate");
        }
        for (MethodNode hotPath : List.of(render, method(moduleClass, "getPanelBaseWidth"))) {
            check(calls(hotPath).stream().noneMatch(call -> call.name.equals("getWidth") || call.name.equals("split")
                            || call.name.equals("splitDescription") || call.name.equals("literal")),
                    "pack text layout and rendering use cached styled lines and measured widths");
        }
        check(calls(method(moduleClass, "updatePackName")).stream().filter(call -> call.name.equals("splitDescription")).count() == 1,
                "styled descriptions are split only when the cached pack changes");

        PackDisplayHud module = PackDisplayHud.getInstance();
        Map<Field, Object> saved = new HashMap<>();
        for (String name : List.of("cachedIcon", "cachedTextWidth", "cachedDescriptionWidth", "cachedDescription",
                "cachedDescriptionLines")) {
            Field field = field(PackDisplayHud.class, name);
            saved.put(field, field.get(module));
        }
        boolean iconEnabled = module.displayPackIcon.isValue();
        boolean descriptionEnabled = module.displayDescription.isValue();
        try {
            field(PackDisplayHud.class, "cachedDescription").set(module, "Cached description");
            List<Text> lines = List.of(Text.literal("Cached description"));
            field(PackDisplayHud.class, "cachedDescriptionLines").set(module, lines);
            for (int[] widths : new int[][]{{100, 70}, {20, 160}, {0, 0}, {400, 1000}}) {
                field(PackDisplayHud.class, "cachedTextWidth").setInt(module, widths[0]);
                field(PackDisplayHud.class, "cachedDescriptionWidth").setInt(module, widths[1]);
                for (boolean present : new boolean[]{false, true}) {
                    field(PackDisplayHud.class, "cachedIcon").set(module, present ? Identifier.of("phaze", "test/pack_icon") : null);
                    for (boolean showIcon : new boolean[]{false, true}) {
                        module.displayPackIcon.setValue(showIcon);
                        float leftEdge = showIcon ? PackDisplayHud.ICON_SIZE + 4 : 4;
                        check(module.getTextStartX() == leftEdge,
                                "text keeps a 4px inset beside the icon slot, reserved even while the icon decodes");
                        for (boolean showDescription : new boolean[]{false, true}) {
                            module.displayDescription.setValue(showDescription);
                            int width = showDescription ? Math.max(widths[0], widths[1]) : widths[0];
                            check(module.getPanelBaseWidth(null) == leftEdge + width + 4,
                                    "the widest visible styled line has equal right padding");
                            check(module.getDescriptionLines(null) == lines, "rendering reuses the cached line list");
                        }
                    }
                }
            }
        } finally {
            for (Map.Entry<Field, Object> entry : saved.entrySet()) entry.getKey().set(module, entry.getValue());
            module.displayPackIcon.setValue(iconEnabled);
            module.displayDescription.setValue(descriptionEnabled);
        }
    }

    private static void packTextFormatting() throws Exception {
        List<Text> lines = splitDescription(Text.literal("§aЗелёный §lжирный\r\n§rОбычный\n§cКрасный 😃"));
        check(lines.stream().map(Text::getString).toList().equals(List.of("Зелёный жирный", "Обычный", "Красный 😃")),
                "legacy colors, CRLF, LF and supplementary characters retain their text");
        List<Run> first = runs(lines.getFirst());
        check(first.size() == 2 && first.get(0).text.equals("Зелёный ") && first.get(1).text.equals("жирный"),
                "descriptions cache formatting runs, not an object for every character");
        check(first.get(0).style.getColor().getRgb() == Formatting.GREEN.getColorValue() && !first.get(0).style.isBold()
                        && first.get(1).style.isBold(), "legacy color and bold formatting are preserved");
        check(runs(lines.get(1)).getFirst().style.getColor() == null && !runs(lines.get(1)).getFirst().style.isBold(),
                "legacy reset does not leak the preceding line's formatting");
        check(runs(lines.get(2)).getFirst().style.getColor().getRgb() == Formatting.RED.getColorValue(),
                "color codes remain effective after a line break");

        Text styled = Text.empty().setStyle(Style.EMPTY.withColor(0x336699).withItalic(true))
                .append(Text.literal("Первая\n").formatted(Formatting.BOLD))
                .append(Text.literal("Вторая").styled(style -> style.withColor(0xFF8822)));
        List<Text> inherited = splitDescription(styled);
        Run firstLine = runs(inherited.getFirst()).getFirst();
        Run secondLine = runs(inherited.get(1)).getFirst();
        check(firstLine.style.isBold() && firstLine.style.isItalic() && firstLine.style.getColor().getRgb() == 0x336699,
                "structured text retains inherited styling across newlines");
        check(!secondLine.style.isBold() && secondLine.style.isItalic() && secondLine.style.getColor().getRgb() == 0xFF8822,
                "explicit sibling styles override only their own properties");
        List<Run> recolored = runs(splitDescription(Text.literal("§lЖирный§cКрасный")).getFirst());
        check(recolored.getFirst().style.isBold() && !recolored.getLast().style.isBold(),
                "a legacy color resets previous legacy modifiers");
        for (String separator : List.of("\n", "\r", "\r\n")) {
            check(splitDescription(Text.literal("A" + separator + separator + "B" + separator)).stream()
                    .map(Text::getString).toList().equals(List.of("A", "", "B", "")),
                    "empty and trailing description lines are preserved");
        }
    }

    private static void hudScaleLimits() throws Exception {
        Set<String> classes = new LinkedHashSet<>();
        for (MethodNode method : readClass("vorga/phazeclient/core/Main").methods) {
            for (MethodInsnNode call : calls(method)) {
                if (call.name.equals("getInstance") && call.owner.startsWith("vorga/phazeclient/implement/features/modules/")) {
                    classes.add(call.owner.replace('/', '.'));
                }
            }
        }
        int hudCount = 0;
        List<Class<?>> samples = List.of(FpsHud.class, ArmorHud.class, InventoryHud.class, ScoreboardHud.class, TrapTimer.class, BossBarHud.class);
        for (String name : classes) {
            Class<?> type = Class.forName(name, false, HudCompatibilityChecks.class.getClassLoader());
            if (!RectHudModule.class.isAssignableFrom(type) && type != ArmorHud.class) continue;
            for (String method : List.of("getMaxHudScale", "getRenderHudScale")) {
                Class<?> scaleOwner = type == ArmorHud.class ? ArmorHud.class
                        : type == BossBarHud.class && method.equals("getRenderHudScale") ? BossBarHud.class : RectHudModule.class;
                check(type.getMethod(method).getDeclaringClass() == scaleOwner,
                        type.getSimpleName() + ": shared limit with an explicit native baseline for Boss Bar");
            }
            hudCount++;
            // Item-based HUD singletons need Minecraft registries. Their shared
            // final limit is checked above; all instances are exercised in the client test.
            if (!samples.contains(type)) continue;
            Object hud = type.getMethod("getInstance").invoke(null);
            Method getScale = type.getMethod("getHudScale");
            Method setScale = type.getMethod("setHudScale", float.class);
            float saved = (float) getScale.invoke(hud);
            float min = (float) type.getMethod("getMinHudScale").invoke(hud);
            try {
                check((float) type.getMethod("getMaxHudScale").invoke(hud) == 3.0F,
                        type.getSimpleName() + ": every HUD exposes the same 3x limit");
                for (float scale : new float[]{-1.0F, 0.0F, min, 1.0F, 2.5F, 3.0F, 3.25F, 5.0F, 8.0F, 100.0F}) {
                    setScale.invoke(hud, scale);
                    float expected = Math.max(min, Math.min(3.0F, scale));
                    check((float) getScale.invoke(hud) == expected,
                            type.getSimpleName() + ": editor/config scale clamps to the shared limits");
                    check((float) type.getMethod("getRenderHudScale").invoke(hud) == expected * (type == BossBarHud.class ? 1.0F : 2.0F),
                            type.getSimpleName() + ": limiting scale preserves the existing visual baseline");
                }
            } finally {
                setScale.invoke(hud, saved);
            }
        }
        check(hudCount >= 25, "scale limits cover the full registered HUD catalog, not just a few examples");
        System.out.println("Uniform 3x scale checked for " + hudCount + " HUDs");
    }

    private static void hudBackgroundOpacity() throws Exception {
        for (Class<?> type : List.of(ArmorHud.class, RectHudModule.class)) {
            MethodNode resolver = method(readClass(type.getName().replace('.', '/')), "getResolvedBackgroundColor");
            List<MethodInsnNode> vanillaColors = calls(resolver).stream().filter(call -> call.owner.equals(
                    "net/minecraft/client/option/GameOptions") && call.name.equals("getTextBackgroundColor")
                    && call.desc.equals("(F)I")).toList();
            check(vanillaColors.size() == 1, type.getSimpleName() + ": Vanilla retains Minecraft's opacity override");
            MethodInsnNode vanillaColor = vanillaColors.getFirst();
            check(vanillaColor.getPrevious() instanceof LdcInsnNode constant && Float.valueOf(0.5F).equals(constant.cst),
                    type.getSimpleName() + ": Vanilla uses the shared 0.5 background opacity");
            check(vanillaColor.getNext().getOpcode() == Opcodes.IRETURN,
                    type.getSimpleName() + ": custom-preset opacity does not alter Vanilla's resolved color");
        }

        ArmorHud armor = ArmorHud.getInstance();
        String savedPreset = armor.backgroundPreset.getSelected();
        float savedOpacity = armor.backgroundOpacity.getValue();
        try {
            for (String preset : List.of("Lunar Blue", "Custom Color", "Gradient")) {
                armor.backgroundPreset.setSelected(preset);
                int rgb = armor.getResolvedBackgroundColor(null) & 0x00FFFFFF;
                for (int opacity : new int[]{0, 20, 50, 100}) {
                    armor.backgroundOpacity.setValue(opacity);
                    int color = armor.getResolvedBackgroundColor(null);
                    check((color >>> 24) == Math.round((opacity / 100.0F) * 255.0F),
                            "Armor " + preset + ": explicit background opacity remains effective");
                    check((color & 0x00FFFFFF) == rgb, "Armor " + preset + ": opacity preserves the selected RGB color");
                }
            }
        } finally {
            armor.backgroundPreset.setSelected(savedPreset);
            armor.backgroundOpacity.setValue(savedOpacity);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Text> splitDescription(Text text) throws Exception {
        Method split = PackDisplayHud.class.getDeclaredMethod("splitDescription", Text.class);
        split.setAccessible(true);
        return (List<Text>) split.invoke(null, text);
    }

    private static List<Run> runs(Text text) {
        List<Run> runs = new ArrayList<>();
        text.visit((style, fragment) -> {
            if (!fragment.isEmpty()) runs.add(new Run(fragment, style));
            return java.util.Optional.empty();
        }, text.getStyle());
        return runs;
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Run(String text, Style style) {
    }

    private static ClassNode readClass(String name) throws Exception {
        try (InputStream input = HudCompatibilityChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class: " + name);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static AnnotationNode findInject(MethodNode method) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations.stream().filter(annotation -> annotation.desc.equals(
                "Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElse(null);
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

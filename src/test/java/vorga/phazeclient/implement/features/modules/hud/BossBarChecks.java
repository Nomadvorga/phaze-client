package vorga.phazeclient.implement.features.modules.hud;

import com.google.gson.JsonObject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vorga.phazeclient.api.feature.module.setting.Setting;
import vorga.phazeclient.api.system.hud.BossBarColorContext;
import vorga.phazeclient.base.util.Lang;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Settings, clock, migration and vanilla-path contracts without a game window. */
public final class BossBarChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        settings();
        rainbowClock();
        tintScope();
        migration();
        cacheBudget();
        vanillaHooks();
        System.out.println("Boss Bar checks passed: " + checks);
    }

    private static void settings() {
        BossBarHud module = BossBarHud.getInstance();
        check(module.getIdentifier().equals("bossbar_tweaker") && module.getName().equals("bossbar_tweaker"),
                "both config formats and server rules retain the original ID");
        check(module.getVisibleName().equals("Boss Bar"), "canonical module title is Boss Bar");
        List<Setting> order = List.of(module.mainSection, module.renderHealth, module.renderText,
                module.colorOptions, module.colorMode, module.fillColor, module.rainbowSpeed);
        check(module.settings().equals(order), "only General and Color controls are registered, in the requested order");
        check(module.mainSection.isFullWidth() && module.colorOptions.isFullWidth(), "section dividers span both columns");
        check(!module.renderHealth.isFullWidth() && !module.renderText.isFullWidth(), "health and text share one row");
        check(Boolean.TRUE.equals(module.renderHealth.getDefaultValue())
                && Boolean.FALSE.equals(module.renderText.getDefaultValue()), "health on, text off by default");
        check(module.colorMode.getList().equals(List.of("Custom", "Rainbow"))
                && module.colorMode.isSelected("Custom"), "arrow selector defaults to Custom");
        check(module.fillColor.getDefaultColor() == 0xFFAA55FF && module.fillColor.isPopupRow()
                && module.fillColor.isNoAlpha(), "opaque purple fill uses the existing popup picker");
        check(module.rainbowSpeed.getMin() == 0.1F && module.rainbowSpeed.getMax() == 1.0F
                && module.rainbowSpeed.getDefaultValue() == 0.5F && module.rainbowSpeed.isFullWidth(),
                "rainbow slider has the requested range, default and separate row");
        String language = Lang.getActive();
        float scale = module.getHudScale();
        try {
            for (String locale : List.of(Lang.EN, Lang.RU, Lang.EN)) {
                Lang.setActive(locale);
                boolean ru = locale.equals(Lang.RU);
                check(module.mainSection.getName().equals(ru ? "Общие" : "General"), "General is localized");
                check(module.colorOptions.getName().equals(ru ? "Цвет" : "Color"), "Color is localized");
                check(module.renderHealth.getName().equals(ru ? "Показывать здоровье" : "Render Health"), "health label");
                check(module.renderText.getName().equals(ru ? "Показывать текст" : "Render Text"), "text label");
                check(module.fillColor.getName().equals(ru ? "Цвет заполнения" : "Fill Color"), "fill label");
                check(module.rainbowSpeed.getName().equals(ru ? "Скорость радуги" : "Rainbow Speed"), "speed label");
                check(module.getDisplayDescription().equals(ru ? "Настраиваемый боссбар" : "Customizable boss bar"),
                        "description uses the requested wording");
                check(module.getVisibleName().equals("Boss Bar") && module.settings().equals(order),
                        "localization changes neither title nor setting identities");
                for (Setting setting : order) {
                    check(setting.getModuleContext().equals("bossbar_tweaker"), "localized setting remains tied to the old ID");
                    if (ru) check(!setting.getDescription().equals(setting.getRawDescription())
                            || setting.getRawDescription().isEmpty(), "every new tooltip is translated");
                }
                for (String mode : List.of("Custom", "Rainbow", "Custom")) {
                    module.colorMode.setSelected(mode);
                    boolean rainbow = mode.equals("Rainbow");
                    check(module.fillColor.isVisible() != rainbow && module.rainbowSpeed.isVisible() == rainbow,
                            "only the applicable color control is visible");
                    check(module.colorMode.isFullWidth() == rainbow, "Custom pairs with the picker; Rainbow occupies its own row");
                }
            }
            for (float requested : new float[]{-100, 0, 0.5F, 1, 1.5F, 2, 2.5F, 3, 4, 100}) {
                module.setHudScale(requested);
                float expected = Math.max(0.5F, Math.min(3.0F, requested));
                check(module.getHudScale() == expected && module.getRenderHudScale() == expected,
                        "Boss Bar scales from the vanilla baseline, not the other HUDs' 2x baseline");
            }
        } finally {
            module.colorMode.reset();
            module.setHudScale(scale);
            Lang.setActive(language);
        }
    }

    private static void rainbowClock() {
        close(BossBarHud.rainbowCycleSeconds(0.1F), 20, "minimum speed is a 20-second full cycle");
        close(BossBarHud.rainbowCycleSeconds(0.5F), 5, "default speed is a 5-second full cycle");
        close(BossBarHud.rainbowCycleSeconds(1), 1, "maximum speed is a 1-second full cycle");
        close(BossBarHud.rainbowCycleSeconds(-1), 20, "slow endpoint clamps");
        close(BossBarHud.rainbowCycleSeconds(5), 1, "fast endpoint clamps");
        float previous = 21;
        for (int i = 0; i <= 90; i++) {
            float speed = 0.1F + i / 100.0F;
            float seconds = BossBarHud.rainbowCycleSeconds(speed);
            check(seconds <= previous && seconds >= 1 && seconds <= 20, "cycle length decreases continuously as speed increases");
            previous = seconds;
            long period = Math.round(seconds * 1_000_000_000.0);
            close(BossBarHud.advanceRainbowHue(0.25F, period, speed), 0.25F, "elapsed time completes exactly one full circle");
            close(BossBarHud.advanceRainbowHue(0.8F, period / 2, speed), 0.3F, "hue wraps through red without resetting time");
            close(BossBarHud.advanceRainbowHue(0.3F, -10, speed), 0.3F, "negative deltas never rewind the color");
        }
        for (int fps : new int[]{30, 60, 120, 240, 1000}) {
            float hue = 0.125F;
            long step = 1_000_000_000L / fps;
            for (int frame = 0; frame < fps; frame++) hue = BossBarHud.advanceRainbowHue(hue, step, 0.5F);
            close(hue, BossBarHud.advanceRainbowHue(0.125F, step * fps, 0.5F), "rainbow duration is independent of display FPS");
        }
    }

    private static void tintScope() {
        int saved = BossBarColorContext.setTint(0xFFFFFFFF);
        try {
            for (int color : new int[]{0, 0xFFFFFFFF, 0x80FFFFFF, 0xFF123456, 0x4060A0E0}) {
                check(BossBarColorContext.applyTint(color) == color, "outside progress scope the vertex color is unchanged");
                for (int tint : new int[]{0xFFFFFFFF, 0xFFFF0000, 0xFFAA55FF, 0x80402010, 0}) {
                    int previous = BossBarColorContext.setTint(tint);
                    int result = BossBarColorContext.applyTint(color);
                    for (int shift : new int[]{24, 16, 8, 0}) {
                        check(((result >>> shift) & 255) == ((color >>> shift) & 255) * ((tint >>> shift) & 255) / 255,
                                "tint multiplies each existing channel rather than overwriting another mod's color");
                    }
                    int nested = BossBarColorContext.setTint(0xFF00FF00);
                    check(BossBarColorContext.applyTint(0xFFFFFFFF) == 0xFF00FF00, "nested scope has its own color");
                    BossBarColorContext.setTint(nested);
                    check(BossBarColorContext.applyTint(color) == result, "nested scope restores its caller's tint");
                    BossBarColorContext.setTint(previous);
                    check(BossBarColorContext.applyTint(color) == color, "background, text and later GUI vertices remain untouched");
                }
            }
        } finally {
            BossBarColorContext.setTint(saved);
        }
    }

    private static void migration() {
        for (boolean nested : new boolean[]{true, false}) {
            for (float scale : new float[]{0.5F, 1, 1.5F, 3, 8}) {
                for (float offset : new float[]{-50, 0, 25, 200}) {
                    for (boolean hidden : new boolean[]{false, true}) {
                        JsonObject source = new JsonObject();
                        JsonObject old = nested ? new JsonObject() : source;
                        if (nested) source.add("settings", old);
                        source.addProperty("enabled", true);
                        old.addProperty("Scale", scale);
                        old.addProperty("Y Offset", offset);
                        old.addProperty("Hide Bossbar", hidden);
                        JsonObject snapshot = source.deepCopy();
                        JsonObject migrated = BossBarHud.migrateLegacyData(source, 1280, 720, 2);
                        JsonObject settings = nested ? migrated.getAsJsonObject("settings") : migrated;
                        float expected = Math.min(3, scale);
                        close(migrated.get("hud_scale").getAsFloat(), expected, "legacy size becomes bounded HUD scale");
                        close(migrated.get("hud_x").getAsFloat(), 640 - 182 * expected, "legacy centered X is preserved");
                        close(migrated.get("hud_y").getAsFloat(), (12 * expected + offset) * 2, "legacy offset and scaling preserve actual Y");
                        check(settings.get("Render Health").getAsBoolean() != hidden, "old Hide Bossbar maps to visibility controls");
                        check(!hidden || !settings.get("Render Text").getAsBoolean(), "hidden old bars stay completely hidden");
                        check(source.equals(snapshot), "loading never mutates the stored legacy JSON");
                        check(!settings.has("Scale") && !settings.has("Y Offset") && !settings.has("Hide Bossbar"), "obsolete settings are removed from the migrated copy");
                        check(BossBarHud.migrateLegacyData(migrated, 1280, 720, 2) == migrated, "new config migration is a no-allocation no-op");
                        check(migrated.get("hud_ref_w").getAsInt() == 1280 && migrated.get("hud_ref_h").getAsInt() == 720,
                                "migrated pixels retain the window reference");
                        source.addProperty("hud_scale", 2.5F);
                        source.addProperty("hud_x_ratio", 0.3F);
                        source.addProperty("hud_y_ratio", 0.2F);
                        old.addProperty("Render Health", true);
                        old.addProperty("Render Text", true);
                        JsonObject newer = BossBarHud.migrateLegacyData(source, 1280, 720, 2);
                        check(newer.get("hud_scale").getAsFloat() == 2.5F && !newer.has("hud_x")
                                && newer.get("hud_x_ratio").getAsFloat() == 0.3F, "new HUD transforms take precedence over leftover legacy keys");
                        JsonObject newerSettings = nested ? newer.getAsJsonObject("settings") : newer;
                        check(newerSettings.get("Render Health").getAsBoolean() && newerSettings.get("Render Text").getAsBoolean(),
                                "explicit new visibility takes precedence over old Hide Bossbar");
                    }
                }
            }
            for (String key : List.of("hud_x", "hud_y", "hud_x_ratio", "hud_y_ratio", "hud_ref_w", "hud_ref_h", "hud_auto_position")) {
                JsonObject source = new JsonObject();
                JsonObject old = nested ? new JsonObject() : source;
                if (nested) source.add("settings", old);
                old.addProperty("Scale", 1.5F);
                old.addProperty("Y Offset", 25);
                if (key.equals("hud_auto_position")) source.addProperty(key, true);
                else source.addProperty(key, key.startsWith("hud_ref_") ? 500 : 0.2F);
                JsonObject snapshot = source.deepCopy();
                JsonObject migrated = BossBarHud.migrateLegacyData(source, 1280, 720, 2);
                check(migrated.get(key).equals(source.get(key)), "each explicit new position field beats legacy data: " + key);
                check(key.equals("hud_x") || !migrated.has("hud_x"), "partial new positioning is not filled with legacy pixels: " + key);
                check(source.equals(snapshot), "mixed old/new migration preserves its input: " + key);
            }
        }
        JsonObject bad = new JsonObject();
        bad.addProperty("Scale", "broken");
        bad.addProperty("Y Offset", "broken");
        bad.addProperty("Hide Bossbar", "broken");
        JsonObject safe = BossBarHud.migrateLegacyData(bad, 1280, 720, 2);
        check(safe.get("hud_scale").getAsFloat() == 1 && safe.get("hud_y").getAsFloat() == 24,
                "malformed legacy fields cannot discard valid module data");
    }

    private static void cacheBudget() throws Exception {
        BossBarHud module = BossBarHud.getInstance();
        module.state = true;
        module.colorMode.setSelected("Custom");
        module.renderHealth.setValue(true);
        module.renderText.setValue(false);
        setField(module, "liveBars", true);
        setField(module, "cacheInitialized", false);
        long now = 1_000_000_000L;
        check(module.shouldRefreshExordium(now), "first Exordium capture is invalidated once");
        for (int frame = 1; frame <= 1000; frame++) {
            check(!module.shouldRefreshExordium(now + frame * 1_000_000L), "unchanged Custom retains Exordium's normal data cache");
        }
        module.fillColor.setColor(0xFF00FF88);
        check(module.shouldRefreshExordium(now + 1_000_000_001L), "picker changes invalidate Custom immediately");
        module.colorMode.setSelected("Rainbow");
        now += 2_000_000_000L;
        check(module.shouldRefreshExordium(now), "switching to Rainbow invalidates the previous Custom capture");
        int refreshes = 0;
        for (int frame = 1; frame <= 240; frame++) {
            if (module.shouldRefreshExordium(now + frame * (1_000_000_000L / 240))) refreshes++;
        }
        check(refreshes <= 60 && refreshes >= 40, "additional Rainbow recaptures are bounded at high display FPS: " + refreshes);
        now += 2_000_000_000L;
        module.shouldRefreshExordium(now);
        module.rainbowSpeed.setValue(1);
        check(module.shouldRefreshExordium(now + 1), "speed changes do not wait for the pacing cooldown");
        module.renderHealth.setValue(false);
        check(module.shouldRefreshExordium(now + 2), "hiding health invalidates its last capture");
        check(!module.shouldRefreshExordium(now + 1_000_000_000L), "hidden progress does not animate Exordium");
        module.renderHealth.setValue(true);
        setField(module, "liveBars", false);
        module.shouldRefreshExordium(now + 2_000_000_000L);
        check(!module.shouldRefreshExordium(now + 3_000_000_000L), "empty boss-bar cache does not animate");
        module.state = false;
        check(module.shouldRefreshExordium(now + 4_000_000_000L), "disabling restores vanilla with one invalidation");
        check(!module.shouldRefreshExordium(now + 5_000_000_000L), "disabled module adds no recurring recaptures");
        module.fillColor.reset();
        module.colorMode.reset();
        module.rainbowSpeed.reset();
        module.renderHealth.reset();
    }

    private static void vanillaHooks() throws Exception {
        ClassNode mixin = readClass("vorga/phazeclient/mixins/BossBarHudMixin");
        MethodNode render = method(mixin, "phaze$renderTransformed");
        check(hasAnnotation(render, "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"), "vanilla render is wrapped, not replaced or cancelled");
        check(calls(render).stream().filter(call -> call.owner.equals("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
                && call.name.equals("call")).count() == 2, "enabled and disabled branches both delegate to the original hook chain");
        check(!render.tryCatchBlocks.isEmpty() && calls(render).stream().filter(call -> call.name.equals("pop")).count() == 2,
                "matrix restoration is protected by finally, including foreign cancellation/exception paths");
        MethodNode tint = method(mixin, "phaze$tintProgress");
        check(!tint.tryCatchBlocks.isEmpty() && calls(tint).stream().filter(call -> call.name.equals("setTint")).count() == 3,
                "progress tint is restored on every exit path");
        MethodNode preview = method(mixin, "phaze$renderPreview");
        check(calls(preview).stream().noneMatch(call -> call.owner.equals("java/util/Map") && call.name.equals("put")),
                "editor preview never inserts fake events into the real map");
        check(calls(preview).stream().anyMatch(call -> call.name.equals("phaze$renderBossBar")), "preview delegates to the private vanilla renderer through an invoker");
        ClassNode vanilla = readClass("net/minecraft/client/gui/hud/BossBarHud");
        check(vanilla.methods.stream().anyMatch(m -> m.name.equals("renderBossBar")
                        && m.desc.equals("(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V")
                        && (m.access & Opcodes.ACC_PRIVATE) != 0), "preview invoker matches the real private vanilla overload");
        ClassNode context = readClass("net/minecraft/client/gui/DrawContext");
        check(context.methods.stream().anyMatch(m -> m.name.equals("drawTexturedQuad")
                && m.desc.equals("(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIIIFFFFI)V")), "vertex tint targets the exact 1.21.4 signature");
        for (MethodNode hot : readClass("vorga/phazeclient/implement/features/modules/hud/BossBarHud").methods) {
            if (!List.of("beginFrame", "shouldRefreshExordium", "applyTransform", "updateLayout").contains(hot.name)) continue;
            check(calls(hot).stream().noneMatch(call -> call.owner.contains("NativeImage") || call.owner.contains("Framebuffer")
                    || call.name.equals("registerTexture") || call.owner.startsWith("java/io/")), "hot path neither rebuilds textures nor creates framebuffers nor does I/O");
        }
        ClassNode inGame = readClass("net/minecraft/client/gui/hud/InGameHud");
        check(calls(method(inGame, "method_55808")).stream().anyMatch(call -> call.owner.equals(vanilla.name)
                && call.name.equals("render")), "NoRender suppression matches Exordium's actual vanilla layer");
    }

    private static void setField(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static ClassNode readClass(String name) throws Exception {
        try (InputStream input = BossBarChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(input != null, "class available: " + name);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static boolean hasAnnotation(MethodNode method, String name) {
        List<AnnotationNode> annotations = new ArrayList<>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations.stream().anyMatch(annotation -> annotation.desc.equals(name));
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.00002F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

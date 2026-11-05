package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import vorga.phazeclient.api.feature.module.setting.implement.SelectSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.animation.Interpolation;
import vorga.phazeclient.base.util.animation.Interpolations;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** Actual column tracking and Sodium packing, with a deterministic clock and no GL/server services. */
public final class ChunkAnimatorChecks {
    private static final String SUBJECT = "vorga/phazeclient/implement/features/modules/other/ChunkAnimator";
    private static final String MIXIN = "vorga/phazeclient/mixins/sodium/DefaultChunkRendererChunkAnimatorMixin";
    private static final String CHECKS = "vorga/phazeclient/implement/features/modules/other/ChunkAnimatorChecks";
    private static final long START = 1_000_000L;
    private static long now = START;
    private static int checks;

    public static void main(String[] args) throws Exception {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        integrationContract();
        Harness harness = new Harness();
        timing(harness);
        directions(harness);
        columnOffsets(harness);
        lateSections(harness);
        shaderFallback(harness);
        sodiumPayload(harness);
        disabledAndNonSpatial(harness);
        System.out.println("Chunk animation checks passed: " + checks);
    }

    private static void integrationContract() throws Exception {
        ClassNode subject = read(SUBJECT);
        check(subject.fields.stream().noneMatch(field -> field.name.equals("playerYAtRegister")),
                "Distance has no player-altitude reference");
        for (String name : new String[]{"getYOffset", "writeRegionSectionYOffsets", "getRegionMagnitude"}) {
            boolean sharedTiming = false;
            for (AbstractInsnNode instruction : method(subject, name).instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    sharedTiming |= call.owner.equals(SUBJECT) && call.name.equals("offsetMagnitude");
                    check(!call.owner.equals("net/minecraft/client/MinecraftClient"),
                            "offset calculation does not read the player's height: " + name);
                }
            }
            check(sharedTiming, "all render paths use the same Duration/easing calculation: " + name);
        }
        check(hasCall(method(subject, "writeAnimationDirectionPerSection"), SUBJECT, "writeAnimationDirection"),
                "Sodium uses the same sign convention as vanilla and the Iris fallback");
        Set<String> chunkEvents = new java.util.HashSet<>();
        for (AbstractInsnNode instruction : method(subject, "<init>").instructions) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientChunkEvents")) {
                chunkEvents.add(field.name);
            }
        }
        check(chunkEvents.equals(Set.of("CHUNK_LOAD", "CHUNK_UNLOAD")), "column tracking remains wired to real load/unload events");
        check(hasCall(method(read("vorga/phazeclient/mixins/WorldRendererChunkAnimatorMixin"),
                        "phaze$chunkAnimatorModelOffset"), SUBJECT, "writeAnimationDirection"),
                "vanilla applies the shared direction to its model offset");
        check(hasCall(method(read(MIXIN), "phaze$fallbackRegionOffset"), SUBJECT, "writeAnimationDirection"),
                "Iris fallback applies the shared direction to its region offset");
    }

    private static void timing(Harness harness) throws Exception {
        for (String name : Interpolations.getAllNames()) {
            Interpolation curve = Interpolations.getByName(name);
            for (int distance : new int[]{8, 64, 256}) {
                for (long duration : new long[]{100, 600, 1200, 5000}) {
                    near(harness.magnitude(distance, 0, duration, curve), distance, name + " starts at exactly Distance");
                    near(harness.magnitude(distance, -1, duration, curve), distance, "clock correction retains the entry position");
                    for (long elapsed : new long[]{1, duration / 4, duration / 2, duration - 1}) {
                        float magnitude = harness.magnitude(distance, elapsed, duration, curve);
                        near(magnitude, (float) Math.max(0, distance * (1 - curve.interpolate((double) elapsed / duration))),
                                name + " uses elapsed/Duration, not frame count");
                        check(Float.isFinite(magnitude) && magnitude >= 0, "easing cannot flip the entry direction");
                    }
                    near(harness.magnitude(distance, duration, duration, curve), 0, "lands exactly at Duration");
                    near(harness.magnitude(distance, duration + 1, duration, curve), 0, "stays at its original position");
                }
            }
        }
        near(harness.magnitude(8, 0, 0, Interpolations.LINEAR), 8, "invalid duration has a safe initial frame");
        near(harness.magnitude(8, 1, 0, Interpolations.LINEAR), 0, "invalid duration is clamped to one millisecond");
    }

    private static void directions(Harness harness) throws Exception {
        for (String type : new String[]{"Bottom", "Top", "Side", "Fade", "Scale"}) {
            harness.type.selected(type);
            for (String side : new String[]{"North", "South", "East", "West"}) {
                harness.side.selected(side);
                float[] expected = switch (type) {
                    case "Bottom" -> new float[]{0, -1, 0};
                    case "Top" -> new float[]{0, 1, 0};
                    case "Side" -> switch (side) {
                        case "North" -> new float[]{0, 0, -1};
                        case "South" -> new float[]{0, 0, 1};
                        case "East" -> new float[]{1, 0, 0};
                        default -> new float[]{-1, 0, 0};
                    };
                    default -> new float[3];
                };
                check(Arrays.equals(harness.direction(false), expected), "vanilla/Iris direction: " + type + "/" + side);
                check(Arrays.equals(harness.direction(true), expected), "Sodium direction: " + type + "/" + side);
            }
        }
    }

    private static void columnOffsets(Harness harness) throws Exception {
        int[] slots = {slot(2, 0, 5), slot(2, 1, 5), slot(2, 2, 5), slot(2, 3, 5)};
        for (String type : new String[]{"Bottom", "Top"}) {
            for (Region region : new Region[]{new Region(0, -64, 0), new Region(-128, 64, 256), new Region(0, 256, 0)}) {
                harness.configure(type);
                long key = harness.freshColumn(region, 2, 5);
                float[] out = new float[256];
                Arrays.fill(out, 999);
                check(harness.offsets(region, slots, out), "freshly loaded column begins animating");
                float sign = type.equals("Bottom") ? -1 : 1;
                for (int y = 0; y < 4; y++) {
                    near(out[slots[y]], 8, "all section heights start at Distance=8");
                    float home = region.originY + y * 16;
                    near(home + out[slots[y]] * sign, home + sign * 8, type + " starts relative to the section, not the player");
                }
                check(harness.columns.size() == 1 && harness.columns.get(key) == START,
                        "all four section slots share one column timestamp");
                check(!harness.pending.contains(key), "first drawable section consumes CHUNK_LOAD exactly once");
                near(out[slot(3, 0, 5)], 0, "reused output clears sections that are not drawable");

                now = START + 150;
                Region higher = new Region(region.originX, region.originY + 64, region.originZ);
                check(harness.offsets(higher, slots, out), "another vertical region joins the same column clock");
                for (int index : slots) near(out[index], 6, "all Y strata share quarter-duration progress");
                near(harness.vanilla(new BlockPos((region.originX >> 4) * 16 + 32, higher.originY,
                        (region.originZ >> 4) * 16 + 80)), 6, "vanilla uses the same column timing as Sodium");
                check(harness.columns.get(key) == START, "later sections cannot restart the timer");

                now = START + 600;
                check(!harness.offsets(region, slots, out), "Sodium finishes at the configured Duration");
                for (float value : out) near(value, 0, "finished region uploads zero offset");
                near(harness.vanilla(new BlockPos(region.originX + 32, region.originY, region.originZ + 80)),
                        0, "vanilla finishes at the same Duration");

                now = START + 10_000;
                harness.columns.clear(); // Equivalent to lazy eviction after completion.
                check(!harness.offsets(region, slots, out) && harness.columns.isEmpty(),
                        "frustum return after eviction cannot register or replay an animation");
                near(harness.vanilla(new BlockPos(region.originX + 32, region.originY, region.originZ + 80)),
                        0, "vanilla frustum return cannot replay an animation either");
                harness.pending.add(key);
                check(harness.offsets(region, slots, out), "a genuine new CHUNK_LOAD can animate the same coordinates again");
                for (int index : slots) near(out[index], 8, "fresh reload starts at full Distance again");
            }
        }
    }

    private static void lateSections(Harness harness) throws Exception {
        harness.configure("Bottom");
        Region region = new Region(-128, -64, -128);
        long key = harness.freshColumn(region, 7, 7);
        float[] out = new float[256];
        check(harness.offsets(region, new int[]{slot(7, 0, 7)}, out), "first section starts its column");
        now = START + 300;
        check(harness.offsets(region, new int[]{slot(7, 3, 7)}, out), "late section joins an in-flight column");
        near(out[slot(7, 3, 7)], 4, "late upload joins halfway, not eight blocks from its home position");
        check(harness.columns.get(key) == START, "late upload retains the original start time");
        now = START + 600;
        check(!harness.offsets(region, new int[]{slot(7, 0, 7), slot(7, 3, 7)}, out), "both early and late sections land together");
    }

    private static void shaderFallback(Harness harness) throws Exception {
        for (String type : new String[]{"Bottom", "Top"}) {
            for (int height : new int[]{-64, 64, 256}) {
                harness.configure(type);
                Region region = new Region(128, height, -128);
                int[] slots = {0};
                near(harness.fallback(region, slots), 8, "Iris fallback starts at Distance");
                long key = ChunkSectionPos.asLong(8, height >> 4, -8);
                now = START + 300;
                near(harness.fallback(region, new int[]{0, 1, 2, 3}), 4, "new drawable sections do not restart a fallback region");
                check(harness.regions.get(key) == START, "fallback region retains its initial timestamp");
                now = START + 600;
                near(harness.fallback(region, slots), 0, "Iris fallback reaches home at Duration");
                now = START + 10_000;
                near(harness.fallback(region, slots), 0, "Iris fallback frustum return does not restart the region");
            }
        }
    }

    private static void sodiumPayload(Harness harness) throws Exception {
        Region region = new Region(0, -64, 0);
        int[] slots = {slot(1, 0, 2), slot(1, 1, 2), slot(1, 2, 2), slot(1, 3, 2)};
        for (String type : new String[]{"Bottom", "Top"}) {
            harness.configure(type);
            harness.freshColumn(region, 1, 2);
            float sign = type.equals("Bottom") ? -1 : 1;
            for (int elapsed : new int[]{0, 150, 300, 599, 600}) {
                now = START + elapsed;
                float[] vec4 = new float[1024];
                Arrays.fill(vec4, 999);
                check(harness.pack(region, slots, 1, vec4) == (elapsed < 600), "actual Sodium packer reports active payloads only before Duration");
                for (int index = 0; index < 256; index++) {
                    boolean drawable = index >= slots[0] && index <= slots[3];
                    near(vec4[index * 4], 0, "vertical entry does not shift X");
                    near(vec4[index * 4 + 1], drawable ? sign * 8 * (1 - elapsed / 600.0F) : 0,
                            "UBO stores magnitude times direction exactly once");
                    near(vec4[index * 4 + 2], 0, "vertical entry does not shift Z");
                    near(vec4[index * 4 + 3], 1, "offset payload preserves fade/scale identity");
                }
            }
        }
    }

    private static void disabledAndNonSpatial(Harness harness) throws Exception {
        Region region = new Region(0, 64, 0);
        int[] slots = {0, 1, 2, 3};
        for (String type : new String[]{"Fade", "Scale"}) {
            harness.configure(type);
            harness.freshColumn(region, 0, 0);
            float[] offsets = new float[256];
            check(!harness.offsets(region, slots, offsets), "non-spatial modes do not translate sections");
            near(harness.vanilla(new BlockPos(0, 64, 0)), 0, "non-spatial modes retain vanilla position");
            float[] vec4 = new float[1024];
            int mode = type.equals("Fade") ? 2 : 3;
            check(harness.pack(region, slots, mode, vec4), "fade/scale keep their independent progress path");
            near(vec4[3], 0, "fade/scale begin at zero progress");
            now = START + 600;
            check(!harness.pack(region, slots, mode, vec4), "fade/scale also finish at Duration");
            near(vec4[3], 1, "fade/scale restore full progress");
        }
        harness.configure("Bottom");
        harness.freshColumn(region, 0, 0);
        harness.enabled(false);
        float[] out = new float[256];
        Arrays.fill(out, 999);
        check(!harness.offsets(region, slots, out), "disabled animator cannot move chunks");
        for (float value : out) near(value, 0, "disabled output clears old offsets");
        near(harness.vanilla(new BlockPos(0, 64, 0)), 0, "disabled vanilla path leaves the chunk at home");
        harness.reset.invoke(harness.subject);
        check(harness.columns.isEmpty() && harness.regions.isEmpty() && harness.pending.isEmpty(),
                "world reset clears all animation trackers");
    }

    /** Test-only replacement for System.currentTimeMillis in the isolated compiled animator. */
    public static long currentTimeMillis() { return now; }

    /** Only the three coordinate getters used by the real CPU-side Sodium packer. */
    public record Region(int originX, int originY, int originZ) {
        public int getOriginX() { return originX; }
        public int getOriginY() { return originY; }
        public int getOriginZ() { return originZ; }
    }

    private static final class Harness {
        final Class<?> typeClass;
        final Object subject;
        final SelectSetting type, side, easing;
        final ValueSetting distance, duration;
        final Map<Long, Long> columns, regions;
        final Set<Long> pending;
        final Method reset, magnitude, direction, sectionDirection, offsets, vanilla, fallback, pack;
        final int[] packedSlots;

        @SuppressWarnings("unchecked")
        Harness() throws Exception {
            OfflineClasses loader = new OfflineClasses();
            ClassNode node = read(SUBJECT);
            // Bypass unrelated server-policy services and their persisted client ID.
            // The real settings and animation methods stay intact. Event wiring is
            // checked above; its callbacks are not installed in a non-Fabric JVM.
            check(node.methods.stream().noneMatch(method -> method.name.equals("isEnabled")), "offline eligibility does not overwrite animator logic");
            MethodNode eligibility = new MethodNode(Opcodes.ACC_PUBLIC, "isEnabled", "()Z", null, null);
            eligibility.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            eligibility.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, "vorga/phazeclient/api/feature/module/Module", "state", "Z"));
            eligibility.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(eligibility);
            MethodNode constructor = method(node, "<init>");
            boolean registration = false;
            int removedRegistrations = 0;
            for (AbstractInsnNode instruction = constructor.instructions.getFirst(); instruction != null;) {
                AbstractInsnNode next = instruction.getNext();
                if (instruction instanceof FieldInsnNode field
                        && field.owner.equals("net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientChunkEvents")) registration = true;
                if (registration) {
                    if (instruction instanceof MethodInsnNode call && call.name.equals("register")) {
                        registration = false;
                        removedRegistrations++;
                    }
                    constructor.instructions.remove(instruction);
                }
                instruction = next;
            }
            check(removedRegistrations == 2 && !registration, "offline fixture isolates only the two Fabric event registrations");
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/System")
                            && call.name.equals("currentTimeMillis")) call.owner = CHECKS;
                }
            }
            typeClass = loader.define(node);
            subject = typeClass.getMethod("getInstance").invoke(null);
            type = (SelectSetting) typeClass.getField("animationType").get(subject);
            side = (SelectSetting) typeClass.getField("directionSide").get(subject);
            easing = (SelectSetting) typeClass.getField("easing").get(subject);
            distance = (ValueSetting) typeClass.getField("distance").get(subject);
            duration = (ValueSetting) typeClass.getField("duration").get(subject);
            columns = (Map<Long, Long>) field(typeClass, "firstSeenMs").get(subject);
            regions = (Map<Long, Long>) field(typeClass, "regionFirstSeenMs").get(subject);
            pending = (Set<Long>) field(typeClass, "pendingAnimationKeys").get(subject);
            reset = typeClass.getMethod("resetTracker");
            magnitude = accessibleMethod(typeClass, "offsetMagnitude", int.class, long.class, long.class, Interpolation.class);
            direction = typeClass.getMethod("writeAnimationDirection", float[].class);
            sectionDirection = typeClass.getMethod("writeAnimationDirectionPerSection", float[].class);
            offsets = typeClass.getMethod("writeRegionSectionYOffsets", int.class, int.class, int.class, int[].class, int.class, float[].class);
            vanilla = typeClass.getMethod("getYOffset", BlockPos.class);
            fallback = typeClass.getMethod("getRegionMagnitude", int.class, int.class, int.class, int[].class, int.class);

            // Run the compiled CPU packer, not a hand-written copy of its math.
            // Strip only GL hooks and replace RenderRegion with the coordinate fixture.
            ClassNode packer = read(MIXIN);
            packer.methods.removeIf(method -> !Set.of("phaze$fillIdentity", "phaze$packAnimData").contains(method.name));
            packer.fields.removeIf(field -> !Set.of("PHAZE$DIR", "PHAZE$SLOT_BUFFER", "PHAZE$SCALAR_SCRATCH").contains(field.name));
            packer.visibleAnnotations = packer.invisibleAnnotations = null;
            MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            for (FieldNode array : packer.fields) {
                init.instructions.add(new IntInsnNode(Opcodes.SIPUSH, array.name.equals("PHAZE$DIR") ? 3 : 256));
                init.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, array.desc.equals("[I") ? Opcodes.T_INT : Opcodes.T_FLOAT));
                init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, MIXIN, array.name, array.desc));
            }
            init.instructions.add(new InsnNode(Opcodes.RETURN));
            packer.methods.add(init);
            ClassNode remapped = new ClassNode();
            packer.accept(new ClassRemapper(remapped, new SimpleRemapper(Map.of(
                    MIXIN, CHECKS + "OfflinePacker",
                    "net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion", CHECKS + "$Region"))));
            Class<?> packerClass = loader.define(remapped);
            pack = accessibleMethod(packerClass, "phaze$packAnimData", typeClass, Region.class, int.class, int.class, float[].class);
            packedSlots = (int[]) field(packerClass, "PHAZE$SLOT_BUFFER").get(null);
        }

        void configure(String animation) throws Exception {
            reset.invoke(subject);
            enabled(true);
            type.selected(animation);
            easing.selected("Linear");
            distance.setValue(8);
            duration.setValue(600);
            now = START;
        }

        void enabled(boolean enabled) throws Exception { typeClass.getField("state").setBoolean(subject, enabled); }

        long freshColumn(Region region, int x, int z) {
            long key = ChunkPos.toLong((region.originX >> 4) + x, (region.originZ >> 4) + z);
            pending.add(key);
            return key;
        }

        float magnitude(int distance, long elapsed, long duration, Interpolation curve) throws Exception {
            return (float) magnitude.invoke(null, distance, elapsed, duration, curve);
        }

        float[] direction(boolean perSection) throws Exception {
            float[] out = {99, 99, 99};
            (perSection ? sectionDirection : direction).invoke(subject, (Object) out);
            return out;
        }

        boolean offsets(Region region, int[] slots, float[] out) throws Exception {
            return (boolean) offsets.invoke(subject, region.originX, region.originY, region.originZ, slots, slots.length, out);
        }

        float vanilla(BlockPos position) throws Exception { return (float) vanilla.invoke(subject, position); }

        float fallback(Region region, int[] slots) throws Exception {
            return (float) fallback.invoke(subject, region.originX, region.originY, region.originZ, slots, slots.length);
        }

        boolean pack(Region region, int[] slots, int mode, float[] vec4) throws Exception {
            System.arraycopy(slots, 0, packedSlots, 0, slots.length);
            return (boolean) pack.invoke(null, subject, region, slots.length, mode, vec4);
        }
    }

    private static final class OfflineClasses extends ClassLoader {
        OfflineClasses() { super(ChunkAnimatorChecks.class.getClassLoader()); }

        Class<?> define(ClassNode node) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] bytes = writer.toByteArray();
            return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }

    private static int slot(int x, int y, int z) { return (x << 5) | (z << 2) | y; }

    private static ClassNode read(String name) throws Exception {
        try (InputStream input = ChunkAnimatorChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG);
            return node;
        }
    }

    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean hasCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
        }
        return false;
    }

    private static Method accessibleMethod(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) <= 0.0001F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

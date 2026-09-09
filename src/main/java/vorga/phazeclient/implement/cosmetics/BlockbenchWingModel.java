package vorga.phazeclient.implement.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Small, deliberately restricted Blockbench reader used for Phaze cosmetics.
 * It accepts the cube + embedded PNG subset emitted by Generic Model projects.
 * No Lua, arbitrary files, or Blockbench scripts are ever executed.
 */
final class BlockbenchWingModel {
    private static final float PIXEL = 1.0F / 16.0F;

    private final List<Cube> cubes;
    private final List<Texture> textures;
    private final Transform root;
    private final Bounds bounds;
    private final Map<String, List<Transform>> markerPaths;
    private final Vector3f attachmentPivot;

    private BlockbenchWingModel(List<Cube> cubes, List<Texture> textures, Transform root,
                                Map<String, List<Transform>> markerPaths) {
        this.cubes = cubes;
        this.textures = textures;
        this.root = root;
        this.bounds = calculateBounds(cubes);
        this.markerPaths = markerPaths;
        this.attachmentPivot = findAttachmentPivot(markerPaths, root);
    }

    static BlockbenchWingModel load(Path file) throws IOException {
        return load(file, "", CosmeticsState.CosmeticType.WING);
    }

    static BlockbenchWingModel load(Path file, String textureVariant) throws IOException {
        return load(file, textureVariant, CosmeticsState.CosmeticType.WING);
    }

    static BlockbenchWingModel load(
            Path file,
            String textureVariant,
            CosmeticsState.CosmeticType cosmeticType
    ) throws IOException {
        JsonObject root = JsonParser.parseString(readModelJson(file)).getAsJsonObject();
        String modelGroup = "";
        String resolvedTextureVariant = textureVariant == null ? "" : textureVariant;
        if (resolvedTextureVariant.toLowerCase(java.util.Locale.ROOT)
                .startsWith("group:")) {
            modelGroup = resolvedTextureVariant.substring("group:".length()).trim();
            resolvedTextureVariant = "";
        }
        boolean localMeshCoordinates = file.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT)
                .contains("goldfish");
        Map<String, float[]> spreadPose = switch (cosmeticType) {
            case WING -> readFirstAnimationRotationPose(root, "spread");
            case PET -> readFirstAnimationRotationPose(
                    root, "idle", "BirbIdle", "float"
            );
            default -> Map.of();
        };
        List<Texture> textures = readTextures(
                root.getAsJsonArray("textures"), file, resolvedTextureVariant
        );
        Map<String, Integer> textureIndexByUuid = new HashMap<>();
        for (int i = 0; i < textures.size(); i++) {
            textureIndexByUuid.put(textures.get(i).uuid, i);
            // In .bbmodel face data, numeric references are positions in the
            // texture array. They are not the texture object's optional "id"
            // field (Figura projects commonly contain duplicate and sparse
            // ids). UUID references are supported alongside array indices.
            textureIndexByUuid.put(Integer.toString(i), i);
        }

        List<Cube> cubes = new ArrayList<>();
        Map<String, Cube> cubesByUuid = new HashMap<>();
        JsonArray elements = root.getAsJsonArray("elements");
        if (elements != null) {
            for (JsonElement element : elements) {
                if (!element.isJsonObject()) continue;
                Cube cube = Cube.read(
                        element.getAsJsonObject(),
                        textureIndexByUuid,
                        localMeshCoordinates
                );
                if (cube != null) {
                    cubes.add(cube);
                    cubesByUuid.put(string(element.getAsJsonObject(), "uuid"), cube);
                }
            }
        }
        if (cubes.isEmpty() || textures.isEmpty()) {
            throw new IOException("Blockbench model has no renderable cubes or embedded textures");
        }
        Map<String, Transform> groupDefinitions = readGroupDefinitions(
                root.getAsJsonArray("groups"), spreadPose
        );
        Map<String, List<Transform>> markerPaths = new HashMap<>();
        JsonArray outliner = root.getAsJsonArray("outliner");
        if (outliner != null) {
            for (JsonElement node : outliner) {
                assignParentTransforms(
                        node, List.of(), cubesByUuid, groupDefinitions,
                        spreadPose, markerPaths
                );
            }

            if (cosmeticType == CosmeticsState.CosmeticType.WING) {
                // Figura wing archives are often full avatars. When the
                // project has explicit wing branches, ignore bundled limbs.
                Set<Cube> cosmeticCubes = new HashSet<>();
                for (JsonElement node : outliner) {
                    collectWingCubes(node, false, cubesByUuid, cosmeticCubes);
                }
                if (!cosmeticCubes.isEmpty()) {
                    cubes.removeIf(cube -> !cosmeticCubes.contains(cube));
                }
            } else if (cosmeticType == CosmeticsState.CosmeticType.HAT) {
                String fileName = file.getFileName().toString()
                        .toLowerCase(java.util.Locale.ROOT);
                if (!modelGroup.isBlank()) {
                    // Hat Kid stores six independently toggleable hats in one
                    // Figura avatar. A stable group id turns each one into its
                    // own lightweight catalog entry without executing Lua.
                    Set<Cube> selectedGroup = new HashSet<>();
                    for (JsonElement node : outliner) {
                        collectNamedGroupCubes(
                                node, modelGroup, false,
                                cubesByUuid, selectedGroup
                        );
                    }
                    if (!selectedGroup.isEmpty()) {
                        cubes.removeIf(cube -> !selectedGroup.contains(cube));
                        if (fileName.contains("violet witch")) {
                            // The selected Head branch also contains the
                            // Figura avatar's vanilla head template. Keep all
                            // sibling hat pieces, including the black backing,
                            // but discard those two template cubes.
                            cubes.removeIf(cube ->
                                    "head".equalsIgnoreCase(cube.name)
                                            || "hat".equalsIgnoreCase(cube.name));
                        }
                    }
                } else {
                    // Turtle and the remaining Figura hat projects include a
                    // vanilla player template. Their actual accessory pieces
                    // are the generically named cubes.
                    cubes.removeIf(cube ->
                            !"cube".equalsIgnoreCase(cube.name));
                }
            } else if (cosmeticType == CosmeticsState.CosmeticType.PET) {
                cubes.removeIf(cube -> "collision".equalsIgnoreCase(cube.name));
            }
        }
        Transform rootTransform = cubes.stream()
                .filter(cube -> !cube.parents.isEmpty())
                .map(cube -> cube.parents.getFirst())
                .findFirst()
                .orElse(new Transform("", "", 0.0F, 0.0F, 0.0F,
                        0.0F, 0.0F, 0.0F, 0));
        return new BlockbenchWingModel(
                List.copyOf(cubes), List.copyOf(textures), rootTransform,
                Map.copyOf(markerPaths)
        );
    }

    private static Map<String, Transform> readGroupDefinitions(
            JsonArray groups,
            Map<String, float[]> pose
    ) {
        Map<String, Transform> definitions = new HashMap<>();
        if (groups == null) return definitions;
        for (JsonElement element : groups) {
            if (!element.isJsonObject()) continue;
            JsonObject group = element.getAsJsonObject();
            float[] origin = numbersOrDefault(group.getAsJsonArray("origin"), 3, 0.0F);
            float[] rotation = numbersOrDefault(group.getAsJsonArray("rotation"), 3, 0.0F);
            String uuid = string(group, "uuid");
            if (!uuid.isEmpty()) {
                float[] posed = pose.get(uuid);
                if (posed != null) {
                    rotation[0] += posed[0];
                    rotation[1] += posed[1];
                    rotation[2] += posed[2];
                }
                definitions.put(uuid, new Transform(
                        uuid, string(group, "name"),
                        origin[0], origin[1], origin[2], rotation[0], rotation[1], rotation[2], 0
                ));
            }
        }
        return definitions;
    }

    private static String readModelJson(Path file) throws IOException {
        if (!file.getFileName().toString().toLowerCase().endsWith(".zip")) {
            return Files.readString(file);
        }
        try (ZipFile archive = new ZipFile(file.toFile())) {
            ZipEntry model = archive.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().toLowerCase().endsWith(".bbmodel"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Cosmetic archive contains no .bbmodel file"));
            try (var input = archive.getInputStream(model)) {
                return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    private static void assignParentTransforms(
            JsonElement node,
            List<Transform> parents,
            Map<String, Cube> cubesByUuid,
            Map<String, Transform> groupDefinitions,
            Map<String, float[]> pose,
            Map<String, List<Transform>> markerPaths
    ) {
        if (node.isJsonPrimitive()) {
            Cube cube = cubesByUuid.get(node.getAsString());
            if (cube != null) cube.parents = List.copyOf(parents);
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject group = node.getAsJsonObject();
        String uuid = string(group, "uuid");
        List<Transform> nestedParents = parents;
        Transform transform = groupDefinitions.get(uuid);
        if (transform == null && group.has("origin")) {
            float[] origin = numbersOrDefault(group.getAsJsonArray("origin"), 3, 0.0F);
            float[] rotation = numbersOrDefault(group.getAsJsonArray("rotation"), 3, 0.0F);
            float[] posed = pose.get(uuid);
            if (posed != null) {
                rotation[0] += posed[0];
                rotation[1] += posed[1];
                rotation[2] += posed[2];
            }
            transform = new Transform(
                    uuid, string(group, "name"),
                    origin[0], origin[1], origin[2], rotation[0], rotation[1], rotation[2], 0
            );
        }
        if (transform != null) {
            transform = transform.atDepth(parents.size());
            nestedParents = new ArrayList<>(parents);
            nestedParents.add(transform);
            if (!transform.name().isBlank()) {
                markerPaths.put(
                        transform.name().toLowerCase(java.util.Locale.ROOT),
                        List.copyOf(nestedParents)
                );
            }
        }
        JsonArray children = group.getAsJsonArray("children");
        if (children != null) {
            for (JsonElement child : children) {
                assignParentTransforms(
                        child, nestedParents, cubesByUuid, groupDefinitions,
                        pose, markerPaths
                );
            }
        }
    }

    /**
     * Figura archives keep their useful rest pose in a Blockbench animation
     * rather than in the outliner rotations. aylDWT calls that pose "spread".
     * Reading the first rotation keyframe gives the authored open-wing shape
     * without executing any Lua bundled in the archive.
     */
    private static Map<String, float[]> readFirstAnimationRotationPose(
            JsonObject root,
            String... animationNames
    ) {
        Map<String, float[]> result = new HashMap<>();
        JsonArray animations = root.getAsJsonArray("animations");
        if (animations == null) return result;
        for (String animationName : animationNames) {
            for (JsonElement element : animations) {
                if (!element.isJsonObject()) continue;
                JsonObject animation = element.getAsJsonObject();
                if (!animationName.equalsIgnoreCase(string(animation, "name"))) continue;
                JsonObject animators = animation.getAsJsonObject("animators");
                if (animators == null) return result;
                for (Map.Entry<String, JsonElement> animatorEntry : animators.entrySet()) {
                    if (!animatorEntry.getValue().isJsonObject()) continue;
                    JsonArray keyframes = animatorEntry.getValue().getAsJsonObject()
                            .getAsJsonArray("keyframes");
                    if (keyframes == null) continue;
                    JsonObject firstRotation = null;
                    float firstTime = Float.POSITIVE_INFINITY;
                    for (JsonElement keyframeElement : keyframes) {
                        if (!keyframeElement.isJsonObject()) continue;
                        JsonObject keyframe = keyframeElement.getAsJsonObject();
                        if (!"rotation".equalsIgnoreCase(string(keyframe, "channel"))) continue;
                        float time = decimal(keyframe, "time", 0.0F);
                        if (time < firstTime) {
                            firstTime = time;
                            firstRotation = keyframe;
                        }
                    }
                    if (firstRotation == null) continue;
                    JsonArray points = firstRotation.getAsJsonArray("data_points");
                    if (points == null || points.isEmpty()
                            || !points.get(0).isJsonObject()) continue;
                    JsonObject point = points.get(0).getAsJsonObject();
                    result.put(animatorEntry.getKey(), new float[]{
                            decimal(point, "x", 0.0F),
                            decimal(point, "y", 0.0F),
                            decimal(point, "z", 0.0F)
                    });
                }
                return result;
            }
        }
        return result;
    }

    private static void collectWingCubes(
            JsonElement node,
            boolean insideWingBranch,
            Map<String, Cube> cubesByUuid,
            Set<Cube> result
    ) {
        if (node.isJsonPrimitive()) {
            if (insideWingBranch) {
                Cube cube = cubesByUuid.get(node.getAsString());
                if (cube != null) result.add(cube);
            }
            return;
        }
        if (!node.isJsonObject()) return;

        JsonObject group = node.getAsJsonObject();
        String name = string(group, "name").toLowerCase(java.util.Locale.ROOT);
        boolean wingBranch = insideWingBranch
                || name.contains("wing")
                || name.contains("membrane")
                || name.contains("elytra");
        JsonArray children = group.getAsJsonArray("children");
        if (children != null) {
            for (JsonElement child : children) {
                collectWingCubes(child, wingBranch, cubesByUuid, result);
            }
        }
    }

    private static void collectNamedGroupCubes(
            JsonElement node,
            String targetGroup,
            boolean insideTarget,
            Map<String, Cube> cubesByUuid,
            Set<Cube> result
    ) {
        if (node.isJsonPrimitive()) {
            if (insideTarget) {
                Cube cube = cubesByUuid.get(node.getAsString());
                if (cube != null) result.add(cube);
            }
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject group = node.getAsJsonObject();
        boolean selected = insideTarget
                || targetGroup.equalsIgnoreCase(string(group, "name"))
                || targetGroup.equalsIgnoreCase(string(group, "uuid"));
        JsonArray children = group.getAsJsonArray("children");
        if (children == null) return;
        for (JsonElement child : children) {
            collectNamedGroupCubes(
                    child, targetGroup, selected, cubesByUuid, result
            );
        }
    }

    private static List<Texture> readTextures(
            JsonArray source,
            Path modelFile,
            String textureVariant
    ) throws IOException {
        List<Texture> result = new ArrayList<>();
        if (source == null) return result;
        int fallbackId = 0;
        for (JsonElement element : source) {
            if (!element.isJsonObject()) continue;
            JsonObject texture = element.getAsJsonObject();
            String data = string(texture, "source");
            String uuid = string(texture, "uuid");
            String name = string(texture, "name");
            int fileId = integer(texture, "id", fallbackId++);
            int width = integer(texture, "width", 16);
            int height = integer(texture, "height", 16);
            if (!data.startsWith("data:image/png;base64,") || uuid.isEmpty()) continue;
            byte[] png = readArchiveTexture(modelFile, textureVariant, name);
            if (png == null) {
                png = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
            }
            result.add(new Texture(fileId, uuid, width, height, png));
        }
        return result;
    }

    private static byte[] readArchiveTexture(
            Path modelFile,
            String textureVariant,
            String textureName
    ) {
        if (textureName == null || textureName.isBlank()
                || !modelFile.getFileName().toString().toLowerCase().endsWith(".zip")) {
            return null;
        }
        String requested = ((textureVariant == null ? "" : textureVariant)
                + "/" + textureName)
                .replace('\\', '/')
                .replaceAll("/+", "/")
                .replaceFirst("^/", "");
        try (ZipFile archive = new ZipFile(modelFile.toFile())) {
            ZipEntry entry = null;
            if (textureVariant != null && !textureVariant.isBlank()) {
                entry = archive.stream()
                        .filter(candidate -> !candidate.isDirectory()
                                && candidate.getName().equalsIgnoreCase(requested))
                        .findFirst()
                        .orElse(null);
            }
            if (entry == null) {
                String suffix = "/" + textureName;
                entry = archive.stream()
                        .filter(candidate -> !candidate.isDirectory())
                        .filter(candidate -> {
                            String candidateName = candidate.getName().replace('\\', '/');
                            return candidateName.equalsIgnoreCase(textureName)
                                    || candidateName.regionMatches(
                                    true,
                                    Math.max(0, candidateName.length() - suffix.length()),
                                    suffix, 0, suffix.length()
                            );
                        })
                        .findFirst()
                        .orElse(null);
            }
            if (entry == null) return null;
            try (var input = archive.getInputStream(entry)) {
                return input.readAllBytes();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    void render(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                CosmeticsPhysics.Pose physics, boolean uniformLighting,
                boolean tipsOnlyPhysics, MatrixStack.Entry stableLightingEntry) {
        renderInternal(
                matrices, consumers, light, physics, uniformLighting,
                tipsOnlyPhysics, stableLightingEntry, false
        );
    }

    void renderCatalog(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                       MatrixStack.Entry stableLightingEntry) {
        renderInternal(
                matrices, consumers, light, CosmeticsRenderer.STATIC_POSE,
                false, false, stableLightingEntry, false
        );
    }

    private void renderInternal(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                CosmeticsPhysics.Pose physics, boolean uniformLighting,
                                boolean tipsOnlyPhysics, MatrixStack.Entry stableLightingEntry,
                                boolean catalogLod) {
        renderInternal(
                matrices, consumers, light, physics, uniformLighting,
                tipsOnlyPhysics, stableLightingEntry, catalogLod, -1
        );
    }

    private void renderInternal(MatrixStack matrices, VertexConsumerProvider consumers, int light,
                                CosmeticsPhysics.Pose physics, boolean uniformLighting,
                                boolean tipsOnlyPhysics, MatrixStack.Entry stableLightingEntry,
                                boolean catalogLod, int targetTexture) {
        // Keep a shader-compatible normal-space transform from before the
        // mirrored Blockbench groups are applied. Both Wimgs halves can then
        // share one direction without sending raw world-space normals.
        MatrixStack.Entry uniformLightingEntry =
                stableLightingEntry == null ? matrices.peek() : stableLightingEntry;
        Map<String, Vector3f> markerPositions = markerPositions(physics, tipsOnlyPhysics);
        for (Cube cube : cubes) {
            if (targetTexture >= 0 && !cube.usesTexture(targetTexture)) continue;
            if (cube.isMembrane() && !markerPositions.isEmpty()) {
                String[] markerNames = MEMBRANE_MARKERS.get(
                        cube.name.toLowerCase(java.util.Locale.ROOT)
                );
                if (markerNames != null) {
                    cube.renderMembrane(
                            matrices, consumers, textures, light,
                            uniformLighting, uniformLightingEntry,
                            markerPositions, markerNames
                    );
                    continue;
                }
            }
            matrices.push();
            for (Transform parent : cube.parents) {
                parent.apply(matrices, physics, tipsOnlyPhysics);
            }
            matrices.translate(cube.ox * PIXEL, cube.oy * PIXEL, cube.oz * PIXEL);
            // Blockbench rotates cubes in XYZ order around their origin.
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(cube.rz));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(cube.ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cube.rx));
            if (!cube.localMeshCoordinates) {
                matrices.translate(
                        -cube.ox * PIXEL,
                        -cube.oy * PIXEL,
                        -cube.oz * PIXEL
                );
            }
            if (catalogLod) {
                cube.renderCatalog(
                        matrices, consumers, textures, light,
                        uniformLighting, uniformLightingEntry, bounds.frontIsXAxis()
                );
            } else {
                cube.render(
                        matrices, consumers, textures, light,
                        uniformLighting, uniformLightingEntry
                );
            }
            matrices.pop();
        }
    }

    private Map<String, Vector3f> markerPositions(
            CosmeticsPhysics.Pose physics,
            boolean tipsOnlyPhysics
    ) {
        if (markerPaths.isEmpty()) return Map.of();
        Map<String, Vector3f> result = new HashMap<>();
        for (Map.Entry<String, List<Transform>> entry : markerPaths.entrySet()) {
            List<Transform> path = entry.getValue();
            if (path.isEmpty()) continue;
            Matrix4f matrix = new Matrix4f();
            for (Transform transform : path) {
                transform.apply(matrix, physics, tipsOnlyPhysics);
            }
            Transform marker = path.getLast();
            result.put(entry.getKey(), matrix.transformPosition(new Vector3f(
                    marker.ox * PIXEL,
                    marker.oy * PIXEL,
                    marker.oz * PIXEL
            )));
        }
        return result;
    }

    private static final Map<String, String[]> MEMBRANE_MARKERS = Map.ofEntries(
            Map.entry("rightmembrane1", new String[]{"rightmbodyup", "rightmwing", "rightmwing", "rightmbodyup"}),
            Map.entry("rightmembrane2", new String[]{"rightmwing", "rightmbodyup", "rightmback", "rightmwing"}),
            Map.entry("rightmembrane3", new String[]{"rightmbodyup", "rightmfront", "rightmback", "rightmbodyup"}),
            Map.entry("rightmembrane4", new String[]{"rightmwing", "rightmback", "rightmrib3", "rightmfinger3"}),
            Map.entry("rightmembrane5", new String[]{"rightmback", "rightmfront", "rightmrib3", "rightmrib3"}),
            Map.entry("rightmembrane6", new String[]{"rightmfinger3", "rightmrib3", "rightmrib2", "rightmfinger2"}),
            Map.entry("rightmembrane7", new String[]{"rightmrib3", "rightmfront", "rightmmetacarpus", "rightmrib2"}),
            Map.entry("rightmembrane8", new String[]{"rightmfinger2", "rightmrib2", "rightmrib1", "rightmfinger1"}),
            Map.entry("rightmembrane9", new String[]{"rightmrib2", "rightmmetacarpus", "rightmmetacarpus", "rightmrib1"}),
            Map.entry("leftmembrane1", new String[]{"leftmbodyup", "leftmwing", "leftmwing", "leftmbodyup"}),
            Map.entry("leftmembrane2", new String[]{"leftmwing", "leftmbodyup", "leftmback", "leftmwing"}),
            Map.entry("leftmembrane3", new String[]{"leftmbodyup", "leftmfront", "leftmback", "leftmbodyup"}),
            Map.entry("leftmembrane4", new String[]{"leftmwing", "leftmback", "leftmrib3", "leftmfinger3"}),
            Map.entry("leftmembrane5", new String[]{"leftmback", "leftmfront", "leftmrib3", "leftmrib3"}),
            Map.entry("leftmembrane6", new String[]{"leftmfinger3", "leftmrib3", "leftmrib2", "leftmfinger2"}),
            Map.entry("leftmembrane7", new String[]{"leftmrib3", "leftmfront", "leftmmetacarpus", "leftmrib2"}),
            Map.entry("leftmembrane8", new String[]{"leftmfinger2", "leftmrib2", "leftmrib1", "leftmfinger1"}),
            Map.entry("leftmembrane9", new String[]{"leftmrib2", "leftmmetacarpus", "leftmmetacarpus", "leftmrib1"})
    );

    Bounds bounds() {
        return bounds;
    }

    /**
     * Computes the authored, static model bounds after every Blockbench group
     * and cube transform has been applied. Card previews use this instead of
     * guessing a player-relative offset/scale.
     */
    private static Bounds calculateBounds(List<Cube> cubes) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (Cube cube : cubes) {
            Matrix4f transform = new Matrix4f();
            for (Transform parent : cube.parents) {
                parent.applyStatic(transform);
            }
            transform.translate(cube.ox * PIXEL, cube.oy * PIXEL, cube.oz * PIXEL);
            transform.rotateZ((float) Math.toRadians(cube.rz));
            transform.rotateY((float) Math.toRadians(cube.ry));
            transform.rotateX((float) Math.toRadians(cube.rx));
            if (!cube.localMeshCoordinates) {
                transform.translate(
                        -cube.ox * PIXEL,
                        -cube.oy * PIXEL,
                        -cube.oz * PIXEL
                );
            }

            for (float x : new float[]{cube.x1, cube.x2}) {
                for (float y : new float[]{cube.y1, cube.y2}) {
                    for (float z : new float[]{cube.z1, cube.z2}) {
                        Vector3f point = transform.transformPosition(
                                new Vector3f(x * PIXEL, y * PIXEL, z * PIXEL)
                        );
                        minX = Math.min(minX, point.x);
                        minY = Math.min(minY, point.y);
                        minZ = Math.min(minZ, point.z);
                        maxX = Math.max(maxX, point.x);
                        maxY = Math.max(maxY, point.y);
                        maxZ = Math.max(maxZ, point.z);
                    }
                }
            }
        }

        if (!Float.isFinite(minX)) {
            return new Bounds(-0.5F, -0.5F, -0.5F, 0.5F, 0.5F, 0.5F);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    void scaleAroundRoot(MatrixStack matrices, float scale) {
        if (scale == 1.0F) return;
        translateToRoot(matrices);
        matrices.scale(scale, scale, scale);
        translateFromRoot(matrices);
    }

    void translateAttachmentTo(
            MatrixStack matrices,
            float targetX,
            float targetY,
            float targetZ
    ) {
        matrices.translate(
                targetX - attachmentPivot.x * PIXEL,
                targetY - attachmentPivot.y * PIXEL,
                targetZ - attachmentPivot.z * PIXEL
        );
    }

    void transformCenterTo(
            MatrixStack matrices,
            float targetX,
            float targetY,
            float targetZ,
            float scale,
            float yawDegrees
    ) {
        matrices.translate(targetX, targetY, targetZ);
        matrices.scale(scale, scale, scale);
        if (yawDegrees != 0.0F) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        }
        matrices.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
    }

    /**
     * Figura WORLD companions are authored with Y pointing up, while the
     * vanilla player-model matrix used by the feature renderer points down.
     * Keep the companion at one stable shoulder-side anchor and correct that
     * axis here instead of making every imported pet carry a special case.
     */
    void transformCompanionTo(
            MatrixStack matrices,
            float targetX,
            float targetY,
            float targetZ,
            float scale,
            float yawDegrees
    ) {
        matrices.translate(targetX, targetY, targetZ);
        matrices.scale(scale, -scale, scale);
        if (yawDegrees != 0.0F) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        }
        matrices.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
    }

    /**
     * Keeps an authored Blockbench attachment group (for example {@code Head})
     * at the vanilla model-part origin. Unlike bounds-centering this preserves
     * the artist's intended offset of a hat or companion relative to the head.
     */
    void transformGroupPivotTo(
            MatrixStack matrices,
            String groupName,
            float targetX,
            float targetY,
            float targetZ,
            float scale,
            float yawDegrees,
            boolean flipY
    ) {
        Transform pivot = groupName == null
                ? null
                : lastTransform(markerPaths.get(
                groupName.toLowerCase(java.util.Locale.ROOT)
        ));
        if (pivot == null) {
            transformCenterTo(
                    matrices, targetX, targetY, targetZ, scale, yawDegrees
            );
            return;
        }
        matrices.translate(targetX, targetY, targetZ);
        matrices.scale(scale, flipY ? -scale : scale, scale);
        if (yawDegrees != 0.0F) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        }
        matrices.translate(
                -pivot.ox * PIXEL,
                -pivot.oy * PIXEL,
                -pivot.oz * PIXEL
        );
    }

    /**
     * Centers an imported companion above vanilla's 8px head and places the
     * lowest rendered point exactly on the head's top plane (local y=-0.5).
     * Figura avatars may keep their cosmetic mesh far away from the Head
     * pivot, so bounds are the only stable attachment reference here.
     */
    void transformOntoHead(
            MatrixStack matrices,
            float scale,
            float yawDegrees
    ) {
        matrices.scale(scale, -scale, scale);
        if (yawDegrees != 0.0F) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));
        }
        matrices.translate(
                -bounds.centerX(),
                0.5F / scale - bounds.minY(),
                -bounds.centerZ()
        );
    }

    void rotateAroundRootX(MatrixStack matrices, float degrees) {
        if (degrees == 0.0F) return;
        translateToRoot(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
        translateFromRoot(matrices);
    }

    void rotateAroundBoundsX(MatrixStack matrices, float degrees) {
        if (degrees == 0.0F) return;
        matrices.translate(
                bounds.centerX(), bounds.centerY(), bounds.centerZ()
        );
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(degrees));
        matrices.translate(
                -bounds.centerX(), -bounds.centerY(), -bounds.centerZ()
        );
    }

    void rotateAroundRootY(MatrixStack matrices, float degrees) {
        if (degrees == 0.0F) return;
        translateToRoot(matrices);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
        translateFromRoot(matrices);
    }

    void rotateAroundRootZ(MatrixStack matrices, float degrees) {
        if (degrees == 0.0F) return;
        translateToRoot(matrices);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        translateFromRoot(matrices);
    }

    void rotateAroundGroupZ(MatrixStack matrices, String groupName, float degrees) {
        if (degrees == 0.0F || groupName == null) return;
        List<Transform> path = markerPaths.get(groupName.toLowerCase(java.util.Locale.ROOT));
        if (path == null || path.isEmpty()) {
            rotateAroundRootZ(matrices, degrees);
            return;
        }
        Transform pivot = path.getLast();
        matrices.translate(pivot.ox * PIXEL, pivot.oy * PIXEL, pivot.oz * PIXEL);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
        matrices.translate(-pivot.ox * PIXEL, -pivot.oy * PIXEL, -pivot.oz * PIXEL);
    }

    private void translateToRoot(MatrixStack matrices) {
        matrices.translate(
                attachmentPivot.x * PIXEL,
                attachmentPivot.y * PIXEL,
                attachmentPivot.z * PIXEL
        );
    }

    private void translateFromRoot(MatrixStack matrices) {
        matrices.translate(
                -attachmentPivot.x * PIXEL,
                -attachmentPivot.y * PIXEL,
                -attachmentPivot.z * PIXEL
        );
    }

    private static Vector3f findAttachmentPivot(
            Map<String, List<Transform>> markerPaths,
            Transform fallback
    ) {
        Transform left = lastTransform(markerPaths.get("leftwing"));
        Transform right = lastTransform(markerPaths.get("rightwing"));
        if (left != null && right != null) {
            return new Vector3f(
                    (left.ox + right.ox) * 0.5F,
                    (left.oy + right.oy) * 0.5F,
                    (left.oz + right.oz) * 0.5F
            );
        }
        for (String preferred : new String[]{"wings_root", "wings", "body"}) {
            Transform transform = lastTransform(markerPaths.get(preferred));
            if (transform != null) {
                return new Vector3f(transform.ox, transform.oy, transform.oz);
            }
        }
        return new Vector3f(fallback.ox, fallback.oy, fallback.oz);
    }

    private static Transform lastTransform(List<Transform> path) {
        return path == null || path.isEmpty() ? null : path.getLast();
    }

    void registerTextures(String modelKey) throws IOException {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) throw new IOException("Minecraft client is not ready");
        for (int i = 0; i < textures.size(); i++) {
            Texture texture = textures.get(i);
            Identifier id = Identifier.of("phaze", "dynamic/cosmetics/" + modelKey + "/" + i);
            String textureLabel = "Phaze cosmetic " + modelKey + "/" + i;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(texture.png));
            client.getTextureManager().destroyTexture(id);
            client.getTextureManager().registerTexture(id,
                    new NativeImageBackedTexture(() -> textureLabel, image));
            texture.id = id;
        }
    }

    boolean renderCatalogBuffers(MatrixStack matrices) {
        // Minecraft 1.21.11 removed the legacy VertexBuffer wrapper used by
        // 1.21.4. Catalog thumbnails are submitted through the modern special
        // GUI element path instead, while world rendering still uses the
        // entity VertexConsumerProvider directly.
        return false;
    }

    void close() {
        // Dynamic textures are owned by TextureManager.
    }

    private static final class Texture {
        private final int fileId;
        private final String uuid;
        private final int width;
        private final int height;
        private final byte[] png;
        private Identifier id;

        private Texture(int fileId, String uuid, int width, int height, byte[] png) {
            this.fileId = fileId;
            this.uuid = uuid;
            this.width = width;
            this.height = height;
            this.png = png;
        }
    }

    private enum DiscardingVertexConsumer implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int color) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static final class Cube {
        private final String name;
        private final float x1, y1, z1, x2, y2, z2, ox, oy, oz, rx, ry, rz;
        private final Map<String, Face> faces;
        private final List<MeshFace> meshFaces;
        private final boolean localMeshCoordinates;
        private List<Transform> parents = List.of();

        private Cube(String name,
                     float x1, float y1, float z1, float x2, float y2, float z2,
                     float ox, float oy, float oz, float rx, float ry, float rz,
                     Map<String, Face> faces, List<MeshFace> meshFaces,
                     boolean localMeshCoordinates) {
            this.name = name;
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.ox = ox; this.oy = oy; this.oz = oz;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.faces = faces;
            this.meshFaces = meshFaces;
            this.localMeshCoordinates = localMeshCoordinates;
        }

        static Cube read(
                JsonObject object,
                Map<String, Integer> textureIndices,
                boolean localMeshCoordinates
        ) {
            String type = string(object, "type");
            if ("mesh".equals(type) || "beveled_cuboid".equals(type)) {
                return readMesh(
                        object, textureIndices, true
                );
            }
            if (!"cube".equals(type)) return null;
            float[] from = numbers(object.getAsJsonArray("from"), 3);
            float[] to = numbers(object.getAsJsonArray("to"), 3);
            // Blockbench omits zero-valued rotation (and occasionally origin)
            // arrays. Treat those missing properties as zero rather than
            // discarding the cube. This is required by both simplewings and
            // most of the aylDWT geometry.
            float[] origin = numbersOrDefault(object.getAsJsonArray("origin"), 3, 0.0F);
            float[] rotation = numbersOrDefault(object.getAsJsonArray("rotation"), 3, 0.0F);
            if (from == null || to == null) return null;
            Map<String, Face> faces = new HashMap<>();
            JsonObject rawFaces = object.getAsJsonObject("faces");
            if (rawFaces != null) {
                for (Map.Entry<String, JsonElement> entry : rawFaces.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject raw = entry.getValue().getAsJsonObject();
                    float[] uv = numbers(raw.getAsJsonArray("uv"), 4);
                    Integer texture = textureIndices.get(string(raw, "texture"));
                    if (uv != null && texture != null) faces.put(entry.getKey(), new Face(uv, texture));
                }
            }
            return faces.isEmpty() ? null : new Cube(
                    string(object, "name"),
                    from[0], from[1], from[2], to[0], to[1], to[2],
                    origin[0], origin[1], origin[2], rotation[0], rotation[1], rotation[2],
                    faces, List.of(), false);
        }

        private static Cube readMesh(
                JsonObject object,
                Map<String, Integer> textureIndices,
                boolean localMeshCoordinates
        ) {
            JsonObject rawVertices = object.getAsJsonObject("vertices");
            JsonObject rawFaces = object.getAsJsonObject("faces");
            if (rawVertices == null || rawFaces == null) return null;

            Map<String, float[]> positions = new HashMap<>();
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (Map.Entry<String, JsonElement> entry : rawVertices.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                float[] point = numbers(entry.getValue().getAsJsonArray(), 3);
                if (point == null) continue;
                positions.put(entry.getKey(), point);
                minX = Math.min(minX, point[0]);
                minY = Math.min(minY, point[1]);
                minZ = Math.min(minZ, point[2]);
                maxX = Math.max(maxX, point[0]);
                maxY = Math.max(maxY, point[1]);
                maxZ = Math.max(maxZ, point[2]);
            }

            List<MeshFace> faces = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : rawFaces.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject face = entry.getValue().getAsJsonObject();
                Integer texture = textureIndices.get(string(face, "texture"));
                JsonArray faceVertices = face.getAsJsonArray("vertices");
                JsonObject rawUv = face.getAsJsonObject("uv");
                if (texture == null || faceVertices == null || rawUv == null
                        || faceVertices.size() < 3) {
                    continue;
                }
                List<MeshVertex> vertices = new ArrayList<>(faceVertices.size());
                for (JsonElement vertexElement : faceVertices) {
                    String vertexId = vertexElement.getAsString();
                    float[] point = positions.get(vertexId);
                    JsonElement uvElement = rawUv.get(vertexId);
                    float[] uv = uvElement != null && uvElement.isJsonArray()
                            ? numbers(uvElement.getAsJsonArray(), 2)
                            : null;
                    if (point != null && uv != null) {
                        vertices.add(new MeshVertex(
                                point[0], point[1], point[2], uv[0], uv[1]
                        ));
                    }
                }
                if (vertices.size() >= 3) {
                    faces.add(new MeshFace(
                            texture,
                            List.copyOf(orderMeshFace(vertices))
                    ));
                }
            }
            if (faces.isEmpty() || !Float.isFinite(minX)) return null;
            float[] origin = numbersOrDefault(object.getAsJsonArray("origin"), 3, 0.0F);
            float[] rotation = numbersOrDefault(object.getAsJsonArray("rotation"), 3, 0.0F);
            return new Cube(
                    string(object, "name"),
                    minX, minY, minZ, maxX, maxY, maxZ,
                    origin[0], origin[1], origin[2],
                    rotation[0], rotation[1], rotation[2],
                    Map.of(), List.copyOf(faces),
                    localMeshCoordinates
            );
        }

        /**
         * Blockbench/Figura exporters do not agree on quad vertex order. Some
         * archives use perimeter order, others store a 2x2 grid, and the
         * Goldfish avatar mixes rotations that make a two-candidate heuristic
         * ambiguous. Sort the vertices geometrically around the face centroid;
         * UV coordinates remain attached to their vertex, so this produces a
         * valid perimeter for every planar mesh without model-specific hacks.
         */
        private static List<MeshVertex> orderMeshFace(List<MeshVertex> vertices) {
            if (vertices.size() != 4) return vertices;
            Vector3f center = new Vector3f();
            for (MeshVertex vertex : vertices) {
                center.add(vertex.x, vertex.y, vertex.z);
            }
            center.mul(0.25F);

            Vector3f referenceNormal = null;
            outer:
            for (int a = 0; a < vertices.size() - 2; a++) {
                for (int b = a + 1; b < vertices.size() - 1; b++) {
                    for (int c = b + 1; c < vertices.size(); c++) {
                        MeshVertex va = vertices.get(a);
                        MeshVertex vb = vertices.get(b);
                        MeshVertex vc = vertices.get(c);
                        Vector3f candidate = new Vector3f(
                                vb.x - va.x, vb.y - va.y, vb.z - va.z
                        ).cross(new Vector3f(
                                vc.x - va.x, vc.y - va.y, vc.z - va.z
                        ));
                        if (candidate.lengthSquared() > 1.0E-8F) {
                            referenceNormal = candidate.normalize();
                            break outer;
                        }
                    }
                }
            }
            if (referenceNormal == null) return vertices;

            MeshVertex first = vertices.getFirst();
            Vector3f axisU = new Vector3f(
                    first.x - center.x, first.y - center.y, first.z - center.z
            );
            if (axisU.lengthSquared() < 1.0E-8F) return vertices;
            axisU.normalize();
            Vector3f axisV = new Vector3f(referenceNormal).cross(axisU).normalize();

            List<MeshVertex> ordered = new ArrayList<>(vertices);
            ordered.sort((left, right) -> {
                float leftAngle = faceAngle(left, center, axisU, axisV);
                float rightAngle = faceAngle(right, center, axisU, axisV);
                return Float.compare(leftAngle, rightAngle);
            });

            MeshVertex a = ordered.get(0);
            MeshVertex b = ordered.get(1);
            MeshVertex c = ordered.get(2);
            Vector3f sortedNormal = new Vector3f(
                    b.x - a.x, b.y - a.y, b.z - a.z
            ).cross(new Vector3f(
                    c.x - a.x, c.y - a.y, c.z - a.z
            ));
            if (sortedNormal.dot(referenceNormal) < 0.0F) {
                java.util.Collections.reverse(ordered);
            }
            return ordered;
        }

        private static float faceAngle(
                MeshVertex vertex,
                Vector3f center,
                Vector3f axisU,
                Vector3f axisV
        ) {
            Vector3f offset = new Vector3f(
                    vertex.x - center.x,
                    vertex.y - center.y,
                    vertex.z - center.z
            );
            return (float) Math.atan2(offset.dot(axisV), offset.dot(axisU));
        }

        boolean isMembrane() {
            return name.toLowerCase(java.util.Locale.ROOT).contains("membrane");
        }

        boolean usesTexture(int textureIndex) {
            for (Face face : faces.values()) {
                if (face.textureIndex == textureIndex) return true;
            }
            for (MeshFace face : meshFaces) {
                if (face.textureIndex == textureIndex) return true;
            }
            return false;
        }

        void renderMembrane(
                MatrixStack matrices,
                VertexConsumerProvider consumers,
                List<Texture> textures,
                int light,
                boolean uniformLighting,
                MatrixStack.Entry uniformLightingEntry,
                Map<String, Vector3f> markers,
                String[] markerNames
        ) {
            Vector3f a = markers.get(markerNames[0]);
            Vector3f b = markers.get(markerNames[1]);
            Vector3f c = markers.get(markerNames[2]);
            Vector3f d = markers.get(markerNames[3]);
            if (a == null || b == null || c == null || d == null) return;
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Vector3f normal = new Vector3f(b).sub(a)
                    .cross(new Vector3f(d).sub(a));
            if (normal.lengthSquared() < 1.0E-8F) {
                normal.set(0.0F, 0.0F, 1.0F);
            } else {
                normal.normalize();
            }
            drawMembraneFace(
                    "north", matrix, matrices, consumers, textures, light,
                    uniformLighting, uniformLightingEntry, a, b, c, d,
                    normal.x, normal.y, normal.z
            );
            drawMembraneFace(
                    "south", matrix, matrices, consumers, textures, light,
                    uniformLighting, uniformLightingEntry, d, c, b, a,
                    -normal.x, -normal.y, -normal.z
            );
        }

        private void drawMembraneFace(
                String side,
                Matrix4f matrix,
                MatrixStack matrices,
                VertexConsumerProvider consumers,
                List<Texture> textures,
                int light,
                boolean uniformLighting,
                MatrixStack.Entry uniformLightingEntry,
                Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                float nx, float ny, float nz
        ) {
            Face face = faces.get(side);
            if (face == null || face.textureIndex >= textures.size()) return;
            Texture texture = textures.get(face.textureIndex);
            if (texture.id == null) return;
            float u0 = face.uv[0] / texture.width;
            float v0 = face.uv[1] / texture.height;
            float u1 = face.uv[2] / texture.width;
            float v1 = face.uv[3] / texture.height;
            VertexConsumer out = consumers.getBuffer(net.minecraft.client.render.RenderLayers.entityTranslucent(texture.id));
            vertexWorld(out, matrix, matrices, uniformLightingEntry, a, u0, v1, light, nx, ny, nz, uniformLighting);
            vertexWorld(out, matrix, matrices, uniformLightingEntry, b, u1, v1, light, nx, ny, nz, uniformLighting);
            vertexWorld(out, matrix, matrices, uniformLightingEntry, c, u1, v0, light, nx, ny, nz, uniformLighting);
            vertexWorld(out, matrix, matrices, uniformLightingEntry, d, u0, v0, light, nx, ny, nz, uniformLighting);
        }

        void render(MatrixStack matrices, VertexConsumerProvider consumers, List<Texture> textures,
                    int light, boolean uniformLighting, MatrixStack.Entry uniformLightingEntry) {
            if (!meshFaces.isEmpty()) {
                renderMesh(matrices, consumers, textures, light, uniformLighting, uniformLightingEntry);
                return;
            }
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawFace("north", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
            drawFace("south", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
            drawFace("west", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
            drawFace("east", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
            drawFace("up", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, 0, 1, 0);
            drawFace("down", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y1, z2, x1, y1, z1, x2, y1, z1, x2, y1, z2, 0, -1, 0);
        }

        void renderCatalog(MatrixStack matrices, VertexConsumerProvider consumers, List<Texture> textures,
                           int light, boolean uniformLighting, MatrixStack.Entry uniformLightingEntry,
                           boolean frontIsXAxis) {
            if (!meshFaces.isEmpty()) {
                renderMesh(matrices, consumers, textures, light, uniformLighting, uniformLightingEntry);
                return;
            }
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            if (frontIsXAxis) {
                drawFace("west", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0);
                drawFace("east", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0);
            } else {
                drawFace("north", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1);
                drawFace("south", matrix, matrices, consumers, textures, light, uniformLighting, uniformLightingEntry, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1);
            }
        }

        private void renderMesh(
                MatrixStack matrices,
                VertexConsumerProvider consumers,
                List<Texture> textures,
                int light,
                boolean uniformLighting,
                MatrixStack.Entry uniformLightingEntry
        ) {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            for (MeshFace face : meshFaces) {
                if (face.textureIndex < 0 || face.textureIndex >= textures.size()) continue;
                Texture texture = textures.get(face.textureIndex);
                if (texture.id == null || face.vertices.size() < 3) continue;
                MeshVertex a = face.vertices.get(0);
                MeshVertex b = face.vertices.get(1);
                MeshVertex c = face.vertices.get(2);
                MeshVertex d = face.vertices.size() >= 4
                        ? face.vertices.get(3)
                        : c;
                Vector3f normal = new Vector3f(
                        b.x - a.x, b.y - a.y, b.z - a.z
                ).cross(new Vector3f(
                        c.x - a.x, c.y - a.y, c.z - a.z
                ));
                if (normal.lengthSquared() < 1.0E-8F) {
                    normal.set(0.0F, 0.0F, 1.0F);
                } else {
                    normal.normalize();
                }
                VertexConsumer out = consumers.getBuffer(
                        net.minecraft.client.render.RenderLayers.entityTranslucent(texture.id)
                );
                meshVertex(out, matrix, matrices, uniformLightingEntry,
                        a, texture, light, normal, uniformLighting);
                meshVertex(out, matrix, matrices, uniformLightingEntry,
                        b, texture, light, normal, uniformLighting);
                meshVertex(out, matrix, matrices, uniformLightingEntry,
                        c, texture, light, normal, uniformLighting);
                meshVertex(out, matrix, matrices, uniformLightingEntry,
                        d, texture, light, normal, uniformLighting);
            }
        }

        private static void meshVertex(
                VertexConsumer out,
                Matrix4f matrix,
                MatrixStack matrices,
                MatrixStack.Entry uniformLightingEntry,
                MeshVertex vertex,
                Texture texture,
                int light,
                Vector3f normal,
                boolean uniformLighting
        ) {
            vertex(
                    out, matrix, matrices, uniformLightingEntry,
                    vertex.x, vertex.y, vertex.z,
                    vertex.u / texture.width, vertex.v / texture.height,
                    light, normal.x, normal.y, normal.z, uniformLighting
            );
        }

        private void drawFace(String side, Matrix4f matrix, MatrixStack matrices, VertexConsumerProvider consumers,
                              List<Texture> textures, int light, boolean uniformLighting,
                              MatrixStack.Entry uniformLightingEntry,
                              float ax, float ay, float az, float bx, float by, float bz,
                              float cx, float cy, float cz, float dx, float dy, float dz,
                              float nx, float ny, float nz) {
            Face face = faces.get(side);
            if (face == null || face.textureIndex >= textures.size()) return;
            Texture texture = textures.get(face.textureIndex);
            if (texture.id == null) return;
            float u0 = face.uv[0] / texture.width;
            float v0 = face.uv[1] / texture.height;
            float u1 = face.uv[2] / texture.width;
            float v1 = face.uv[3] / texture.height;
            VertexConsumer out = consumers.getBuffer(net.minecraft.client.render.RenderLayers.entityTranslucent(texture.id));
            vertex(out, matrix, matrices, uniformLightingEntry, ax, ay, az, u0, v1, light, nx, ny, nz, uniformLighting);
            vertex(out, matrix, matrices, uniformLightingEntry, bx, by, bz, u1, v1, light, nx, ny, nz, uniformLighting);
            vertex(out, matrix, matrices, uniformLightingEntry, cx, cy, cz, u1, v0, light, nx, ny, nz, uniformLighting);
            vertex(out, matrix, matrices, uniformLightingEntry, dx, dy, dz, u0, v0, light, nx, ny, nz, uniformLighting);
        }

        private static void vertex(VertexConsumer out, Matrix4f matrix, MatrixStack matrices,
                                   MatrixStack.Entry uniformLightingEntry,
                                   float x, float y, float z, float u, float v, int light,
                                   float nx, float ny, float nz, boolean uniformLighting) {
            VertexConsumer vertex = out.vertex(matrix, x * PIXEL, y * PIXEL, z * PIXEL)
                    .color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                    .light(light);
            if (uniformLighting) {
                // Wimgs has mirrored group transforms. Supplying a stable
                // entity-space normal keeps both halves equally grey instead
                // of letting one mirrored half become bright white.
                vertex.normal(uniformLightingEntry, 0.0F, 0.0F, 1.0F);
            } else {
                vertex.normal(matrices.peek(), nx, ny, nz);
            }
        }

        private static void vertexWorld(
                VertexConsumer out,
                Matrix4f matrix,
                MatrixStack matrices,
                MatrixStack.Entry uniformLightingEntry,
                Vector3f point,
                float u, float v, int light,
                float nx, float ny, float nz,
                boolean uniformLighting
        ) {
            VertexConsumer vertex = out.vertex(matrix, point.x, point.y, point.z)
                    .color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                    .light(light);
            if (uniformLighting) {
                vertex.normal(uniformLightingEntry, 0.0F, 0.0F, 1.0F);
            } else {
                vertex.normal(matrices.peek(), nx, ny, nz);
            }
        }
    }

    private record Face(float[] uv, int textureIndex) { }

    private record MeshVertex(float x, float y, float z, float u, float v) { }

    private record MeshFace(int textureIndex, List<MeshVertex> vertices) { }

    /** A Blockbench group pivot. Applied root-to-leaf before its cube. */
    private record Transform(
            String uuid,
            String name,
            float ox, float oy, float oz,
            float rx, float ry, float rz,
            int depth
    ) {
        Transform atDepth(int depth) {
            return new Transform(uuid, name, ox, oy, oz, rx, ry, rz, depth);
        }

        void apply(MatrixStack matrices, CosmeticsPhysics.Pose physics,
                   boolean tipsOnlyPhysics) {
            matrices.translate(ox * PIXEL, oy * PIXEL, oz * PIXEL);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
            float flex = depthFlex(tipsOnlyPhysics);
            if (flex != 0.0F) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                        physics.segmentYaw() * flex * sideSign()
                ));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(physics.segmentPitch() * flex));
            }
            matrices.translate(-ox * PIXEL, -oy * PIXEL, -oz * PIXEL);
        }

        void apply(Matrix4f matrix, CosmeticsPhysics.Pose physics,
                   boolean tipsOnlyPhysics) {
            matrix.translate(ox * PIXEL, oy * PIXEL, oz * PIXEL);
            matrix.rotateZ((float) Math.toRadians(rz));
            matrix.rotateY((float) Math.toRadians(ry));
            matrix.rotateX((float) Math.toRadians(rx));
            float flex = depthFlex(tipsOnlyPhysics);
            if (flex != 0.0F) {
                matrix.rotateY((float) Math.toRadians(
                        physics.segmentYaw() * flex * sideSign()
                ));
                matrix.rotateX((float) Math.toRadians(physics.segmentPitch() * flex));
            }
            matrix.translate(-ox * PIXEL, -oy * PIXEL, -oz * PIXEL);
        }

        void applyStatic(Matrix4f matrix) {
            matrix.translate(ox * PIXEL, oy * PIXEL, oz * PIXEL);
            matrix.rotateZ((float) Math.toRadians(rz));
            matrix.rotateY((float) Math.toRadians(ry));
            matrix.rotateX((float) Math.toRadians(rx));
            matrix.translate(-ox * PIXEL, -oy * PIXEL, -oz * PIXEL);
        }

        private float depthFlex(boolean tipsOnlyPhysics) {
            if (tipsOnlyPhysics) {
                if (depth < 4) return 0.0F;
                return depth == 4 ? 0.65F : 1.15F;
            }
            return switch (depth) {
                case 0 -> 0.0F;
                case 1 -> 0.08F;
                case 2 -> 0.24F;
                case 3 -> 0.48F;
                case 4 -> 0.74F;
                default -> 1.0F;
            };
        }

        private float sideSign() {
            String normalized = name.toLowerCase(java.util.Locale.ROOT);
            if (normalized.contains("left")) return -1.0F;
            return 1.0F;
        }
    }

    record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float width() {
            return Math.max(0.001F, maxX - minX);
        }

        float height() {
            return Math.max(0.001F, maxY - minY);
        }

        float depth() {
            return Math.max(0.001F, maxZ - minZ);
        }

        float centerX() {
            return (minX + maxX) * 0.5F;
        }

        float centerY() {
            return (minY + maxY) * 0.5F;
        }

        float centerZ() {
            return (minZ + maxZ) * 0.5F;
        }

        boolean frontIsXAxis() {
            return width() < depth();
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
    }

    private static float decimal(JsonObject object, String key, float fallback) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return Float.parseFloat(object.get(key).getAsString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float[] numbers(JsonArray array, int expectedSize) {
        if (array == null || array.size() != expectedSize) return null;
        float[] values = new float[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static float[] numbersOrDefault(JsonArray array, int expectedSize, float fallback) {
        float[] values = numbers(array, expectedSize);
        if (values != null) return values;
        values = new float[expectedSize];
        java.util.Arrays.fill(values, fallback);
        return values;
    }
}

package vorga.phazeclient.implement.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2f;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vorga.phazeclient.implement.features.modules.client.Theme;
import vorga.phazeclient.mixins.DrawContextStateAccessor;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client-only entry point for attached cosmetics. */
public final class CosmeticsRenderer {
    public static final Identifier PHAZE_CAPE_TEXTURE =
            Identifier.of("phaze", "textures/cosmetics/phaze_cape.png");
    private static final Logger LOG = LoggerFactory.getLogger("PhazeCosmetics");
    private static final Map<ModelKey, CachedModel> MODELS = new HashMap<>();
    private static final Map<ModelKey, Long> FAILED_MODELS = new HashMap<>();
    private static final Map<ModelKey, CompletableFuture<PreparedModel>> PREPARING_MODELS = new HashMap<>();
    private static final Map<Path, CachedCape> CAPES = new HashMap<>();
    private static final Map<Path, CompletableFuture<PreparedCape>> PREPARING_CAPES = new HashMap<>();
    private static final ExecutorService MODEL_PRELOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Phaze cosmetic preloader");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final ThreadLocal<String> PREVIEW_SELECTION = new ThreadLocal<>();
    private static final ThreadLocal<Float> PREVIEW_ALPHA = new ThreadLocal<>();
    private static final ThreadLocal<Float> PREVIEW_BODY_YAW = new ThreadLocal<>();
    /*
     * EntityGuiElementRenderer switches RenderSystem's output target to its
     * own framebuffer while it invokes the entity renderer.  The normal world
     * entity provider is flushed later, after that target has been restored,
     * which used to draw cosmetic glow outside of the player preview.  Keep a
     * separate immediate provider for the current preview and flush it before
     * the entity renderer returns.
     */
    private static final ThreadLocal<PreviewVertexConsumers> PREVIEW_VERTEX_CONSUMERS =
            ThreadLocal.withInitial(PreviewVertexConsumers::new);
    static final CosmeticsPhysics.Pose STATIC_POSE =
            new CosmeticsPhysics.Pose(0.0F, 0.0F, 0.0F, 0.0F);
    private static final long FILE_STAMP_REFRESH_NS = 1_000_000_000L;

    private CosmeticsRenderer() { }

    public static void renderLocalPlayer(MatrixStack matrices, VertexConsumerProvider consumers,
                                         PlayerEntityRenderState state, int light,
                                         MatrixStack.Entry stableLightingEntry) {
        CosmeticsState settings = CosmeticsState.getInstance();
        String previewSelection = PREVIEW_SELECTION.get();
        boolean preview = previewSelection != null;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity self = client.player;
        if (self == null || client.world == null) return;
        Entity renderedEntity = client.world.getEntityById(state.id);
        if (!(renderedEntity instanceof PlayerEntity player)) return;
        boolean localPlayer = player.getId() == self.getId();
        if (preview) {
            if (!localPlayer || CosmeticsState.isCape(previewSelection)
                    || CosmeticsState.isHat(previewSelection)) return;
            renderBodySelection(
                    matrices, consumers, state, light, stableLightingEntry,
                    player, previewSelection, true
            );
            return;
        }
        if (!shouldRenderFor(player, state)) return;
        if (localPlayer) {
            if (client.options.getPerspective().isFirstPerson()) return;
            if (settings.isWingEquipped()) {
                renderBodySelection(
                        matrices, consumers, state, light, stableLightingEntry,
                        player, settings.getSelectedWing(), false
                );
            }
            if (settings.isPetEquipped()) {
                renderBodySelection(
                        matrices, consumers, state, light, stableLightingEntry,
                        player, settings.getSelectedPet(), false
                );
            }
        } else {
            if (!Theme.getInstance().renderOtherPlayerCosmetics.isValue()) {
                return;
            }
            CosmeticsSyncService sync = CosmeticsSyncService.getInstance();
            String wing = sync.wingSelectionFor(player.getUuid());
            if (wing != null) {
                renderBodySelection(
                        matrices, consumers, state, light, stableLightingEntry,
                        player, wing, false
                );
            }
            String pet = sync.petSelectionFor(player.getUuid());
            if (pet != null) {
                renderBodySelection(
                        matrices, consumers, state, light, stableLightingEntry,
                        player, pet, false
                );
            }
        }
    }

    public static void renderHeadCosmetic(MatrixStack matrices, VertexConsumerProvider consumers,
                                          PlayerEntityRenderState state, int light,
                                          MatrixStack.Entry stableLightingEntry) {
        CosmeticsState settings = CosmeticsState.getInstance();
        String previewSelection = PREVIEW_SELECTION.get();
        boolean preview = previewSelection != null;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity self = client.player;
        if (self == null || client.world == null) return;
        Entity renderedEntity = client.world.getEntityById(state.id);
        if (!(renderedEntity instanceof PlayerEntity player)) return;
        boolean localPlayer = player.getId() == self.getId();
        String selection;
        if (preview) {
            if (!localPlayer || !CosmeticsState.isHat(previewSelection)) return;
            selection = previewSelection;
        } else if (!shouldRenderFor(player, state)) {
            return;
        } else if (localPlayer) {
            if (!settings.isHatEquipped()
                    || client.options.getPerspective().isFirstPerson()) return;
            selection = settings.getSelectedHat();
        } else {
            if (!Theme.getInstance().renderOtherPlayerCosmetics.isValue()) {
                return;
            }
            selection = CosmeticsSyncService.getInstance()
                    .hatSelectionFor(player.getUuid());
            if (selection == null) return;
        }
        renderHeadSelection(
                matrices, consumers, light, stableLightingEntry, selection
        );
    }

    private static void renderBodySelection(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            PlayerEntityRenderState state,
            int light,
            MatrixStack.Entry stableLightingEntry,
            PlayerEntity player,
            String selection,
            boolean preview
    ) {
        if (CosmeticsState.NONE.equalsIgnoreCase(selection)) return;
        if (CosmeticsState.isCape(selection)) return;

        BlockbenchWingModel model = model(selection);
        if (model == null) return;

        matrices.push();
        if (CosmeticsState.isPet(selection)) {
            CosmeticsPhysics.CompanionPose companion =
                    CosmeticsPhysics.getInstance().sampleCompanion(
                            player, state, preview,
                            CosmeticsState.getInstance()
                                    .getPetFollowIntensity()
                    );
            float scale = petScale(selection);
            String lowerSelection = selection.toLowerCase(
                    java.util.Locale.ROOT
            );
            // The Goldfish mesh is authored facing the opposite direction
            // from the cube-based companions. Allay/Birb use the regular
            // half-turn so their face (and Allay's eyes) points forward.
            float companionYaw = lowerSelection.contains("goldfish")
                    ? 0.0F
                    : 180.0F;
            model.transformCompanionTo(
                    matrices,
                    companion.x(), companion.y(), companion.z(),
                    scale, companionYaw
            );
            model.render(
                    matrices, consumers, light, STATIC_POSE,
                    false, false, stableLightingEntry
            );
            matrices.pop();
            return;
        }

        CosmeticsPhysics.Pose physics = CosmeticsPhysics.getInstance().sample(
                player, state, preview,
                CosmeticsState.getInstance().getWingMotion()
        );
        // Figura models use a feet-up Y axis (shoulders around Y=22-24),
        // while vanilla's player model uses a head-down model axis. Move the
        // imported pivot into the upper-back region before drawing it inside
        // LivingEntityRenderer's already-established body transform.
        boolean isWimgs = CosmeticsState.WIMGS.equalsIgnoreCase(selection);
        boolean isFluffyWings = CosmeticsState.FLUFFY_WINGS.equalsIgnoreCase(selection);
        boolean isCodexWings = selection.toLowerCase(java.util.Locale.ROOT).startsWith("ayldwt");
        // Every imported project uses different absolute Blockbench
        // coordinates. Normalize its real central wing hinge onto the same
        // point at the rear face of the vanilla torso. The surrounding matrix
        // already contains PlayerEntityModel.body's live pose.
        // LivingEntityRenderer's player-body transform presents its rear face
        // on positive local Z here. Wimgs and Fluffy previously used negative
        // Z and therefore crossed the torso onto the chest. Keep their hinge
        // just outside the actual back; Fluffy is authored slightly too high,
        // so lower only that model without changing its rotation or spread.
        float attachmentY = isFluffyWings ? 0.19F : 0.10F;
        float attachmentZ = (isWimgs || isFluffyWings || isCodexWings) ? 0.128F : -0.132F;
        model.translateAttachmentTo(matrices, 0.0F, attachmentY, attachmentZ);
        if (isWimgs) {
            // The supplied Wimgs project faces the opposite direction from
            // vanilla's back-feature convention. Rotate around the authored
            // wing root, not the body-space origin: a global Y rotation moves
            // the already-attached pivot through the torso and makes the
            // model appear on the chest or float away from the player.
            model.rotateAroundRootY(matrices, 180.0F);
            // Generic Blockbench uses an up-facing Y axis. Turn the supplied
            // project around its authored spine pivot so its feathers hang
            // down without moving the attachment point.
            model.rotateAroundRootZ(matrices, 180.0F);
            // Keep the lower feathers clear of the ground while preserving
            // the exact attachment position.
            model.scaleAroundRoot(matrices, 0.90F);
        }
        // Root inertia follows movement, flight and sharp body turns.
        model.rotateAroundRootX(matrices, physics.rootPitch());
        model.rotateAroundRootY(matrices, physics.rootYaw());
        // Segment flex is distributed by hierarchy depth in the loader,
        // therefore future grouped models receive physics automatically.
        model.render(
                matrices, consumers, light, physics,
                true, false, stableLightingEntry
        );
        matrices.pop();
    }

    private static void renderHeadSelection(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            int light,
            MatrixStack.Entry stableLightingEntry,
            String selection
    ) {
        if (selection == null || CosmeticsState.NONE.equalsIgnoreCase(selection)
                || !CosmeticsState.isHat(selection)) return;
        BlockbenchWingModel model = model(selection);
        if (model == null) return;
        boolean turtle = selection.toLowerCase(java.util.Locale.ROOT)
                .contains("turtle");
        String lowerSelection = selection.toLowerCase(java.util.Locale.ROOT);
        float scale;
        if (turtle) {
            scale = 0.65F;
        } else if (lowerSelection.contains("maple witch")) {
            scale = 0.82F;
        } else if (lowerSelection.contains("violet witch")) {
            scale = 1.05F;
        } else {
            scale = 1.0F;
        }
        matrices.push();
        if (lowerSelection.contains("ice hat")) {
            // Keep the brim just clear of the vanilla head surface so the two
            // coplanar faces do not flicker (z-fighting).
            matrices.translate(0.0F, 0.10F, -0.015625F);
        } else if (lowerSelection.contains("sprint hat")) {
            matrices.translate(0.0F, 0.11F, 0.0F);
        } else if (lowerSelection.contains("brewing hat")) {
            matrices.translate(0.0F, 0.06F, 0.0F);
        } else if (lowerSelection.contains("maple witch")) {
            matrices.translate(0.0F, 0.18F, 0.0F);
        } else if (lowerSelection.contains("violet witch")) {
            matrices.translate(0.0F, 0.15F, 0.0F);
        }
        model.transformOntoHead(matrices, scale, 0.0F);
        model.render(
                matrices, consumers, light, STATIC_POSE,
                false, false, stableLightingEntry
        );
        matrices.pop();
    }

    private static float petScale(String selection) {
        String lower = selection.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("goldfish")) return 1.15F;
        if (lower.contains("ally")) return 0.65F;
        return 1.0F;
    }

    public static void renderPreview(String selection, float alpha, Runnable renderer) {
        renderPreview(selection, alpha, 0.0F, renderer);
    }

    /**
     * Renders a cosmetic preview while exposing the model's GUI yaw to
     * renderer hooks. World-space labels inherit that GUI rotation, whereas
     * their billboard must remain facing the preview camera.
     */
    public static void renderPreview(String selection, float alpha, float bodyYaw, Runnable renderer) {
        String previous = PREVIEW_SELECTION.get();
        Float previousAlpha = PREVIEW_ALPHA.get();
        Float previousBodyYaw = PREVIEW_BODY_YAW.get();
        PREVIEW_SELECTION.set(selection);
        PREVIEW_ALPHA.set(Math.max(0.0F, Math.min(1.0F, alpha)));
        PREVIEW_BODY_YAW.set(bodyYaw);
        try {
            renderer.run();
        } finally {
            if (previous == null) {
                PREVIEW_SELECTION.remove();
            } else {
                PREVIEW_SELECTION.set(previous);
            }
            if (previousAlpha == null) {
                PREVIEW_ALPHA.remove();
            } else {
                PREVIEW_ALPHA.set(previousAlpha);
            }
            if (previousBodyYaw == null) {
                PREVIEW_BODY_YAW.remove();
            } else {
                PREVIEW_BODY_YAW.set(previousBodyYaw);
            }
        }
    }

    public static float previewAlpha() {
        Float alpha = PREVIEW_ALPHA.get();
        return alpha == null ? 1.0F : alpha;
    }

    /**
     * @return the yaw applied by the cosmetics GUI, or {@code null} outside
     * a live player preview.
     */
    public static Float previewBodyYaw() {
        return PREVIEW_BODY_YAW.get();
    }

    /**
     * Draws only the imported cosmetic model in GUI space. No player entity,
     * feature renderer or physics participates in this path.
     */
    public static boolean renderCatalogModel(DrawContext context, String selection,
                                             float x, float y, float width, float height,
                                             float alpha, long frameId) {
        if (CosmeticsState.isCape(selection)) {
            // Catalog cards intentionally stay static, even for animated GIF
            // capes. The full player preview and actual cape use capeTexture.
            Identifier texture = capePreviewTextureIfReady(selection);
            if (texture == null) return false;
            int drawHeight = Math.max(1, Math.round(height * 0.88F));
            int drawWidth = Math.max(1, Math.round(drawHeight * (10.0F / 16.0F)));
            int drawX = Math.round(x + (width - drawWidth) * 0.5F);
            int drawY = Math.round(y + (height - drawHeight) * 0.5F);
            int color = ((int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24)
                    | 0x00FFFFFF;
            // The HD file is a 4x standard 64x32 cape. Draw the outer
            // 10x16 face (logical UV 1,1) from its real 256x128 pixels.
            context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                    texture, drawX, drawY, 4.0F, 4.0F, drawWidth, drawHeight,
                    40, 64, 256, 128, color);
            return true;
        }
        if (thumbnailModel(selection) == null || width <= 1.0F || height <= 1.0F) return false;
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        int x2 = Math.round(x + width);
        int y2 = Math.round(y + height);
        ((DrawContextStateAccessor) (Object) context).phaze$getGuiRenderState()
                .addSpecialElement(new CosmeticGuiElementState(
                        selection, x1, y1, x2, y2,
                        alpha, frameId, new Matrix3x2f(context.getMatrices()),
                        context.scissorStack.peekLast()));
        return true;
    }

    public static boolean hasCachedCatalogThumbnail(String selection) {
        return CosmeticGuiElementRenderer.hasCachedThumbnail(selection);
    }

    public static void beginCatalogModelBatch(float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.getBufferBuilders().getEntityVertexConsumers().draw();
        // 1.21.11 bakes blend/depth state into RenderLayers.
    }

    /**
     * Queues one wing-only catalog thumbnail. The caller owns a matching
     * begin/end pair so every visible card can share one entity-buffer flush.
     */
    public static boolean renderCatalogWingBatched(DrawContext context, String selection,
                                                   float x, float y, float width, float height) {
        if (CosmeticsState.isCape(selection)) return false;
        BlockbenchWingModel model = model(selection);
        MinecraftClient client = MinecraftClient.getInstance();
        if (model == null || client == null || width <= 1.0F || height <= 1.0F) {
            return false;
        }

        BlockbenchWingModel.Bounds bounds = model.bounds();
        boolean frontIsXAxis = bounds.frontIsXAxis();
        float projectedWidth = frontIsXAxis ? bounds.depth() : bounds.width();
        float scale = Math.min(
                width * 0.84F / projectedWidth,
                height * 0.84F / bounds.height()
        );

        // DrawContext is 2D in 1.21.11. Model cards use a registered special
        // GUI element renderer; this legacy immediate path is intentionally
        // disabled and retained only as a compatibility API for the view.
        return false;
        /* MatrixStack matrices = context.getMatrices();
        VertexConsumerProvider.Immediate consumers =
                client.getBufferBuilders().getEntityVertexConsumers();

        matrices.push();
        matrices.translate(x + width * 0.5F, y + height * 0.5F, 260.0F);
        matrices.scale(scale, -scale, scale);
        if (frontIsXAxis) {
            // A flat YZ-authored cosmetic is viewed along X.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
        } else {
            // Standard Blockbench front: camera looks at the model's Z face.
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        }
        String lowerSelection = selection.toLowerCase(
                java.util.Locale.ROOT
        );
        if (lowerSelection.contains("ally")
                || lowerSelection.contains("birb")) {
            // Three-quarter view keeps the face readable in a static card.
            matrices.multiply(
                    RotationAxis.POSITIVE_Y.rotationDegrees(45.0F)
            );
        } else if (lowerSelection.contains("turtle")) {
            matrices.multiply(
                    RotationAxis.POSITIVE_Y.rotationDegrees(180.0F)
            );
        }
        if (CosmeticsState.isWing(selection)
                && !CosmeticsState.WIMGS.equalsIgnoreCase(selection)
                && !CosmeticsState.FLUFFY_WINGS.equalsIgnoreCase(selection)) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        }
        matrices.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());
        if (!model.renderCatalogBuffers(matrices)) {
            model.renderCatalog(matrices, consumers, 0x00F000F0, matrices.peek());
        }
        matrices.pop();
        return true; */
    }

    public static void endCatalogModelBatch() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.getBufferBuilders().getEntityVertexConsumers().draw();
        }
        // No global render state to restore on 1.21.11.
    }

    public static boolean isRenderingPreview() {
        return PREVIEW_SELECTION.get() != null;
    }

    public static VertexConsumerProvider.Immediate previewVertexConsumers() {
        return PREVIEW_VERTEX_CONSUMERS.get().consumers;
    }

    public static void flushPreviewVertexConsumers() {
        PREVIEW_VERTEX_CONSUMERS.get().consumers.draw();
    }

    private static final class PreviewVertexConsumers {
        private static final int BUFFER_SIZE = 1 << 20;
        private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
        private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
    }

    /**
     * Resolves the cosmetic represented by a player render state. Shared by
     * wings and the native cape feature so local previews and remote sync use
     * exactly the same selection rules.
     */
    public static String capeSelectionFor(PlayerEntityRenderState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null || state == null) {
            return null;
        }
        if (state instanceof PreviewMarker marker) {
            String preview = marker.phaze$previewSelection();
            if (preview != null) {
                return CosmeticsState.isCape(preview) ? preview : null;
            }
        }
        Entity renderedEntity = client.world.getEntityById(state.id);
        if (!(renderedEntity instanceof PlayerEntity player)) return null;
        return capeSelectionFor(player);
    }

    /**
     * Invisible players only expose cosmetics when their actual armour is
     * visible too.  This keeps a fully invisible player fully hidden, while
     * preserving the expected cosmetic attachment on visible armour.
     */
    public static boolean shouldRenderFor(PlayerEntityRenderState state) {
        if (state == null || !state.invisible) return true;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null) return false;
        Entity entity = client.world.getEntityById(state.id);
        return entity instanceof PlayerEntity player && hasVisibleArmor(player);
    }

    public static boolean shouldRenderFor(PlayerEntity player) {
        return player == null || !player.isInvisible() || hasVisibleArmor(player);
    }

    private static boolean shouldRenderFor(PlayerEntity player, PlayerEntityRenderState state) {
        return state == null || !state.invisible || hasVisibleArmor(player);
    }

    private static boolean hasVisibleArmor(PlayerEntity player) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                    && !player.getEquippedStack(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static String capeSelectionFor(PlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || player == null) return null;
        boolean localPlayer = player.getId() == client.player.getId();
        String preview = PREVIEW_SELECTION.get();
        if (preview != null) {
            return localPlayer && CosmeticsState.isCape(preview) ? preview : null;
        }
        if (localPlayer) {
            CosmeticsState settings = CosmeticsState.getInstance();
            return settings.isCapeEquipped() ? settings.getSelectedCape() : null;
        }
        if (!Theme.getInstance().renderOtherPlayerCosmetics.isValue()) {
            return null;
        }
        return CosmeticsSyncService.getInstance().capeSelectionFor(player.getUuid());
    }

    public static synchronized Identifier capeTexture(String selection) {
        return resolveCapeTexture(selection, true);
    }

    /** Returns the first GIF frame so catalog cards never animate. */
    public static synchronized Identifier capePreviewTexture(String selection) {
        return resolveCapeTexture(selection, false);
    }

    /** Starts PNG/GIF decoding away from the render thread. */
    public static synchronized void requestCapeWarmup(String selection) {
        try {
            CosmeticsState.CosmeticEntry entry = CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || !CosmeticsState.isCape(selection)) return;
            Path file = entry.file().toAbsolutePath().normalize();
            long stamp = Files.getLastModifiedTime(file).toMillis();
            CachedCape cached = CAPES.get(file);
            if (cached != null && cached.stamp() == stamp) return;
            if (PREPARING_CAPES.containsKey(file)) return;
            String token = Integer.toUnsignedString(file.toString().hashCode(), 16);
            PREPARING_CAPES.put(file, CompletableFuture.supplyAsync(() -> {
                try {
                    List<DecodedCapeFrame> decoded = isGif(file)
                            ? readGifFrames(file) : readStaticCapeFrame(file);
                    return new PreparedCape(decoded, stamp, token);
                } catch (Throwable error) {
                    LOG.error("Failed to prepare cape '{}' from {}", selection, file, error);
                    return null;
                }
            }, MODEL_PRELOADER));
        } catch (Throwable error) {
            LOG.error("Failed to queue cape '{}' for preparation", selection, error);
        }
    }

    /** Finishes an asynchronously decoded cape without blocking on file IO. */
    public static synchronized Identifier capePreviewTextureIfReady(String selection) {
        try {
            CosmeticsState.CosmeticEntry entry = CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || !CosmeticsState.isCape(selection)) return null;
            Path file = entry.file().toAbsolutePath().normalize();
            long stamp = Files.getLastModifiedTime(file).toMillis();
            long now = System.nanoTime();
            CachedCape cached = CAPES.get(file);
            if (cached != null && cached.stamp() == stamp) {
                cached.checkedAtNanos = now;
                return cached.textureAt(now, false);
            }
            CompletableFuture<PreparedCape> future = PREPARING_CAPES.get(file);
            if (future == null) {
                requestCapeWarmup(selection);
                return null;
            }
            if (!future.isDone()) return null;
            PREPARING_CAPES.remove(file);
            PreparedCape prepared = future.getNow(null);
            if (prepared == null || prepared.stamp() != stamp) return null;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return null;
            if (cached != null) cached.destroy(client);
            List<CapeFrame> frames = registerCapeFrames(
                    client, prepared.frames(), prepared.textureToken());
            if (frames.isEmpty()) return null;
            cached = new CachedCape(frames, stamp, now);
            CAPES.put(file, cached);
            return cached.textureAt(now, false);
        } catch (Throwable error) {
            LOG.error("Failed to finish prepared cape '{}'", selection, error);
            return null;
        }
    }

    /**
     * Loads static PNG capes and every GIF frame from the same local catalog.
     * Animated capes advance only in live player renders; the catalog uses the
     * first frame through {@link #capePreviewTexture(String)}.
     */
    private static Identifier resolveCapeTexture(String selection, boolean animated) {
        try {
            CosmeticsState.CosmeticEntry entry =
                    CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || !CosmeticsState.isCape(selection)) return null;
            Path file = entry.file().toAbsolutePath().normalize();
            CachedCape cached = CAPES.get(file);
            long now = System.nanoTime();
            if (cached != null && now - cached.checkedAtNanos() < FILE_STAMP_REFRESH_NS) {
                return cached.textureAt(now, animated);
            }
            long stamp = Files.getLastModifiedTime(file).toMillis();
            if (cached != null && cached.stamp() == stamp) {
                cached.checkedAtNanos = now;
                return cached.textureAt(now, animated);
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return null;
            if (cached != null) {
                cached.destroy(client);
            }
            String token = Integer.toUnsignedString(file.toString().hashCode(), 16);
            List<CapeFrame> frames = loadCapeFrames(client, file, token);
            if (frames.isEmpty()) return null;
            cached = new CachedCape(frames, stamp, now);
            CAPES.put(file, cached);
            return cached.textureAt(now, animated);
        } catch (Throwable error) {
            LOG.error("Failed to load cape texture '{}'", selection, error);
            return null;
        }
    }

    private static List<CapeFrame> loadCapeFrames(
            MinecraftClient client, Path file, String textureToken
    ) throws Exception {
        List<DecodedCapeFrame> decoded = isGif(file)
                ? readGifFrames(file)
                : readStaticCapeFrame(file);
        return registerCapeFrames(client, decoded, textureToken);
    }

    private static List<CapeFrame> registerCapeFrames(
            MinecraftClient client, List<DecodedCapeFrame> decoded, String textureToken
    ) {
        List<CapeFrame> frames = new ArrayList<>(decoded.size());
        try {
            for (int index = 0; index < decoded.size(); index++) {
                DecodedCapeFrame frame = decoded.get(index);
                Identifier id = Identifier.of("phaze", "dynamic/capes/" + textureToken + "/" + index);
                String textureLabel = "Phaze cape frame " + index;
                client.getTextureManager().destroyTexture(id);
                client.getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(() -> textureLabel, frame.image()));
                frames.add(new CapeFrame(id, frame.durationNanos()));
            }
            return frames;
        } catch (Throwable error) {
            for (CapeFrame frame : frames) {
                client.getTextureManager().destroyTexture(frame.id());
            }
            for (int index = frames.size(); index < decoded.size(); index++) {
                decoded.get(index).image().close();
            }
            throw error;
        }
    }

    private static List<DecodedCapeFrame> readStaticCapeFrame(Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            return List.of(new DecodedCapeFrame(NativeImage.read(input), 0L));
        }
    }

    private static List<DecodedCapeFrame> readGifFrames(Path file) throws Exception {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw new IllegalStateException("GIF reader is unavailable");
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                int frameCount = reader.getNumImages(true);
                int canvasWidth = gifDimension(reader.getStreamMetadata(), "logicalScreenWidth", reader.getWidth(0));
                int canvasHeight = gifDimension(reader.getStreamMetadata(), "logicalScreenHeight", reader.getHeight(0));
                BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                BufferedImage restorePoint = null;
                GifFrameInfo previous = null;
                List<DecodedCapeFrame> frames = new ArrayList<>(frameCount);
                try {
                    for (int index = 0; index < frameCount; index++) {
                        if (previous != null) {
                            applyGifDisposal(canvas, restorePoint, previous);
                        }
                        BufferedImage raw = reader.read(index);
                        GifFrameInfo current = gifFrameInfo(reader.getImageMetadata(index), raw);
                        restorePoint = "restoreToPrevious".equals(current.disposalMethod())
                                ? copyImage(canvas) : null;
                        Graphics2D graphics = canvas.createGraphics();
                        try {
                            int drawX = raw.getWidth() == canvasWidth && raw.getHeight() == canvasHeight ? 0 : current.x();
                            int drawY = raw.getWidth() == canvasWidth && raw.getHeight() == canvasHeight ? 0 : current.y();
                            graphics.drawImage(raw, drawX, drawY, null);
                        } finally {
                            graphics.dispose();
                            raw.flush();
                        }
                        frames.add(new DecodedCapeFrame(toNativeCapeImage(canvas), current.durationNanos()));
                        previous = current;
                    }
                    return frames;
                } catch (Throwable error) {
                    for (DecodedCapeFrame frame : frames) frame.image().close();
                    throw error;
                } finally {
                    canvas.flush();
                    if (restorePoint != null) restorePoint.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean isGif(Path file) {
        return file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
    }

    private static int gifDimension(IIOMetadata metadata, String attribute, int fallback) {
        String value = gifMetadataAttribute(metadata, "LogicalScreenDescriptor", attribute);
        try {
            return value == null ? fallback : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static GifFrameInfo gifFrameInfo(IIOMetadata metadata, BufferedImage raw) {
        int x = gifIntAttribute(metadata, "ImageDescriptor", "imageLeftPosition", 0);
        int y = gifIntAttribute(metadata, "ImageDescriptor", "imageTopPosition", 0);
        int width = gifIntAttribute(metadata, "ImageDescriptor", "imageWidth", raw.getWidth());
        int height = gifIntAttribute(metadata, "ImageDescriptor", "imageHeight", raw.getHeight());
        int delayCentiseconds = gifIntAttribute(metadata, "GraphicControlExtension", "delayTime", 10);
        String disposal = gifMetadataAttribute(metadata, "GraphicControlExtension", "disposalMethod");
        return new GifFrameInfo(x, y, width, height, disposal == null ? "none" : disposal,
                Math.max(50_000_000L, delayCentiseconds * 10_000_000L));
    }

    private static int gifIntAttribute(IIOMetadata metadata, String nodeName, String attribute, int fallback) {
        String value = gifMetadataAttribute(metadata, nodeName, attribute);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String gifMetadataAttribute(IIOMetadata metadata, String nodeName, String attribute) {
        if (metadata == null) return null;
        try {
            Node node = findMetadataNode(metadata.getAsTree(metadata.getNativeMetadataFormatName()), nodeName);
            return node == null || node.getAttributes() == null || node.getAttributes().getNamedItem(attribute) == null
                    ? null : node.getAttributes().getNamedItem(attribute).getNodeValue();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Node findMetadataNode(Node root, String name) {
        if (root == null) return null;
        if (name.equals(root.getNodeName())) return root;
        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findMetadataNode(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static void applyGifDisposal(BufferedImage canvas, BufferedImage restorePoint, GifFrameInfo previous) {
        if ("restoreToBackgroundColor".equals(previous.disposalMethod())) {
            Graphics2D graphics = canvas.createGraphics();
            try {
                graphics.setComposite(java.awt.AlphaComposite.Clear);
                graphics.fillRect(previous.x(), previous.y(), previous.width(), previous.height());
            } finally {
                graphics.dispose();
            }
        } else if ("restoreToPrevious".equals(previous.disposalMethod()) && restorePoint != null) {
            Graphics2D graphics = canvas.createGraphics();
            try {
                graphics.drawImage(restorePoint, 0, 0, null);
            } finally {
                graphics.dispose();
                restorePoint.flush();
            }
        }
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static NativeImage toNativeCapeImage(BufferedImage source) throws Exception {
        // The catalogue preview uses the same 4x 64x32 cape UV layout as
        // bundled capes (256x128), including the outer 10x16 face crop.
        BufferedImage cape = new BufferedImage(256, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cape.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, 256, 128, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(cape, "png", output);
            try (ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray())) {
                return NativeImage.read(input);
            }
        } finally {
            cape.flush();
        }
    }

    /** Starts file parsing away from the render thread; GPU upload stays deferred. */
    public static synchronized void requestModelWarmup(String selection) {
        try {
            CosmeticsState.CosmeticEntry entry = CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || CosmeticsState.isCape(selection)
                    || !Files.isRegularFile(entry.file())) return;
            Path file = entry.file().toAbsolutePath().normalize();
            String variant = entry.textureVariant() == null ? "" : entry.textureVariant();
            CosmeticsState.CosmeticType type = CosmeticsState.typeOf(selection);
            ModelKey key = new ModelKey(file, variant, type);
            long stamp = Files.getLastModifiedTime(file).toMillis();
            CachedModel cached = MODELS.get(key);
            if (cached != null && cached.stamp == stamp) return;
            if (PREPARING_MODELS.containsKey(key)) return;
            PREPARING_MODELS.put(key, CompletableFuture.supplyAsync(() -> {
                try {
                    return new PreparedModel(
                            BlockbenchWingModel.load(file, variant, type), stamp);
                } catch (Throwable error) {
                    LOG.error("Failed to prepare cosmetic '{}' from {}", selection, file, error);
                    return null;
                }
            }, MODEL_PRELOADER));
        } catch (Throwable error) {
            LOG.error("Failed to queue cosmetic '{}' for preparation", selection, error);
        }
    }

    static synchronized BlockbenchWingModel thumbnailModel(String selection) {
        try {
            CosmeticsState.CosmeticEntry entry = CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || CosmeticsState.isCape(selection)
                    || !Files.isRegularFile(entry.file())) return null;
            Path file = entry.file().toAbsolutePath().normalize();
            String variant = entry.textureVariant() == null ? "" : entry.textureVariant();
            CosmeticsState.CosmeticType type = CosmeticsState.typeOf(selection);
            ModelKey key = new ModelKey(file, variant, type);
            long stamp = Files.getLastModifiedTime(file).toMillis();
            CachedModel cached = MODELS.get(key);
            if (cached != null && cached.stamp == stamp) return cached.model;

            CompletableFuture<PreparedModel> future = PREPARING_MODELS.get(key);
            if (future == null) {
                requestModelWarmup(selection);
                return null;
            }
            if (!future.isDone()) return null;
            PREPARING_MODELS.remove(key);
            PreparedModel prepared = future.getNow(null);
            if (prepared == null || prepared.stamp != stamp) return null;
            prepared.model.registerTextures(Integer.toUnsignedString(key.hashCode(), 16));
            if (cached != null) cached.model.close();
            CachedModel installed = new CachedModel(prepared.model, stamp, System.nanoTime());
            MODELS.put(key, installed);
            FAILED_MODELS.remove(key);
            return installed.model;
        } catch (Throwable error) {
            LOG.error("Failed to finish prepared cosmetic '{}'", selection, error);
            return null;
        }
    }

    static synchronized BlockbenchWingModel model(String selection) {
        try {
            CosmeticsState.CosmeticEntry entry =
                    CosmeticsState.getInstance().resolveEntry(selection);
            if (entry == null || !Files.isRegularFile(entry.file())) return null;
            Path file = entry.file().toAbsolutePath().normalize();
            String variant = entry.textureVariant() == null ? "" : entry.textureVariant();
            CosmeticsState.CosmeticType type = CosmeticsState.typeOf(selection);
            ModelKey key = new ModelKey(file, variant, type);
            CachedModel cached = MODELS.get(key);
            long now = System.nanoTime();
            if (cached != null && now - cached.checkedAtNanos < FILE_STAMP_REFRESH_NS) {
                return cached.model;
            }
            long stamp = Files.getLastModifiedTime(file).toMillis();
            if (cached == null || stamp != cached.stamp) {
                try {
                    BlockbenchWingModel loaded = BlockbenchWingModel.load(file, variant, type);
                    String textureKey = Integer.toUnsignedString(key.hashCode(), 16);
                    loaded.registerTextures(textureKey);
                    if (cached != null) {
                        cached.model.close();
                    }
                    cached = new CachedModel(loaded, stamp, now);
                    MODELS.put(key, cached);
                    FAILED_MODELS.remove(key);
                } catch (Throwable error) {
                    Long failedStamp = FAILED_MODELS.put(key, stamp);
                    if (failedStamp == null || failedStamp != stamp) {
                        LOG.error("Failed to load cosmetic '{}' from {} (variant '{}')",
                                selection, file, variant, error);
                    }
                    return null;
                }
            }
            cached.checkedAtNanos = now;
            return cached.model;
        } catch (Throwable error) {
            // Do not make a malformed cosmetic capable of taking down the renderer.
            LOG.error("Failed to resolve cosmetic '{}'", selection, error);
            return null;
        }
    }

    private static final class CachedModel {
        private final BlockbenchWingModel model;
        private final long stamp;
        private long checkedAtNanos;

        private CachedModel(BlockbenchWingModel model, long stamp, long checkedAtNanos) {
            this.model = model;
            this.stamp = stamp;
            this.checkedAtNanos = checkedAtNanos;
        }
    }

    private record PreparedModel(BlockbenchWingModel model, long stamp) { }

    private record PreparedCape(List<DecodedCapeFrame> frames, long stamp,
                                String textureToken) { }

    private static final class CachedCape {
        private final List<CapeFrame> frames;
        private final long stamp;
        private long checkedAtNanos;

        private CachedCape(List<CapeFrame> frames, long stamp, long checkedAtNanos) {
            this.frames = List.copyOf(frames);
            this.stamp = stamp;
            this.checkedAtNanos = checkedAtNanos;
        }

        private Identifier textureAt(long nowNanos, boolean animated) {
            if (frames.isEmpty()) return null;
            if (!animated || frames.size() == 1) return frames.get(0).id();
            long duration = 0L;
            for (CapeFrame frame : frames) duration += frame.durationNanos();
            if (duration <= 0L) return frames.get(0).id();
            long elapsed = Math.floorMod(nowNanos, duration);
            for (CapeFrame frame : frames) {
                if (elapsed < frame.durationNanos()) return frame.id();
                elapsed -= frame.durationNanos();
            }
            return frames.get(frames.size() - 1).id();
        }

        private void destroy(MinecraftClient client) {
            for (CapeFrame frame : frames) {
                client.getTextureManager().destroyTexture(frame.id());
            }
        }

        private long stamp() {
            return stamp;
        }

        private long checkedAtNanos() {
            return checkedAtNanos;
        }
    }

    private record CapeFrame(Identifier id, long durationNanos) { }

    private record DecodedCapeFrame(NativeImage image, long durationNanos) { }

    private record GifFrameInfo(int x, int y, int width, int height,
                                String disposalMethod, long durationNanos) { }

    private record ModelKey(
            Path file,
            String textureVariant,
            CosmeticsState.CosmeticType type
    ) {
    }
}

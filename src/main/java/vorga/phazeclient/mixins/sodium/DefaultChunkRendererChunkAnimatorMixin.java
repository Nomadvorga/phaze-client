package vorga.phazeclient.mixins.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vorga.phazeclient.base.util.shader.ChunkAnimatorShaderPatcher;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

/**
 * Sodium 0.8 renderer path for World Animator.
 *
 * <p>Each Sodium region contains at most 256 sections. The matching shader
 * patch reads one vec4 for each section, allowing a newly uploaded section to
 * move independently instead of moving the whole 128x64x128 region.</p>
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class DefaultChunkRendererChunkAnimatorMixin {
    private static final int[] PHAZE$SLOTS = new int[256];
    private static final float[] PHAZE$VALUES = new float[256];
    private static final float[] PHAZE$DATA = new float[256 * 4];
    private static final float[] PHAZE$DIRECTION = new float[3];

    private static ChunkShaderInterface phaze$shader;
    private static int phaze$blockIndex = GL31.GL_INVALID_INDEX;
    private static int phaze$modeLocation = -1;
    private static int phaze$ubo;
    private static int phaze$lastMode = Integer.MIN_VALUE;
    private static boolean phaze$lastIdentity;

    @ModifyArgs(
            method = "setModelMatrixUniforms",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setRegionOffset(FFF)V",
                    remap = false
            ),
            remap = false
    )
    private static void phaze$prepareProgram(
            Args args,
            @Local(argsOnly = true) ChunkShaderInterface shader,
            @Local(argsOnly = true) RenderRegion region) {
        phaze$refreshProgram(shader);

        ChunkAnimator animator = ChunkAnimator.getInstance();
        if (animator == null || !animator.isEnabled()
                || phaze$blockIndex != GL31.GL_INVALID_INDEX || region == null
                || animator.getAnimationModeIndex() != 1) {
            return;
        }

        int count = phaze$collectSlots(region);
        if (count == 0) {
            return;
        }
        float distance = animator.getRegionMagnitude(
                region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                PHAZE$SLOTS, count);
        if (distance == 0.0F) {
            return;
        }
        animator.writeAnimationDirection(PHAZE$DIRECTION);
        args.set(0, args.<Float>get(0) + distance * PHAZE$DIRECTION[0]);
        args.set(1, args.<Float>get(1) + distance * PHAZE$DIRECTION[1]);
        args.set(2, args.<Float>get(2) + distance * PHAZE$DIRECTION[2]);
    }

    @Inject(method = "setModelMatrixUniforms", at = @At("TAIL"), remap = false)
    private static void phaze$uploadSectionAnimation(
            ChunkShaderInterface shader, RenderRegion region, CameraTransform camera,
            GlBuffer chunkData, CallbackInfo ci) {
        phaze$refreshProgram(shader);
        if (phaze$blockIndex == GL31.GL_INVALID_INDEX) {
            return;
        }

        ChunkAnimator animator = ChunkAnimator.getInstance();
        int mode = animator == null ? 0 : animator.getAnimationModeIndex();
        if (mode == 0) {
            if (!phaze$lastIdentity) {
                phaze$fillIdentity();
                phaze$upload();
                phaze$lastIdentity = true;
            }
            phaze$setMode(0);
            return;
        }

        int count = phaze$collectSlots(region);
        if (count == 0) {
            return;
        }

        boolean animating = phaze$pack(animator, region, count, mode);
        if (animating || !phaze$lastIdentity) {
            phaze$upload();
            phaze$lastIdentity = !animating;
        }
        phaze$setMode(mode);
    }

    private static void phaze$refreshProgram(ChunkShaderInterface shader) {
        if (shader == phaze$shader) {
            return;
        }
        phaze$shader = shader;
        phaze$blockIndex = GL31.GL_INVALID_INDEX;
        phaze$modeLocation = -1;
        phaze$lastMode = Integer.MIN_VALUE;
        phaze$lastIdentity = false;
        if (shader == null) {
            return;
        }
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) {
            return;
        }
        phaze$blockIndex = GL31.glGetUniformBlockIndex(program, "PhazeChunkAnimBlock");
        if (phaze$blockIndex != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, phaze$blockIndex,
                    ChunkAnimatorShaderPatcher.CHUNK_ANIM_UBO_BINDING);
            phaze$modeLocation = GL20.glGetUniformLocation(program, "u_PhazeChunkAnimMode");
        }
    }

    private static int phaze$collectSlots(RenderRegion region) {
        if (region == null) {
            return 0;
        }
        ChunkRenderList list = region.getRenderList();
        if (list == null) {
            return 0;
        }
        ByteIterator iterator = list.sectionsWithGeometryIterator(false);
        int count = 0;
        while (iterator != null && iterator.hasNext() && count < PHAZE$SLOTS.length) {
            PHAZE$SLOTS[count++] = iterator.nextByteAsInt();
        }
        return count;
    }

    private static boolean phaze$pack(ChunkAnimator animator, RenderRegion region, int count, int mode) {
        phaze$fillIdentity();
        if (mode == 1) {
            if (!animator.writeRegionSectionYOffsets(region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOTS, count, PHAZE$VALUES)) {
                return false;
            }
            animator.writeAnimationDirectionPerSection(PHAZE$DIRECTION);
            for (int i = 0; i < 256; i++) {
                float offset = PHAZE$VALUES[i];
                if (offset != 0.0F) {
                    int data = i * 4;
                    PHAZE$DATA[data] = offset * PHAZE$DIRECTION[0];
                    PHAZE$DATA[data + 1] = offset * PHAZE$DIRECTION[1];
                    PHAZE$DATA[data + 2] = offset * PHAZE$DIRECTION[2];
                }
            }
            return true;
        }
        if (mode == 3) {
            if (!animator.writeRegionSectionScaleValues(region.getOriginX(), region.getOriginY(), region.getOriginZ(),
                    PHAZE$SLOTS, count, PHAZE$VALUES)) {
                return false;
            }
            for (int i = 0; i < 256; i++) {
                PHAZE$DATA[i * 4 + 3] = PHAZE$VALUES[i];
            }
            return true;
        }
        return false;
    }

    private static void phaze$fillIdentity() {
        for (int i = 0; i < 256; i++) {
            int data = i * 4;
            PHAZE$DATA[data] = 0.0F;
            PHAZE$DATA[data + 1] = 0.0F;
            PHAZE$DATA[data + 2] = 0.0F;
            PHAZE$DATA[data + 3] = 1.0F;
        }
    }

    private static void phaze$upload() {
        if (phaze$ubo == 0) {
            phaze$ubo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, phaze$ubo);
        GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, PHAZE$DATA, GL15.GL_STREAM_DRAW);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER,
                ChunkAnimatorShaderPatcher.CHUNK_ANIM_UBO_BINDING, phaze$ubo);
    }

    private static void phaze$setMode(int mode) {
        if (phaze$modeLocation >= 0 && phaze$lastMode != mode) {
            GL20.glUniform1i(phaze$modeLocation, mode);
            phaze$lastMode = mode;
        }
    }
}

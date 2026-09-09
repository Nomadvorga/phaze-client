package vorga.phazeclient.base.util.shader;

import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import vorga.phazeclient.implement.features.modules.other.ChunkAnimator;

import java.util.HashMap;
import java.util.Map;

/** Sets the four lightweight uniforms used with Sodium's native chunk timing UBO. */
public final class SodiumChunkAnimatorUniforms {
    private static final float[] DIRECTION = new float[3];
    private static final Map<Integer, Locations> PROGRAMS = new HashMap<>();

    private SodiumChunkAnimatorUniforms() { }

    public static void upload(RenderRegion ignoredRegion) {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program == 0) return;

        Locations locations = PROGRAMS.computeIfAbsent(program, SodiumChunkAnimatorUniforms::findLocations);
        if (!locations.valid()) return;

        ChunkAnimator animator = ChunkAnimator.getInstance();
        int mode = animator != null && animator.isEnabled() ? animator.getAnimationModeIndex() : 0;
        GL20.glUniform1i(locations.mode, mode);
        if (mode == 0) return;

        GL20.glUniform1f(locations.durationInv,
                1.0F / Math.max(1.0F, animator.duration.getInt()));

        if (mode == 1) {
            animator.writeAnimationDirectionPerSection(DIRECTION);
            GL20.glUniform1f(locations.distance, animator.distance.getInt());
            GL20.glUniform3f(locations.direction, DIRECTION[0], DIRECTION[1], DIRECTION[2]);
        }
    }

    private static Locations findLocations(int program) {
        return new Locations(
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimMode"),
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimDurationInv"),
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimDistance"),
                GL20.glGetUniformLocation(program, "u_PhazeChunkAnimDirection")
        );
    }

    private record Locations(int mode, int durationInv, int distance, int direction) {
        boolean valid() {
            return mode >= 0 && durationInv >= 0;
        }
    }
}

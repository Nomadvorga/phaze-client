package vorga.phazeclient.api.system.colorcorrection;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.lwjgl.opengl.GL20;

public final class WorldColorShaderHelper {
    public static final String BLOCK_COLOR_UNIFORM = "PhazeBlockColor";
    public static final String BLOCK_PARAMS_UNIFORM = "PhazeBlockParams";
    public static final String FLUID_COLOR_UNIFORM = "PhazeFluidColor";
    public static final String FLUID_PARAMS_UNIFORM = "PhazeFluidParams";
    public static final String PLAYER_COLOR_UNIFORM = "PhazePlayerColor";
    public static final String PLAYER_PARAMS_UNIFORM = "PhazePlayerParams";
    public static final String ENTITY_COLOR_UNIFORM = "PhazeEntityColor";
    public static final String ENTITY_PARAMS_UNIFORM = "PhazeEntityParams";

    private static final WorldColorCorrectionController.Target[] RAW_TARGETS = {
            WorldColorCorrectionController.Target.BLOCKS,
            WorldColorCorrectionController.Target.FLUIDS,
            WorldColorCorrectionController.Target.PLAYERS,
            WorldColorCorrectionController.Target.ENTITIES
    };
    private static final Int2ObjectOpenHashMap<ProgramState> PROGRAM_STATES = new Int2ObjectOpenHashMap<>();

    private WorldColorShaderHelper() {
    }

    public static void uploadTerrainUniformsToCurrentGlProgram() {
        try {
            uploadTerrainUniforms(GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        } catch (Throwable ignored) {
        }
    }

    /** For a bound program whose id is already known; runs on every bind, so it must not query GL. */
    public static void uploadTerrainUniforms(int program) {
        try {
            if (program <= 0) {
                return;
            }

            ProgramState state = PROGRAM_STATES.get(program);
            if (state == null) {
                state = new ProgramState(program);
                PROGRAM_STATES.put(program, state);
            }
            if (!state.locations.any()) {
                return;
            }
            long signature = computeStateSignature();
            if (state.lastSignature == signature) {
                return;
            }

            ProgramLocations locations = state.locations;
            uploadRawTarget(locations.blockColor, locations.blockParams, WorldColorCorrectionController.Target.BLOCKS);
            uploadRawTarget(locations.fluidColor, locations.fluidParams, WorldColorCorrectionController.Target.FLUIDS);
            uploadRawTarget(locations.playerColor, locations.playerParams, WorldColorCorrectionController.Target.PLAYERS);
            uploadRawTarget(locations.entityColor, locations.entityParams, WorldColorCorrectionController.Target.ENTITIES);
            state.lastSignature = signature;
        } catch (Throwable ignored) {
        }
    }

    public static void registerCurrentProgramInstance() {
        registerProgramInstance(GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM));
    }

    /** A new program object may reuse a deleted program's id; drop that id's cached locations. */
    public static void registerProgramInstance(int program) {
        if (program > 0) {
            PROGRAM_STATES.remove(program);
        }
    }

    private static long computeStateSignature() {
        long signature = 0x9E3779B97F4A7C15L;
        for (WorldColorCorrectionController.Target target : RAW_TARGETS) {
            boolean active = WorldColorCorrectionController.isTargetActive(target);
            signature = signature * 31L + (active ? 1L : 0L);
            if (!active) {
                continue;
            }
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getRed(target));
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getGreen(target));
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getBlue(target));
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getAlpha(target));
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getSaturation(target));
            signature = signature * 31L + Float.floatToRawIntBits(WorldColorCorrectionController.getBrightness(target));
        }
        return signature;
    }

    private static void uploadRawTarget(int colorLocation, int paramsLocation, WorldColorCorrectionController.Target target) {
        boolean active = WorldColorCorrectionController.isTargetActive(target);
        float alpha = WorldColorCorrectionController.supportsAlpha(target)
                ? WorldColorCorrectionController.getAlpha(target)
                : 1.0F;

        if (colorLocation >= 0) {
            if (active) {
                GL20.glUniform4f(
                        colorLocation,
                        WorldColorCorrectionController.getRed(target),
                        WorldColorCorrectionController.getGreen(target),
                        WorldColorCorrectionController.getBlue(target),
                        alpha
                );
            } else {
                GL20.glUniform4f(colorLocation, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        if (paramsLocation >= 0) {
            if (active) {
                GL20.glUniform4f(
                        paramsLocation,
                        WorldColorCorrectionController.getSaturation(target),
                        WorldColorCorrectionController.getBrightness(target),
                        1.0F,
                        0.0F
                );
            } else {
                GL20.glUniform4f(paramsLocation, 1.0F, 0.0F, 0.0F, 0.0F);
            }
        }
    }

    private record ProgramLocations(
            int blockColor,
            int blockParams,
            int fluidColor,
            int fluidParams,
            int playerColor,
            int playerParams,
            int entityColor,
            int entityParams
    ) {
        boolean any() {
            return blockColor >= 0 || blockParams >= 0 || fluidColor >= 0 || fluidParams >= 0
                    || playerColor >= 0 || playerParams >= 0 || entityColor >= 0 || entityParams >= 0;
        }

        private ProgramLocations(int program) {
            this(
                    GL20.glGetUniformLocation(program, BLOCK_COLOR_UNIFORM),
                    GL20.glGetUniformLocation(program, BLOCK_PARAMS_UNIFORM),
                    GL20.glGetUniformLocation(program, FLUID_COLOR_UNIFORM),
                    GL20.glGetUniformLocation(program, FLUID_PARAMS_UNIFORM),
                    GL20.glGetUniformLocation(program, PLAYER_COLOR_UNIFORM),
                    GL20.glGetUniformLocation(program, PLAYER_PARAMS_UNIFORM),
                    GL20.glGetUniformLocation(program, ENTITY_COLOR_UNIFORM),
                    GL20.glGetUniformLocation(program, ENTITY_PARAMS_UNIFORM)
            );
        }
    }

    private static final class ProgramState {
        private final ProgramLocations locations;
        private long lastSignature = Long.MIN_VALUE;

        private ProgramState(int program) {
            this.locations = new ProgramLocations(program);
        }
    }
}

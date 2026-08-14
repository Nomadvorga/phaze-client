package vorga.phazeclient.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import vorga.phazeclient.implement.features.modules.other.CustomFog;

/**
 * Custom Fog: overrides the fog colour and distances from the module's own
 * settings, rather than scaling whatever vanilla computed.
 *
 * <h3>1.21.11 port</h3>
 *
 * <p>{@code BackgroundRenderer} no longer exists. Fog moved to
 * {@link FogRenderer}, and the whole "return a Fog packet" design went with
 * it: {@code Fog} and {@code FogShape} are gone, and the fog parameters are
 * now written into a std140 UBO through
 * {@code applyFog(ByteBuffer, int, Vector4f, float x6)}. The old
 * {@code @Inject ... cancellable} that returned a hand-built {@code Fog} has
 * no equivalent, so this hooks the UBO write instead and rewrites the values
 * on their way in.
 *
 * <p>The six floats are, in order: environmental start/end, render-distance
 * start/end, sky end, cloud end. Sky and cloud only follow the module when
 * "Affect Sky" is on, which is what that setting used to gate via the removed
 * {@code FogType.FOG_SKY} branch.
 *
 * <p><b>Why this cancels rather than amends.</b> An earlier version modified
 * the arguments of vanilla's UBO write, which left the result dependent on
 * everything else that runs in that method. Sodium Extra drives the same path,
 * so the two fought and the distance setting had no visible effect. Cancelling
 * at HEAD and writing the block here makes the module the sole author of the
 * fog state - the same contract the 1.21.4 version had when it returned its own
 * fully specified {@code Fog} object.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererCustomFogMixin {

    @Shadow @Final private MappableRingBuffer fogBuffer;

    @Unique private static boolean phaze$sodiumFogLookupDone;
    @Unique private static Constructor<?> phaze$sodiumFogConstructor;
    @Unique private static Field phaze$sodiumFogField;

    /**
     * Vanilla's own std140 writer for the fog block.
     *
     * <p>Reused rather than reimplemented so the byte layout can never drift
     * from whatever {@code fog.glsl} expects.
     */
    @Invoker("applyFog")
    abstract void phaze$writeFogBlock(ByteBuffer buffer, int offset, Vector4f color,
                                      float environmentalStart, float environmentalEnd,
                                      float renderDistanceStart, float renderDistanceEnd,
                                      float skyEnd, float cloudEnd);

    /**
     * Take the fog over completely instead of amending vanilla's result.
     *
     * <p>The previous approach hooked the UBO write at the end of vanilla's
     * calculation, which meant the values still had to survive everything else
     * in that method - including other mods. Sodium Extra manipulates the same
     * path, so Custom Fog ended up fighting it and the distance setting could
     * not move the result.
     *
     * <p>Cancelling at HEAD makes this module the sole author of the fog block:
     * nothing vanilla or anyone else computes downstream is consulted, which is
     * the same "fully specified fog packet" contract the 1.21.4 version had
     * when it returned its own {@code Fog} object.
     */
    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void phaze$replaceFog(Camera camera,
                                  int viewDistance,
                                  RenderTickCounter tickCounter,
                                  float skyDarkness,
                                  ClientWorld world,
                                  CallbackInfoReturnable<Vector4f> cir) {
        CustomFog module = CustomFog.getInstance();
        if (phaze$shouldSkip(module, camera)) {
            return;
        }

        float distance = phaze$finiteOr(module.getDistance(), 96.0F);
        distance = Math.max(2.0F, distance);
        float density = phaze$clamp01(phaze$finiteOr(module.getDensity(), 0.5F));
        float start = distance * (1.0F - density);

        int rgb = module.getResolvedRgb();
        Vector4f color = new Vector4f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                1.0F);

        // Park sky and cloud fog outside the reachable range when the module
        // is configured to affect terrain only.
        float skyEnd = module.isAffectSky() ? distance : PHAZE_UNREACHABLE;
        float cloudEnd = module.isAffectSky() ? distance : PHAZE_UNREACHABLE;

        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(fogBuffer.getBlocking(), false, true)) {
            phaze$writeFogBlock(
                    view.data(),
                    0,
                    color,
                    // Environmental is the SPHERICAL term and render-distance the
                    // CYLINDRICAL one; fog.glsl takes their max. Parking the
                    // spherical pair out of reach leaves the cylindrical term
                    // alone, which is FogShape.CYLINDER as 1.21.4 used.
                    PHAZE_UNREACHABLE, PHAZE_UNREACHABLE,
                    start, distance,
                    skyEnd, cloudEnd);
        }

        // Sodium snapshots the result of this method for its chunk shaders.
        // Since Custom Fog intentionally cancels the method at HEAD, Sodium's
        // normal RETURN hook never runs; update that optional snapshot here.
        phaze$syncSodiumFogSnapshot(color, start, distance);

        cir.setReturnValue(color);
    }

    @Unique
    private void phaze$syncSodiumFogSnapshot(Vector4f color, float start, float end) {
        try {
            if (!phaze$sodiumFogLookupDone) {
                phaze$sodiumFogLookupDone = true;
                Class<?> parametersClass = Class.forName(
                        "net.caffeinemc.mods.sodium.client.util.FogParameters",
                        false,
                        getClass().getClassLoader());
                phaze$sodiumFogConstructor = parametersClass.getDeclaredConstructor(
                        float.class, float.class, float.class, float.class,
                        float.class, float.class, float.class, float.class);
                phaze$sodiumFogConstructor.setAccessible(true);
                for (Field candidate : getClass().getDeclaredFields()) {
                    if (candidate.getType() == parametersClass) {
                        candidate.setAccessible(true);
                        phaze$sodiumFogField = candidate;
                        break;
                    }
                }
            }
            if (phaze$sodiumFogConstructor == null || phaze$sodiumFogField == null) {
                return;
            }
            Object parameters = phaze$sodiumFogConstructor.newInstance(
                    color.x, color.y, color.z, color.w,
                    PHAZE_UNREACHABLE, PHAZE_UNREACHABLE, start, end);
            phaze$sodiumFogField.set(this, parameters);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Sodium is optional; vanilla already consumed the UBO above.
        }
    }

    /**
     * A fog bound no vertex can reach, used to switch a term off.
     *
     * <p>Deliberately far below {@code Float.MAX_VALUE}: the shader computes
     * {@code (d - start) / (end - start)} before the range checks short-circuit
     * on some drivers, and huge magnitudes there invite inf/NaN. A million
     * blocks is unreachable in practice and stays in comfortable float range.
     */
    @Unique
    private static final float PHAZE_UNREACHABLE = 1.0E6F;

    /**
     * Recolour the fog colour itself.
     *
     * <p>The UBO hook above only changes how fog is BLENDED onto geometry. The
     * sky is different: vanilla clears the background and tints the horizon
     * band with the value {@code getFogColor} returns, which is why overriding
     * only the UBO left the middle of the sky untouched while everything else
     * took the custom tint.
     *
     * <p>1.21.11 made this an instance method (it was static on the old
     * {@code BackgroundRenderer}), so the handler is non-static too.
     */
    @ModifyReturnValue(method = "getFogColor", at = @At("RETURN"), require = 1)
    private Vector4f phaze$fogColor(Vector4f original,
                                    Camera camera,
                                    float tickDelta,
                                    ClientWorld world,
                                    int viewDistance,
                                    float skyDarkness) {
        CustomFog module = CustomFog.getInstance();
        if (phaze$shouldSkip(module, camera)) {
            return original;
        }
        int rgb = module.getResolvedRgb();
        return new Vector4f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                original.w);
    }

    /**
     * Keep the fog UBO live even when vanilla fog is switched off.
     *
     * <p>{@code getFogBuffer} early-returns an all-zero "empty" slice while
     * {@code fogEnabled} is false (that flag is what F3+F toggles). Without
     * this, disabling vanilla fog would silently disable Custom Fog too, since
     * both read the same buffer. Reporting the flag as true for our own module
     * lets the values written above reach the shaders regardless.
     *
     * <p>Only the read inside {@code getFogBuffer} is touched, so
     * {@code toggleFog()} still flips the real field and vanilla's own state
     * stays consistent.
     */
    @ModifyExpressionValue(
            method = "getFogBuffer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/fog/FogRenderer;fogEnabled:Z",
                    opcode = Opcodes.GETSTATIC
            ),
            require = 1
    )
    private boolean phaze$keepFogBufferAlive(boolean original) {
        if (original) {
            return true;
        }
        return !phaze$shouldSkip(CustomFog.getInstance());
    }

    /**
     * Submersion fog (water / lava / powder snow) and the blindness and
     * darkness effects are left to vanilla on purpose - they are gameplay
     * visibility cues, and overriding them would hand the user an advantage
     * the module is not meant to give.
     */
    @Unique
    private static boolean phaze$shouldSkip(CustomFog module) {
        if (module == null || !module.isEnabled()) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null) {
            return true;
        }
        Camera camera = client.gameRenderer.getCamera();
        return phaze$shouldSkip(module, camera);
    }

    @Unique
    private static boolean phaze$shouldSkip(CustomFog module, Camera camera) {
        if (module == null || !module.isEnabled() || camera == null) {
            return true;
        }
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) {
            return true;
        }
        Entity entity = camera.getFocusedEntity();
        if (entity instanceof LivingEntity living) {
            return living.hasStatusEffect(StatusEffects.BLINDNESS)
                    || living.hasStatusEffect(StatusEffects.DARKNESS);
        }
        return false;
    }

    @Unique
    private static float phaze$clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }

    @Unique
    private static float phaze$finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}

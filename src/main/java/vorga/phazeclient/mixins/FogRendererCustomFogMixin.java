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

@Mixin(FogRenderer.class)
public abstract class FogRendererCustomFogMixin {

    @Shadow @Final private MappableRingBuffer fogBuffer;

    @Unique private static boolean phaze$sodiumFogLookupDone;
    @Unique private static Constructor<?> phaze$sodiumFogConstructor;
    @Unique private static Field phaze$sodiumFogField;

    @Invoker("applyFog")
    abstract void phaze$writeFogBlock(ByteBuffer buffer, int offset, Vector4f color,
                                      float environmentalStart, float environmentalEnd,
                                      float renderDistanceStart, float renderDistanceEnd,
                                      float skyEnd, float cloudEnd);

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

        float skyEnd = module.isAffectSky() ? distance : PHAZE_UNREACHABLE;
        float cloudEnd = module.isAffectSky() ? distance : PHAZE_UNREACHABLE;

        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder()
                .mapBuffer(fogBuffer.getBlocking(), false, true)) {
            phaze$writeFogBlock(
                    view.data(),
                    0,
                    color,

                    PHAZE_UNREACHABLE, PHAZE_UNREACHABLE,
                    start, distance,
                    skyEnd, cloudEnd);
        }

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

        }
    }

    @Unique
    private static final float PHAZE_UNREACHABLE = 1.0E6F;

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

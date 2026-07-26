package vorga.phazeclient.api.system.colorcorrection;

import java.lang.reflect.Method;

public final class WorldColorTerrainSync {
    private WorldColorTerrainSync() {
    }

    public static boolean isShaderPackActive() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = irisApiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return false;
            }
            Method isShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
            Object value = isShaderPackInUse.invoke(api);
            return value instanceof Boolean active && active;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldUseBakedFluidColors() {
        // Fluid colors come from the chunk shader uniforms, so slider changes
        // apply to already-built chunks without a terrain rebuild.
        return false;
    }

    public static void requestTerrainRefreshThrottled(long minDelayMs) {
        // Live shader-uniform updates are used now.
    }
}

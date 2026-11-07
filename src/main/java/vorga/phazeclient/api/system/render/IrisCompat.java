package vorga.phazeclient.api.system.render;

import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;

/** Optional Iris queries. Iris classes are only touched when the mod is actually loaded. */
public final class IrisCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("iris");

    private IrisCompat() {}

    /**
     * Iris renders entities a second time from the sun's point of view. Geometry captured
     * there carries the shadow camera in its matrices and must never reach the main view.
     */
    public static boolean isShadowPass() {
        return LOADED && Api.isShadowPass();
    }

    private static final class Api {
        private static boolean isShadowPass() {
            return IrisApi.getInstance().isRenderingShadowPass();
        }
    }
}

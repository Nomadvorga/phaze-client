package vorga.phazeclient.mixins;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class PhazeMixinPlugin implements IMixinConfigPlugin {

    private static final boolean SODIUM_LOADED =
            FabricLoader.getInstance().isModLoaded("sodium");

    private static final boolean IRIS_LOADED =
            FabricLoader.getInstance().isModLoaded("iris");

    private static final boolean EXORDIUM_LOADED =
            FabricLoader.getInstance().isModLoaded("exordium");

    private static final boolean SCREENCOPY_MOD_LOADED =
            FabricLoader.getInstance().isModLoaded("screencopy");

    private static final boolean REESES_LOADED =
            FabricLoader.getInstance().isModLoaded("reeses-sodium-options");

    private static final boolean SODIUM_EXTRA_LOADED =
            FabricLoader.getInstance().isModLoaded("sodium-extra");

    private static final Set<String> VANILLA_ONLY = Set.of(
            "vorga.phazeclient.mixins.WorldRendererChunkAnimatorMixin"
    );

    private static final Set<String> SODIUM_ONLY = Set.of(
            "vorga.phazeclient.mixins.sodium.DefaultChunkRendererChunkAnimatorMixin",
            "vorga.phazeclient.mixins.sodium.ShaderParserChunkAnimatorMixin",
            "vorga.phazeclient.mixins.sodium.SodiumGlShaderChunkAnimatorMixin"
    );

    private static final Set<String> REESES_ONLY = Set.of();

    private static final Set<String> IRIS_ONLY = Set.of(
            "vorga.phazeclient.mixins.iris.IrisGlShaderChunkAnimatorMixin"
    );

    private static final Set<String> SODIUM_EXTRA_ONLY = Set.of(
            "vorga.phazeclient.mixins.sodiumextra.SodiumExtraFrameCounterMixin"
    );

    private static final Set<String> EXORDIUM_ONLY = Set.of(
            "vorga.phazeclient.mixins.exordium.ExordiumBufferInstanceMixin",
            "vorga.phazeclient.mixins.exordium.ExordiumBufferedComponentMixin",
            "vorga.phazeclient.mixins.exordium.ExordiumDelayedRenderCallManagerMixin"
    );

    private static final Set<String> SKIP_IF_SCREENCOPY_MOD = Set.of(
            "vorga.phazeclient.mixins.ScreenshotRecorderScreencopyMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {

        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (SODIUM_LOADED && VANILLA_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (!SODIUM_LOADED && SODIUM_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (!IRIS_LOADED && IRIS_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (!SODIUM_EXTRA_LOADED && SODIUM_EXTRA_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (!EXORDIUM_LOADED && EXORDIUM_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (!REESES_LOADED && REESES_ONLY.contains(mixinClassName)) {
            return false;
        }
        if (SCREENCOPY_MOD_LOADED && SKIP_IF_SCREENCOPY_MOD.contains(mixinClassName)) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {

        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}

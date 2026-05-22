package vorga.phazeclient.mixins;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin that gates chunk-animator mixins by renderer.
 * Phaze ships two parallel implementations of the ChunkAnimator:
 *
 * <ul>
 *   <li>{@link WorldRendererChunkAnimatorMixin} — targets the vanilla
 *       {@code WorldRenderer.renderLayer} model-offset uniform and
 *       only works when Sodium is <em>not</em> installed.</li>
 *   <li>{@code sodium.DefaultChunkRendererChunkAnimatorMixin} —
 *       targets Sodium's {@code DefaultChunkRenderer.setModelMatrixUniforms}
 *       and only works when Sodium <em>is</em> installed.</li>
 * </ul>
 *
 * <p>We cannot leave both mixins active unconditionally. Sodium's
 * {@code LevelRendererMixin} {@code @Overwrite}s vanilla
 * {@code renderLayer}, replacing the model-offset uniform path with
 * its own region renderer; any {@code @At("INVOKE"...GlUniform.set...)}
 * injection from our mod is then rejected by the Mixin processor
 * at the prepare stage (merged-same-priority error), taking the whole
 * game down at init. Forcing {@code require = 0, expect = 0} doesn't
 * help because the failure is before the match-count check.
 *
 * <p>The correct fix is to skip loading the conflicting mixin
 * entirely, which is exactly what this plugin does via
 * {@link #shouldApplyMixin(String, String)} — Mixin consults the
 * plugin before class-level processing, so an unapplied mixin never
 * reaches the injector preparation phase and can't generate that
 * error. Symmetrically, the Sodium-only mixin is skipped when Sodium
 * is missing so users without Sodium don't get a
 * {@code NoClassDefFoundError} trying to resolve
 * {@code net.caffeinemc.mods.sodium.*} at classload time.
 */
public final class PhazeMixinPlugin implements IMixinConfigPlugin {

    /**
     * Cached at plugin load; {@code FabricLoader.isModLoaded} is safe
     * to call from {@link #onLoad(String)} onwards and doesn't change
     * for the lifetime of the JVM. Reading a field is cheaper than
     * hitting the loader map on every mixin gate check.
     */
    private static final boolean SODIUM_LOADED =
            FabricLoader.getInstance().isModLoaded("sodium");

    /**
     * Iris loaded? When true, the Iris-side ChunkAnimator mixin
     * patches Iris-compiled chunk shaders so per-section animation
     * works under shader packs. When false, the Iris-only mixin's
     * target class ({@code net.irisshaders.iris.gl.shader.GlShader})
     * isn't on classpath and loading the mixin would NCDFE.
     */
    private static final boolean IRIS_LOADED =
            FabricLoader.getInstance().isModLoaded("iris");

    /**
     * The standalone {@code screencopy} mod (ImUrX, the same project
     * Phaze's clipboard path was adapted from) hooks
     * {@code ScreenshotRecorder.saveScreenshotInner} via its own
     * mixin. When both Phaze's screencopy mixin and the standalone
     * mod are loaded, Mixin sees the post-transform method body the
     * standalone mod produced - which has a different parameter list
     * - and rejects our injector with an "Invalid descriptor" apply
     * error, taking the game down at init. Skipping our mixin in
     * that case is correct: the standalone mod already does the
     * clipboard mirror, so Phaze's ChatHelper "Screencopy" toggle
     * just visibly becomes a no-op while the standalone mod keeps
     * working. Removing the standalone mod re-enables Phaze's path
     * automatically.
     */
    private static final boolean SCREENCOPY_MOD_LOADED =
            FabricLoader.getInstance().isModLoaded("screencopy");

    /**
     * Reese's Sodium Options loaded? It's an optional add-on to
     * Sodium that replaces the stock options screen with a tabbed
     * UI (search field, category tabs). Phaze ships a cursor mixin
     * that targets one of its internal classes, gated behind this
     * flag so users running plain Sodium without Reese's don't
     * crash with a NoClassDefFoundError on the missing target.
     */
    private static final boolean REESES_LOADED =
            FabricLoader.getInstance().isModLoaded("reeses-sodium-options");

    /**
     * Mixins that must NOT load when Sodium is present. Full class
     * names so they compare directly against the {@code mixinClassName}
     * the {@link IMixinConfigPlugin} contract passes in.
     */
    private static final Set<String> VANILLA_ONLY = Set.of(
            "vorga.phazeclient.mixins.WorldRendererChunkAnimatorMixin"
    );

    /**
     * Mixins that must NOT load when Sodium is absent. Same rationale
     * as {@link #VANILLA_ONLY} but for the other direction — their
     * target classes live in the Sodium jar and wouldn't resolve
     * without it.
     */
    private static final Set<String> SODIUM_ONLY = Set.of(
            "vorga.phazeclient.mixins.sodium.DefaultChunkRendererChunkAnimatorMixin",
            "vorga.phazeclient.mixins.sodium.ShaderParserChunkAnimatorMixin",
            "vorga.phazeclient.mixins.sodium.SodiumGlShaderChunkAnimatorMixin",
            "vorga.phazeclient.mixins.sodium.SodiumFlatButtonCursorMixin",
            "vorga.phazeclient.mixins.sodium.SodiumControlElementCursorMixin",
            "vorga.phazeclient.mixins.sodium.SodiumSliderDragCursorMixin",
            "vorga.phazeclient.mixins.sodium.ReesesSearchFieldCursorMixin"
    );

    /**
     * Mixins that need Reese's Sodium Options (an optional add-on
     * to Sodium) on classpath. They reference
     * {@code me.flashyreese.mods.reeses_sodium_options.*} classes
     * which don't exist on classpath unless the mod is installed,
     * so loading them under plain Sodium would NCDFE at the mixin
     * apply phase. Note this is checked in addition to
     * {@link #SODIUM_LOADED} - Reese's depends on Sodium, so a
     * machine without Sodium also won't have Reese's, and the
     * Sodium gate filters out those cases first.
     */
    private static final Set<String> REESES_ONLY = Set.of(
            "vorga.phazeclient.mixins.sodium.ReesesSearchFieldCursorMixin"
    );

    /**
     * Mixins that must NOT load when Iris is absent. Their
     * {@code @Mixin(targets = ...)} reference Iris-internal classes
     * which don't exist on classpath unless Iris is installed.
     */
    private static final Set<String> IRIS_ONLY = Set.of(
            "vorga.phazeclient.mixins.iris.IrisGlShaderChunkAnimatorMixin",
            "vorga.phazeclient.mixins.iris.IrisElementWidgetCursorMixin"
    );

    /**
     * Mixins disabled when the standalone {@code screencopy} mod is
     * loaded - see {@link #SCREENCOPY_MOD_LOADED} for the full
     * rationale. {@code NativeImageGetColorInvoker} is left active
     * regardless because it's a passive accessor that can't conflict
     * with anything.
     */
    private static final Set<String> SKIP_IF_SCREENCOPY_MOD = Set.of(
            "vorga.phazeclient.mixins.ScreenshotRecorderScreencopyMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        // No-op: everything we need is already decided from
        // FabricLoader state captured in the static initialiser.
    }

    @Override
    public String getRefMapperConfig() {
        // Using the default refmap ("phaze.refmap.json" generated
        // by Loom) - no custom mapper.
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
        // No additional target classes beyond what each mixin declares.
    }

    @Override
    public List<String> getMixins() {
        // No dynamically-added mixins - the JSON list is the source
        // of truth.
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op.
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // No-op.
    }
}

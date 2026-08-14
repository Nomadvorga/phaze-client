package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vorga.phazeclient.implement.cosmetics.PreviewMarker;

/** Persists GUI preview data until 1.21.11's deferred entity pass runs. */
@Mixin(PlayerEntityRenderState.class)
public abstract class PlayerEntityRenderStatePreviewMixin implements PreviewMarker {
    @Unique private @Nullable String phaze$previewSelection;
    @Unique private float phaze$previewYaw;
    @Unique private float phaze$previewAlpha = 1.0F;

    @Override
    public @Nullable String phaze$previewSelection() {
        return phaze$previewSelection;
    }

    @Override
    public void phaze$previewSelection(@Nullable String selection) {
        phaze$previewSelection = selection;
    }

    @Override
    public float phaze$previewYaw() {
        return phaze$previewYaw;
    }

    @Override
    public void phaze$previewYaw(float yaw) {
        phaze$previewYaw = yaw;
    }

    @Override
    public float phaze$previewAlpha() {
        return phaze$previewAlpha;
    }

    @Override
    public void phaze$previewAlpha(float alpha) {
        phaze$previewAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
    }
}

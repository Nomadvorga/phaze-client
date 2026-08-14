package vorga.phazeclient.implement.cosmetics;

import org.jetbrains.annotations.Nullable;

/** Cosmetic data attached to a deferred GUI player render state. */
public interface PreviewMarker {
    @Nullable String phaze$previewSelection();

    void phaze$previewSelection(@Nullable String selection);

    float phaze$previewYaw();

    void phaze$previewYaw(float yaw);

    float phaze$previewAlpha();

    void phaze$previewAlpha(float alpha);
}

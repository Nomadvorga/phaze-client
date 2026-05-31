package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import vorga.phazeclient.implement.hitcolor.OverlayRendered;

@Mixin(
    value = {ArmorFeatureRenderer.class},
    priority = 999
)
public class ArmorFeatureRendererMixin implements OverlayRendered {
    @Shadow
    @Final
    private EquipmentRenderer equipmentRenderer;

    @Override
    public void setOverlay(int coords) {
        if (this.equipmentRenderer instanceof OverlayRendered rendered) {
            rendered.setOverlay(coords);
        }
    }
}

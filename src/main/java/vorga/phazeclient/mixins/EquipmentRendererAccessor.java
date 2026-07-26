package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EquipmentRenderer.class)
public interface EquipmentRendererAccessor {
    @Accessor("equipmentModelLoader")
    EquipmentModelLoader phaze$getEquipmentModelLoader();
}

package vorga.phazeclient.mixins;

import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vorga.phazeclient.api.system.itemphysics.PhazeItemEntityRenderState;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStatePhysicsMixin implements PhazeItemEntityRenderState {

    @Unique
    private int phaze$entityId = -1;

    @Override
    public int phaze$entityId() {
        return phaze$entityId;
    }

    @Override
    public void phaze$entityId(int id) {
        phaze$entityId = id;
    }
}

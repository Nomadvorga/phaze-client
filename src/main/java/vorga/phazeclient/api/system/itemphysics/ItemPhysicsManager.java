package vorga.phazeclient.api.system.itemphysics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import org.joml.Vector3f;
import vorga.phazeclient.implement.features.modules.other.ItemPhysics;

import java.util.HashMap;
import java.util.Map;

public class ItemPhysicsManager {
    private static final ItemPhysicsManager INSTANCE = new ItemPhysicsManager();
    private static final float BASE_MULTIPLIER = 0.25f;

    private final Map<Integer, ItemPhysicsData> itemDataMap = new HashMap<>();

    public static ItemPhysicsManager getInstance() {
        return INSTANCE;
    }

    public void updateRotation(ItemEntity entity, boolean isBlock) {
        if (!ItemPhysics.getInstance().isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float rotateBy = mc.getRenderTickCounter().getTickDelta(true) * BASE_MULTIPLIER * ItemPhysics.getInstance().getRotationSpeed();
        if (mc.isPaused()) rotateBy = 0;

        int id = entity.getId();
        ItemPhysicsData data = itemDataMap.computeIfAbsent(id, k -> new ItemPhysicsData());

        if (entity.isOnGround()) {
            data.onGround = true;
            float xRot = data.xRot;
            float target;
            if (isBlock) {
                target = Math.round(xRot / 90.0f) * 90.0f;
            } else {
                target = Math.round(xRot / 180.0f) * 180.0f;
            }
            // Instant snap to target
            data.xRot = target;
        } else {
            data.onGround = false;
            // Spin in air
            data.xRot += rotateBy * 2.0f;
        }

        data.yRot = entity.getYaw();
    }

    public void removeItem(int id) {
        itemDataMap.remove(id);
    }

    public ItemPhysicsData getItemData(int id) {
        return itemDataMap.get(id);
    }

    public static class ItemPhysicsData {
        public float xRot = 0;
        public float yRot = 0;
        public boolean onGround = false;
    }
}

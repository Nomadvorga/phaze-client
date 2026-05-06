package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.base.util.ServerUtil;

public final class PotionAuto extends Module {
    private static final PotionAuto INSTANCE = new PotionAuto();
    private static final int REFRESH_THRESHOLD_TICKS = 200;
    private static final long DRINK_TIMEOUT_MS = 4000L;
    private static final long RETRY_DELAY_MS = 250L;
    private static final long USE_RETRY_DELAY_MS = 150L;

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting invisibility = new BooleanSetting("Invisibility", "Auto drink invisibility potions").setValue(true);
    public final BooleanSetting speed = new BooleanSetting("Speed", "Auto drink speed potions").setValue(true);

    private DrinkState drinkState = DrinkState.IDLE;
    private PotionType activePotionType;
    private int originalSelectedSlot = -1;
    private int activeHotbarSlot = -1;
    private int sourceScreenSlot = -1;
    private boolean startedUsingItem;
    private long stateStartMs;
    private long nextAttemptMs;
    private long nextUseAttemptMs;

    private enum DrinkState {
        IDLE, PREPARING, DRINKING
    }

    private enum PotionType {
        INVISIBILITY(StatusEffects.INVISIBILITY),
        SPEED(StatusEffects.SPEED);

        private final RegistryEntry<StatusEffect> effect;

        PotionType(RegistryEntry<StatusEffect> effect) {
            this.effect = effect;
        }
    }

    private record PotionSlot(int screenSlotId, int hotbarIndex, boolean hotbarSlot) {}

    private PotionAuto() {
        super("autopotion", "Auto Potion", ModuleCategory.UTILITIES);
        invisibility.setFullWidth(true);
        speed.setFullWidth(true);
        setup(generalSection, invisibility, speed);
    }

    public static PotionAuto getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically drinks potions when effects expire";
    }

    @Override
    public String getIcon() {
        return "autopotion.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        return ServerUtil.isAutoPotionSupported();
    }

    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        
        if (drinkState != DrinkState.IDLE) {
            if (!isEnabled() || !ServerUtil.isAutoPotionSupported() || mc.player == null || mc.world == null || mc.interactionManager == null) {
                cancelCurrentDrink();
                return;
            }
            
            if (mc.currentScreen != null) {
                cancelCurrentDrink();
                return;
            }
            
            tickActiveDrink();
            return;
        }
        
        if (!isEnabled()) {
            return;
        }
        
        if (!ServerUtil.isAutoPotionSupported()) {
            return;
        }
        
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextAttemptMs || mc.player.isUsingItem()) {
            return;
        }

        PotionType potionType = findNeededPotionType();
        if (potionType == null) {
            return;
        }

        PotionSlot potionSlot = findPotionSlot(potionType);
        if (potionSlot == null) {
            return;
        }

        startDrinking(potionType, potionSlot, now);
    }
    
    public boolean isDrinking() {
        return drinkState != DrinkState.IDLE;
    }
    
    public int getLockedHotbarSlot() {
        return activeHotbarSlot;
    }

    private void tickActiveDrink() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) {
            cancelCurrentDrink();
            return;
        }

        lockActiveSlot();

        long now = System.currentTimeMillis();
        if (now - stateStartMs > DRINK_TIMEOUT_MS) {
            cancelCurrentDrink();
            return;
        }

        if (drinkState == DrinkState.PREPARING) {
            ItemStack heldStack = mc.player.getMainHandStack();
            if (!matchesPotion(heldStack, activePotionType)) {
                cancelCurrentDrink();
                return;
            }

            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.options.useKey.setPressed(true);
            drinkState = DrinkState.DRINKING;
            nextUseAttemptMs = now + USE_RETRY_DELAY_MS;
            return;
        }

        mc.options.useKey.setPressed(true);
        if (mc.player.isUsingItem()) {
            startedUsingItem = true;
            return;
        }

        if (startedUsingItem) {
            finishCurrentDrink();
            return;
        }

        if (now >= nextUseAttemptMs) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            nextUseAttemptMs = now + USE_RETRY_DELAY_MS;
        }
    }

    private void startDrinking(PotionType potionType, PotionSlot potionSlot, long now) {
        MinecraftClient mc = MinecraftClient.getInstance();
        originalSelectedSlot = mc.player.getInventory().selectedSlot;
        activeHotbarSlot = potionSlot.hotbarSlot() ? potionSlot.hotbarIndex() : originalSelectedSlot;
        sourceScreenSlot = potionSlot.hotbarSlot() ? -1 : potionSlot.screenSlotId();
        activePotionType = potionType;
        startedUsingItem = false;
        stateStartMs = now;
        nextUseAttemptMs = 0L;

        if (!potionSlot.hotbarSlot()) {
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    potionSlot.screenSlotId(),
                    activeHotbarSlot,
                    SlotActionType.SWAP,
                    mc.player
            );
        }

        selectHotbarSlot(activeHotbarSlot);
        drinkState = DrinkState.PREPARING;
    }

    private void finishCurrentDrink() {
        cleanupDrinkState(true);
    }

    private void cancelCurrentDrink() {
        cleanupDrinkState(false);
    }

    private void cleanupDrinkState(boolean completed) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            mc.options.useKey.setPressed(false);
        }

        if (mc.player != null && mc.interactionManager != null) {
            if (sourceScreenSlot >= 0 && activeHotbarSlot >= 0 && sourceScreenSlot < mc.player.playerScreenHandler.slots.size()) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        sourceScreenSlot,
                        activeHotbarSlot,
                        SlotActionType.SWAP,
                        mc.player
                );
            }

            if (originalSelectedSlot >= 0) {
                selectHotbarSlot(originalSelectedSlot);
            }
        }

        drinkState = DrinkState.IDLE;
        activePotionType = null;
        originalSelectedSlot = -1;
        activeHotbarSlot = -1;
        sourceScreenSlot = -1;
        startedUsingItem = false;
        stateStartMs = 0L;
        nextUseAttemptMs = 0L;
        nextAttemptMs = System.currentTimeMillis() + (completed ? RETRY_DELAY_MS : RETRY_DELAY_MS * 2L);
    }

    private void lockActiveSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (activeHotbarSlot < 0 || mc.player == null) {
            return;
        }

        if (mc.player.getInventory().selectedSlot != activeHotbarSlot) {
            selectHotbarSlot(activeHotbarSlot);
        }
    }

    private void selectHotbarSlot(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || slot < 0 || slot > 8) {
            return;
        }

        mc.player.getInventory().selectedSlot = slot;
        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private PotionType findNeededPotionType() {
        if (invisibility.isValue() && needsRefresh(PotionType.INVISIBILITY)) {
            return PotionType.INVISIBILITY;
        }
        if (speed.isValue() && needsRefresh(PotionType.SPEED)) {
            return PotionType.SPEED;
        }
        return null;
    }

    private boolean needsRefresh(PotionType potionType) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }

        StatusEffectInstance effectInstance = mc.player.getStatusEffect(potionType.effect);
        if (effectInstance == null) {
            return true;
        }

        return !effectInstance.isInfinite() && effectInstance.getDuration() <= REFRESH_THRESHOLD_TICKS;
    }

    private PotionSlot findPotionSlot(PotionType potionType) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return null;
        }

        for (int slotId = 36; slotId <= 44; slotId++) {
            if (matchesPotion(mc.player.playerScreenHandler.getSlot(slotId).getStack(), potionType)) {
                return new PotionSlot(slotId, slotId - 36, true);
            }
        }

        for (int slotId = 9; slotId <= 35; slotId++) {
            if (matchesPotion(mc.player.playerScreenHandler.getSlot(slotId).getStack(), potionType)) {
                return new PotionSlot(slotId, -1, false);
            }
        }

        return null;
    }

    private boolean matchesPotion(ItemStack stack, PotionType potionType) {
        if (stack.isEmpty() || !stack.isOf(Items.POTION)) {
            return false;
        }

        var potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) {
            return false;
        }

        for (StatusEffectInstance effectInstance : potionContents.getEffects()) {
            if (effectInstance.getEffectType().equals(potionType.effect)) {
                return true;
            }
        }

        return false;
    }

    protected void onDisable() {
        cancelCurrentDrink();
    }
}

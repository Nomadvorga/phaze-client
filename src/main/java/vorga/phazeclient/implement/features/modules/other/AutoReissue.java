package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

public final class AutoReissue extends Module {
    private static final AutoReissue INSTANCE = new AutoReissue();

    private static final int AUCTION_TARGET_ROW = 6;
    private static final int FIRST_AUCTION_COLUMN = 2;
    private static final int SECOND_AUCTION_COLUMN = 8;
    private static final int FIRST_AUCTION_SLOT_FALLBACK = 47;
    private static final int SECOND_AUCTION_SLOT_FALLBACK = 53;
    private static final long SCREEN_CLOSE_SETTLE_MS = 150L;
    private static final long INTER_CLICK_DELAY_MS = 1_000L;
    private static final long MIN_STATE_TIMEOUT_MS = 15_000L;

    private enum State {
        IDLE,
        CLOSING_SCREENS,
        WAIT_FIRST_CLICK,
        WAIT_SECOND_CLICK,
        WAIT_THIRD_CLICK
    }

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting intervalSeconds = new ValueSetting("Interval", "Delay before the first /ah click and between reissue cycles in seconds")
            .range(1, 600)
            .setValue(60);

    private State state = State.IDLE;
    private long lastCycleMs = 0L;
    private long stateSinceMs = 0L;

    private AutoReissue() {
        super("autoreissue", "Auto Reissue", ModuleCategory.UTILITIES);

        intervalSeconds.setFullWidth(true);
        setup(generalSection, intervalSeconds);
    }

    public static AutoReissue getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Automatically reissues your auction items on FunTime";
    }

    @Override
    public String getIcon() {
        return "autoreissue.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    @Override
    public boolean isServerAllowed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return false;
        }
        if (mc.isInSingleplayer()) {
            return true;
        }
        return ServerUtil.isFunTimeServer();
    }

    @Override
    public void deactivate() {
        state = State.IDLE;
        lastCycleMs = 0L;
        stateSinceMs = 0L;
    }

    public void tick() {
        if (!isEnabled()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) {
            return;
        }

        if (!isServerAllowed()) {
            return;
        }

        long now = System.currentTimeMillis();
        long delayMs = Math.max(1L, intervalSeconds.getInt()) * 1_000L;
        long timeoutMs = Math.max(MIN_STATE_TIMEOUT_MS, delayMs + 10_000L);
        if (state != State.IDLE && now - stateSinceMs >= timeoutMs) {
            closeAllScreens(mc);
            state = State.IDLE;
            stateSinceMs = 0L;
            lastCycleMs = now;
            return;
        }

        switch (state) {
            case IDLE -> {
                if (now - lastCycleMs >= delayMs) {
                    closeAllScreens(mc);
                    state = State.CLOSING_SCREENS;
                    stateSinceMs = now;
                }
            }
            case CLOSING_SCREENS -> {
                closeAllScreens(mc);
                if (mc.currentScreen == null && now - stateSinceMs >= SCREEN_CLOSE_SETTLE_MS) {
                    mc.getNetworkHandler().sendChatCommand("ah");
                    state = State.WAIT_FIRST_CLICK;
                    stateSinceMs = now;
                }
            }
            case WAIT_FIRST_CLICK -> {
                if (now - stateSinceMs >= delayMs && clickCurrentHandledScreenSlot(
                        mc,
                        AUCTION_TARGET_ROW,
                        FIRST_AUCTION_COLUMN,
                        FIRST_AUCTION_SLOT_FALLBACK
                )) {
                    state = State.WAIT_SECOND_CLICK;
                    stateSinceMs = now;
                }
            }
            case WAIT_SECOND_CLICK -> {
                if (now - stateSinceMs >= INTER_CLICK_DELAY_MS && clickCurrentHandledScreenSlot(
                        mc,
                        AUCTION_TARGET_ROW,
                        SECOND_AUCTION_COLUMN,
                        SECOND_AUCTION_SLOT_FALLBACK
                )) {
                    state = State.WAIT_THIRD_CLICK;
                    stateSinceMs = now;
                }
            }
            case WAIT_THIRD_CLICK -> {
                if (now - stateSinceMs >= INTER_CLICK_DELAY_MS && clickCurrentHandledScreenSlot(
                        mc,
                        AUCTION_TARGET_ROW,
                        FIRST_AUCTION_COLUMN,
                        FIRST_AUCTION_SLOT_FALLBACK
                )) {
                    closeAllScreens(mc);
                    lastCycleMs = now;
                    state = State.IDLE;
                    stateSinceMs = 0L;
                }
            }
        }
    }

    private static void closeAllScreens(MinecraftClient mc) {
        if (mc.player == null) {
            return;
        }
        if (mc.currentScreen instanceof HandledScreen<?>) {
            mc.player.closeHandledScreen();
        }
        if (mc.currentScreen != null) {
            mc.setScreen(null);
        }
    }

    private static boolean clickCurrentHandledScreenSlot(
            MinecraftClient mc,
            int rowOneBased,
            int columnOneBased,
            int fallbackSlotId
    ) {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return false;
        }

        var handler = screen.getScreenHandler();
        int slotId = resolveAuctionSlotId(handler, rowOneBased, columnOneBased, fallbackSlotId);
        if (slotId < 0 || slotId >= handler.slots.size()) {
            return false;
        }

        mc.interactionManager.clickSlot(
                handler.syncId,
                slotId,
                0,
                SlotActionType.PICKUP,
                mc.player
        );
        return true;
    }

    private static int resolveAuctionSlotId(
            ScreenHandler handler,
            int rowOneBased,
            int columnOneBased,
            int fallbackSlotId
    ) {
        int containerSlotCount = resolveContainerSlotCount(handler);
        if (containerSlotCount >= rowOneBased * 9 && columnOneBased >= 1 && columnOneBased <= 9) {
            int resolvedSlotId = (rowOneBased - 1) * 9 + (columnOneBased - 1);
            if (resolvedSlotId >= 0 && resolvedSlotId < containerSlotCount) {
                return resolvedSlotId;
            }
        }
        return fallbackSlotId;
    }

    private static int resolveContainerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler genericContainer) {
            return genericContainer.getRows() * 9;
        }
        return Math.min(handler.slots.size(), 54);
    }
}

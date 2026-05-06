package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.ValueSetting;
import vorga.phazeclient.base.util.ServerUtil;

import java.util.Locale;

public final class AutoReissue extends Module {
    private static final AutoReissue INSTANCE = new AutoReissue();

    private static final int STORAGE_BUTTON_SLOT = 46;
    private static final int RESELL_BUTTON_SLOT = 52;

    private enum State {
        IDLE,
        OPENING_AUCTION,
        CLICK_STORAGE,
        CLICK_RESELL,
        CLOSING
    }

    public final SectionSetting generalSection = new SectionSetting("General");
    public final ValueSetting intervalSeconds = new ValueSetting("Interval", "Interval between reissue cycles in seconds")
            .range(30, 600)
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

        boolean auctionOpen = isHandledScreenWithTitle("auction", "аукцион");
        boolean storageOpen = isHandledScreenWithTitle("storage", "хранилище");
        long now = System.currentTimeMillis();

        if (!auctionOpen && !storageOpen && state != State.IDLE && state != State.OPENING_AUCTION) {
            state = State.IDLE;
        }

        switch (state) {
            case IDLE -> {
                long intervalMs = Math.max(30L, intervalSeconds.getInt()) * 1000L;
                if (now - lastCycleMs >= intervalMs && mc.currentScreen == null) {
                    mc.getNetworkHandler().sendChatCommand("ah");
                    state = State.OPENING_AUCTION;
                    stateSinceMs = now;
                    lastCycleMs = now;
                }
            }
            case OPENING_AUCTION -> {
                if (auctionOpen && now - stateSinceMs >= 500L) {
                    state = State.CLICK_STORAGE;
                    stateSinceMs = now;
                }
            }
            case CLICK_STORAGE -> {
                if (auctionOpen && now - stateSinceMs >= 300L) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            STORAGE_BUTTON_SLOT,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                    );
                    state = State.CLICK_RESELL;
                    stateSinceMs = now;
                }
            }
            case CLICK_RESELL -> {
                if (storageOpen && now - stateSinceMs >= 500L) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            RESELL_BUTTON_SLOT,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                    );
                    state = State.CLOSING;
                    stateSinceMs = now;
                }
            }
            case CLOSING -> {
                if (now - stateSinceMs >= 500L) {
                    mc.player.closeHandledScreen();
                    state = State.IDLE;
                }
            }
        }
    }

    private boolean isHandledScreenWithTitle(String... needles) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            return false;
        }

        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (title.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}

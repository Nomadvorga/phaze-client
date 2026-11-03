package vorga.phazeclient.implement.menu;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screen.Screen;
import vorga.phazeclient.implement.features.modules.client.Theme;

/** Verifies top-tab clicks are routed before an open detail view can consume them. */
public final class MenuTopTabClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.waitFor(client -> client.getOverlay() == null);
        SavedState saved = context.computeOnClient(client -> new SavedState(
                client.currentScreen, MenuScreen.INSTANCE.getModuleDetailComponent().getModule(),
                MenuScreen.INSTANCE.isCosmeticsViewOpen()));
        try {
            context.runOnClient(client -> MenuScreen.INSTANCE.openGui());
            context.waitTicks(4);
            clickTab(context, "SETTINGS");
            context.waitTicks(2);
            context.runOnClient(client -> check(
                    MenuScreen.INSTANCE.getModuleDetailComponent().getModule() == Theme.getInstance(),
                    "MODS -> SETTINGS is not swallowed by module detail"));

            clickTab(context, "COSMETICS");
            context.waitTicks(2);
            context.runOnClient(client -> check(MenuScreen.INSTANCE.isCosmeticsViewOpen(),
                    "SETTINGS -> COSMETICS remains routable"));

            clickTab(context, "MODS");
            context.waitTicks(2);
            context.runOnClient(client -> check(!MenuScreen.INSTANCE.isCosmeticsViewOpen()
                            && !MenuScreen.INSTANCE.getModuleDetailComponent().isOpen(),
                    "COSMETICS -> MODS remains routable"));
        } finally {
            context.runOnClient(client -> {
                MenuScreen.INSTANCE.closeCosmeticsView();
                MenuScreen.INSTANCE.closeModuleDetail();
                client.setScreen(saved.screen());
                if (saved.module() != null) MenuScreen.INSTANCE.openModuleDetail(saved.module());
            });
        }
        System.out.println("Top-tab routing client checks PASSED");
    }

    private static void clickTab(ClientGameTestContext context, String label) {
        double[] cursor = context.computeOnClient(client -> {
            MenuScreen menu = MenuScreen.INSTANCE;
            float tabHeight = 17.0F;
            float gap = 8.0F;
            float textSize = 6.9F;
            String[] labels = {"MODS", "SETTINGS", "COSMETICS", "CONFIGS", "CHAT"};
            float total = 0.0F;
            for (String tab : labels) total += MsdfWidth.width(tab, textSize) + 19.0F;
            total += gap * (labels.length - 1);
            if (menu.isConfigsViewOpen()) total += gap + tabHeight;
            float left = menu.x + menu.width / 2.0F - total / 2.0F;
            float tabX = left;
            for (String tab : labels) {
                float tabWidth = MsdfWidth.width(tab, textSize) + 19.0F;
                if (tab.equals(label)) {
                    float logicalX = tabX + tabWidth / 2.0F;
                    float logicalY = menu.y + (34.0F - tabHeight) / 2.0F + tabHeight / 2.0F;
                    double scale = menu.getOverlayRenderScale() * client.getWindow().getScaleFactor();
                    return new double[]{(menu.x + menu.width / 2.0F
                            + (logicalX - (menu.x + menu.width / 2.0F))
                            * MenuUiSettings.getInstance().getGuiScale()) * scale,
                            (menu.y + menu.height / 2.0F
                            + (logicalY - (menu.y + menu.height / 2.0F))
                            * MenuUiSettings.getInstance().getGuiScale()) * scale};
                }
                tabX += tabWidth + gap;
            }
            throw new AssertionError("missing tab " + label);
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().pressMouse(0);
        context.getInput().releaseMouse(0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SavedState(Screen screen, vorga.phazeclient.api.feature.module.Module module,
                              boolean cosmetics) {}

    /** Width lookup is isolated so the fixture uses the same MSDF atlas as the menu. */
    private static final class MsdfWidth {
        private static float width(String text, float size) {
            return vorga.phazeclient.api.system.font.msdf.MsdfFonts.bold().getWidth(text, size);
        }
    }
}

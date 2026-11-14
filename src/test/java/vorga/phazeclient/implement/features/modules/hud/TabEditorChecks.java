package vorga.phazeclient.implement.features.modules.hud;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vorga.phazeclient.api.feature.module.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Headless checks of the Tab Editor's settings layout, defaults and name styling. */
public final class TabEditorChecks {
    private static int checks;

    public static void main(String[] args) {
        TabEditor tab = TabEditor.getInstance();
        layout(tab);
        defaults(tab);
        visibility(tab);
        pingColors(tab);
        names();
        System.out.println("Tab Editor checks passed: " + checks);
    }

    private static void layout(TabEditor tab) {
        expect(tab.getVisibleName().equals("Tab Editor") && tab.getIdentifier().equals("tab_hud"),
                "renamed module keeps its identifier");
        expect(tab.settings().equals(List.of(
                tab.generalSection, tab.toggleTab, tab.disableHeader, tab.displayPlayerHead, tab.disableFooter,
                tab.colorSection, tab.headerColor, tab.footerColor, tab.backgroundColor, tab.usePingColorForRow, tab.rowColor,
                tab.nameSection, tab.highlightOwnName, tab.tabNameColor, tab.nameShadow, tab.moveSelfToTop,
                tab.pingSection, tab.hidePing, tab.hidePingOver500, tab.pingNumberShadow, tab.dynamicPingNumber,
                tab.lowPingColor, tab.mediumPingColor, tab.highPingColor, tab.extremePingColor)), "settings order");
        for (Setting paired : List.of(tab.toggleTab, tab.disableHeader, tab.displayPlayerHead, tab.disableFooter,
                tab.highlightOwnName, tab.tabNameColor, tab.nameShadow, tab.moveSelfToTop,
                tab.hidePing, tab.hidePingOver500, tab.pingNumberShadow, tab.dynamicPingNumber)) {
            expect(!paired.isFullWidth(), "paired setting shares its row: " + paired.getRawName());
        }
        for (Setting wide : List.of(tab.headerColor, tab.footerColor, tab.backgroundColor, tab.usePingColorForRow,
                tab.rowColor, tab.lowPingColor, tab.mediumPingColor, tab.highPingColor, tab.extremePingColor)) {
            expect(wide.isFullWidth(), "full-width setting: " + wide.getRawName());
        }
        expect(tab.settings().stream().noneMatch(s -> s.getRawName().contains("Round")), "no corner rounding");
    }

    private static void defaults(TabEditor tab) {
        expect(!tab.toggleTab.isValue() && tab.displayPlayerHead.isValue()
                && !tab.disableHeader.isValue() && !tab.disableFooter.isValue(), "general defaults");
        expect(tab.lowPingColor.getColor() == 0xFF55FF55 && tab.mediumPingColor.getColor() == 0xFFFFFF55
                && tab.highPingColor.getColor() == 0xFFFF5555 && tab.extremePingColor.getColor() == 0xFFAA0000,
                "ping colour defaults");
        expect((tab.tabNameColor.getColor() & 0xFFFFFF) == 0xFF5555, "own name defaults to red");
    }

    private static void visibility(TabEditor tab) {
        tab.disableHeader.setValue(true);
        expect(!tab.headerColor.isVisible() && tab.footerColor.isVisible(), "disabled header hides its colour");
        tab.disableHeader.setValue(false);
        tab.disableFooter.setValue(true);
        expect(!tab.footerColor.isVisible() && tab.headerColor.isVisible(), "disabled footer hides its colour");
        tab.disableFooter.setValue(false);
        tab.usePingColorForRow.setValue(true);
        expect(!tab.rowColor.isVisible(), "ping-coloured rows hide Row Color");
        tab.usePingColorForRow.setValue(false);
        expect(tab.rowColor.isVisible(), "Row Color returns");
        tab.dynamicPingNumber.setValue(false);
        expect(!tab.lowPingColor.isVisible(), "ping colours follow Dynamic Ping Number");
        tab.dynamicPingNumber.setValue(true);
        expect(tab.lowPingColor.isVisible(), "ping colours shown with Dynamic Ping Number");
    }

    private static void pingColors(TabEditor tab) {
        expect(tab.pingColor(64) == 0xFF55FF55 && tab.pingColor(65) == 0xFFFFFF55 && tab.pingColor(120) == 0xFFFFFF55
                && tab.pingColor(121) == 0xFFFF5555 && tab.pingColor(250) == 0xFFFF5555
                && tab.pingColor(251) == 0xFFAA0000, "ping thresholds");
        tab.hidePingOver500.setValue(true);
        expect(tab.hidesPing(501) && !tab.hidesPing(500) && tab.hidesPing(-1), "ping above 500 hides");
        tab.hidePingOver500.setValue(false);
    }

    private static void names() {
        Text name = Text.literal("[VIP] ").formatted(Formatting.GOLD).append(Text.literal("Nick").formatted(Formatting.GRAY));
        Text colored = TabEditor.recolor(name, "nick", 0xFF0000);
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        colored.visit((style, part) -> {
            parts.add(part);
            styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        expect(colored.getString().equals("[VIP] Nick"), "text unchanged");
        int prefix = parts.indexOf("[VIP] ");
        int nick = parts.indexOf("Nick");
        expect(prefix >= 0 && styles.get(prefix).getColor().getRgb() == 0xFFAA00, "prefix keeps the server colour");
        expect(nick >= 0 && styles.get(nick).getColor().getRgb() == 0xFF0000, "only the nickname is recoloured");
        expect(TabEditor.recolor(Text.literal("Hidden"), "nick", 0xFF0000).getStyle().getColor().getRgb() == 0xFF0000,
                "unknown layout colours the whole name");
        expect(TabEditor.containsWord("[A] Nick | 5", "nick") && !TabEditor.containsWord("[A] Nick_2", "nick")
                && !TabEditor.containsWord("Nickname", "nick"), "nickname matches whole words only");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}

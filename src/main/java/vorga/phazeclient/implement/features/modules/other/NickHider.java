package vorga.phazeclient.implement.features.modules.other;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;
import vorga.phazeclient.api.feature.module.setting.implement.TextSetting;

/**
 * Replaces the local player's username with a configurable string in any
 * chat message that funnels through {@link net.minecraft.client.gui.hud.ChatHud}.
 *
 * <p>Scope is intentionally limited to the local player only - the user
 * specifically asked NOT to hide other players' names. The rewrite walks
 * the styled text via {@link Text#visit} so per-fragment formatting is
 * preserved; only the literal username substring is swapped within each
 * styled run. This is the same approach the soup-better
 * {@code NameProtect} module uses, minus the friend-list expansion.
 */
public final class NickHider extends Module {
    private static final NickHider INSTANCE = new NickHider();

    public final SectionSetting generalSection = new SectionSetting("General");
    public final TextSetting replacement = new TextSetting(
            "Replacement",
            "Text shown in place of your username"
    ).setText("Phaze").setMax(16);

    private NickHider() {
        super("nick_hider", "Nick Hider", ModuleCategory.UTILITIES);
        replacement.setFullWidth(true);
        setup(generalSection, replacement);
    }

    public static NickHider getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Replaces your own username in chat with a custom string";
    }

    @Override
    public String getIcon() {
        return "nick_hider.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    /**
     * Local player's profile name as reported by the active session, or
     * {@code null} when called too early in the lifecycle.
     */
    private static String selfName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) {
            return null;
        }
        String name = mc.getSession().getUsername();
        return (name == null || name.isEmpty()) ? null : name;
    }

    /**
     * Rewrites {@code original} so every occurrence of the local player's
     * username inside any styled text fragment is replaced with the user's
     * configured {@link #replacement} string. Returns the original
     * reference unchanged when no work is needed; the {@link
     * vorga.phazeclient.mixins.ChatHudNickHiderMixin} relies on that to
     * skip its substitution path.
     *
     * <p>The traversal uses {@link Text#visit(net.minecraft.text.StringVisitable.StyledVisitor, Style)}
     * which yields each contiguous styled string fragment. Each fragment
     * is independently {@code String.replace}d and re-wrapped in a new
     * {@link Text#literal} carrying the same {@link Style}, so colors,
     * bold, hover events, etc. survive intact. Names that span multiple
     * fragments (rare - vanilla almost always emits a sender name as a
     * single literal arg of {@code chat.type.text}) are not handled by
     * the per-fragment replace; this matches the upstream NameProtect
     * behavior and is acceptable for the documented use case.
     */
    public Text rewrite(Text original) {
        if (!isEnabled() || original == null) {
            return original;
        }
        String name = selfName();
        if (name == null) {
            return original;
        }
        if (!original.getString().contains(name)) {
            return original;
        }

        String repl = replacement.getText();
        if (repl == null) {
            repl = "";
        }
        final String replFinal = repl;
        MutableText rebuilt = Text.empty();
        original.visit((style, fragment) -> {
            String out = fragment.contains(name) ? fragment.replace(name, replFinal) : fragment;
            rebuilt.append(Text.literal(out).setStyle(style));
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return rebuilt;
    }
}

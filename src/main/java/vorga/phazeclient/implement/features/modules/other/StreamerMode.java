package vorga.phazeclient.implement.features.modules.other;

import vorga.phazeclient.api.feature.module.Module;
import vorga.phazeclient.api.feature.module.ModuleCategory;
import vorga.phazeclient.api.feature.module.setting.implement.BooleanSetting;
import vorga.phazeclient.api.feature.module.setting.implement.SectionSetting;

/**
 * Privacy module for streaming / sharing screens. Hides two pieces of
 * information that streamers commonly leak by accident:
 *
 * <ul>
 *   <li><b>Coordinates</b> - replaces the {@code XYZ:} / {@code Block:} /
 *       {@code Chunk:} lines on the F3 debug overlay with a "hidden"
 *       placeholder, suppresses the {@code (x, y, z)} line in the WAILA
 *       HUD, and short-circuits the entire Coordinates HUD render. The
 *       three render paths are hooked at their existing call sites in
 *       {@code DebugHud#getLeftText} and {@code InGameHudMixin}, so the
 *       gating cost is one boolean read per frame per surface.</li>
 *   <li><b>Passwords</b> - while the user is typing into the chat input
 *       and the text starts with a recognised password command (see
 *       {@link #PASSWORD_COMMANDS}) the visible glyphs of every argument
 *       after the first space are replaced with {@code '*'} during
 *       render. The actual stored text is untouched so the server sees
 *       the real password on send.</li>
 * </ul>
 *
 * Both toggles default ON because the cost of the feature being on is a
 * single boolean check per affected surface and forgetting to enable
 * privacy before going live is exactly the failure mode this module
 * exists to prevent.
 */
public final class StreamerMode extends Module {
    private static final StreamerMode INSTANCE = new StreamerMode();

    /**
     * Recognised password-command prefixes (lowercase, with leading slash).
     * Match is case-insensitive against the typed text and requires the
     * char immediately after the prefix to be either end-of-string or a
     * space, so {@code /reg} doesn't accidentally trigger on
     * {@code /region} or similar unrelated commands.
     *
     * <p>Covers the AuthMe / nLogin / xAuth families found on most
     * survival servers, plus the short-form aliases ({@code /l},
     * {@code /r}) that the user explicitly listed. Includes the
     * password-change variants so a streamer who runs e.g.
     * {@code /changepass oldpw newpw} doesn't leak either side.
     */
    private static final String[] PASSWORD_COMMANDS = {
            "/l",
            "/login",
            "/log",
            "/r",
            "/reg",
            "/register",
            "/registration",
            "/changepass",
            "/changepassword",
            "/changepw",
            "/cp",
            "/auth",
            "/authme",
            "/pass",
            "/password",
            "/unreg",
            "/unregister",
            // Russian aliases - many RU servers (FunTime, HoneyMine,
            // ReallyWorld, etc.) accept Cyrillic commands. We can't
            // typo-match every server's auth plugin, but the common
            // shorthand spellings are well-covered.
            "/л",
            "/логин",
            "/р",
            "/рег",
            "/регистрация",
            "/смп",
            "/сменапароля",
    };

    public final SectionSetting generalSection = new SectionSetting("General");
    public final BooleanSetting hideCoordinates = new BooleanSetting(
            "Hide Coordinates",
            "Hide your XYZ position in the F3 debug screen, the WAILA HUD, and the Coordinates HUD"
    ).setValue(true);
    public final BooleanSetting hidePasswords = new BooleanSetting(
            "Hide Passwords",
            "Replace passwords with * (visually only) when typing /login, /reg, /register, /changepass, etc."
    ).setValue(true);

    private StreamerMode() {
        // Lives in OTHER because it sits next to NickHider / HideJoinLeave /
        // ChatHelper - the cluster of cosmetic privacy / chat-control modules
        // a streamer typically tweaks together. The previous UTILITIES slot
        // was a leftover from when the module only did the F3 coord mask.
        super("streamer_mode", "Streamer Mode", ModuleCategory.OTHER);
        hideCoordinates.setFullWidth(true);
        hidePasswords.setFullWidth(true);
        setup(generalSection, hideCoordinates, hidePasswords);
    }

    public static StreamerMode getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return "Hides sensitive info while streaming: coordinates and passwords";
    }

    @Override
    public String getIcon() {
        return "streamer_mode.png";
    }

    @Override
    public float getIconSize() {
        return 21.0F;
    }

    public boolean isHideCoordinatesEnabled() {
        return isEnabled() && hideCoordinates.isValue();
    }

    public boolean isHidePasswordsEnabled() {
        return isEnabled() && hidePasswords.isValue();
    }

    /**
     * Returns the masked rendition of {@code text} when it begins with a
     * recognised {@link #PASSWORD_COMMANDS password command}, or the
     * unchanged {@code text} otherwise. Length is preserved one-to-one so
     * cursor / selection indices computed against the original text stay
     * valid against the masked one - the chat input field reads
     * {@code selectionStart} / {@code selectionEnd} as raw int indices,
     * never mapping them through char widths, so a same-length swap keeps
     * the cursor logically on the right character.
     *
     * <p>Spaces inside the argument region are preserved literally so
     * commands that take multiple password-style args (notably
     * {@code /register pass pass} and {@code /changepass old new}) still
     * tokenize correctly under the mask.
     */
    public static String maskPasswordIfMatching(String text) {
        if (text == null || text.length() < 2 || text.charAt(0) != '/') {
            return text;
        }
        String lower = text.toLowerCase();
        for (String cmd : PASSWORD_COMMANDS) {
            if (!lower.startsWith(cmd)) {
                continue;
            }
            // Reject unrelated commands that share the prefix - eg /reg
            // must not match /region. Either the typed text ends exactly
            // at the prefix (no args yet) or the next char is a space.
            if (lower.length() != cmd.length() && lower.charAt(cmd.length()) != ' ') {
                continue;
            }
            int spaceIdx = text.indexOf(' ');
            if (spaceIdx < 0) {
                // No args typed yet - nothing to mask.
                return text;
            }
            StringBuilder sb = new StringBuilder(text.length());
            sb.append(text, 0, spaceIdx + 1);
            for (int i = spaceIdx + 1; i < text.length(); i++) {
                char c = text.charAt(i);
                sb.append(c == ' ' ? ' ' : '*');
            }
            return sb.toString();
        }
        return text;
    }
}

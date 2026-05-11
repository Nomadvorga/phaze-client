package vorga.phazeclient.base.util.other;

import lombok.experimental.UtilityClass;
import org.lwjgl.glfw.GLFW;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.QuickImports;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UtilityClass
public class StringUtil implements QuickImports {

    /**
     * Static lookup table for non-letter / non-digit / non-Fn GLFW key codes.
     * Built once at class init so {@link #getBindName(int)} stays O(1) on the
     * common dispatch path (this gets called per-frame for every bind label
     * on screen, so the previous implementation's allocations + GLFW JNI
     * call were measurable on big config screens).
     *
     * <p>Mapping is locale-agnostic and uses short uppercase labels that fit
     * the bind badges; that's why modifiers are abbreviated to LSHIFT/RALT
     * etc. rather than the GLFW raw names, and why the navigation cluster
     * uses two-letter shortenings (PG UP, PG DN).
     */
    private static final Map<Integer, String> SPECIAL_KEY_NAMES = buildSpecialKeyNames();

    private static Map<Integer, String> buildSpecialKeyNames() {
        Map<Integer, String> m = new HashMap<>();
        // Whitespace / line breaks
        m.put(GLFW.GLFW_KEY_SPACE, "SPACE");
        m.put(GLFW.GLFW_KEY_ENTER, "ENTER");
        m.put(GLFW.GLFW_KEY_TAB, "TAB");
        m.put(GLFW.GLFW_KEY_BACKSPACE, "BACKSPACE");
        m.put(GLFW.GLFW_KEY_ESCAPE, "ESC");
        // Editing
        m.put(GLFW.GLFW_KEY_INSERT, "INSERT");
        m.put(GLFW.GLFW_KEY_DELETE, "DELETE");
        m.put(GLFW.GLFW_KEY_HOME, "HOME");
        m.put(GLFW.GLFW_KEY_END, "END");
        m.put(GLFW.GLFW_KEY_PAGE_UP, "PG UP");
        m.put(GLFW.GLFW_KEY_PAGE_DOWN, "PG DN");
        // Arrows
        m.put(GLFW.GLFW_KEY_LEFT, "LEFT");
        m.put(GLFW.GLFW_KEY_RIGHT, "RIGHT");
        m.put(GLFW.GLFW_KEY_UP, "UP");
        m.put(GLFW.GLFW_KEY_DOWN, "DOWN");
        // Locks + sys
        m.put(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS");
        m.put(GLFW.GLFW_KEY_SCROLL_LOCK, "SCROLL");
        m.put(GLFW.GLFW_KEY_NUM_LOCK, "NUM LOCK");
        m.put(GLFW.GLFW_KEY_PRINT_SCREEN, "PRT SC");
        m.put(GLFW.GLFW_KEY_PAUSE, "PAUSE");
        m.put(GLFW.GLFW_KEY_MENU, "MENU");
        // Modifiers (the whole reason this rewrite exists - default
        // glfwGetKeyName returns null for these, so users saw "KEY_342").
        m.put(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT");
        m.put(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL");
        m.put(GLFW.GLFW_KEY_LEFT_ALT, "LALT");
        m.put(GLFW.GLFW_KEY_LEFT_SUPER, "LSUPER");
        m.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT");
        m.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL");
        m.put(GLFW.GLFW_KEY_RIGHT_ALT, "RALT");
        m.put(GLFW.GLFW_KEY_RIGHT_SUPER, "RSUPER");
        // Punctuation (hard-coded so we never fall through to
        // glfwGetKeyName on a non-Latin layout where it would return
        // Cyrillic or similar). Values match the US QWERTY printable.
        m.put(GLFW.GLFW_KEY_APOSTROPHE, "'");
        m.put(GLFW.GLFW_KEY_COMMA, ",");
        m.put(GLFW.GLFW_KEY_MINUS, "-");
        m.put(GLFW.GLFW_KEY_PERIOD, ".");
        m.put(GLFW.GLFW_KEY_SLASH, "/");
        m.put(GLFW.GLFW_KEY_SEMICOLON, ";");
        m.put(GLFW.GLFW_KEY_EQUAL, "=");
        m.put(GLFW.GLFW_KEY_LEFT_BRACKET, "[");
        m.put(GLFW.GLFW_KEY_BACKSLASH, "\\");
        m.put(GLFW.GLFW_KEY_RIGHT_BRACKET, "]");
        m.put(GLFW.GLFW_KEY_GRAVE_ACCENT, "`");
        // Numpad operators / non-digit numpad keys
        m.put(GLFW.GLFW_KEY_KP_DECIMAL, "NP .");
        m.put(GLFW.GLFW_KEY_KP_DIVIDE, "NP /");
        m.put(GLFW.GLFW_KEY_KP_MULTIPLY, "NP *");
        m.put(GLFW.GLFW_KEY_KP_SUBTRACT, "NP -");
        m.put(GLFW.GLFW_KEY_KP_ADD, "NP +");
        m.put(GLFW.GLFW_KEY_KP_ENTER, "NP ENT");
        m.put(GLFW.GLFW_KEY_KP_EQUAL, "NP =");
        return m;
    }

    public String randomString(int length) {
        return IntStream.range(0, length)
                .mapToObj(operand -> String.valueOf((char) new Random().nextInt('a', 'z' + 1)))
                .collect(Collectors.joining());
    }

    /**
     * Build a short, locale-agnostic English label for a stored bind key.
     *
     * <p>{@code key} stores either a GLFW key code (for keyboard binds) or a
     * GLFW mouse button index (for binds set via right/middle/aux click) -
     * those share the same int field on {@code BindSetting}. We disambiguate
     * by range: anything in {@code [0, 7]} is a mouse button (GLFW mouse
     * buttons are 0..7) and everything else is treated as a GLFW key code
     * (the first real key, {@code GLFW_KEY_SPACE}, is 32).
     *
     * <p>For letters and digits we compute the glyph from the key code
     * directly rather than asking GLFW: {@code GLFW_KEY_A..GLFW_KEY_Z} and
     * {@code GLFW_KEY_0..GLFW_KEY_9} match ASCII, so {@code (char) key}
     * yields {@code "B"} regardless of whether the user's OS layout would
     * paint Cyrillic / Hebrew / etc. on that physical key. This is the
     * fix for "I see "И" instead of "B" on a RU layout".
     *
     * <p>Modifier keys (LSHIFT, RCTRL, ALT, ...) and everything else
     * non-printable goes through {@link #SPECIAL_KEY_NAMES}. The previous
     * fallback returned "KEY_342" for ALT because {@code glfwGetKeyName}
     * returns {@code null} for non-printable codes.
     */
    public String getBindName(int key) {
        if (key < 0) return "N/A";

        // Mouse buttons - BindComponent stores `button` directly when the
        // user middle-clicks / aux-clicks the bind field, so the same int
        // field can carry mouse codes 0..7.
        if (key <= 7) {
            return switch (key) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "MB " + (key + 1);
            };
        }

        // A-Z / 0-9 via direct ASCII mapping - forces English glyphs even
        // when the OS keyboard layout would paint a different character.
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) key);
        }

        // F1..F25 - contiguous block, just offset off F1's code.
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        // Numpad digits
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "NP " + (key - GLFW.GLFW_KEY_KP_0);
        }

        // Named keys (modifiers, navigation, punctuation, numpad ops...)
        String mapped = SPECIAL_KEY_NAMES.get(key);
        if (mapped != null) return mapped;

        // Genuinely unknown / exotic - keep the diagnostic code so a user
        // can still report what they pressed if it isn't covered above.
        return "KEY " + key;
    }

    public String wrap(String input, int width, int size) {
        String[] words = input.split(" ");
        StringBuilder output = new StringBuilder();
        float lineWidth = 0;
        for (String word : words) {
            float wordWidth = Fonts.getSize(size).getStringWidth(word);
            if (lineWidth + wordWidth > width) {
                output.append("\n");
                lineWidth = 0;
            } else if (lineWidth > 0) {
                output.append(" ");
                lineWidth += Fonts.getSize(size).getStringWidth(" ");
            }
            output.append(word);
            lineWidth += wordWidth;
        }
        return output.toString();
    }

    public String getUserRole() {
        return "USER";
    }

    public void refreshRoles() {
    }

    public Set<String> getDevelopers() {
        return Collections.emptySet();
    }

    public Set<String> getYoutubers() {
        return Collections.emptySet();
    }

    public Set<String> getTesters() {
        return Collections.emptySet();
    }

    public Set<String> getPasters() {
        return Collections.emptySet();
    }

    public Set<String> getCrow() {
        return Collections.emptySet();
    }

    public String getDuration(int time) {
        int mins = time / 60;
        String sec = String.format("%02d", time % 60);
        return mins + ":" + sec;
    }

    public String toRoman(int number) {
        if (number <= 0) return "";
        if (number >= 10) return "X";

        String[] romanNumerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number <= romanNumerals.length ? romanNumerals[number - 1] : String.valueOf(number);
    }
}

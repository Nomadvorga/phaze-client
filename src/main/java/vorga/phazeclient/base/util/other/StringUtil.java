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

    private static final Map<Integer, String> SPECIAL_KEY_NAMES = buildSpecialKeyNames();

    private static Map<Integer, String> buildSpecialKeyNames() {
        Map<Integer, String> m = new HashMap<>();

        m.put(GLFW.GLFW_KEY_SPACE, "SPACE");
        m.put(GLFW.GLFW_KEY_ENTER, "ENTER");
        m.put(GLFW.GLFW_KEY_TAB, "TAB");
        m.put(GLFW.GLFW_KEY_BACKSPACE, "BACKSPACE");
        m.put(GLFW.GLFW_KEY_ESCAPE, "ESC");

        m.put(GLFW.GLFW_KEY_INSERT, "INSERT");
        m.put(GLFW.GLFW_KEY_DELETE, "DELETE");
        m.put(GLFW.GLFW_KEY_HOME, "HOME");
        m.put(GLFW.GLFW_KEY_END, "END");
        m.put(GLFW.GLFW_KEY_PAGE_UP, "PG UP");
        m.put(GLFW.GLFW_KEY_PAGE_DOWN, "PG DN");

        m.put(GLFW.GLFW_KEY_LEFT, "LEFT");
        m.put(GLFW.GLFW_KEY_RIGHT, "RIGHT");
        m.put(GLFW.GLFW_KEY_UP, "UP");
        m.put(GLFW.GLFW_KEY_DOWN, "DOWN");

        m.put(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS");
        m.put(GLFW.GLFW_KEY_SCROLL_LOCK, "SCROLL");
        m.put(GLFW.GLFW_KEY_NUM_LOCK, "NUM LOCK");
        m.put(GLFW.GLFW_KEY_PRINT_SCREEN, "PRT SC");
        m.put(GLFW.GLFW_KEY_PAUSE, "PAUSE");
        m.put(GLFW.GLFW_KEY_MENU, "MENU");

        m.put(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT");
        m.put(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL");
        m.put(GLFW.GLFW_KEY_LEFT_ALT, "LALT");
        m.put(GLFW.GLFW_KEY_LEFT_SUPER, "LSUPER");
        m.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT");
        m.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL");
        m.put(GLFW.GLFW_KEY_RIGHT_ALT, "RALT");
        m.put(GLFW.GLFW_KEY_RIGHT_SUPER, "RSUPER");

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

    public String getBindName(int key) {
        if (key < 0) return "N/A";

        if (key <= 7) {
            return switch (key) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                default -> "MB " + (key + 1);
            };
        }

        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) key);
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) key);
        }

        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }

        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return "NP " + (key - GLFW.GLFW_KEY_KP_0);
        }

        String mapped = SPECIAL_KEY_NAMES.get(key);
        if (mapped != null) return mapped;

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

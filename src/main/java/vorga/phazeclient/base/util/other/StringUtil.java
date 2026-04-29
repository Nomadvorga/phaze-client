package vorga.phazeclient.base.util.other;

import lombok.experimental.UtilityClass;
import vorga.phazeclient.api.system.font.Fonts;
import vorga.phazeclient.base.QuickImports;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UtilityClass
public class StringUtil implements QuickImports {
    public String randomString(int length) {
        return IntStream.range(0, length)
                .mapToObj(operand -> String.valueOf((char) new Random().nextInt('a', 'z' + 1)))
                .collect(Collectors.joining());
    }

    public String getBindName(int key) {
        if (key < 0) return "N/A";
        return "KEY_" + key;
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

package vorga.phazeclient.base.util;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conservative linear-time client mirror of the server chat filter. */
public final class MessengerTextFilter {
    public enum Result { ALLOW, REPEATED_FLOOD, ABUSE }

    private static final Set<String> DIRECT = Set.of(
            "хуйло", "хуесос", "еблан", "долбоеб", "долбаеб", "мразь",
            "тварь", "шлюха", "пидор", "пидорас", "блядь", "блядина");
    private static final Set<String> POLITICAL = Set.of(
            "россия", "россии", "россию", "российский", "российская", "российское",
            "украина", "украины", "украину", "америка", "америки", "сша",
            "израиль", "палестина", "путин", "трамп", "байден", "зеленский", "медведев");
    private static final Set<String> TARGETED = Set.of("параша", "помойка", "говно", "дно", "зло", "мусор");
    private static final Set<String> NEGATION = Set.of("не", "нет", "против", "осуждаю", "осуждает", "никогда");

    private MessengerTextFilter() {}

    public static Result check(String raw) {
        if (raw == null || raw.isBlank()) return Result.ABUSE;
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (text.codePointCount(0, text.length()) > 255 || containsControl(text)) return Result.ABUSE;
        String[] words = words(text);
        for (String word : words) if (DIRECT.contains(word)) return Result.ABUSE;
        for (int i = 0; i < words.length; i++) {
            if (POLITICAL.contains(words[i])) {
                for (int j = i + 1; j < Math.min(words.length, i + 4); j++)
                    if (TARGETED.contains(words[j]) && !hasNegation(words, i + 1, j)) return Result.ABUSE;
            }
            if (TARGETED.contains(words[i])) {
                for (int j = i + 1; j < Math.min(words.length, i + 4); j++)
                    if (POLITICAL.contains(words[j]) && !hasNegation(words, i + 1, j)) return Result.ABUSE;
            }
        }
        String compact = text.replaceAll("\\s+", "");
        return compact.codePointCount(0, compact.length()) >= 32 && repeated(compact)
                ? Result.REPEATED_FLOOD : Result.ALLOW;
    }

    private static String[] words(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('a', 'а').replace('e', 'е').replace('o', 'о')
                .replace('p', 'р').replace('c', 'с').replace('x', 'х').replace('y', 'у')
                .split("[^\\p{L}\\p{N}]+");
    }

    private static boolean hasNegation(String[] words, int from, int to) {
        for (int i = from; i < to; i++) if (NEGATION.contains(words[i])) return true;
        return false;
    }

    private static boolean containsControl(String text) {
        return text.codePoints().anyMatch(cp -> cp < 0x20 || cp == 0x7F || (cp >= 0x80 && cp <= 0x9F));
    }

    private static boolean repeated(String text) {
        int total = 0, run = 0, maxRun = 0, previous = -1, maxFrequency = 0;
        Set<Integer> chars = new HashSet<>();
        Map<Integer, Integer> frequencies = new HashMap<>();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            offset += Character.charCount(cp);
            total++;
            run = cp == previous ? run + 1 : 1;
            maxRun = Math.max(maxRun, run);
            previous = cp;
            frequencies.merge(cp, 1, Integer::sum);
            chars.add(cp);
        }
        for (int cp : chars) maxFrequency = Math.max(maxFrequency, frequencies.get(cp));
        return maxRun / (double) total >= 0.9 || maxFrequency / (double) total >= 0.9;
    }
}

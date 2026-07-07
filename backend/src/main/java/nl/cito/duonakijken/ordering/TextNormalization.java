package nl.cito.duonakijken.ordering;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TextNormalization {
    private TextNormalization() {}

    static String normalize(String text, boolean foldDiacritics) {
        if (text == null || text.isBlank()) {
            return "";
        }

        var normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (foldDiacritics) {
            normalized = foldDiacritics(normalized);
        }

        var builder = new StringBuilder(normalized.length());
        var previousWasSpace = false;
        for (int i = 0; i < normalized.length(); i++) {
            var ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                builder.append(' ');
                previousWasSpace = true;
            }
        }
        return collapseWhitespace(builder.toString());
    }

    static List<String> tokenize(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return List.of();
        }
        var parts = normalizedText.trim().split("\\s+");
        var tokens = new ArrayList<String>(parts.length);
        for (var part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    static Map<String, Integer> extractCharNGrams(String normalizedText, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        var compact = normalizedText.replace(" ", "");
        if (compact.isEmpty()) {
            return Map.of();
        }
        if (compact.length() <= n) {
            return Map.of(compact, 1);
        }
        var grams = new HashMap<String, Integer>();
        for (int index = 0; index <= compact.length() - n; index++) {
            var gram = compact.substring(index, index + n);
            grams.merge(gram, 1, Integer::sum);
        }
        return grams;
    }

    static boolean isJunk(String originalText, String normalizedText) {
        if (normalizedText.isEmpty()) {
            return true;
        }
        if (!containsLetterOrDigit(normalizedText)) {
            return true;
        }
        if (originalText == null || originalText.isBlank()) {
            return true;
        }
        return !containsLetterOrDigit(originalText);
    }

    private static boolean containsLetterOrDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String foldDiacritics(String text) {
        var decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        var builder = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            var ch = decomposed.charAt(i);
            var type = Character.getType(ch);
            if (type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK) {
                continue;
            }
            builder.append(ch);
        }
        return Normalizer.normalize(builder.toString(), Normalizer.Form.NFKC);
    }

    private static String collapseWhitespace(String text) {
        var builder = new StringBuilder(text.length());
        var previousWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousWasSpace) {
                    builder.append(' ');
                    previousWasSpace = true;
                }
            } else {
                builder.append(ch);
                previousWasSpace = false;
            }
        }
        return builder.toString().trim();
    }
}

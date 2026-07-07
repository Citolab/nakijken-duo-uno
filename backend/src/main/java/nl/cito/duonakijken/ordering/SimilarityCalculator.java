package nl.cito.duonakijken.ordering;

import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFeatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SimilarityCalculator {
    private SimilarityCalculator() {}

    static double charNGramCosine(Map<String, Integer> left, Map<String, Integer> right) {
        var leftDouble = new HashMap<String, Double>(left.size());
        for (var entry : left.entrySet()) {
            leftDouble.put(entry.getKey(), entry.getValue().doubleValue());
        }
        var rightDouble = new HashMap<String, Double>(right.size());
        for (var entry : right.entrySet()) {
            rightDouble.put(entry.getKey(), entry.getValue().doubleValue());
        }
        return cosine(leftDouble, rightDouble);
    }

    static double tokenJaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 1d;
        }

        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }

        var intersection = 0;
        for (var token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        var union = left.size() + right.size() - intersection;
        return union == 0 ? 0d : (double) intersection / union;
    }

    static double normalizedEditDistance(String left, String right) {
        if (left.isEmpty() && right.isEmpty()) {
            return 0d;
        }

        var maxLength = Math.max(left.length(), right.length());
        return maxLength == 0 ? 0d : (double) damerauLevenshteinDistance(left, right) / maxLength;
    }

    private static double diceBigramSimilarity(String compactLeft, String compactRight) {
        if (compactLeft.length() < 2 || compactRight.length() < 2) {
            return 0d;
        }

        var leftCounts = new HashMap<String, Integer>();
        var rightCounts = new HashMap<String, Integer>();
        addBigrams(compactLeft, leftCounts);
        addBigrams(compactRight, rightCounts);

        var intersection = 0;
        for (var entry : leftCounts.entrySet()) {
            var rightCount = rightCounts.get(entry.getKey());
            if (rightCount != null) {
                intersection += Math.min(entry.getValue(), rightCount);
            }
        }

        var totalBigrams = leftCounts.values().stream().mapToInt(Integer::intValue).sum()
            + rightCounts.values().stream().mapToInt(Integer::intValue).sum();
        return totalBigrams == 0 ? 0d : (2d * intersection) / totalBigrams;
    }

    private static void addBigrams(String text, Map<String, Integer> counts) {
        for (var i = 0; i < text.length() - 1; i++) {
            var key = text.substring(i, i + 2);
            counts.merge(key, 1, Integer::sum);
        }
    }

    static double localPhraseCosine(Map<String, Double> left, Map<String, Double> right) {
        return cosine(left, right);
    }

    static double surfaceSimilarity(ResponseFeatures left, ResponseFeatures right) {
        var charCosine = charNGramCosine(left.charNGrams, right.charNGrams);
        var editSimilarity = 1d - normalizedEditDistance(left.compactText, right.compactText);
        var tokenJaccard = tokenJaccard(left.tokenSet, right.tokenSet);

        var maxChars = Math.max(left.characterLength, right.characterLength);
        var minChars = Math.min(left.characterLength, right.characterLength);
        if (maxChars <= 44 && minChars >= 2) {
            var dice = diceBigramSimilarity(left.compactText, right.compactText);
            return clamp01(
                (0.40 * charCosine)
                    + (0.22 * editSimilarity)
                    + (0.14 * tokenJaccard)
                    + (0.24 * dice));
        }

        return clamp01((0.50 * charCosine) + (0.30 * editSimilarity) + (0.20 * tokenJaccard));
    }

    static double structureDistance(ResponseFeatures left, ResponseFeatures right) {
        var tokenDifference = normalizeDifference(left.tokenCount, right.tokenCount);
        var charDifference = normalizeDifference(left.characterLength, right.characterLength);
        return clamp01((0.70 * tokenDifference) + (0.30 * charDifference));
    }

    static double familySimilarity(ResponseFeatures left, ResponseFeatures right) {
        var surface = surfaceSimilarity(left, right);
        var phrase = localPhraseCosine(left.localPhraseVector, right.localPhraseVector);
        var structure = structureDistance(left, right);
        return clamp01((0.75 * surface) + (0.15 * phrase) + (0.10 * (1d - structure)));
    }

    static double effectiveFamilySimilarity(ResponseFeatures left, ResponseFeatures right) {
        var baseSimilarity = familySimilarity(left, right);
        var tokenContainment = tokenContainment(left.tokenSet, right.tokenSet);
        var charCosine = charNGramCosine(left.charNGrams, right.charNGrams);
        var editSimilarity = 1d - normalizedEditDistance(left.compactText, right.compactText);
        var compactContains = left.compactText.contains(right.compactText)
            || right.compactText.contains(left.compactText);
        var tokenDifference = Math.abs(left.tokenCount - right.tokenCount);
        var charLengthRatio = Math.max(left.characterLength, right.characterLength) == 0
            ? 1d
            : (double) Math.max(left.characterLength, right.characterLength)
                / Math.max(1, Math.min(left.characterLength, right.characterLength));
        var orderedWrapperVariant = isOrderedWrapperVariant(left.tokens, right.tokens);
        var isCompactWrapperVariant =
            tokenContainment >= 0.999
                && tokenDifference <= 2
                && charLengthRatio <= 2.0
                && (compactContains || orderedWrapperVariant);

        var nearDuplicateBoost = 0d;
        if (isCompactWrapperVariant) {
            nearDuplicateBoost = 0.90 + (0.10 * Math.min(1d, (charCosine + editSimilarity) / 2d));
        } else if (charCosine >= 0.78
            && editSimilarity >= 0.64
            && tokenDifference <= 3
            && charLengthRatio <= 2.0) {
            nearDuplicateBoost = 0.84 + (0.16 * Math.min(1d, (charCosine + editSimilarity) / 2d));
        }

        var compactMorphBoost = 0d;
        var maxCompact = Math.max(left.compactText.length(), right.compactText.length());
        var minCompact = Math.min(left.compactText.length(), right.compactText.length());
        if (minCompact >= 2
            && maxCompact <= 96
            && tokenDifference <= 5
            && charLengthRatio <= 2.8) {
            var dice = diceBigramSimilarity(left.compactText, right.compactText);
            if (dice >= 0.56) {
                compactMorphBoost = 0.76 + (0.24 * dice);
            }
        }

        return clamp01(Math.max(baseSimilarity, Math.max(nearDuplicateBoost, compactMorphBoost)));
    }

    static double familyOrderingDistance(ResponseFeatures left, ResponseFeatures right, boolean shortAnswerMode) {
        var surface = surfaceSimilarity(left, right);
        var phrase = localPhraseCosine(left.localPhraseVector, right.localPhraseVector);
        var structure = structureDistance(left, right);
        var rubric = Math.abs(left.rubricProximity - right.rubricProximity);

        var distance = shortAnswerMode
            ? (0.65 * (1d - surface)) + (0.10 * (1d - phrase)) + (0.10 * structure) + (0.15 * rubric)
            : (0.40 * (1d - surface)) + (0.35 * (1d - phrase)) + (0.10 * structure) + (0.15 * rubric);

        return clamp01(distance);
    }

    private static double normalizeDifference(int left, int right) {
        var max = Math.max(left, right);
        if (max == 0) {
            return 0d;
        }

        return (double) Math.abs(left - right) / max;
    }

    private static double cosine(Map<String, Double> left, Map<String, Double> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }

        double dot = 0;
        for (var entry : left.entrySet()) {
            var other = right.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
        }

        var leftNorm = Math.sqrt(left.values().stream().mapToDouble(Double::doubleValue).map(v -> v * v).sum());
        var rightNorm = Math.sqrt(right.values().stream().mapToDouble(Double::doubleValue).map(v -> v * v).sum());
        if (leftNorm == 0 || rightNorm == 0) {
            return 0d;
        }

        return clamp01(dot / (leftNorm * rightNorm));
    }

    private static int damerauLevenshteinDistance(String left, String right) {
        var matrix = new int[left.length() + 1][right.length() + 1];
        for (var i = 0; i <= left.length(); i++) {
            matrix[i][0] = i;
        }

        for (var j = 0; j <= right.length(); j++) {
            matrix[0][j] = j;
        }

        for (var i = 1; i <= left.length(); i++) {
            for (var j = 1; j <= right.length(); j++) {
                var cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                matrix[i][j] = Math.min(
                    Math.min(matrix[i - 1][j] + 1, matrix[i][j - 1] + 1),
                    matrix[i - 1][j - 1] + cost);

                if (i > 1
                    && j > 1
                    && left.charAt(i - 1) == right.charAt(j - 2)
                    && left.charAt(i - 2) == right.charAt(j - 1)) {
                    matrix[i][j] = Math.min(matrix[i][j], matrix[i - 2][j - 2] + cost);
                }
            }
        }

        return matrix[left.length()][right.length()];
    }

    private static double clamp01(double value) {
        return Math.clamp(value, 0d, 1d);
    }

    private static double tokenContainment(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }

        var intersection = 0;
        for (var token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        return (double) intersection / Math.min(left.size(), right.size());
    }

    private static boolean isOrderedWrapperVariant(List<String> left, List<String> right) {
        var shorter = left.size() <= right.size() ? left : right;
        var longer = left.size() <= right.size() ? right : left;

        if (shorter.isEmpty() || longer.isEmpty()) {
            return false;
        }

        if (longer.size() - shorter.size() > 2) {
            return false;
        }

        var shorterIndex = 0;
        var extraTokenCount = 0;
        var extraCharacterCount = 0;

        for (var longerIndex = 0; longerIndex < longer.size(); longerIndex++) {
            if (shorterIndex < shorter.size() && longer.get(longerIndex).equals(shorter.get(shorterIndex))) {
                shorterIndex++;
                continue;
            }

            extraTokenCount++;
            extraCharacterCount += longer.get(longerIndex).length();
            if (extraTokenCount > 2 || extraCharacterCount > 4) {
                return false;
            }
        }

        return shorterIndex == shorter.size();
    }
}

package nl.cito.duonakijken.ordering;

import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;

final class PathSeriation {
    private PathSeriation() {}

    static List<ResponseFamily> orderFamilies(List<ResponseFamily> families, boolean shortAnswerMode) {
        if (families.size() <= 1) {
            return families;
        }

        var maxRubric = families.stream().mapToDouble(f -> f.representative.rubricProximity).max().orElse(0d);
        var maxCount = families.stream().mapToInt(ResponseFamily::totalResponseCount).max().orElse(0);

        var start = families.stream()
            .max(Comparator
                .comparingDouble((ResponseFamily family) -> startScore(family, maxRubric, maxCount))
                .thenComparing(ResponseFamily::sortKey))
            .orElseThrow();

        var ordered = new ArrayList<ResponseFamily>();
        ordered.add(start);
        var remaining = new IdentityHashMap<ResponseFamily, Boolean>();
        for (var family : families) {
            if (family != start) {
                remaining.put(family, Boolean.TRUE);
            }
        }

        while (!remaining.isEmpty()) {
            var current = ordered.get(ordered.size() - 1);
            var next = remaining.keySet().stream()
                .min(Comparator
                    .comparingDouble((ResponseFamily family) ->
                        SimilarityCalculator.familyOrderingDistance(
                            current.representative, family.representative, shortAnswerMode))
                    .thenComparing((ResponseFamily family) -> family.representative.rubricProximity, Comparator.reverseOrder())
                    .thenComparing(ResponseFamily::totalResponseCount, Comparator.reverseOrder())
                    .thenComparing(ResponseFamily::sortKey))
                .orElseThrow();

            ordered.add(next);
            remaining.remove(next);
        }

        twoOpt(ordered, shortAnswerMode);

        if (ordered.size() > 1
            && ordered.get(ordered.size() - 1).representative.rubricProximity
                > ordered.get(0).representative.rubricProximity) {
            Collections.reverse(ordered);
        }

        return ordered;
    }

    private static double startScore(ResponseFamily family, double maxRubric, int maxCount) {
        var normalizedRubric = maxRubric <= 0 ? 0d : family.representative.rubricProximity / maxRubric;
        var normalizedCount = maxCount <= 0 ? 0d : (double) family.totalResponseCount() / maxCount;
        return (0.60 * normalizedRubric) + (0.40 * normalizedCount);
    }

    private static void twoOpt(List<ResponseFamily> families, boolean shortAnswerMode) {
        if (families.size() < 4) {
            return;
        }

        var improved = true;
        while (improved) {
            improved = false;
            for (var i = 1; i < families.size() - 2; i++) {
                for (var j = i + 1; j < families.size() - 1; j++) {
                    var currentDistance = segmentDistance(families.get(i - 1), families.get(i), shortAnswerMode)
                        + segmentDistance(families.get(j), families.get(j + 1), shortAnswerMode);
                    var swappedDistance = segmentDistance(families.get(i - 1), families.get(j), shortAnswerMode)
                        + segmentDistance(families.get(i), families.get(j + 1), shortAnswerMode);

                    if (swappedDistance + 1e-9 < currentDistance) {
                        Collections.reverse(families.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
    }

    private static double segmentDistance(ResponseFamily left, ResponseFamily right, boolean shortAnswerMode) {
        return SimilarityCalculator.familyOrderingDistance(left.representative, right.representative, shortAnswerMode);
    }
}

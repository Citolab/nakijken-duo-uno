package nl.cito.duonakijken.ordering;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static nl.cito.duonakijken.ordering.OrderingModels.OrderedUniqueResponse;
import static nl.cito.duonakijken.ordering.OrderingModels.PhraseFeatureSet;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFamily;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFamilyInfo;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFeatures;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingDiagnostics;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingOptions;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingResult;
import static nl.cito.duonakijken.ordering.OrderingModels.UniqueResponseInput;

@Service
public class ResponseScanOrderingService {
    public ResponseScanOrderingResult orderResponses(
        List<UniqueResponseInput> responses,
        String answerModel,
        ResponseScanOrderingOptions options
    ) {
        var effectiveOptions = (options == null ? ResponseScanOrderingOptions.defaults() : options).normalized();
        var normalizedAnswerModel = TextNormalization.normalize(answerModel, effectiveOptions.foldDiacritics());
        var answerModelTokens = TextNormalization.tokenize(normalizedAnswerModel);
        var answerModelTokenSet = new HashSet<>(answerModelTokens);
        var answerModelNGrams = TextNormalization.extractCharNGrams(normalizedAnswerModel, effectiveOptions.charNGramSize());

        var features = responses.stream()
            .map(response -> buildFeatures(response, normalizedAnswerModel, answerModelTokenSet, answerModelNGrams, effectiveOptions))
            .toList();

        var nonJunk = features.stream().filter(feature -> !feature.junk).toList();
        var junk = features.stream().filter(feature -> feature.junk).toList();

        var shortAnswerMode = detectShortAnswerMode(nonJunk, effectiveOptions);
        var phraseFeatures = PhraseMining.mine(nonJunk, normalizedAnswerModel, effectiveOptions);
        for (var feature : nonJunk) {
            feature.localPhraseVector = PhraseMining.buildVector(feature.tokens, phraseFeatures);
        }

        var groupedFamilies = groupFamilies(nonJunk, shortAnswerMode, effectiveOptions);
        var orderedFamilies = PathSeriation.orderFamilies(groupedFamilies, shortAnswerMode);
        var orderedJunkFamilies = buildJunkFamilies(junk);

        var allFamilies = new ArrayList<ResponseFamily>(orderedFamilies.size() + orderedJunkFamilies.size());
        allFamilies.addAll(orderedFamilies);
        allFamilies.addAll(orderedJunkFamilies);

        var orderedResponses = new ArrayList<OrderedUniqueResponse>(responses.size());
        var familyInfos = new ArrayList<ResponseFamilyInfo>(allFamilies.size());
        var sortIndex = 0;

        for (var familyOrder = 0; familyOrder < allFamilies.size(); familyOrder++) {
            var family = allFamilies.get(familyOrder);
            var orderedMembers = sortMembers(family.members);
            var representative = family.representative;

            familyInfos.add(new ResponseFamilyInfo(
                family.familyId,
                representative.response.uniqueId(),
                representative.response.responseText(),
                orderedMembers.stream().map(member -> member.response.uniqueId()).toList(),
                family.totalResponseCount(),
                family.junk,
                representative.rubricProximity
            ));

            for (var member : orderedMembers) {
                orderedResponses.add(new OrderedUniqueResponse(
                    member.response,
                    sortIndex++,
                    family.familyId,
                    familyOrder,
                    family.junk,
                    member.normalizedText,
                    member.rubricProximity,
                    member == representative
                ));
            }
        }

        return new ResponseScanOrderingResult(
            orderedResponses,
            familyInfos,
            new ResponseScanOrderingDiagnostics(
                shortAnswerMode,
                nonJunk.size(),
                junk.size(),
                allFamilies.size(),
                effectiveOptions.includeDiagnostics() ? phraseFeatures.selectedPhrases() : List.of()
            )
        );
    }

    private static ResponseFeatures buildFeatures(
        UniqueResponseInput response,
        String normalizedAnswerModel,
        Set<String> answerModelTokenSet,
        java.util.Map<String, Integer> answerModelNGrams,
        ResponseScanOrderingOptions options
    ) {
        var normalizedText = TextNormalization.normalize(response.responseText(), options.foldDiacritics());
        var tokens = TextNormalization.tokenize(normalizedText);
        var charNGrams = TextNormalization.extractCharNGrams(normalizedText, options.charNGramSize());
        var tokenSet = new HashSet<>(tokens);
        var junk = TextNormalization.isJunk(response.responseText(), normalizedText);

        var rubricProximity = 0d;
        if (!normalizedAnswerModel.isEmpty() && !junk) {
            var charCosine = SimilarityCalculator.charNGramCosine(charNGrams, answerModelNGrams);
            var tokenJaccard = SimilarityCalculator.tokenJaccard(tokenSet, answerModelTokenSet);
            rubricProximity = Math.clamp((0.70 * charCosine) + (0.30 * tokenJaccard), 0d, 1d);
        }

        var feature = new ResponseFeatures();
        feature.response = response;
        feature.normalizedText = normalizedText;
        feature.compactText = normalizedText.replace(" ", "");
        feature.tokens = tokens;
        feature.tokenSet = tokenSet;
        feature.charNGrams = charNGrams;
        feature.localPhraseVector = java.util.Map.of();
        feature.tokenCount = tokens.size();
        feature.characterLength = normalizedText.length();
        feature.junk = junk;
        feature.rubricProximity = rubricProximity;
        return feature;
    }

    private static boolean detectShortAnswerMode(List<ResponseFeatures> nonJunk, ResponseScanOrderingOptions options) {
        if (nonJunk.isEmpty()) {
            return true;
        }
        var medianTokenCount = median(nonJunk.stream().mapToInt(feature -> feature.tokenCount).toArray());
        var medianCharacterLength = median(nonJunk.stream().mapToInt(feature -> feature.characterLength).toArray());
        return medianTokenCount <= options.shortAnswerMedianTokenThreshold() || medianCharacterLength <= 30;
    }

    private static List<ResponseFamily> groupFamilies(
        List<ResponseFeatures> nonJunk,
        boolean shortAnswerMode,
        ResponseScanOrderingOptions options
    ) {
        var threshold = shortAnswerMode
            ? options.shortAnswerFamilyThreshold()
            : options.longAnswerFamilyThreshold();

        var orderedCandidates = nonJunk.stream()
            .sorted(Comparator
                .comparing((ResponseFeatures feature) -> feature.response.responseCount()).reversed()
                .thenComparing((ResponseFeatures feature) -> feature.rubricProximity, Comparator.reverseOrder())
                .thenComparing(feature -> feature.normalizedText)
                .thenComparing(feature -> feature.response.uniqueId()))
            .toList();

        var families = new ArrayList<ResponseFamily>();
        for (var candidate : orderedCandidates) {
            ResponseFamily bestFamily = null;
            var bestSimilarity = Double.NEGATIVE_INFINITY;

            for (var family : families) {
                var similarity = Math.max(
                    SimilarityCalculator.effectiveFamilySimilarity(candidate, family.anchor),
                    SimilarityCalculator.effectiveFamilySimilarity(candidate, family.representative)
                );
                if (similarity > bestSimilarity + 1e-9
                    || (Math.abs(similarity - bestSimilarity) <= 1e-9 && compareFamilyTieBreak(candidate, family, bestFamily) < 0)) {
                    bestSimilarity = similarity;
                    bestFamily = family;
                }
            }

            if (bestFamily != null && bestSimilarity >= threshold) {
                bestFamily.members.add(candidate);
                bestFamily.representative = selectMedoid(bestFamily.members, shortAnswerMode);
                continue;
            }

            var family = new ResponseFamily();
            family.familyId = "family-" + String.format("%04d", families.size());
            family.members = new ArrayList<>(List.of(candidate));
            family.junk = false;
            family.anchor = candidate;
            family.representative = candidate;
            families.add(family);
        }

        for (var family : families) {
            family.representative = selectMedoid(family.members, shortAnswerMode);
        }
        return families;
    }

    private static int compareFamilyTieBreak(ResponseFeatures candidate, ResponseFamily family, ResponseFamily currentBest) {
        if (currentBest == null) {
            return -1;
        }
        return compareFeatures(family.representative, currentBest.representative);
    }

    private record MedoidCandidate(ResponseFeatures member, double weightedDistance) {}

    private static ResponseFeatures selectMedoid(List<ResponseFeatures> members, boolean shortAnswerMode) {
        if (members.size() == 1) {
            return members.getFirst();
        }
        return members.stream()
            .map(member -> new MedoidCandidate(
                member,
                members.stream()
                    .mapToDouble(other ->
                        SimilarityCalculator.familyOrderingDistance(member, other, shortAnswerMode)
                            * Math.max(1, other.response.responseCount()))
                    .sum()
            ))
            .min(Comparator
                .comparingDouble(MedoidCandidate::weightedDistance)
                .thenComparing((MedoidCandidate item) -> item.member().response.responseCount(), Comparator.reverseOrder())
                .thenComparing((MedoidCandidate item) -> item.member().rubricProximity, Comparator.reverseOrder())
                .thenComparing(item -> item.member().normalizedText)
                .thenComparing(item -> item.member().response.uniqueId()))
            .map(MedoidCandidate::member)
            .orElse(members.getFirst());
    }

    private static List<ResponseFamily> buildJunkFamilies(List<ResponseFeatures> junk) {
        var orderedJunk = junk.stream()
            .sorted(Comparator
                .comparing((ResponseFeatures feature) -> feature.response.responseCount()).reversed()
                .thenComparing(feature -> feature.normalizedText)
                .thenComparing(feature -> feature.response.uniqueId()))
            .toList();

        var families = new ArrayList<ResponseFamily>(orderedJunk.size());
        for (var index = 0; index < orderedJunk.size(); index++) {
            var member = orderedJunk.get(index);
            var family = new ResponseFamily();
            family.familyId = "junk-" + String.format("%04d", index);
            family.members = new ArrayList<>(List.of(member));
            family.junk = true;
            family.anchor = member;
            family.representative = member;
            families.add(family);
        }
        return families;
    }

    private static List<ResponseFeatures> sortMembers(List<ResponseFeatures> members) {
        return members.stream()
            .sorted(Comparator
                .comparing((ResponseFeatures member) -> member.response.responseCount()).reversed()
                .thenComparing((ResponseFeatures member) -> member.rubricProximity, Comparator.reverseOrder())
                .thenComparing(member -> member.normalizedText)
                .thenComparing(member -> member.response.uniqueId()))
            .toList();
    }

    private static int compareFeatures(ResponseFeatures left, ResponseFeatures right) {
        var countCompare = Integer.compare(right.response.responseCount(), left.response.responseCount());
        if (countCompare != 0) {
            return countCompare;
        }
        var rubricCompare = Double.compare(right.rubricProximity, left.rubricProximity);
        if (rubricCompare != 0) {
            return rubricCompare;
        }
        var normalizedCompare = left.normalizedText.compareTo(right.normalizedText);
        if (normalizedCompare != 0) {
            return normalizedCompare;
        }
        return left.response.uniqueId().compareTo(right.response.uniqueId());
    }

    private static double median(int[] values) {
        if (values.length == 0) {
            return 0d;
        }
        java.util.Arrays.sort(values);
        var middle = values.length / 2;
        return values.length % 2 == 1
            ? values[middle]
            : (values[middle - 1] + values[middle]) / 2d;
    }
}

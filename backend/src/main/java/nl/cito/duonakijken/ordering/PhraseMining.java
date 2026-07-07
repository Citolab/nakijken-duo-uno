package nl.cito.duonakijken.ordering;

import static nl.cito.duonakijken.ordering.OrderingModels.PhraseFeatureSet;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseFeatures;
import static nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PhraseMining {
    private PhraseMining() {}

    static PhraseFeatureSet mine(
        List<ResponseFeatures> responses,
        String normalizedAnswerModel,
        ResponseScanOrderingOptions options) {
        if (responses.isEmpty() || options.maxLocalPhraseFeatures() <= 0) {
            return new PhraseFeatureSet(List.of(), Map.of());
        }

        var nonJunkCount = responses.size();
        var maxDocumentFrequency = Math.max(1, (int) Math.floor(nonJunkCount * 0.8));
        var weightedSupport = new HashMap<String, Double>();
        var documentFrequency = new HashMap<String, Integer>();

        for (var response : responses) {
            var phrases = extractPhrases(response.tokens);
            for (var phrase : phrases) {
                weightedSupport.merge(phrase, (double) Math.max(1, response.response.responseCount()), Double::sum);
                documentFrequency.merge(phrase, 1, Integer::sum);
            }
        }

        var answerModelPhrases = new HashSet<String>();
        for (var phrase : extractPhrases(TextNormalization.tokenize(normalizedAnswerModel))) {
            if (documentFrequency.containsKey(phrase)) {
                answerModelPhrases.add(phrase);
            }
        }

        var ranked = new ArrayList<RankedPhrase>();
        for (var entry : weightedSupport.entrySet()) {
            var phrase = entry.getKey();
            var df = documentFrequency.get(phrase);
            var idf = Math.log((1d + nonJunkCount) / (1d + df));
            var answerModelBoost = answerModelPhrases.contains(phrase) ? 0.25 : 0d;
            var score = (entry.getValue() * idf) + answerModelBoost;
            if (df <= maxDocumentFrequency) {
                ranked.add(new RankedPhrase(phrase, score, df));
            }
        }

        ranked.sort(Comparator
            .comparingDouble(RankedPhrase::score).reversed()
            .thenComparing(RankedPhrase::phrase));

        var limit = Math.min(options.maxLocalPhraseFeatures(), ranked.size());
        var selectedPhrases = new ArrayList<String>(limit);
        var phraseWeights = new HashMap<String, Double>();
        for (var i = 0; i < limit; i++) {
            var item = ranked.get(i);
            selectedPhrases.add(item.phrase());
            phraseWeights.put(item.phrase(), item.score());
        }

        return new PhraseFeatureSet(List.copyOf(selectedPhrases), Map.copyOf(phraseWeights));
    }

    static Map<String, Double> buildVector(List<String> tokens, PhraseFeatureSet featureSet) {
        if (tokens.isEmpty() || featureSet.selectedPhrases().isEmpty()) {
            return Map.of();
        }

        var selected = new HashSet<>(featureSet.selectedPhrases());
        var vector = new HashMap<String, Double>();
        for (var phrase : extractPhrases(tokens)) {
            if (!selected.contains(phrase)) {
                continue;
            }

            vector.merge(phrase, featureSet.phraseWeights().get(phrase), Double::sum);
        }

        return vector;
    }

    private static Set<String> extractPhrases(List<String> tokens) {
        var phrases = new HashSet<String>();
        for (var size = 1; size <= 3; size++) {
            if (tokens.size() < size) {
                break;
            }

            for (var index = 0; index <= tokens.size() - size; index++) {
                var slice = tokens.subList(index, index + size);
                if (slice.isEmpty() || slice.stream().allMatch(token -> token.length() <= 1)) {
                    continue;
                }

                phrases.add(String.join(" ", slice));
            }
        }

        return phrases;
    }

    private record RankedPhrase(String phrase, double score, int documentFrequency) {}
}

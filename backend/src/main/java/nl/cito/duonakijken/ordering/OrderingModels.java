package nl.cito.duonakijken.ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class OrderingModels {
    private OrderingModels() {}

    public record ResponseScanOrderingOptions(
        int charNGramSize,
        int maxLocalPhraseFeatures,
        double shortAnswerFamilyThreshold,
        double longAnswerFamilyThreshold,
        int shortAnswerMedianTokenThreshold,
        boolean foldDiacritics,
        boolean includeDiagnostics
    ) {
        public static ResponseScanOrderingOptions defaults() {
            return new ResponseScanOrderingOptions(3, 32, 0.72, 0.68, 6, true, false);
        }

        public ResponseScanOrderingOptions normalized() {
            return new ResponseScanOrderingOptions(
                Math.max(1, charNGramSize),
                Math.max(0, maxLocalPhraseFeatures),
                Math.clamp(shortAnswerFamilyThreshold, 0, 1),
                Math.clamp(longAnswerFamilyThreshold, 0, 1),
                Math.max(1, shortAnswerMedianTokenThreshold),
                foldDiacritics,
                includeDiagnostics
            );
        }
    }

    public record UniqueResponseInput(String uniqueId, String responseText, int responseCount) {}

    public record OrderedUniqueResponse(
        UniqueResponseInput response,
        int sortIndex,
        String familyId,
        int familyOrder,
        boolean junk,
        String normalizedText,
        double rubricProximity,
        boolean familyRepresentative
    ) {}

    public record ResponseFamilyInfo(
        String familyId,
        String representativeUniqueId,
        String representativeText,
        List<String> memberUniqueIds,
        int totalResponseCount,
        boolean junk,
        double rubricProximity
    ) {}

    public record ResponseScanOrderingDiagnostics(
        boolean shortAnswerMode,
        int nonJunkCount,
        int junkCount,
        int familyCount,
        List<String> selectedLocalPhrases
    ) {}

    public record ResponseScanOrderingResult(
        List<OrderedUniqueResponse> orderedResponses,
        List<ResponseFamilyInfo> families,
        ResponseScanOrderingDiagnostics diagnostics
    ) {}

    record PhraseFeatureSet(List<String> selectedPhrases, Map<String, Double> phraseWeights) {}

    static final class ResponseFeatures {
        UniqueResponseInput response;
        String normalizedText = "";
        String compactText = "";
        List<String> tokens = List.of();
        java.util.Set<String> tokenSet = java.util.Set.of();
        Map<String, Integer> charNGrams = Map.of();
        Map<String, Double> localPhraseVector = Map.of();
        int tokenCount;
        int characterLength;
        boolean junk;
        double rubricProximity;
    }

    static final class ResponseFamily {
        String familyId;
        List<ResponseFeatures> members = new ArrayList<>();
        boolean junk;
        ResponseFeatures anchor;
        ResponseFeatures representative;

        int totalResponseCount() {
            return members.stream().mapToInt(m -> Math.max(1, m.response.responseCount())).sum();
        }

        String sortKey() {
            return representative.normalizedText + "\u001f" + representative.response.uniqueId();
        }
    }
}

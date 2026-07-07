package nl.cito.duonakijken.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class DemoModels {
    private DemoModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DemoFixture(
        int fixtureVersion,
        String description,
        List<String> activeTaxonomyIds,
        Assessment assessment,
        Delivery delivery,
        List<Session> sessions,
        List<ItemStatistics> itemStatistics,
        List<JsonNode> studentResults,
        List<ScoringDefinition> scoringDefinitions
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Assessment(
        String id,
        String name,
        String assessmentHref,
        String packageBaseHref,
        List<ItemInfo> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemInfo(
        String identifier,
        String title,
        String href,
        String interactionType,
        Double maxScore,
        Boolean manualScoringRequired,
        Map<String, String> metadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delivery(
        String id,
        String code,
        String name,
        String assessmentId,
        String state,
        Integer studentsStarted,
        Integer studentsFinished
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Session(
        String id,
        String code,
        String deliveryId,
        String assessmentId,
        SessionOptions options,
        String identification,
        String sessionState
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionOptions(String firstname, String lastname) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemStatistics(
        String itemId,
        String itemTitle,
        String interactionType,
        Double maxScore,
        List<UniqueResponse> responses
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("itemIdentifier")
        public String itemIdentifier() {
            return itemId;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UniqueResponse(
        String id,
        String responseIdentifier,
        String value,
        int count,
        List<String> sessionIds,
        List<String> responseIds,
        Boolean confirmed,
        Integer scoreAI,
        Integer teacherScore,
        Integer effectiveAiScore,
        Double scoreExternal,
        String scoreSource,
        Boolean isEmptyResponse,
        String explanationAI,
        String issueLabel,
        Integer sortIndex,
        String familyId,
        Boolean flagged,
        String flagComment
    ) {
        public UniqueResponse withOrdering(Integer sortIndex, String familyId) {
            return new UniqueResponse(
                id, responseIdentifier, value, count, sessionIds, responseIds,
                confirmed, scoreAI, teacherScore, effectiveAiScore, scoreExternal, scoreSource,
                isEmptyResponse, explanationAI, issueLabel, sortIndex, familyId, flagged, flagComment);
        }

        public UniqueResponse withManualState(
            Boolean confirmed,
            Integer teacherScore,
            Integer effectiveAiScore,
            Double scoreExternal,
            String scoreSource
        ) {
            return new UniqueResponse(
                id, responseIdentifier, value, count, sessionIds, responseIds,
                confirmed, scoreAI, teacherScore, effectiveAiScore, scoreExternal, scoreSource,
                isEmptyResponse, explanationAI, issueLabel, sortIndex, familyId, flagged, flagComment);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoringDefinition(
        String itemIdentifier,
        Double maxScore,
        String question,
        String answerModel,
        JsonNode criteria
    ) {}
}

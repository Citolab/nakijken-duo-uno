package nl.cito.duonakijken.ordering;

import nl.cito.duonakijken.ordering.OrderingModels.OrderedUniqueResponse;
import nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingOptions;
import nl.cito.duonakijken.ordering.OrderingModels.UniqueResponseInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseScanOrderingServiceTest {
    private final ResponseScanOrderingService service = new ResponseScanOrderingService();

    private static final String ANSWER_MODEL = "glucose en zuurstof";

    private static List<UniqueResponseInput> sampleResponses() {
        return List.of(
            new UniqueResponseInput("u1", "glucose en zuurstof", 5),
            new UniqueResponseInput("u2", "Glucose en zuurstof.", 3),
            new UniqueResponseInput("u3", "zuurstof en glucose", 2),
            new UniqueResponseInput("u4", "water en koolstofdioxide", 2),
            new UniqueResponseInput("u5", "weet ik niet", 1),
            new UniqueResponseInput("u6", "???", 1),
            new UniqueResponseInput("u7", "", 1)
        );
    }

    @Test
    void everyInputAppearsExactlyOnceInTheOutput() {
        var result = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        assertThat(result.orderedResponses())
            .extracting(entry -> entry.response().uniqueId())
            .containsExactlyInAnyOrder("u1", "u2", "u3", "u4", "u5", "u6", "u7");
    }

    @Test
    void sortIndexesAreContiguousFromZero() {
        var result = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        assertThat(result.orderedResponses())
            .extracting(OrderedUniqueResponse::sortIndex)
            .containsExactlyElementsOf(IntStream.range(0, 7).boxed().toList());
    }

    @Test
    void orderingIsDeterministic() {
        var first = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);
        var second = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        assertThat(second.orderedResponses()).isEqualTo(first.orderedResponses());
        assertThat(second.families()).isEqualTo(first.families());
    }

    @Test
    void orderingIsDeterministicRegardlessOfInputOrder() {
        var reversed = sampleResponses().reversed();

        var first = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);
        var second = service.orderResponses(reversed, ANSWER_MODEL, null);

        assertThat(second.orderedResponses())
            .extracting(entry -> entry.response().uniqueId())
            .containsExactlyElementsOf(
                first.orderedResponses().stream().map(entry -> entry.response().uniqueId()).toList());
    }

    @Test
    void nearIdenticalResponsesShareAFamily() {
        var result = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        var familyOfU1 = familyOf(result.orderedResponses(), "u1");
        var familyOfU2 = familyOf(result.orderedResponses(), "u2");

        assertThat(familyOfU2).isEqualTo(familyOfU1);
    }

    @Test
    void junkResponsesAreOrderedLastInJunkFamilies() {
        var result = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        var ordered = result.orderedResponses();
        var junkEntries = ordered.stream().filter(OrderedUniqueResponse::junk).toList();

        assertThat(junkEntries)
            .extracting(entry -> entry.response().uniqueId())
            .containsExactlyInAnyOrder("u6", "u7");
        assertThat(junkEntries)
            .allSatisfy(entry -> assertThat(entry.familyId()).startsWith("junk-"));
        // Junk must come after all non-junk entries.
        var firstJunkIndex = ordered.stream().filter(OrderedUniqueResponse::junk)
            .mapToInt(OrderedUniqueResponse::sortIndex).min().orElseThrow();
        var lastNonJunkIndex = ordered.stream().filter(entry -> !entry.junk())
            .mapToInt(OrderedUniqueResponse::sortIndex).max().orElseThrow();
        assertThat(firstJunkIndex).isGreaterThan(lastNonJunkIndex);
    }

    @Test
    void diagnosticsCountJunkAndFamilies() {
        var result = service.orderResponses(sampleResponses(), ANSWER_MODEL, null);

        assertThat(result.diagnostics().junkCount()).isEqualTo(2);
        assertThat(result.diagnostics().nonJunkCount()).isEqualTo(5);
        assertThat(result.diagnostics().familyCount()).isEqualTo(result.families().size());
    }

    @Test
    void handlesEmptyInput() {
        var result = service.orderResponses(List.of(), ANSWER_MODEL, null);

        assertThat(result.orderedResponses()).isEmpty();
        assertThat(result.families()).isEmpty();
    }

    @Test
    void handlesMissingAnswerModel() {
        var result = service.orderResponses(sampleResponses(), null, ResponseScanOrderingOptions.defaults());

        assertThat(result.orderedResponses()).hasSize(7);
    }

    private static String familyOf(List<OrderedUniqueResponse> ordered, String uniqueId) {
        return ordered.stream()
            .filter(entry -> entry.response().uniqueId().equals(uniqueId))
            .findFirst()
            .orElseThrow()
            .familyId();
    }
}

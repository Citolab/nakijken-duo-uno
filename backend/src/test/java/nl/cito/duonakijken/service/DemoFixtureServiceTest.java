package nl.cito.duonakijken.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.cito.duonakijken.model.DemoModels.UniqueResponse;
import nl.cito.duonakijken.ordering.ResponseScanOrderingService;
import nl.cito.duonakijken.service.DemoFixtureService.TeacherScoreEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoFixtureServiceTest {

    private static final String FIXTURE_JSON = """
        {
          "fixtureVersion": 1,
          "assessment": {"id": "demo", "name": "Demo", "items": []},
          "delivery": {"id": "ZSVY", "code": "ZSVY", "name": "Demo delivery"},
          "sessions": [],
          "itemStatistics": [
            {
              "itemId": "item-1",
              "itemTitle": "Item 1",
              "interactionType": "extendedTextEntry",
              "responses": [
                {"id": "u1", "value": "glucose en zuurstof", "count": 3,
                 "responseIds": ["r1", "r2", "r3"], "scoreAI": 2},
                {"id": "u2", "value": "water", "count": 1, "responseIds": ["r4"]}
              ]
            }
          ],
          "scoringDefinitions": [
            {"itemIdentifier": "item-1", "maxScore": 2.0, "answerModel": "glucose en zuurstof"}
          ]
        }
        """;

    @TempDir
    Path tempDir;

    private Path scoresPath;

    @BeforeEach
    void setUp() {
        scoresPath = tempDir.resolve("manual-scores.json");
    }

    private DemoFixtureService newService() throws IOException {
        var service = new DemoFixtureService(
            new ObjectMapper(),
            new ResponseScanOrderingService(),
            new ByteArrayResource(FIXTURE_JSON.getBytes(StandardCharsets.UTF_8)),
            scoresPath.toString()
        );
        service.loadFixture();
        return service;
    }

    private UniqueResponse responseById(DemoFixtureService service, String uniqueId, String sortingMode) {
        return service.itemStatistics("ZSVY", sortingMode).stream()
            .flatMap(item -> item.responses().stream())
            .filter(response -> response.id().equals(uniqueId))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void teacherScoreIsAppliedAndPersisted() throws IOException {
        var service = newService();
        service.applyTeacherScores(List.of(new TeacherScoreEntry("u2", 1)));

        assertThat(responseById(service, "u2", "scan").teacherScore()).isEqualTo(1);
        assertThat(scoresPath).exists();
    }

    @Test
    void scoresSurviveARestart() throws IOException {
        newService().applyTeacherScores(List.of(new TeacherScoreEntry("u2", 1)));

        var reloaded = newService();
        assertThat(responseById(reloaded, "u2", "scan").teacherScore()).isEqualTo(1);
    }

    @Test
    void memberResponseIdResolvesToUniqueResponse() throws IOException {
        var service = newService();
        // r2 is one of u1's member response ids.
        service.applyTeacherScores(List.of(new TeacherScoreEntry("r2", 2)));

        assertThat(responseById(service, "u1", "scan").teacherScore()).isEqualTo(2);
    }

    @Test
    void confirmingWithoutTeacherScoreStoresTheAiScore() throws IOException {
        var service = newService();
        service.confirmResponses(List.of("u1"));

        var confirmed = responseById(service, "u1", "scan");
        assertThat(confirmed.confirmed()).isTrue();
        assertThat(confirmed.scoreExternal()).isEqualTo(2.0);
        assertThat(confirmed.scoreSource()).isEqualTo("Manual");
    }

    @Test
    void confirmingUsesTeacherScoreWhenPresent() throws IOException {
        var service = newService();
        service.applyTeacherScores(List.of(new TeacherScoreEntry("u1", 1)));
        service.confirmResponses(List.of("u1"));

        var confirmed = responseById(service, "u1", "scan");
        assertThat(confirmed.confirmed()).isTrue();
        assertThat(confirmed.scoreExternal()).isEqualTo(1.0);
    }

    @Test
    void unconfirmKeepsTeacherScoreButClearsConfirmation() throws IOException {
        var service = newService();
        service.applyTeacherScores(List.of(new TeacherScoreEntry("u1", 1)));
        service.confirmResponses(List.of("u1"));
        service.unconfirmResponses(List.of("u1"));

        var response = responseById(service, "u1", "scan");
        assertThat(response.confirmed()).isFalse();
        assertThat(response.teacherScore()).isEqualTo(1);
    }

    @Test
    void negativeTeacherScoreIsRejected() throws IOException {
        var service = newService();

        assertThatThrownBy(() -> service.applyTeacherScores(List.of(new TeacherScoreEntry("u1", -1))))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void teacherScoreAboveMaxScoreIsRejected() throws IOException {
        var service = newService();

        assertThatThrownBy(() -> service.applyTeacherScores(List.of(new TeacherScoreEntry("u1", 3))))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void corruptScoresFileDoesNotPreventStartup() throws IOException {
        Files.writeString(scoresPath, "{not valid json");

        assertThatCode(this::newService).doesNotThrowAnyException();
        assertThat(responseById(newService(), "u1", "scan").teacherScore()).isNull();
    }

    @Test
    void clearAllManualScoresRemovesStateAndFile() throws IOException {
        var service = newService();
        service.applyTeacherScores(List.of(new TeacherScoreEntry("u1", 1)));
        service.clearAllManualScores();

        assertThat(responseById(service, "u1", "scan").teacherScore()).isNull();
        assertThat(scoresPath).doesNotExist();
    }

    @Test
    void scanOrderingEnrichesResponsesWithSortIndexAndFamily() throws IOException {
        var service = newService();
        var responses = service.itemStatistics("ZSVY", "scan").getFirst().responses();

        assertThat(responses).extracting(UniqueResponse::sortIndex).doesNotContainNull();
        assertThat(responses).extracting(UniqueResponse::familyId).doesNotContainNull();
    }

    @Test
    void gradingModeSortsByEffectiveScoreDescending() throws IOException {
        var service = newService();
        service.applyTeacherScores(List.of(new TeacherScoreEntry("u2", 2)));

        var responses = service.itemStatistics("ZSVY", "grading").getFirst().responses();
        // u1 has AI score 2 and count 3; u2 now has teacher score 2 but count 1.
        assertThat(responses.getFirst().id()).isEqualTo("u1");
    }

    @Test
    void unknownDeliveryReturnsEmptyStatistics() throws IOException {
        assertThat(newService().itemStatistics("nope", "scan")).isEmpty();
    }
}

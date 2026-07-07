package nl.cito.duonakijken.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import nl.cito.duonakijken.model.DemoModels.Assessment;
import nl.cito.duonakijken.model.DemoModels.Delivery;
import nl.cito.duonakijken.model.DemoModels.DemoFixture;
import nl.cito.duonakijken.model.DemoModels.ItemStatistics;
import nl.cito.duonakijken.model.DemoModels.ScoringDefinition;
import nl.cito.duonakijken.model.DemoModels.Session;
import nl.cito.duonakijken.model.DemoModels.UniqueResponse;
import nl.cito.duonakijken.ordering.OrderingModels.ResponseScanOrderingOptions;
import nl.cito.duonakijken.ordering.OrderingModels.UniqueResponseInput;
import nl.cito.duonakijken.ordering.ResponseScanOrderingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoFixtureService {
    private static final Logger log = LoggerFactory.getLogger(DemoFixtureService.class);

    private final ObjectMapper objectMapper;
    private final ResponseScanOrderingService orderingService;
    private final Resource fixtureResource;
    private final Path manualScoresPath;

    private DemoFixture fixture;
    private final Map<String, ManualScoreState> manualScores = new ConcurrentHashMap<>();
    private final Object persistLock = new Object();

    private Map<String, String> uniqueIdByResponseId = Map.of();
    private Map<String, UniqueResponse> responseByUniqueId = Map.of();
    private Map<String, Integer> maxScoreByUniqueId = Map.of();
    private final Map<String, List<UniqueResponse>> scanOrderCache = new ConcurrentHashMap<>();

    public DemoFixtureService(
        ObjectMapper objectMapper,
        ResponseScanOrderingService orderingService,
        @Value("${demo.fixture-path}") Resource fixtureResource,
        @Value("${demo.manual-scores-path:demo-manual-scores.json}") String manualScoresPath
    ) {
        this.objectMapper = objectMapper;
        this.orderingService = orderingService;
        this.fixtureResource = fixtureResource;
        this.manualScoresPath = Path.of(manualScoresPath);
    }

    @PostConstruct
    void loadFixture() throws IOException {
        fixture = objectMapper.readValue(fixtureResource.getInputStream(), DemoFixture.class);
        buildResponseIndexes();
        loadManualScores();
    }

    private void buildResponseIndexes() {
        var byResponseId = new HashMap<String, String>();
        var byUniqueId = new HashMap<String, UniqueResponse>();
        var maxScores = new HashMap<String, Integer>();
        for (var item : fixture.itemStatistics() == null ? List.<ItemStatistics>of() : fixture.itemStatistics()) {
            if (item.responses() == null) {
                continue;
            }
            var maxScore = resolveMaxScore(item);
            for (var response : item.responses()) {
                byUniqueId.put(response.id(), response);
                byResponseId.put(response.id(), response.id());
                if (maxScore != null) {
                    maxScores.put(response.id(), maxScore);
                }
                for (var responseId : response.responseIds() == null ? List.<String>of() : response.responseIds()) {
                    byResponseId.put(responseId, response.id());
                }
            }
        }
        uniqueIdByResponseId = Map.copyOf(byResponseId);
        responseByUniqueId = Map.copyOf(byUniqueId);
        maxScoreByUniqueId = Map.copyOf(maxScores);
    }

    private Integer resolveMaxScore(ItemStatistics item) {
        var fromDefinition = scoringDefinition(item.itemIdentifier())
            .map(ScoringDefinition::maxScore)
            .orElse(null);
        var fromAssessment = fixture.assessment() == null || fixture.assessment().items() == null
            ? null
            : fixture.assessment().items().stream()
                .filter(info -> item.itemIdentifier().equals(info.identifier()))
                .map(info -> info.maxScore())
                .filter(score -> score != null)
                .findFirst()
                .orElse(null);
        var maxScore = fromDefinition != null ? fromDefinition
            : fromAssessment != null ? fromAssessment
            : item.maxScore();
        return maxScore == null ? null : (int) Math.round(maxScore);
    }

    private void loadManualScores() {
        if (!Files.exists(manualScoresPath)) {
            return;
        }
        try {
            var stored = objectMapper.readValue(
                Files.readString(manualScoresPath),
                new TypeReference<Map<String, ManualScoreState>>() {}
            );
            manualScores.clear();
            manualScores.putAll(stored);
        } catch (IOException exception) {
            // A corrupt state file must not prevent startup; demo state is expendable.
            log.warn("Could not read manual scores from {}; starting with empty scores", manualScoresPath, exception);
        }
    }

    private void persistManualScores() {
        synchronized (persistLock) {
            try {
                var target = manualScoresPath.toAbsolutePath();
                var parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                var temp = Files.createTempFile(parent, "manual-scores-", ".tmp");
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), Map.copyOf(manualScores));
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to persist manual scores to " + manualScoresPath, exception);
            }
        }
    }

    private String resolveUniqueResponseId(String responseId) {
        return uniqueIdByResponseId.getOrDefault(responseId, responseId);
    }

    public DemoFixture fixture() {
        return fixture;
    }

    public Optional<Assessment> assessment(String assessmentId) {
        if (fixture.assessment() != null && assessmentId.equals(fixture.assessment().id())) {
            return Optional.of(fixture.assessment());
        }
        return Optional.empty();
    }

    public Optional<Delivery> delivery(String deliveryId) {
        if (fixture.delivery() != null && deliveryId.equals(fixture.delivery().id())) {
            return Optional.of(fixture.delivery());
        }
        return Optional.empty();
    }

    public List<Session> sessionsForDelivery(String deliveryId) {
        return fixture.sessions().stream()
            .filter(session -> deliveryId.equals(session.deliveryId()))
            .toList();
    }

    public Optional<ScoringDefinition> scoringDefinition(String itemIdentifier) {
        if (fixture.scoringDefinitions() == null) {
            return Optional.empty();
        }
        return fixture.scoringDefinitions().stream()
            .filter(def -> itemIdentifier.equals(def.itemIdentifier()))
            .findFirst();
    }

    public List<ItemStatistics> itemStatistics(String deliveryId, String sortingMode) {
        if (fixture.delivery() == null || !deliveryId.equals(fixture.delivery().id())) {
            return List.of();
        }

        var stats = fixture.itemStatistics();
        if (stats == null) {
            return List.of();
        }

        return stats.stream()
            .map(item -> applyOrdering(item, sortingMode))
            .toList();
    }

    private ItemStatistics applyOrdering(ItemStatistics item, String sortingMode) {
        if (item.responses() == null || item.responses().isEmpty()) {
            return item;
        }

        if ("grading".equalsIgnoreCase(sortingMode)) {
            var sorted = item.responses().stream()
                .sorted(gradingComparator())
                .map(this::applyManualState)
                .toList();
            return withResponses(item, sorted);
        }

        // Scan ordering only depends on the immutable fixture, so it is computed once per item.
        var ordered = scanOrderCache.computeIfAbsent(item.itemId(), id -> orderForScan(item));
        var withState = ordered.stream()
            .map(this::applyManualState)
            .toList();
        return withResponses(item, withState);
    }

    private List<UniqueResponse> orderForScan(ItemStatistics item) {
        var answerModel = scoringDefinition(item.itemIdentifier())
            .map(ScoringDefinition::answerModel)
            .orElse(null);

        var originalById = new HashMap<String, UniqueResponse>(item.responses().size());
        for (var response : item.responses()) {
            originalById.put(response.id(), response);
        }

        var inputs = item.responses().stream()
            .map(response -> new UniqueResponseInput(
                response.id(),
                response.value(),
                Math.max(1, response.count())
            ))
            .toList();

        var ordered = orderingService.orderResponses(
            inputs,
            answerModel,
            ResponseScanOrderingOptions.defaults()
        );

        var reordered = new ArrayList<UniqueResponse>(item.responses().size());
        for (var entry : ordered.orderedResponses()) {
            var original = originalById.get(entry.response().uniqueId());
            reordered.add(original.withOrdering(entry.sortIndex(), entry.familyId()));
        }
        return List.copyOf(reordered);
    }

    private ItemStatistics withResponses(ItemStatistics item, List<UniqueResponse> responses) {
        return new ItemStatistics(
            item.itemId(), item.itemTitle(),
            item.interactionType(), item.maxScore(), responses
        );
    }

    private UniqueResponse applyManualState(UniqueResponse response) {
        var state = manualScores.get(response.id());
        if (state == null) {
            return response;
        }

        Double scoreExternal = response.scoreExternal();
        if (state.confirmed()) {
            scoreExternal = state.score() != null ? state.score().doubleValue() : null;
        }

        return response.withManualState(
            state.confirmed(),
            state.teacherScore(),
            state.teacherScore() != null ? state.teacherScore() : response.effectiveAiScore(),
            scoreExternal,
            state.confirmed() ? "Manual" : response.scoreSource()
        );
    }

    private Comparator<UniqueResponse> gradingComparator() {
        return Comparator
            .comparing((UniqueResponse r) -> effectiveScore(r), Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(r -> r.count(), Comparator.reverseOrder())
            .thenComparing(r -> r.value() == null ? "" : r.value());
    }

    private Integer effectiveScore(UniqueResponse response) {
        var state = manualScores.get(response.id());
        if (state != null && state.teacherScore() != null) {
            return state.teacherScore();
        }
        return effectiveFixtureScore(response);
    }

    private Integer effectiveFixtureScore(UniqueResponse response) {
        if (response == null) {
            return null;
        }
        if (response.teacherScore() != null) {
            return response.teacherScore();
        }
        if (response.effectiveAiScore() != null) {
            return response.effectiveAiScore();
        }
        return response.scoreAI();
    }

    public void applyTeacherScores(List<TeacherScoreEntry> entries) {
        for (var entry : entries) {
            var uniqueId = resolveUniqueResponseId(entry.responseId());
            validateScore(uniqueId, entry.teacherScore());
            manualScores.compute(uniqueId, (id, existing) -> {
                var base = existing != null ? existing : ManualScoreState.empty(id);
                return base.withTeacherScore(entry.teacherScore());
            });
        }
        persistManualScores();
    }

    private void validateScore(String uniqueId, Integer score) {
        if (score == null) {
            return;
        }
        if (score < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teacherScore must not be negative");
        }
        var maxScore = maxScoreByUniqueId.get(uniqueId);
        if (maxScore != null && score > maxScore) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "teacherScore " + score + " exceeds max score " + maxScore + " for response " + uniqueId
            );
        }
    }

    public void confirmResponses(List<String> responseIds) {
        for (var responseId : responseIds) {
            var uniqueId = resolveUniqueResponseId(responseId);
            manualScores.compute(uniqueId, (id, existing) -> {
                var base = existing != null ? existing : ManualScoreState.empty(id);
                // Confirming without an explicit teacher score accepts the score shown in
                // the UI, which falls back to the AI score from the fixture.
                var score = base.teacherScore() != null
                    ? base.teacherScore()
                    : effectiveFixtureScore(responseByUniqueId.get(id));
                return base.withConfirmed(true, score);
            });
        }
        persistManualScores();
    }

    public void unconfirmResponses(List<String> responseIds) {
        for (var responseId : responseIds) {
            var uniqueId = resolveUniqueResponseId(responseId);
            manualScores.computeIfPresent(uniqueId, (id, existing) -> existing.withConfirmed(false, null));
        }
        persistManualScores();
    }

    public void clearAllManualScores() {
        synchronized (persistLock) {
            manualScores.clear();
            try {
                Files.deleteIfExists(manualScoresPath);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to delete manual scores from " + manualScoresPath, exception);
            }
        }
    }

    public record TeacherScoreEntry(String responseId, Integer teacherScore) {}

    private record ManualScoreState(
        String responseId,
        Integer teacherScore,
        Integer score,
        boolean confirmed
    ) {
        static ManualScoreState empty(String responseId) {
            return new ManualScoreState(responseId, null, null, false);
        }

        ManualScoreState withTeacherScore(Integer teacherScore) {
            return new ManualScoreState(responseId, teacherScore, teacherScore, confirmed);
        }

        ManualScoreState withConfirmed(boolean confirmed, Integer score) {
            return new ManualScoreState(responseId, teacherScore, score, confirmed);
        }
    }
}

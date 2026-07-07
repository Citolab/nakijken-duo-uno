package nl.cito.duonakijken.controller;

import nl.cito.duonakijken.model.DemoModels.Assessment;
import nl.cito.duonakijken.model.DemoModels.Delivery;
import nl.cito.duonakijken.model.DemoModels.ItemStatistics;
import nl.cito.duonakijken.model.DemoModels.ScoringDefinition;
import nl.cito.duonakijken.model.DemoModels.Session;
import nl.cito.duonakijken.service.DemoFixtureService;
import nl.cito.duonakijken.service.DemoFixtureService.TeacherScoreEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final DemoFixtureService fixtureService;

    public ReviewController(DemoFixtureService fixtureService) {
        this.fixtureService = fixtureService;
    }

    @GetMapping("/assessments/{assessmentId}")
    public ResponseEntity<Assessment> getAssessment(@PathVariable String assessmentId) {
        return fixtureService.assessment(assessmentId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/deliveries/{deliveryId}")
    public ResponseEntity<Delivery> getDelivery(@PathVariable String deliveryId) {
        return fixtureService.delivery(deliveryId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/deliveries/{deliveryId}/sessions")
    public List<Session> getSessions(@PathVariable String deliveryId) {
        return fixtureService.sessionsForDelivery(deliveryId);
    }

    @GetMapping("/deliveries/{deliveryId}/item-stats")
    public ItemStatsResponse getItemStats(
        @PathVariable String deliveryId,
        @RequestParam(defaultValue = "scan") String sortingMode
    ) {
        return new ItemStatsResponse(fixtureService.itemStatistics(deliveryId, sortingMode));
    }

    @GetMapping("/assessments/{assessmentId}/items/{itemIdentifier}/scoring-definition")
    public ResponseEntity<ScoringDefinition> getScoringDefinition(
        @PathVariable String assessmentId,
        @PathVariable String itemIdentifier
    ) {
        return fixtureService.scoringDefinition(itemIdentifier)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/assessments/{assessmentId}/responses/confirm-scores")
    public SuccessResponse confirmScores(@RequestBody ConfirmRequest request) {
        fixtureService.confirmResponses(request.responseIds() == null ? List.of() : request.responseIds());
        return SuccessResponse.OK;
    }

    @PostMapping("/assessments/{assessmentId}/responses/unconfirm-scores")
    public SuccessResponse unconfirmScores(@RequestBody ConfirmRequest request) {
        fixtureService.unconfirmResponses(request.responseIds() == null ? List.of() : request.responseIds());
        return SuccessResponse.OK;
    }

    @PostMapping("/assessments/{assessmentId}/responses/clear-scores")
    public SuccessResponse clearScores(@PathVariable String assessmentId) {
        fixtureService.clearAllManualScores();
        return SuccessResponse.OK;
    }

    @PostMapping("/aigrading/teacher-score")
    public SuccessResponse teacherScore(@RequestBody TeacherScoreRequest request) {
        fixtureService.applyTeacherScores(request.entries() == null ? List.of() : request.entries());
        return SuccessResponse.OK;
    }

    public record ConfirmRequest(List<String> responseIds) {}

    public record TeacherScoreRequest(List<TeacherScoreEntry> entries) {}

    public record ItemStatsResponse(List<ItemStatistics> itemStatistics) {}

    public record SuccessResponse(boolean success) {
        static final SuccessResponse OK = new SuccessResponse(true);
    }
}

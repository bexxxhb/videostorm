package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.maintenance.application.port.in.DuplicateScanReports;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves a single duplicate scan run's groups on demand, so the run-result page never carries the
 * groups and members of every past scan. The drill-down layer fetches these for the run it opens.
 * Returns a JSON array of groups; 404 when the run is unknown, {@code []} when it found no duplicates.
 *
 * <p>Part of the gated maintenance area — registered only in maintenance mode, and reachable only by
 * an authenticated admin like the rest of {@code /maintenance}.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.operating.mode", havingValue = "maintenance")
public class DuplicateGroupsController {

    private final DuplicateScanReports duplicateScanReports;

    @GetMapping(value = "/maintenance/duplicate-scans/{runId}/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DuplicateGroupResponse>> groups(@PathVariable long runId) {
        return duplicateScanReports.findRun(runId)
                .map(run -> ResponseEntity.ok(run.groups().stream().map(DuplicateGroupResponse::from).toList()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

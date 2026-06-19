package com.example.dashboard.web;

import com.example.dashboard.dto.CreateRepoRequest;
import com.example.dashboard.dto.RepoWithLatestDto;
import com.example.dashboard.dto.SnapshotDto;
import com.example.dashboard.dto.SummaryDto;
import com.example.dashboard.model.TrackedRepo;
import com.example.dashboard.service.RepoService;
import com.example.dashboard.service.SummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final RepoService repoService;
    private final SummaryService summaryService;

    public RepoController(RepoService repoService, SummaryService summaryService) {
        this.repoService = repoService;
        this.summaryService = summaryService;
    }

    /**
     * GET /api/repos — list all repos with latest snapshot
     */
    @GetMapping
    public ResponseEntity<List<RepoWithLatestDto>> getAllRepos() {
        return ResponseEntity.ok(repoService.getAllReposWithLatest());
    }

    /**
     * GET /api/repos/{id}/history?days=N — historical snapshots
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<SnapshotDto>> getHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int days) {
        List<SnapshotDto> history = repoService.getRepoHistory(id, days);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /api/repos/{id}/summary — current stats + 7-day change
     */
    @GetMapping("/{id}/summary")
    public ResponseEntity<SummaryDto> getSummary(@PathVariable Long id) {
        SummaryDto summary = summaryService.getSummary(id);
        return ResponseEntity.ok(summary);
    }

    /**
     * POST /api/repos — add new repo
     */
    @PostMapping
    public ResponseEntity<TrackedRepo> addRepo(@RequestBody CreateRepoRequest request) {
        TrackedRepo repo = repoService.createRepo(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo);
    }

    /**
     * DELETE /api/repos/{id} — remove repo
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepo(@PathVariable Long id) {
        repoService.deleteRepo(id);
        return ResponseEntity.noContent().build();
    }
}
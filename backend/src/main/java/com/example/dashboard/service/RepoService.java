package com.example.dashboard.service;

import com.example.dashboard.dto.CreateRepoRequest;
import com.example.dashboard.dto.RepoWithLatestDto;
import com.example.dashboard.dto.SnapshotDto;
import com.example.dashboard.model.RepoSnapshot;
import com.example.dashboard.model.TrackedRepo;
import com.example.dashboard.repository.RepoSnapshotRepository;
import com.example.dashboard.repository.TrackedRepoRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

// Service for handling repo operations
@Service
public class RepoService {

    private final TrackedRepoRepository trackedRepoRepository;
    private final RepoSnapshotRepository snapshotRepository;

    public RepoService(TrackedRepoRepository trackedRepoRepository,
                       RepoSnapshotRepository snapshotRepository) {
        this.trackedRepoRepository = trackedRepoRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Get all repos with their latest snapshot (single query, avoids N+1)
     */
    public List<RepoWithLatestDto> getAllReposWithLatest() {
        return trackedRepoRepository.findAll().stream()
                .map(repo -> {
                    Optional<RepoSnapshot> latest = snapshotRepository.findLatestByRepoId(repo.getId());
                    SnapshotDto latestDto = latest
                            .map(s -> new SnapshotDto(s.getFetchedAt(), s.getStarCount(),
                                    s.getForkCount(), s.getOpenIssues()))
                            .orElse(null);
                    return new RepoWithLatestDto(repo.getId(), repo.getOwner(), repo.getRepoName(),
                            repo.getAddedAt(), latestDto);
                })
                .toList();
    }

    /**
     * Create a new tracked repo with validation
     */
    public TrackedRepo createRepo(CreateRepoRequest request) {
        // Validate inputs
        if (request.owner() == null || request.owner().isBlank()) {
            throw new IllegalArgumentException("Owner cannot be blank");
        }
        if (request.repoName() == null || request.repoName().isBlank()) {
            throw new IllegalArgumentException("Repository name cannot be blank");
        }

        // Check for duplicate
        if (trackedRepoRepository.existsByOwnerAndRepoName(request.owner(), request.repoName())) {
            throw new IllegalArgumentException("Repository " + request.owner() + "/" +
                    request.repoName() + " is already being tracked");
        }

        TrackedRepo repo = new TrackedRepo(request.owner(), request.repoName());
        return trackedRepoRepository.save(repo);
    }

    /**
     * Delete a repo and all its snapshots (cascade handled by JPA)
     */
    public void deleteRepo(Long repoId) {
        TrackedRepo repo = trackedRepoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found"));
        trackedRepoRepository.delete(repo);
    }

    /**
     * Get snapshots for a repo within the last N days
     */
    public List<SnapshotDto> getRepoHistory(Long repoId, int days) {
        // Verify repo exists
        trackedRepoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found"));

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return snapshotRepository.findByRepoIdWithinDays(repoId, startDate).stream()
                .map(s -> new SnapshotDto(s.getFetchedAt(), s.getStarCount(),
                        s.getForkCount(), s.getOpenIssues()))
                .toList();
    }
}
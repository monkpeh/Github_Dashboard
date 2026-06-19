package com.example.dashboard.service;

import com.example.dashboard.dto.ChangeDto;
import com.example.dashboard.dto.SnapshotDto;
import com.example.dashboard.dto.SummaryDto;
import com.example.dashboard.model.RepoSnapshot;
import com.example.dashboard.repository.RepoSnapshotRepository;
import com.example.dashboard.repository.TrackedRepoRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SummaryService {

    private final TrackedRepoRepository trackedRepoRepository;
    private final RepoSnapshotRepository snapshotRepository;

    public SummaryService(TrackedRepoRepository trackedRepoRepository,
                          RepoSnapshotRepository snapshotRepository) {
        this.trackedRepoRepository = trackedRepoRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Get current stats + 7-day percentage change
     */
    public SummaryDto getSummary(Long repoId) {
        // Verify repo exists
        trackedRepoRepository.findById(repoId)
                .orElseThrow(() -> new NoSuchElementException("Repository not found"));

        // Get most recent snapshot
        Optional<RepoSnapshot> current = snapshotRepository.findLatestByRepoId(repoId);
        if (current.isEmpty()) {
            throw new NoSuchElementException("No snapshots found for repository");
        }

        RepoSnapshot currentSnapshot = current.get();
        SnapshotDto currentDto = new SnapshotDto(
                currentSnapshot.getFetchedAt(),
                currentSnapshot.getStarCount(),
                currentSnapshot.getForkCount(),
                currentSnapshot.getOpenIssues()
        );

        // Find snapshot closest to 7 days ago
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        Optional<RepoSnapshot> baseline = snapshotRepository.findClosestToDate(repoId, sevenDaysAgo);

        ChangeDto changeDto;
        if (baseline.isEmpty()) {
            // No 7-day-old data, return nulls
            changeDto = new ChangeDto(null, null, null);
        } else {
            RepoSnapshot baselineSnapshot = baseline.get();
            changeDto = new ChangeDto(
                    calculatePercentChange(baselineSnapshot.getStarCount(), currentSnapshot.getStarCount()),
                    calculatePercentChange(baselineSnapshot.getForkCount(), currentSnapshot.getForkCount()),
                    calculatePercentChange(baselineSnapshot.getOpenIssues(), currentSnapshot.getOpenIssues())
            );
        }

        return new SummaryDto(currentDto, changeDto);
    }

    /**
     * Calculate percentage change: ((new - old) / old) * 100
     * Returns null if old is 0 (avoid divide-by-zero)
     */
    private Double calculatePercentChange(Integer oldValue, Integer newValue) {
        if (oldValue == null || newValue == null || oldValue == 0) {
            return null;
        }
        return ((newValue - oldValue) / (double) oldValue) * 100.0;
    }
}
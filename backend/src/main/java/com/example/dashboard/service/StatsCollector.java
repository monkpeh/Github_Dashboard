package com.example.dashboard.service;

import com.example.dashboard.dto.GitHubRepoResponse;
import com.example.dashboard.model.RepoSnapshot;
import com.example.dashboard.model.TrackedRepo;
import com.example.dashboard.repository.RepoSnapshotRepository;
import com.example.dashboard.repository.TrackedRepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StatsCollector {

    private static final Logger logger = LoggerFactory.getLogger(StatsCollector.class);

    private final TrackedRepoRepository trackedRepoRepository;
    private final RepoSnapshotRepository snapshotRepository;
    private final GitHubClient gitHubClient;

    public StatsCollector(TrackedRepoRepository trackedRepoRepository,
                          RepoSnapshotRepository snapshotRepository,
                          GitHubClient gitHubClient) {
        this.trackedRepoRepository = trackedRepoRepository;
        this.snapshotRepository = snapshotRepository;
        this.gitHubClient = gitHubClient;
    }

    /**
     * Scheduled job: runs every 60 minutes (3,600,000 ms)
     */
    @Scheduled(fixedRate = 3_600_000)
    public void collectStats() {
        logger.info("Starting scheduled stats collection");
        List<TrackedRepo> repos = trackedRepoRepository.findAll();

        for (TrackedRepo repo : repos) {
            try {
                logger.debug("Fetching stats for {}/{}", repo.getOwner(), repo.getRepoName());
                GitHubRepoResponse stats = gitHubClient.fetchRepoStats(repo.getOwner(), repo.getRepoName());

                // Create and save snapshot
                RepoSnapshot snapshot = new RepoSnapshot(
                        repo,
                        stats.starsCount(),
                        stats.forksCount(),
                        stats.openIssuesCount(),
                        stats.pushedAt()
                );
                snapshotRepository.save(snapshot);
                logger.info("Saved snapshot for {}/{}", repo.getOwner(), repo.getRepoName());

            } catch (Exception e) {
                logger.error("Error collecting stats for {}/{}: {}", repo.getOwner(), repo.getRepoName(), e.getMessage());
                // Continue to next repo (don't abort on single failure)
            }
        }

        logger.info("Stats collection cycle completed");
    }

    /**
     * Manual trigger for testing / startup
     */
    public void collectNow() {
        collectStats();
    }
}
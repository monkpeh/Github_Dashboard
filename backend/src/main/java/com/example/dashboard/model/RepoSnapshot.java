package com.example.dashboard.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repo_snapshot", indexes = {
        @Index(name = "idx_repo_fetched", columnList = "tracked_repo_id, fetched_at")
})
public class RepoSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracked_repo_id", nullable = false)
    private TrackedRepo trackedRepo;

    @Column(nullable = false)
    private Integer starCount;

    @Column(nullable = false)
    private Integer forkCount;

    @Column(nullable = false)
    private Integer openIssues;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "last_commit_date")
    private LocalDateTime lastCommitDate;

    // Constructors, getters, setters
    public RepoSnapshot() {}

    public RepoSnapshot(TrackedRepo trackedRepo, Integer starCount, Integer forkCount,
                        Integer openIssues, LocalDateTime lastCommitDate) {
        this.trackedRepo = trackedRepo;
        this.starCount = starCount;
        this.forkCount = forkCount;
        this.openIssues = openIssues;
        this.fetchedAt = LocalDateTime.now();
        this.lastCommitDate = lastCommitDate;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TrackedRepo getTrackedRepo() { return trackedRepo; }
    public void setTrackedRepo(TrackedRepo trackedRepo) { this.trackedRepo = trackedRepo; }

    public Integer getStarCount() { return starCount; }
    public void setStarCount(Integer starCount) { this.starCount = starCount; }

    public Integer getForkCount() { return forkCount; }
    public void setForkCount(Integer forkCount) { this.forkCount = forkCount; }

    public Integer getOpenIssues() { return openIssues; }
    public void setOpenIssues(Integer openIssues) { this.openIssues = openIssues; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }

    public LocalDateTime getLastCommitDate() { return lastCommitDate; }
    public void setLastCommitDate(LocalDateTime lastCommitDate) { this.lastCommitDate = lastCommitDate; }
}